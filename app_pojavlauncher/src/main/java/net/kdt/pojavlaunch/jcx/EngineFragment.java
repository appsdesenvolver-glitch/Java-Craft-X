package net.kdt.pojavlaunch.jcx;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * X-ENGINE: capacidades do aparelho (X-Adaptive), Renderer Manager por perfil com recomendação
 * automática e diagnóstico, e Runtime Manager (runtimes Java instalados pelo motor).
 */
public class EngineFragment extends Fragment {
    public static final String TAG = "JCX_ENGINE";
    private LinearLayout mBody;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JcxUi.Screen s = JcxUi.screen(requireContext(), "X-Engine", "Motor, renderizadores, runtimes e capacidades do aparelho");
        mBody = s.body;
        build();
        return s.root;
    }

    private static String glesVersion(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            return am.getDeviceConfigurationInfo().getGlEsVersion();
        } catch (Throwable t) {
            return "?";
        }
    }

    private void build() {
        if (!isAdded()) return;
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));

        // ---------------- X-Adaptive
        mBody.addView(JcxUi.section(c, "Aparelho"));
        LinearLayout dev = JcxUi.card(c, JcxUi.CYAN);
        boolean vulkan = Tools.checkVulkanSupport(c.getPackageManager());
        String gles = glesVersion(c);
        int ram = Tools.getTotalDeviceMemory(c);
        dev.addView(JcxUi.label(c, Build.MANUFACTURER + " " + Build.MODEL + "  •  Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        dev.addView(JcxUi.label(c, "Arquitetura: " + Architecture.archAsString(Tools.DEVICE_ARCHITECTURE)
                + "  •  RAM: " + ram + " MB  •  Livre no armazenamento: " + JcxUi.size(JcxPaths.freeBytes(new File(Tools.DIR_GAME_HOME)))));
        dev.addView(JcxUi.label(c, "OpenGL ES: " + gles + "  •  Vulkan: " + (vulkan ? "disponível (não garante que o Zink funcione)" : "não disponível")));
        mBody.addView(dev);

        LinearLayout rec = JcxUi.card(c, JcxUi.PURPLE);
        rec.addView(JcxUi.text(c, "Recomendações para este aparelho", 15, JcxUi.TEXT, true));
        if (ram < 3072) {
            rec.addView(JcxUi.label(c, "• Recursos limitados: X-PERFORMANCE no modo ECO (resolução 50%, distância 4, 30 FPS)."));
            rec.addView(JcxUi.label(c, "• Prefira versões até 1.16.5 (Java 8, mais leves) e poucos mods; shaders desativados."));
        } else if (ram < 6144) {
            rec.addView(JcxUi.label(c, "• Modo BALANCED; Sodium (Fabric) costuma ajudar em 1.18+. Shaders só os 'potato/low'."));
        } else {
            rec.addView(JcxUi.label(c, "• Aparelho com memória folgada: BALANCED ou HIGH PERFORMANCE conforme a temperatura."));
        }
        if (Architecture.is32BitsDevice()) rec.addView(JcxUi.label(c, "• Sistema 32-bit: heap limitado a ~1 GB pelo motor."));
        mBody.addView(rec);

        // ---------------- Renderer Manager
        mBody.addView(JcxUi.section(c, "Renderer Manager — perfil " + JcxPaths.profileName()));
        Tools.RenderersList available = Tools.getCompatibleRenderers(c);
        String[] allIds = c.getResources().getStringArray(R.array.renderer_values);
        String[] allNames = c.getResources().getStringArray(R.array.renderer);
        String current = JcxPaths.renderer();
        String recommended = recommend(available.rendererIds, ram);
        TextView recTxt = JcxUi.label(c, "Recomendado automaticamente: " + JcxPaths.rendererLabel(recommended) + " — " + reason(recommended));
        recTxt.setTextColor(JcxUi.CYAN);
        mBody.addView(recTxt);
        String diag = lastGraphicsFailure();
        if (diag != null) {
            TextView dt = JcxUi.label(c, "Diagnóstico: a última execução registrou falha gráfica (" + diag + "). Experimente outro renderizador abaixo.");
            dt.setTextColor(JcxUi.WARN);
            mBody.addView(dt);
        }
        for (int i = 0; i < allIds.length && i < allNames.length; i++) {
            String id = allIds[i];
            boolean ok = available.rendererIds.contains(id);
            boolean active = id.equals(current);
            LinearLayout card = JcxUi.card(c, active ? JcxUi.CYAN : ok ? JcxUi.BLUE : JcxUi.TEXT_DIM);
            card.addView(JcxUi.text(c, allNames[i] + (active ? "  • EM USO" : "") + (id.equals(recommended) ? "  • RECOMENDADO" : ""), 14,
                    ok ? JcxUi.TEXT : JcxUi.TEXT_DIM, true));
            card.addView(JcxUi.label(c, requirements(id)));
            if (!ok) {
                TextView na = JcxUi.label(c, "Indisponível neste aparelho/APK.");
                na.setTextColor(JcxUi.BAD);
                card.addView(na);
            } else if (!active) {
                Button use = JcxUi.button(c, "Usar neste perfil", false);
                use.setOnClickListener(v -> setRenderer(id));
                card.addView(JcxUi.buttonRow(c, use));
            }
            mBody.addView(card);
        }
        Button auto = JcxUi.button(c, "Aplicar recomendado", true);
        auto.setOnClickListener(v -> setRenderer(recommended));
        Button reset = JcxUi.button(c, "Padrão do motor", false);
        reset.setOnClickListener(v -> setRenderer(null));
        mBody.addView(JcxUi.buttonRow(c, auto, reset));

        // ---------------- Runtime Manager
        mBody.addView(JcxUi.section(c, "Runtime Manager (Java)"));
        mBody.addView(JcxUi.label(c, "Seleção automática por versão: 1.16.5 e anteriores usam Java 8; 1.17–1.20.4 usam Java 17; 1.20.5+ usam Java 21; "
                + "versões mais novas que exigirem Java 25 usam o runtime 25 quando houver build Android. Runtimes de outra arquitetura nunca são usados."));
        List<Runtime> rts;
        try { rts = MultiRTUtils.getInstalledRuntimes(); } catch (Throwable t) { rts = null; }
        if (rts == null || rts.isEmpty()) {
            mBody.addView(JcxUi.label(c, "Nenhum runtime instalado ainda (o Java 8 é extraído na primeira abertura do jogo)."));
        } else {
            for (Runtime r : rts) {
                LinearLayout card = JcxUi.card(c, JcxUi.GRAPHITE_LIGHT);
                card.addView(JcxUi.text(c, r.name, 14, JcxUi.TEXT, true));
                card.addView(JcxUi.label(c, String.format(Locale.ROOT, "Java %d  •  %s  •  arquitetura %s", r.javaVersion,
                        r.versionString != null ? r.versionString : "?", r.arch != null ? r.arch : "?")));
                Button del = JcxUi.button(c, "Remover", false, JcxUi.BAD);
                del.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Remover runtime " + r.name + "?")
                        .setMessage("Runtimes internos são extraídos de novo automaticamente; runtimes baixados serão baixados de novo quando uma versão precisar.")
                        .setPositiveButton("Remover", (d, w) -> {
                            try { MultiRTUtils.removeRuntimeNamed(r.name); Toast.makeText(c, "Removido", Toast.LENGTH_SHORT).show(); }
                            catch (Exception e) { Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                            build();
                        })
                        .setNegativeButton(android.R.string.cancel, null).show());
                card.addView(JcxUi.buttonRow(c, del));
                mBody.addView(card);
            }
        }
        mBody.addView(JcxUi.label(c, "Áudio: OpenAL Soft (via motor), iniciado junto com o jogo; volume pelas opções de som do Minecraft."));
    }

    /** Escolha automática: MobileGlues por padrão; Krypton em aparelhos fracos para versões até 1.21.4. */
    private static String recommend(List<String> ids, int ram) {
        String mc = JcxPaths.mcVersion();
        boolean newerThan1214 = mc != null && compare(mc, "1.21.4") > 0;
        if (ram < 4096 && !newerThan1214 && ids.contains("opengles2")) return "opengles2";
        if (ids.contains("opengles_mobileglues")) return "opengles_mobileglues";
        return ids.isEmpty() ? "opengles_mobileglues" : ids.get(0);
    }

    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? num(x[i]) : 0, yi = i < y.length ? num(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static String reason(String id) {
        if ("opengles2".equals(id)) return "o mais leve para GPUs de entrada (até 1.21.4).";
        if ("opengles_mobileglues".equals(id)) return "boa compatibilidade com versões novas e mods (exige OpenGL ES 3).";
        return "primeiro disponível neste aparelho.";
    }

    private static String requirements(String id) {
        if (id.equals("opengles2")) return "OpenGL ES 3 • até Minecraft 1.21.4 • mais leve • sem shaders";
        if (id.equals("opengles_mobileglues")) return "OpenGL ES 3.0+ (melhor em 3.2) • versões novas • alguns shaders via Iris";
        if (id.contains("zink")) return "Vulkan + driver compatível • mais pesado • varia muito entre GPUs";
        if (id.contains("ltw")) return "OpenGL ES 3 • componente opcional • experimental";
        return "";
    }

    private void setRenderer(@Nullable String id) {
        Context c = requireContext();
        try {
            LauncherProfiles.load();
            String key = net.kdt.pojavlaunch.prefs.LauncherPreferences.DEFAULT_PREF.getString(net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            MinecraftProfile p = key == null ? null : LauncherProfiles.mainProfileJson.profiles.get(key);
            if (p == null) { Toast.makeText(c, "Selecione um perfil primeiro", Toast.LENGTH_LONG).show(); return; }
            p.pojavRendererName = id;
            LauncherProfiles.write();
            Toast.makeText(c, "Renderizador do perfil: " + (id == null ? "padrão do motor" : JcxPaths.rendererLabel(id)), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        build();
    }

    /** Usa o Crash Analyzer no último log para detectar falha gráfica/nativa. */
    private static String lastGraphicsFailure() {
        try {
            File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if (!log.isFile()) return null;
            CrashAnalyzer.Result r = new CrashAnalyzer.Result();
            CrashAnalyzer.analyze(JcxFiles.readTail(log, 1024 * 1024), log.getName(), r);
            for (CrashAnalyzer.Finding f : r.findings)
                if (f.id.equals("graphics") || f.id.equals("native") || f.id.equals("shader")) return f.title;
        } catch (Exception ignored) {}
        return null;
    }
}
