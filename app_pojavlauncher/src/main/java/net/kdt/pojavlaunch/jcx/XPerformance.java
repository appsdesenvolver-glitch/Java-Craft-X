package net.kdt.pojavlaunch.jcx;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * X-PERFORMANCE: lógica (sem UI) dos perfis de desempenho.
 * Altera somente: heap Java e escala de resolução do launcher, e o options.txt do perfil.
 * Tudo é reversível (cópia original guardada antes da primeira alteração).
 */
public final class XPerformance {
    private XPerformance() {}

    public static final String MODE_ECO = "ECO";
    public static final String MODE_BALANCED = "BALANCED";
    public static final String MODE_HIGH = "HIGH PERFORMANCE";
    public static final String MODE_CUSTOM = "CUSTOM";

    private static final String BACKUP_PREFS = "jcx_perf_backup";

    public static final class Settings {
        public int heapMb;
        public int resolutionPercent;
        public int renderDistance;
        public int simulationDistance;
        public int maxFps;          // 260 = ilimitado no Minecraft
        public boolean vsync;
        public int particles;       // 0 = todas, 1 = reduzidas, 2 = mínimas
        public boolean fancy;
        public boolean entityShadows;
        public int mipmap;
        public int biomeBlend;
        public String clouds;       // "false", "fast", "true"
    }

    public static final class DeviceInfo {
        public int totalRamMb, availRamMb, thresholdMb;
        public boolean lowMemory, is32Bit;
        public int safeMaxHeapMb, recommendedHeapMb, currentHeapMb, currentResolution;
        public float batteryTempC = Float.NaN;
        public long freeStorage;
    }

    public static DeviceInfo device(Context c) {
        DeviceInfo d = new DeviceInfo();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        d.totalRamMb = (int) (mi.totalMem / 1048576L);
        d.availRamMb = (int) (mi.availMem / 1048576L);
        d.thresholdMb = (int) (mi.threshold / 1048576L);
        d.lowMemory = mi.lowMemory;
        d.is32Bit = Architecture.is32BitsDevice();
        d.safeMaxHeapMb = safeMaxHeap(d.totalRamMb, d.is32Bit);
        d.recommendedHeapMb = Math.min(recommendedHeap(d.totalRamMb, d.is32Bit), d.safeMaxHeapMb);
        d.currentHeapMb = LauncherPreferences.PREF_RAM_ALLOCATION;
        d.currentResolution = Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f);
        d.batteryTempC = batteryTemp(c);
        d.freeStorage = JcxPaths.freeBytes(new File(Tools.DIR_GAME_HOME));
        return d;
    }

    /** Mesmo limite do launcher: deixa memória para o sistema "respirar". */
    public static int safeMaxHeap(int deviceRam, boolean is32) {
        if (is32 || deviceRam < 2048) return Math.min(1024, deviceRam);
        return deviceRam - (deviceRam < 3064 ? 800 : 1024);
    }

    public static int recommendedHeap(int deviceRam, boolean is32) {
        if (deviceRam < 1024) return 296;
        if (deviceRam < 1536) return 448;
        if (deviceRam < 2048) return 656;
        if (is32) return 696;
        if (deviceRam < 3064) return 936;
        if (deviceRam < 4096) return 1144;
        if (deviceRam < 6144) return 1536;
        return 2048;
    }

    public static float batteryTemp(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return Float.NaN;
            int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t == Integer.MIN_VALUE) return Float.NaN;
            return t / 10f;
        } catch (Throwable e) {
            return Float.NaN;
        }
    }

    public static Settings preset(String mode, DeviceInfo d) {
        Settings s = new Settings();
        switch (mode) {
            case MODE_ECO:
                s.heapMb = Math.min(d.recommendedHeapMb, d.safeMaxHeapMb);
                s.resolutionPercent = 50;
                s.renderDistance = 4; s.simulationDistance = 5; s.maxFps = 30; s.vsync = false;
                s.particles = 2; s.fancy = false; s.entityShadows = false; s.mipmap = 0; s.biomeBlend = 0; s.clouds = "false";
                break;
            case MODE_HIGH:
                s.heapMb = Math.min(Math.max(d.recommendedHeapMb, Math.round(d.safeMaxHeapMb * 0.75f / 64f) * 64), d.safeMaxHeapMb);
                s.resolutionPercent = 100;
                s.renderDistance = 10; s.simulationDistance = 8; s.maxFps = 120; s.vsync = false;
                s.particles = 0; s.fancy = true; s.entityShadows = true; s.mipmap = 4; s.biomeBlend = 2; s.clouds = "true";
                break;
            case MODE_BALANCED:
            default:
                s.heapMb = Math.min(d.recommendedHeapMb, d.safeMaxHeapMb);
                s.resolutionPercent = 75;
                s.renderDistance = 6; s.simulationDistance = 6; s.maxFps = 60; s.vsync = false;
                s.particles = 1; s.fancy = false; s.entityShadows = false; s.mipmap = 2; s.biomeBlend = 1; s.clouds = "fast";
                break;
        }
        s.heapMb = clampHeap(s.heapMb, d);
        return s;
    }

    public static int clampHeap(int heap, DeviceInfo d) {
        int h = Math.max(256, Math.min(heap, d.safeMaxHeapMb));
        return (h / 8) * 8;
    }

    /** Lê o options.txt (chave:valor) preservando a ordem. */
    public static LinkedHashMap<String, String> readOptions(File f) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        if (!f.exists()) return m;
        try {
            for (String line : JcxFiles.readSmall(f).split("\r?\n")) {
                int i = line.indexOf(':');
                if (i <= 0) continue;
                m.put(line.substring(0, i), line.substring(i + 1));
            }
        } catch (IOException ignored) {}
        return m;
    }

    public static File optionsFile() {
        return new File(JcxPaths.gameDir(), "options.txt");
    }

    /** Aplica as configurações; guarda o estado original na primeira vez. */
    public static void apply(Context c, String mode, Settings s) throws IOException {
        SharedPreferences backup = c.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE);
        File opt = optionsFile();
        File optBackup = new File(opt.getAbsolutePath() + ".jcx-original");
        if (!backup.getBoolean("saved", false)) {
            backup.edit()
                    .putBoolean("saved", true)
                    .putInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION)
                    .putInt("resolutionRatio", Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100f))
                    .apply();
        }
        if (opt.exists() && !optBackup.exists()) JcxFiles.copyFile(opt, optBackup);

        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt("allocation", s.heapMb)
                .putInt("resolutionRatio", s.resolutionPercent)
                .putString("jcx_perf_mode", mode)
                .apply();
        LauncherPreferences.PREF_RAM_ALLOCATION = s.heapMb;
        LauncherPreferences.PREF_SCALE_FACTOR = s.resolutionPercent / 100f;

        LinkedHashMap<String, String> o = readOptions(opt);
        o.put("renderDistance", String.valueOf(s.renderDistance));
        o.put("simulationDistance", String.valueOf(s.simulationDistance));
        o.put("maxFps", String.valueOf(s.maxFps));
        o.put("enableVsync", String.valueOf(s.vsync));
        o.put("particles", String.valueOf(s.particles));
        o.put("graphicsMode", s.fancy ? "1" : "0");   // 1.16+
        o.put("fancyGraphics", String.valueOf(s.fancy)); // até 1.15
        o.put("entityShadows", String.valueOf(s.entityShadows));
        o.put("mipmapLevels", String.valueOf(s.mipmap));
        o.put("biomeBlendRadius", String.valueOf(s.biomeBlend));
        // valores de texto: só altera se a chave já existir, mantendo o formato (com ou sem aspas)
        String clouds = o.get("renderClouds");
        if (clouds != null) {
            boolean quoted = clouds.startsWith("\"");
            o.put("renderClouds", quoted ? "\"" + s.clouds + "\"" : s.clouds);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : o.entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        JcxFiles.writeText(opt, sb.toString());
    }

    public static boolean hasBackup(Context c) {
        return c.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE).getBoolean("saved", false)
                || new File(optionsFile().getAbsolutePath() + ".jcx-original").exists();
    }

    /** Desfaz: restaura heap, resolução e o options.txt original. */
    public static void restore(Context c) throws IOException {
        SharedPreferences backup = c.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE);
        if (backup.getBoolean("saved", false)) {
            int a = backup.getInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION);
            int r = backup.getInt("resolutionRatio", 100);
            LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", a).putInt("resolutionRatio", r).remove("jcx_perf_mode").apply();
            LauncherPreferences.PREF_RAM_ALLOCATION = a;
            LauncherPreferences.PREF_SCALE_FACTOR = r / 100f;
            backup.edit().clear().apply();
        }
        File opt = optionsFile();
        File optBackup = new File(opt.getAbsolutePath() + ".jcx-original");
        if (optBackup.exists()) {
            JcxFiles.copyFile(optBackup, opt);
            //noinspection ResultOfMethodCallIgnored
            optBackup.delete();
        }
    }

    public static String currentMode() {
        return LauncherPreferences.DEFAULT_PREF.getString("jcx_perf_mode", null);
    }
}
