package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.Locale;

/** Tela X-PERFORMANCE: monitor do aparelho + perfis ECO / BALANCED / HIGH PERFORMANCE / CUSTOM. */
public class XPerformanceFragment extends Fragment {
    public static final String TAG = "JCX_XPERFORMANCE";

    private LinearLayout mBody;
    private XPerformance.DeviceInfo mDevice;
    private XPerformance.Settings mCustom;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        JcxUi.Screen s = JcxUi.screen(c, "X-PERFORMANCE", "Otimização real dentro dos limites do seu aparelho");
        mBody = s.body;
        build();
        return s.root;
    }

    private void build() {
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));
        mDevice = XPerformance.device(c);

        // ---- Monitor
        mBody.addView(JcxUi.section(c, "Monitor do aparelho"));
        LinearLayout mon = JcxUi.card(c, JcxUi.CYAN);
        mon.addView(JcxUi.label(c, String.format(Locale.ROOT, "RAM: %d MB livres de %d MB%s",
                mDevice.availRamMb, mDevice.totalRamMb, mDevice.is32Bit ? "  •  sistema 32-bit" : "")));
        TextView pressure = JcxUi.label(c, mDevice.lowMemory
                ? "⚠ Pressão de memória: ALTA — o Android pode encerrar o jogo. Feche outros apps."
                : "Pressão de memória: normal (limite do sistema " + mDevice.thresholdMb + " MB)");
        pressure.setTextColor(mDevice.lowMemory ? JcxUi.BAD : JcxUi.GOOD);
        mon.addView(pressure);
        mon.addView(JcxUi.label(c, "Heap Java atual: " + mDevice.currentHeapMb + " MB  •  recomendado: "
                + mDevice.recommendedHeapMb + " MB  •  máximo seguro: " + mDevice.safeMaxHeapMb + " MB"));
        mon.addView(JcxUi.label(c, "Resolução de renderização atual: " + mDevice.currentResolution + "%"));
        if (!Float.isNaN(mDevice.batteryTempC)) {
            TextView temp = JcxUi.label(c, String.format(Locale.ROOT, "Temperatura da bateria: %.1f °C%s", mDevice.batteryTempC,
                    mDevice.batteryTempC >= 42f ? "  — QUENTE: o aparelho pode reduzir o desempenho" : ""));
            temp.setTextColor(mDevice.batteryTempC >= 42f ? JcxUi.WARN : JcxUi.TEXT_DIM);
            mon.addView(temp);
        } else {
            mon.addView(JcxUi.label(c, "Temperatura: indisponível neste aparelho"));
        }
        if (mDevice.freeStorage >= 0) mon.addView(JcxUi.label(c, "Armazenamento livre: " + JcxUi.size(mDevice.freeStorage)));
        mon.addView(JcxUi.label(c, "Perfil: " + JcxPaths.profileName() + "  •  Renderizador: " + JcxPaths.rendererLabel(JcxPaths.renderer())));
        mon.addView(JcxUi.label(c, "FPS: medido dentro do jogo (tecla F3 / botão Debug do X-TOUCH)."));
        Button refresh = JcxUi.button(c, "Atualizar leitura", false);
        refresh.setOnClickListener(v -> build());
        mon.addView(JcxUi.buttonRow(c, refresh));
        mBody.addView(mon);

        String active = XPerformance.currentMode();
        mBody.addView(JcxUi.section(c, "Modos" + (active != null ? "  (ativo: " + active + ")" : "")));
        addMode(XPerformance.MODE_ECO, "Estabilidade em aparelhos limitados. Resolução 50%, distância 4, 30 FPS, partículas mínimas.", JcxUi.GOOD);
        addMode(XPerformance.MODE_BALANCED, "Equilíbrio entre visual e fluidez. Resolução 75%, distância 6, 60 FPS.", JcxUi.CYAN);
        addMode(XPerformance.MODE_HIGH, "Usa mais do hardware em aparelhos potentes: resolução 100%, distância 10, 120 FPS, gráficos detalhados.", JcxUi.PURPLE);
        addCustom();

        // ---- Restaurar
        LinearLayout rest = JcxUi.card(c, JcxUi.WARN);
        rest.addView(JcxUi.text(c, "Ajustes reversíveis", 15, JcxUi.TEXT, true));
        rest.addView(JcxUi.label(c, "Antes da primeira alteração o JAVA CRAFT X guarda o heap, a resolução e o options.txt originais. "
                + "Nenhum arquivo do Android é modificado."));
        Button undo = JcxUi.button(c, "Restaurar configuração original", false, JcxUi.WARN);
        undo.setEnabled(XPerformance.hasBackup(c));
        undo.setOnClickListener(v -> {
            try {
                XPerformance.restore(c);
                Toast.makeText(c, "Configuração original restaurada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(c, "Falha ao restaurar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            build();
        });
        rest.addView(JcxUi.buttonRow(c, undo));
        mBody.addView(rest);
    }

    private void addMode(String mode, String desc, int accent) {
        Context c = requireContext();
        XPerformance.Settings s = XPerformance.preset(mode, mDevice);
        LinearLayout card = JcxUi.card(c, accent);
        TextView title = JcxUi.text(c, mode + " MODE", 16, accent, true);
        card.addView(title);
        card.addView(JcxUi.label(c, desc));
        card.addView(JcxUi.label(c, "Heap: " + s.heapMb + " MB  •  Simulação: " + s.simulationDistance + " chunks"));
        Button apply = JcxUi.button(c, "Aplicar " + mode, true);
        apply.setOnClickListener(v -> {
            if (XPerformance.MODE_HIGH.equals(mode) && mDevice.totalRamMb < 6000) {
                new AlertDialog.Builder(c)
                        .setTitle("Aparelho com " + mDevice.totalRamMb + " MB de RAM")
                        .setMessage("O modo HIGH PERFORMANCE foi pensado para aparelhos com 6 GB ou mais. Em aparelhos menores pode causar travamentos ou encerramento do jogo. Aplicar mesmo assim?")
                        .setPositiveButton("Aplicar", (d, w) -> applySettings(mode, s))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else applySettings(mode, s);
        });
        card.addView(JcxUi.buttonRow(c, apply));
        mBody.addView(card);
    }

    private void applySettings(String mode, XPerformance.Settings s) {
        Context c = requireContext();
        try {
            XPerformance.apply(c, mode, s);
            Toast.makeText(c, mode + " aplicado ao perfil " + JcxPaths.profileName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "Falha ao aplicar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        build();
    }

    private interface IntSetter { void set(int v); }

    private void addSlider(LinearLayout parent, String name, int min, int max, int step, int value, String suffix, IntSetter setter) {
        Context c = requireContext();
        TextView l = JcxUi.label(c, "");
        SeekBar sb = new SeekBar(c);
        int steps = (max - min) / step;
        sb.setMax(steps);
        int pos = Math.max(0, Math.min(steps, (value - min) / step));
        sb.setProgress(pos);
        final String fmt = name + ": %d" + suffix;
        l.setText(String.format(Locale.ROOT, fmt, min + pos * step));
        setter.set(min + pos * step);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress * step;
                l.setText(String.format(Locale.ROOT, fmt, v));
                setter.set(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(l);
        parent.addView(sb);
    }

    private void addCustom() {
        Context c = requireContext();
        mCustom = XPerformance.preset(XPerformance.MODE_BALANCED, mDevice);
        mCustom.heapMb = XPerformance.clampHeap(mDevice.currentHeapMb, mDevice);
        mCustom.resolutionPercent = Math.max(25, Math.min(100, mDevice.currentResolution));
        java.util.LinkedHashMap<String, String> opt = XPerformance.readOptions(XPerformance.optionsFile());
        mCustom.renderDistance = parse(opt.get("renderDistance"), mCustom.renderDistance);
        mCustom.simulationDistance = parse(opt.get("simulationDistance"), mCustom.simulationDistance);
        mCustom.maxFps = parse(opt.get("maxFps"), mCustom.maxFps);
        mCustom.particles = parse(opt.get("particles"), mCustom.particles);

        LinearLayout card = JcxUi.card(c, JcxUi.BLUE);
        card.addView(JcxUi.text(c, "CUSTOM MODE", 16, JcxUi.BLUE, true));
        card.addView(JcxUi.label(c, "Configuração manual. O limite de memória nunca passa do máximo seguro do aparelho."));
        addSlider(card, "Heap Java", 256, Math.max(256, mDevice.safeMaxHeapMb), 64, mCustom.heapMb, " MB", v -> mCustom.heapMb = v);
        addSlider(card, "Resolução de renderização", 25, 100, 5, mCustom.resolutionPercent, "%", v -> mCustom.resolutionPercent = v);
        addSlider(card, "Distância de renderização", 2, 16, 1, mCustom.renderDistance, " chunks", v -> mCustom.renderDistance = v);
        addSlider(card, "Distância de simulação (1.18+)", 5, 12, 1, mCustom.simulationDistance, " chunks", v -> mCustom.simulationDistance = v);
        addSlider(card, "Limite de FPS (260 = ilimitado)", 30, 260, 10, mCustom.maxFps, "", v -> mCustom.maxFps = v);
        addSlider(card, "Partículas (0 todas • 1 reduzidas • 2 mínimas)", 0, 2, 1, mCustom.particles, "", v -> mCustom.particles = v);
        CheckBox vsync = new CheckBox(c);
        vsync.setText("VSync (quando o renderizador suportar)");
        vsync.setTextColor(JcxUi.TEXT_DIM);
        vsync.setChecked("true".equals(opt.get("enableVsync")));
        CheckBox fancy = new CheckBox(c);
        fancy.setText("Gráficos detalhados (Fancy)");
        fancy.setTextColor(JcxUi.TEXT_DIM);
        fancy.setChecked("1".equals(opt.get("graphicsMode")) || "true".equals(opt.get("fancyGraphics")));
        CheckBox shadows = new CheckBox(c);
        shadows.setText("Sombras de entidades");
        shadows.setTextColor(JcxUi.TEXT_DIM);
        shadows.setChecked("true".equals(opt.get("entityShadows")));
        card.addView(vsync);
        card.addView(fancy);
        card.addView(shadows);
        Button apply = JcxUi.button(c, "Aplicar CUSTOM", true);
        apply.setOnClickListener(v -> {
            mCustom.vsync = vsync.isChecked();
            mCustom.fancy = fancy.isChecked();
            mCustom.entityShadows = shadows.isChecked();
            mCustom.heapMb = XPerformance.clampHeap(mCustom.heapMb, mDevice);
            applySettings(XPerformance.MODE_CUSTOM, mCustom);
        });
        card.addView(JcxUi.buttonRow(c, apply));
        mBody.addView(card);
    }

    private static int parse(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.replace("\"", "").trim()); } catch (NumberFormatException e) { return def; }
    }
}
