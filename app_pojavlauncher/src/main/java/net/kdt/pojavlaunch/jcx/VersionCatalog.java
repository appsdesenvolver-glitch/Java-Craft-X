package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UNIVERSAL VERSION HUB — catálogo dinâmico a partir do manifesto oficial da Mojang
 * (piston-meta version_manifest_v2). Nada é fixo no código: novas versões aparecem sozinhas.
 * Também mantém favoritos e a matriz de compatibilidade (resultados de teste registrados).
 */
public final class VersionCatalog {
    public static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String PREFS = "jcx_versions";

    public enum Compat { COMPATIBLE, LIMITED, UNSUPPORTED, UNKNOWN }

    public static final class Entry {
        public String id, type, url, time, releaseTime, sha1;
        public boolean inManifest = true;

        /** release, snapshot, pre-release, release-candidate, old_beta, old_alpha, experimental, modded */
        public String category() {
            String l = id.toLowerCase(Locale.ROOT);
            if (!inManifest) return "modded";
            if ("old_alpha".equals(type) || "old_beta".equals(type)) return type;
            if (l.contains("experimental") || l.contains("combat")) return "experimental";
            if (l.contains("-rc") || l.contains(" release candidate")) return "release-candidate";
            if (l.contains("-pre") || l.contains(" pre-release")) return "pre-release";
            return type;
        }
    }

    public static final class Catalog {
        public final List<Entry> entries = new ArrayList<>();
        public String latestRelease, latestSnapshot;
        public boolean fromCache;
        public List<String> newSinceLastVisit = new ArrayList<>();
    }

    private VersionCatalog() {}

    private static File cacheFile() { return new File(JcxPaths.jcxHome("cache"), "version_manifest_v2.json"); }

    /** Consulta o manifesto (rede) e cai para o cache local se estiver offline. */
    public static Catalog load(Context c) throws IOException {
        String json;
        boolean cached = false;
        try {
            json = JcxDownloader.getText(MANIFEST_URL);
            JcxFiles.writeText(cacheFile(), json);
        } catch (IOException e) {
            if (!cacheFile().isFile()) throw new IOException("Sem internet e sem catálogo salvo: " + e.getMessage());
            json = JcxFiles.readSmall(cacheFile());
            cached = true;
        }
        Catalog cat = parse(json);
        cat.fromCache = cached;
        addLocalOnly(cat);
        detectNew(c, cat);
        return cat;
    }

    static Catalog parse(String json) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            Catalog cat = new Catalog();
            JSONObject latest = o.optJSONObject("latest");
            if (latest != null) {
                cat.latestRelease = latest.optString("release", null);
                cat.latestSnapshot = latest.optString("snapshot", null);
            }
            JSONArray vs = o.getJSONArray("versions");
            for (int i = 0; i < vs.length(); i++) {
                JSONObject v = vs.getJSONObject(i);
                Entry e = new Entry();
                e.id = v.getString("id");
                e.type = v.optString("type");
                e.url = v.optString("url");
                e.time = v.optString("time");
                e.releaseTime = v.optString("releaseTime");
                e.sha1 = v.optString("sha1", null);
                cat.entries.add(e);
            }
            return cat;
        } catch (Exception e) {
            throw new IOException("Manifesto de versões inválido", e);
        }
    }

    /** Versões instaladas que não estão no manifesto (Fabric, Forge, OptiFine…). */
    private static void addLocalOnly(Catalog cat) {
        File[] dirs = new File(Tools.DIR_HOME_VERSION).listFiles(File::isDirectory);
        if (dirs == null) return;
        Set<String> known = new HashSet<>();
        for (Entry e : cat.entries) known.add(e.id);
        for (File d : dirs) {
            if (known.contains(d.getName())) continue;
            if (!new File(d, d.getName() + ".json").isFile()) continue;
            Entry e = new Entry();
            e.id = d.getName();
            e.type = "modded";
            e.inManifest = false;
            e.releaseTime = "";
            cat.entries.add(e);
        }
    }

    private static void detectNew(Context c, Catalog cat) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lr = p.getString("lastRelease", null);
        String ls = p.getString("lastSnapshot", null);
        if (lr != null && cat.latestRelease != null && !lr.equals(cat.latestRelease)) cat.newSinceLastVisit.add(cat.latestRelease);
        if (ls != null && cat.latestSnapshot != null && !ls.equals(cat.latestSnapshot) && !cat.latestSnapshot.equals(cat.latestRelease))
            cat.newSinceLastVisit.add(cat.latestSnapshot);
        p.edit().putString("lastRelease", cat.latestRelease).putString("lastSnapshot", cat.latestSnapshot).apply();
    }

    // ------------------------------------------------------------ instalação

    public static File versionDir(String id) { return new File(Tools.DIR_HOME_VERSION, id); }

    /** 0 = não instalada, 1 = parcial (só metadados), 2 = arquivos principais presentes. */
    public static int installState(String id) {
        File d = versionDir(id);
        File json = new File(d, id + ".json");
        File jar = new File(d, id + ".jar");
        if (!json.isFile() || json.length() == 0) return 0;
        if (jar.isFile() && jar.length() > 0) return 2;
        // versões de loader herdam o .jar da versão base
        try {
            JSONObject o = new JSONObject(JcxFiles.readSmall(json));
            String parent = o.optString("inheritsFrom", null);
            if (parent != null && !parent.isEmpty()) return installState(parent) == 2 ? 2 : 1;
        } catch (Exception ignored) {}
        return 1;
    }

    /** Detalhes vindos do JSON da versão (local se instalado; senão baixado e conferido por SHA-1). */
    public static final class Details {
        public int javaMajor = -1;
        public long clientSize = -1, assetsSize = -1;
        public int libraries = -1;
        public String assetIndex, mainClass, inheritsFrom;
    }

    public static Details details(Entry e) throws IOException {
        String text;
        File local = new File(versionDir(e.id), e.id + ".json");
        if (local.isFile() && local.length() > 0) {
            text = JcxFiles.readSmall(local);
        } else {
            if (e.url == null || e.url.isEmpty()) throw new IOException("Sem metadados para " + e.id);
            JcxDownloader.checkUrl(e.url, "mojang.com", "minecraft.net");
            text = JcxDownloader.getText(e.url);
            if (e.sha1 != null && !e.sha1.isEmpty()) {
                String h = sha1(text.getBytes(StandardCharsets.UTF_8));
                if (!h.equalsIgnoreCase(e.sha1)) throw new IOException("Metadados não conferem com o SHA-1 do manifesto");
            }
        }
        try {
            JSONObject o = new JSONObject(text);
            Details d = new Details();
            JSONObject jv = o.optJSONObject("javaVersion");
            if (jv != null) d.javaMajor = jv.optInt("majorVersion", -1);
            JSONObject dl = o.optJSONObject("downloads");
            if (dl != null && dl.optJSONObject("client") != null) d.clientSize = dl.optJSONObject("client").optLong("size", -1);
            JSONArray libs = o.optJSONArray("libraries");
            if (libs != null) d.libraries = libs.length();
            JSONObject ai = o.optJSONObject("assetIndex");
            if (ai != null) { d.assetIndex = ai.optString("id"); d.assetsSize = ai.optLong("totalSize", -1); }
            d.mainClass = o.optString("mainClass", null);
            d.inheritsFrom = o.optString("inheritsFrom", null);
            if (d.javaMajor < 0) d.javaMajor = 8; // versões antigas não declaram: rodam em Java 8
            return d;
        } catch (Exception ex) {
            throw new IOException("JSON da versão inválido", ex);
        }
    }

    private static String sha1(byte[] b) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format(Locale.ROOT, "%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------ favoritos e compatibilidade

    public static Set<String> favorites(Context c) {
        return new HashSet<>(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("favorites", new HashSet<String>()));
    }

    public static void toggleFavorite(Context c, String id) {
        Set<String> f = favorites(c);
        if (!f.remove(id)) f.add(id);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("favorites", f).apply();
    }

    /** Resultado de teste registrado pelo usuário neste aparelho: "COMPATIBLE|data|renderer|nota". */
    public static void recordTest(Context c, String id, Compat result, String renderer, String note) {
        String v = result.name() + "|" + System.currentTimeMillis() + "|" + renderer + "|" + (note == null ? "" : note.replace("|", "/"))
                + "|" + Build.MANUFACTURER + " " + Build.MODEL;
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("test_" + id, v).apply();
    }

    public static String[] testRecord(Context c, String id) {
        String v = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("test_" + id, null);
        return v == null ? null : v.split("\\|", -1);
    }

    public static final class Verdict {
        public Compat compat = Compat.UNKNOWN;
        public final List<String> notes = new ArrayList<>();
    }

    /**
     * Classificação honesta: COMPATIBLE/LIMITED/UNSUPPORTED só a partir de testes registrados
     * ou de impossibilidade técnica comprovada; o resto é UNKNOWN com observações.
     */
    public static Verdict classify(Context c, Entry e, Details d, int deviceRamMb) {
        Verdict v = new Verdict();
        String[] t = testRecord(c, e.id);
        if (t != null) {
            try { v.compat = Compat.valueOf(t[0]); } catch (Exception ignored) {}
            v.notes.add("Resultado registrado neste aparelho (" + (t.length > 4 ? t[4] : "") + ", renderizador " + (t.length > 2 ? JcxPaths.rendererLabel(t[2]) : "?") + ")"
                    + (t.length > 3 && !t[3].isEmpty() ? ": " + t[3] : ""));
        }
        if (d != null && d.javaMajor > 25) {
            v.compat = Compat.UNSUPPORTED;
            v.notes.add("Exige Java " + d.javaMajor + ": ainda não existe runtime Android para essa versão no motor.");
        } else if (d != null) {
            v.notes.add("Runtime: Java " + d.javaMajor + (d.javaMajor == 8 ? " (já incluído no app)" : " (baixado automaticamente na primeira execução)"));
        }
        String cat = e.category();
        if ("old_alpha".equals(cat) || "old_beta".equals(cat))
            v.notes.add("Versão histórica: pode ter problemas de entrada/janela no motor atual.");
        if (cat.contains("snapshot") || cat.contains("pre") || cat.contains("candidate") || "experimental".equals(cat))
            v.notes.add("Versão de teste da Mojang: instável por natureza; mods geralmente indisponíveis.");
        if (e.releaseTime != null && e.releaseTime.compareTo("2025-03-25") > 0 && !e.releaseTime.isEmpty())
            v.notes.add("1.21.5 ou mais nova: o renderizador Krypton (GL4ES) não atende; use MobileGlues.");
        if (deviceRamMb > 0 && deviceRamMb < 3072 && e.releaseTime != null && e.releaseTime.compareTo("2021-11-30") > 0)
            v.notes.add("Aparelho com " + deviceRamMb + " MB de RAM: 1.18+ tende a ficar pesado — use o modo ECO do X-PERFORMANCE.");
        if (t == null && v.compat == Compat.UNKNOWN) v.notes.add("Sem testes registrados: a compatibilidade ainda é desconhecida.");
        return v;
    }

    public static String label(Compat c) {
        switch (c) {
            case COMPATIBLE: return "COMPATIBLE";
            case LIMITED: return "LIMITED";
            case UNSUPPORTED: return "UNSUPPORTED";
            default: return "UNKNOWN";
        }
    }

    public static int color(Compat c) {
        switch (c) {
            case COMPATIBLE: return JcxUi.GOOD;
            case LIMITED: return JcxUi.WARN;
            case UNSUPPORTED: return JcxUi.BAD;
            default: return JcxUi.TEXT_DIM;
        }
    }

    public static String categoryLabel(String cat) {
        switch (cat) {
            case "release": return "Release";
            case "snapshot": return "Snapshot";
            case "pre-release": return "Pre-release";
            case "release-candidate": return "Release Candidate";
            case "old_beta": return "Beta (histórica)";
            case "old_alpha": return "Alpha (histórica)";
            case "experimental": return "Experimental";
            case "modded": return "Instalação com loader";
            default: return cat;
        }
    }
}
