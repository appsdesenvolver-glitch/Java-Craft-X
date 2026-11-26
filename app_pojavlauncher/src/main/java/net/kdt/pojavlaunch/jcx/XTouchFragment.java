package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.content.Intent;
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

import android.net.Uri;
import android.view.InputDevice;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;

/** X-TOUCH: layouts próprios (Survival, PVP, Creative, Parkour, Custom), sensibilidade e resposta ao toque. */
public class XTouchFragment extends Fragment {
    public static final String TAG = "JCX_XTOUCH";

    private static final String[][] LAYOUTS = {
            {"xtouch_survival", "SURVIVAL", "Controles completos de sobrevivência: atacar, usar, agachar, correr, inventário, soltar e mão secundária."},
            {"xtouch_pvp", "PVP", "Botão de ataque grande, ações rápidas, atalhos de hotbar 1-2-3 e correr fixo."},
            {"xtouch_creative", "CREATIVE", "Quebrar/colocar, pegar bloco, trocar bloco na hotbar, voar/descer e atalho para comandos (/)."},
            {"xtouch_parkour", "PARKOUR", "Pulo grande, correr fixo e trava de avanço no analógico para saltos precisos."},
            {"xtouch_builder", "BUILDER", "Construção: COLOCAR em destaque, quebrar, pegar bloco, trocar bloco e agachar na beirada."},
            {"xtouch_minimal", "MINIMAL", "Só o essencial (pular, atacar, usar, inventário) para ver mais da tela."},
            {"xtouch_custom", "CUSTOM", "Ponto de partida para personalizar tudo no editor (mover, redimensionar, opacidade, ação)."},
    };

    private LinearLayout mBody;

    private final ActivityResultLauncher<String> mImport =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImport);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JcxUi.Screen s = JcxUi.screen(requireContext(), "X-TOUCH", "Controles touchscreen do JAVA CRAFT X");
        mBody = s.body;
        build();
        return s.root;
    }

    private File ctrlDir() {
        File d = new File(Tools.CTRLMAP_PATH);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private void build() {
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));
        String current = new File(LauncherPreferences.PREF_DEFAULTCTRL_PATH).getName();
        TextView cur = JcxUi.label(c, "Layout em uso no jogo: " + current);
        cur.setTextColor(JcxUi.CYAN);
        mBody.addView(cur);
        Button editor = JcxUi.button(c, "Abrir editor de controles", true);
        editor.setOnClickListener(v -> startActivity(new Intent(c, CustomControlsActivity.class)));
        mBody.addView(JcxUi.buttonRow(c, editor));
        mBody.addView(JcxUi.label(c, "No editor: arraste para mover, use as alças para redimensionar e toque no botão para mudar ação, opacidade, cor e toggle. "
                + "Durante a partida, o botão X MENU abre o menu rápido (layout, configurações, monitor)."));

        mBody.addView(JcxUi.section(c, "Layouts X-TOUCH"));
        for (String[] l : LAYOUTS) mBody.addView(layoutCard(l[0], l[1], l[2], current));

        mBody.addView(JcxUi.section(c, "Sensibilidade e resposta"));
        LinearLayout sens = JcxUi.card(c, JcxUi.BLUE);
        addSlider(sens, "Sensibilidade da câmera/mouse", 25, 300, 5, Math.round(LauncherPreferences.PREF_MOUSESPEED * 100), "%", v -> {
            LauncherPreferences.DEFAULT_PREF.edit().putInt("mousespeed", v).apply();
            LauncherPreferences.PREF_MOUSESPEED = v / 100f;
        });
        addSlider(sens, "Escala dos botões", 50, 200, 5, Math.round(LauncherPreferences.PREF_BUTTONSIZE), "%", v -> {
            LauncherPreferences.DEFAULT_PREF.edit().putInt("buttonscale", v).apply();
            LauncherPreferences.PREF_BUTTONSIZE = v;
        });
        addSlider(sens, "Toque longo (quebrar bloco / clique direito)", 100, 1000, 25, LauncherPreferences.PREF_LONGPRESS_TRIGGER, " ms", v -> {
            LauncherPreferences.DEFAULT_PREF.edit().putInt("timeLongPressTrigger", v).apply();
            LauncherPreferences.PREF_LONGPRESS_TRIGGER = v;
        });
        CheckBox gestures = new CheckBox(c);
        gestures.setText("Gestos na área da câmera (toque = usar, segurar = quebrar)");
        gestures.setTextColor(JcxUi.TEXT_DIM);
        gestures.setChecked(!LauncherPreferences.PREF_DISABLE_GESTURES);
        gestures.setOnCheckedChangeListener((b, on) -> {
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("disableGestures", !on).apply();
            LauncherPreferences.PREF_DISABLE_GESTURES = !on;
        });
        sens.addView(gestures);
        sens.addView(JcxUi.label(c, "Multitoque, teclado, mouse e gamepads compatíveis funcionam ao mesmo tempo. Remapeamento de gamepad: Configurações → Controles."));
        mBody.addView(sens);

        mBody.addView(JcxUi.section(c, "Controle Bluetooth/USB, teclado e mouse"));
        LinearLayout inp = JcxUi.card(c, JcxUi.CYAN);
        inp.addView(JcxUi.label(c, connectedDevices()));
        inp.addView(JcxUi.label(c, "Teclado e mouse funcionam direto no jogo (o mouse é capturado na partida; use o botão X MENU para liberar). "
                + "Gamepads: o mapeamento padrão já funciona; ajuste os botões abaixo."));
        Button map = JcxUi.button(c, "Mapear botões do controle", true);
        map.setOnClickListener(v -> Tools.swapFragment(requireActivity(), net.kdt.pojavlaunch.fragments.GamepadMapperFragment.class,
                net.kdt.pojavlaunch.fragments.GamepadMapperFragment.TAG, null));
        Button rescan = JcxUi.button(c, "Detectar de novo", false);
        rescan.setOnClickListener(v -> build());
        inp.addView(JcxUi.buttonRow(c, map, rescan));
        mBody.addView(inp);

        mBody.addView(JcxUi.section(c, "Meus layouts"));
        Button imp = JcxUi.button(c, "Importar layout (.json)", false);
        imp.setOnClickListener(v -> mImport.launch("*/*"));
        mBody.addView(JcxUi.buttonRow(c, imp));
        File[] mine = ctrlDir().listFiles((d, n) -> n.endsWith(".json"));
        if (mine == null || mine.length == 0) {
            mBody.addView(JcxUi.label(c, "Nenhum layout salvo."));
        } else {
            Arrays.sort(mine, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : mine) mBody.addView(userCard(f, current));
        }
    }

    private View layoutCard(String id, String title, String desc, String current) {
        Context c = requireContext();
        boolean active = current.equals(id + ".json");
        LinearLayout card = JcxUi.card(c, active ? JcxUi.CYAN : JcxUi.PURPLE);
        card.addView(JcxUi.text(c, title + (active ? "  • EM USO" : ""), 16, active ? JcxUi.CYAN : JcxUi.TEXT, true));
        card.addView(JcxUi.label(c, desc));
        Button use = JcxUi.button(c, active ? "Em uso" : "Usar", true);
        use.setEnabled(!active);
        use.setOnClickListener(v -> {
            File f = new File(ctrlDir(), id + ".json");
            try {
                if (!f.exists()) copyAsset(id, f);
                setDefault(f);
            } catch (Exception e) {
                Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            build();
        });
        Button dup = JcxUi.button(c, "Duplicar", false);
        dup.setOnClickListener(v -> {
            try {
                File src = new File(ctrlDir(), id + ".json");
                if (!src.exists()) copyAsset(id, src);
                File copy = new File(ctrlDir(), uniqueJson(id + "_copia"));
                JcxFiles.copyFile(src, copy);
                Toast.makeText(c, "Criado " + copy.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            build();
        });
        Button reset = JcxUi.button(c, "Restaurar", false, JcxUi.WARN);
        reset.setOnClickListener(v -> new AlertDialog.Builder(c)
                .setTitle("Restaurar " + title + "?")
                .setMessage("As alterações feitas neste layout serão substituídas pelo padrão do JAVA CRAFT X.")
                .setPositiveButton("Restaurar", (d, w) -> {
                    try {
                        copyAsset(id, new File(ctrlDir(), id + ".json"));
                        Toast.makeText(c, title + " restaurado", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null).show());
        card.addView(JcxUi.buttonRow(c, use, dup, reset));
        return card;
    }

    private View userCard(File f, String current) {
        Context c = requireContext();
        boolean active = current.equals(f.getName());
        LinearLayout card = JcxUi.card(c, active ? JcxUi.CYAN : JcxUi.GRAPHITE_LIGHT);
        card.addView(JcxUi.text(c, f.getName() + (active ? "  • EM USO" : ""), 14, JcxUi.TEXT, true));
        Button use = JcxUi.button(c, "Usar", false);
        use.setEnabled(!active);
        use.setOnClickListener(v -> { setDefault(f); build(); });
        Button dup = JcxUi.button(c, "Duplicar", false);
        dup.setOnClickListener(v -> {
            try {
                String base = f.getName().substring(0, f.getName().length() - 5);
                JcxFiles.copyFile(f, new File(ctrlDir(), uniqueJson(base + "_copia")));
            } catch (Exception e) {
                Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
            build();
        });
        Button del = JcxUi.button(c, "Excluir", false, JcxUi.BAD);
        del.setEnabled(!active && !f.getAbsolutePath().equals(Tools.CTRLDEF_FILE));
        del.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Excluir " + f.getName() + "?")
                .setPositiveButton("Excluir", (d, w) -> { //noinspection ResultOfMethodCallIgnored
                    f.delete(); build(); })
                .setNegativeButton(android.R.string.cancel, null).show());
        card.addView(JcxUi.buttonRow(c, use, dup, del));
        Button exp = JcxUi.button(c, "Exportar", false);
        exp.setOnClickListener(v -> export(f));
        Button perProfile = JcxUi.button(c, "Só neste perfil", false);
        perProfile.setOnClickListener(v -> setForProfile(f));
        card.addView(JcxUi.buttonRow(c, exp, perProfile));
        return card;
    }

    private String connectedDevices() {
        StringBuilder sb = new StringBuilder("Dispositivos detectados:\n");
        int found = 0;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d == null || d.isVirtual()) continue;
            int src = d.getSources();
            String kind = null;
            if ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) kind = "Controle (gamepad)";
            else if ((src & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) kind = "Mouse";
            else if (d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) kind = "Teclado";
            else if ((src & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN) kind = "Tela touch";
            if (kind == null) continue;
            sb.append("• ").append(kind).append(": ").append(d.getName()).append('\n');
            found++;
        }
        if (found == 0) sb.append("• nenhum além dos botões do aparelho\n");
        return sb.toString().trim();
    }

    private void export(File f) {
        Context c = requireContext();
        try {
            File out = new File(JcxPaths.jcxHome("exports"), f.getName());
            JcxFiles.copyFile(f, out);
            Tools.openPath(c, out, true);
        } catch (Exception e) {
            Toast.makeText(c, "Falha ao exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setForProfile(File f) {
        Context c = requireContext();
        try {
            net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.load();
            String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile p = key == null ? null
                    : net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.mainProfileJson.profiles.get(key);
            if (p == null) { Toast.makeText(c, "Selecione um perfil primeiro", Toast.LENGTH_LONG).show(); return; }
            p.controlFile = f.getName();
            net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles.write();
            Toast.makeText(c, f.getName() + " será usado no perfil " + JcxPaths.profileName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onImport(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        Context c = requireContext();
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("arquivo inacessível");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) != -1) {
                bo.write(buf, 0, r);
                if (bo.size() > 2 * 1024 * 1024) throw new java.io.IOException("arquivo grande demais para um layout");
            }
            String text = bo.toString("UTF-8");
            org.json.JSONObject o = new org.json.JSONObject(text);
            if (!o.has("mControlDataList")) throw new java.io.IOException("não é um layout de controles");
            String name = uri.getLastPathSegment();
            if (name != null && name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
            name = JcxPaths.safeFileName(name);
            if (name == null || name.isEmpty()) name = "importado";
            if (name.endsWith(".json")) name = name.substring(0, name.length() - 5);
            File out = new File(ctrlDir(), uniqueJson(name));
            JcxFiles.writeText(out, text);
            Toast.makeText(c, "Layout importado: " + out.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "Importação recusada: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        build();
    }

    private String uniqueJson(String base) {
        int i = 1;
        String n = base + ".json";
        while (new File(ctrlDir(), n).exists()) n = base + "_" + (++i) + ".json";
        return n;
    }

    private void copyAsset(String id, File out) throws java.io.IOException {
        try (InputStream in = requireContext().getAssets().open("xtouch/" + id + ".json");
             OutputStream os = new FileOutputStream(out)) {
            JcxFiles.copyStream(in, os, null);
        }
    }

    private void setDefault(File f) {
        LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", f.getAbsolutePath()).apply();
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = f.getAbsolutePath();
        Toast.makeText(requireContext(), "Layout padrão: " + f.getName(), Toast.LENGTH_SHORT).show();
    }

    private interface IntSetter { void set(int v); }

    private void addSlider(LinearLayout parent, String name, int min, int max, int step, int value, String suffix, IntSetter onStop) {
        Context c = requireContext();
        TextView l = JcxUi.label(c, "");
        SeekBar sb = new SeekBar(c);
        int steps = (max - min) / step;
        sb.setMax(steps);
        int pos = Math.max(0, Math.min(steps, (value - min) / step));
        sb.setProgress(pos);
        l.setText(String.format(Locale.ROOT, "%s: %d%s", name, min + pos * step, suffix));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                l.setText(String.format(Locale.ROOT, "%s: %d%s", name, min + p * step, suffix));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { onStop.set(min + s.getProgress() * step); }
        });
        parent.addView(l);
        parent.addView(sb);
    }
}
