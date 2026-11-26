package net.kdt.pojavlaunch.jcx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

import java.util.Locale;

/** JAVA CRAFT X GAME HUD: ações do menu rápido durante a partida (sem pausar a renderização). */
public final class XHud {
    private XHud() {}

    /** Ajusta o tamanho de todos os botões na hora, mantendo o layout carregado. */
    public static void scaleDialog(Activity a, ControlLayout layout) {
        if (layout == null || layout.getLayout() == null) return;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = JcxUi.dp(a, 20);
        box.setPadding(p, p / 2, p, 0);
        TextView label = new TextView(a);
        SeekBar bar = new SeekBar(a);
        bar.setMax(30); // 50% a 200% em passos de 5
        int current = Math.round(LauncherPreferences.PREF_BUTTONSIZE);
        bar.setProgress(Math.max(0, Math.min(30, (current - 50) / 5)));
        label.setText(String.format(Locale.ROOT, "Tamanho dos botões: %d%%", 50 + bar.getProgress() * 5));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean u) { label.setText(String.format(Locale.ROOT, "Tamanho dos botões: %d%%", 50 + v * 5)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(label);
        box.addView(bar);
        new AlertDialog.Builder(a).setTitle("X-TOUCH").setView(box)
                .setPositiveButton("Aplicar", (d, w) -> {
                    int value = 50 + bar.getProgress() * 5;
                    try {
                        // cópia do layout atual: recarregar o mesmo objeto apagaria os botões
                        CustomControls copy = Tools.GLOBAL_GSON.fromJson(Tools.GLOBAL_GSON.toJson(layout.getLayout()), CustomControls.class);
                        LauncherPreferences.DEFAULT_PREF.edit().putInt("buttonscale", value).apply();
                        LauncherPreferences.PREF_BUTTONSIZE = value;
                        layout.loadLayout(copy);
                        layout.refreshControlButtonPositions();
                    } catch (Throwable t) {
                        Toast.makeText(a, "Não foi possível redimensionar: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    public static void toggleControls(ControlLayout layout) {
        if (layout != null) layout.toggleControlVisible();
    }

    /** Envia F2 ao jogo: o próprio Minecraft salva a captura em screenshots/. */
    public static void screenshot(Activity a) {
        CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_F2);
        Toast.makeText(a, "Captura salva na pasta screenshots do perfil", Toast.LENGTH_SHORT).show();
    }

    /**
     * Volta ao launcher. O jogo roda num processo interno separado (":game") e o launcher no
     * processo ":launcher"; encerrar o processo do jogo deixa o launcher intacto.
     * Peça para salvar antes (Salvar e sair para o título).
     */
    public static void backToLauncher(Activity a) {
        new AlertDialog.Builder(a).setTitle("Voltar ao launcher")
                .setMessage("Antes, use Pausa → \"Salvar e sair para o título\" para salvar o mundo. O jogo será encerrado e o JAVA CRAFT X reaberto.")
                .setPositiveButton("Voltar agora", (d, w) -> {
                    Intent i = new Intent(a, LauncherActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    a.startActivity(i);
                    a.getWindow().getDecorView().postDelayed(Tools::fullyExit, 400);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
