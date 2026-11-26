package net.kdt.pojavlaunch.jcx;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Debug;
import android.os.Process;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.Locale;

/** X-MONITOR: painel do menu rápido in-game com memória e temperatura medidas na hora. */
public final class XMonitor {
    private XMonitor() {}

    public static String snapshot(Context c) {
        StringBuilder sb = new StringBuilder();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long totalMb = mi.totalMem / 1048576L;
        long availMb = mi.availMem / 1048576L;
        sb.append(String.format(Locale.ROOT, "RAM do aparelho: %d MB livres de %d MB\n", availMb, totalMb));
        sb.append(mi.lowMemory ? "⚠ Pressão de memória ALTA — risco de o Android encerrar o jogo\n" : "Pressão de memória: normal\n");
        try {
            Debug.MemoryInfo[] pm = am.getProcessMemoryInfo(new int[]{Process.myPid()});
            if (pm != null && pm.length > 0)
                sb.append(String.format(Locale.ROOT, "Memória usada pelo jogo (PSS): %d MB\n", pm[0].getTotalPss() / 1024));
        } catch (Throwable ignored) {}
        sb.append("Heap Java configurado: ").append(LauncherPreferences.PREF_RAM_ALLOCATION).append(" MB\n");
        sb.append(String.format(Locale.ROOT, "Resolução de renderização: %d%%\n", Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100)));
        float t = XPerformance.batteryTemp(c);
        if (!Float.isNaN(t)) {
            sb.append(String.format(Locale.ROOT, "Temperatura da bateria: %.1f °C", t));
            if (t >= 42f) sb.append("  — QUENTE: considere o modo ECO");
            sb.append('\n');
        }
        String mode = XPerformance.currentMode();
        sb.append("Modo X-PERFORMANCE: ").append(mode != null ? mode : "padrão do launcher").append('\n');
        sb.append("\nFPS: abra o Debug (F3) pelo botão MAIS do X-TOUCH.\n");
        sb.append("Heap, resolução e gráficos mudam no X-PERFORMANCE e valem na próxima vez que o jogo abrir; a resolução também pode ser ajustada agora em \"Configurações rápidas\".");
        return sb.toString();
    }

    public static void show(Activity a) {
        new AlertDialog.Builder(a)
                .setTitle("X-MONITOR")
                .setMessage(snapshot(a))
                .setPositiveButton("Atualizar", (d, w) -> show(a))
                .setNegativeButton("Voltar ao jogo", null)
                .show();
    }
}
