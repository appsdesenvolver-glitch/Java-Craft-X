package net.kdt.pojavlaunch.jcx;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Estado do login guardado fora da pasta de contas (para não aparecer como conta na lista):
 *  - autorização pendente: quando o login passa da etapa Microsoft e falha depois (ex.: falta o perfil Xbox),
 *    a autorização (cifrada no Keystore) fica guardada para "Tentar de novo" sem digitar a senha;
 *  - último diagnóstico (sem tokens) e último login concluído.
 */
public final class JcxAuthStore {
    private JcxAuthStore() {}

    private static File dir() {
        File d = new File(Tools.DIR_DATA, "jcx_auth");
        if (!d.isDirectory()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    private static void write(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(f)) { //noinspection ResultOfMethodCallIgnored
            f.delete(); //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(f); }
    }

    private static String read(File f) {
        try { return f.isFile() ? JcxFiles.readSmall(f) : null; } catch (IOException e) { return null; }
    }

    public static synchronized void savePending(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("t", JcxVault.seal(refreshToken));
            o.put("at", System.currentTimeMillis());
            write(new File(dir(), "pending.json"), o.toString());
        } catch (Exception ignored) {}
    }

    /** Autorização pendente, se houver e tiver menos de 30 dias. */
    public static synchronized String pending() {
        String s = read(new File(dir(), "pending.json"));
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            if (System.currentTimeMillis() - o.optLong("at") > 30L * 86400000L) { clearPending(); return null; }
            return JcxVault.open(o.optString("t", null));
        } catch (Exception e) { return null; }
    }

    public static boolean hasPending() { return new File(dir(), "pending.json").isFile(); }

    public static synchronized void clearPending() {
        //noinspection ResultOfMethodCallIgnored
        new File(dir(), "pending.json").delete();
    }

    public static synchronized void saveLastError(String report) {
        try {
            JSONObject o = new JSONObject();
            o.put("at", System.currentTimeMillis());
            o.put("report", JcxAuthLog.sanitize(report));
            write(new File(dir(), "last_error.json"), o.toString());
        } catch (Exception ignored) {}
    }

    /** {at, report} ou null. */
    public static JSONObject lastError() {
        String s = read(new File(dir(), "last_error.json"));
        try { return s == null ? null : new JSONObject(s); } catch (Exception e) { return null; }
    }

    public static synchronized void recordSuccess(String username, JcxOwnership.Kind kind) {
        try {
            JSONObject o = new JSONObject();
            o.put("at", System.currentTimeMillis());
            o.put("user", username);
            o.put("kind", kind.name());
            write(new File(dir(), "last_ok.json"), o.toString());
            //noinspection ResultOfMethodCallIgnored
            new File(dir(), "last_error.json").delete();
        } catch (Exception ignored) {}
    }

    public static JSONObject lastSuccess() {
        String s = read(new File(dir(), "last_ok.json"));
        try { return s == null ? null : new JSONObject(s); } catch (Exception e) { return null; }
    }
}
