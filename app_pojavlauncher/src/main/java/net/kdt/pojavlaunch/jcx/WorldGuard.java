package net.kdt.pojavlaunch.jcx;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * WORLD SAVE PROTECTION (sem dependências de Android, testável na JVM):
 * detecta mundos gravados numa sessão, confere o level.dat e mantém backups automáticos
 * com rotação — backups manuais nunca são apagados.
 */
public final class WorldGuard {
    private WorldGuard() {}

    public static final int KEEP_AUTO = 5;

    public static final class WorldState {
        public File dir;
        public String name;          // LevelName (ou nome da pasta)
        public long lastWrite;       // última gravação detectada (ms)
        public boolean levelOk;      // level.dat legível
        public String error;
        public boolean sessionLockLeft;
        public long dayTimeTicks = -1; // tempo total do mundo (ticks), quando disponível
        public String backupResult;  // preenchido após o backup automático
    }

    /** Última gravação real dentro do mundo (level.dat, region, playerdata). */
    public static long lastWrite(File world) {
        long t = 0;
        File[] probes = {new File(world, "level.dat"), new File(world, "region"), new File(world, "playerdata"), new File(world, "data")};
        for (File f : probes) if (f.exists()) t = Math.max(t, f.lastModified());
        File region = new File(world, "region");
        File[] mca = region.listFiles();
        if (mca != null) for (File f : mca) t = Math.max(t, f.lastModified());
        return t;
    }

    /** Mundos com gravação posterior a {@code sinceMs}. */
    public static List<WorldState> modifiedSince(File savesDir, long sinceMs) {
        List<WorldState> out = new ArrayList<>();
        File[] worlds = savesDir.listFiles(File::isDirectory);
        if (worlds == null) return out;
        for (File w : worlds) {
            long lw = lastWrite(w);
            if (lw < sinceMs) continue;
            out.add(inspect(w, lw));
        }
        return out;
    }

    public static WorldState inspect(File w, long lastWrite) {
        WorldState s = new WorldState();
        s.dir = w;
        s.name = w.getName();
        s.lastWrite = lastWrite;
        s.sessionLockLeft = new File(w, "session.lock").exists();
        try {
            Nbt.Root r = Nbt.readGzip(new File(w, "level.dat"));
            Nbt.Tag data = r.tag.get("Data");
            if (data == null) throw new IOException("level.dat sem bloco Data");
            Nbt.Tag n = data.get("LevelName");
            if (n != null && n.asString() != null && !n.asString().isEmpty()) s.name = n.asString();
            Nbt.Tag time = data.get("Time");
            if (time != null && time.asLong() != null) s.dayTimeTicks = time.asLong();
            s.levelOk = true;
        } catch (Exception e) {
            s.levelOk = false;
            s.error = e.getMessage();
        }
        return s;
    }

    /** Nome de backup automático: "Pasta__auto-yyyyMMdd-HHmmss.zip" (compatível com o World Manager). */
    public static String autoName(String folder, Date when) {
        return folder + "__auto-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(when) + ".zip";
    }

    /** Remove backups AUTOMÁTICOS antigos do mundo, mantendo os {@code keep} mais recentes. */
    public static int pruneAuto(File backupDir, String folder, int keep) {
        final String prefix = folder + "__auto-";
        File[] list = backupDir.listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(".zip"));
        if (list == null || list.length <= keep) return 0;
        Arrays.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) { return b.getName().compareTo(a.getName()); }
        });
        int removed = 0;
        for (int i = keep; i < list.length; i++) if (list[i].delete()) removed++;
        return removed;
    }

    /**
     * Backup automático seguro: só copia mundo íntegro (para não substituir uma cópia boa por uma
     * corrompida), confere espaço e aplica rotação.
     */
    public static String autoBackup(WorldState s, File backupDir, long freeBytes) {
        if (!s.levelOk) return "não feito: level.dat ilegível — backups anteriores foram mantidos";
        long size = JcxFiles.dirSize(s.dir);
        if (freeBytes >= 0 && freeBytes < size + size / 5 + 200L * 1024 * 1024)
            return "não feito: espaço insuficiente (" + JcxUi.size(size) + " necessários)";
        File out = new File(backupDir, autoName(s.dir.getName(), new Date()));
        try {
            JcxFiles.zipDir(s.dir, out);
            if (JcxFiles.findWorldPrefix(out) == null) throw new IOException("backup sem level.dat");
            int pruned = pruneAuto(backupDir, s.dir.getName(), KEEP_AUTO);
            return "feito: " + out.getName() + " (" + JcxUi.size(out.length()) + ")" + (pruned > 0 ? ", " + pruned + " antigo(s) removido(s)" : "");
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            new File(out.getAbsolutePath() + ".part").delete();
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return "falhou: " + e.getMessage();
        }
    }
}
