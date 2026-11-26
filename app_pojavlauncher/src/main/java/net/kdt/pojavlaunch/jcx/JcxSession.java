package net.kdt.pojavlaunch.jcx;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * SESSION MANAGER: registra o início de cada partida (quando o JOGAR realmente dispara a
 * inicialização) e detecta o fim quando o processo interno do jogo (":game") não existe mais.
 * Não interfere no jogo nem em regras da Mojang — só observa arquivos e processos do próprio app.
 */
public final class JcxSession {
    private static final String PREFS = "jcx_session";
    private JcxSession() {}

    public static final class Info {
        public long start, end;
        public String profileName, versionId, gameDir;
        public boolean demo, local;
    }

    /** Chamado pelo launcher logo antes de iniciar o jogo. */
    public static void onLaunch(Context c, MinecraftProfile prof, boolean demo, boolean local) {
        File gd;
        if (prof != null && prof.gameDir != null) gd = Tools.getGameDirPath(prof);
        else if (demo) gd = new File(Tools.DIR_DATA + "/demo/.minecraft"); // pasta própria da demo no motor
        else gd = new File(Tools.DIR_GAME_HOME + "/.minecraft");
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("active", true)
                .putLong("start", System.currentTimeMillis())
                .putString("profile", prof != null && prof.name != null ? prof.name : (prof != null ? prof.lastVersionId : "?"))
                .putString("version", prof != null ? prof.lastVersionId : "?")
                .putString("gameDir", gd.getAbsolutePath())
                .putBoolean("demo", demo)
                .putBoolean("local", local)
                .apply();
    }

    public static boolean isGameProcessRunning(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null) return false;
            for (ActivityManager.RunningAppProcessInfo p : procs)
                if (p.processName != null && p.processName.endsWith(":game")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Se havia uma sessão ativa e o jogo não está mais rodando, fecha a sessão e devolve os dados.
     * Inicializações que nem chegaram a rodar (ex.: sem conta) são descartadas em silêncio.
     */
    public static Info finishIfEnded(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("active", false)) return null;
        if (isGameProcessRunning(c)) return null;
        Info i = new Info();
        i.start = p.getLong("start", 0);
        i.profileName = p.getString("profile", "?");
        i.versionId = p.getString("version", "?");
        i.gameDir = p.getString("gameDir", Tools.DIR_GAME_NEW);
        i.demo = p.getBoolean("demo", false);
        i.local = p.getBoolean("local", false);
        File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        long logTime = log.isFile() ? log.lastModified() : 0;
        long worlds = WorldGuard.modifiedSince(new File(i.gameDir, "saves"), i.start).size();
        i.end = Math.max(logTime, i.start);
        p.edit().putBoolean("active", false).apply();
        // o jogo nunca chegou a iniciar: nada a relatar
        if (logTime < i.start && worlds == 0) return null;
        return i;
    }

    /** Guarda um resumo no histórico (últimas 20 sessões) em jcx/sessions.json. */
    public static void record(Info i, List<WorldGuard.WorldState> worlds) {
        try {
            File f = new File(JcxPaths.jcxHome(""), "sessions.json");
            JSONArray arr = f.isFile() ? new JSONArray(JcxFiles.readSmall(f)) : new JSONArray();
            JSONObject o = new JSONObject();
            o.put("start", i.start);
            o.put("end", i.end);
            o.put("profile", i.profileName);
            o.put("version", i.versionId);
            o.put("demo", i.demo);
            JSONArray ws = new JSONArray();
            for (WorldGuard.WorldState w : worlds) {
                JSONObject jw = new JSONObject();
                jw.put("folder", w.dir.getName());
                jw.put("name", w.name);
                jw.put("levelOk", w.levelOk);
                jw.put("gameDir", i.gameDir);
                ws.put(jw);
            }
            o.put("worlds", ws);
            arr.put(o);
            while (arr.length() > 20) arr.remove(0);
            JcxFiles.writeText(f, arr.toString(1));
        } catch (Exception ignored) {}
    }

    /** Último mundo jogado (para o cartão "Bem-vindo de volta"). {nome, pasta, gameDir, quando} ou null. */
    public static String[] lastWorld() {
        try {
            File f = new File(JcxPaths.jcxHome(""), "sessions.json");
            if (!f.isFile()) return null;
            JSONArray arr = new JSONArray(JcxFiles.readSmall(f));
            for (int k = arr.length() - 1; k >= 0; k--) {
                JSONObject o = arr.getJSONObject(k);
                JSONArray ws = o.optJSONArray("worlds");
                if (ws == null || ws.length() == 0) continue;
                JSONObject w = ws.getJSONObject(0);
                File dir = new File(new File(w.optString("gameDir"), "saves"), w.optString("folder"));
                if (!dir.isDirectory()) continue; // mundo foi removido: não inventa nada
                return new String[]{w.optString("name"), w.optString("folder"), w.optString("gameDir"), String.valueOf(o.optLong("end"))};
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean autoBackupEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("autoBackup", true);
    }

    public static void setAutoBackup(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("autoBackup", on).apply();
    }
}
