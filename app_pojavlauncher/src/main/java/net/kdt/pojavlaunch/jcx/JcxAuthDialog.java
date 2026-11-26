package net.kdt.pojavlaunch.jcx;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.Tools;

/**
 * Diálogo de erro de login: etapa, código, descrição, causa provável, ação recomendada e,
 * quando existe, o link oficial que resolve (criar perfil Xbox, nome de jogador, Família Microsoft).
 * O botão do link NÃO fecha o diálogo: o usuário resolve no site, volta e toca em "Tentar de novo".
 */
public final class JcxAuthDialog {
    private JcxAuthDialog() {}

    public static void show(Activity a, JcxAuthError e, Runnable retry, Runnable relogin) {
        if (a == null || a.isFinishing()) return;
        Context c = a;
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = JcxUi.dp(c, 18);
        body.setPadding(p, p / 2, p, 0);

        body.addView(JcxUi.text(c, e.step.label + (e.http > 0 ? "  •  HTTP " + e.http : "") + (e.xerr != 0 ? "  •  XErr " + e.xerr : ""), 11, JcxUi.CYAN, false));
        body.addView(JcxUi.text(c, e.description, 14, JcxUi.TEXT, false));
        body.addView(JcxUi.section(c, "Causa provável"));
        body.addView(JcxUi.label(c, e.cause));
        body.addView(JcxUi.section(c, "O que fazer"));
        body.addView(JcxUi.label(c, e.action));
        if (e.canRetryWithoutLogin)
            body.addView(JcxUi.text(c, "Sua autorização Microsoft ficou guardada com segurança: não será preciso digitar a senha de novo.", 12, JcxUi.GOOD, false));

        ScrollView sv = new ScrollView(c);
        sv.addView(body);

        String primary = e.canRetryWithoutLogin ? "Tentar de novo" : e.needsNewLogin ? "Entrar de novo" : "OK";
        AlertDialog.Builder b = new AlertDialog.Builder(c)
                .setTitle(e.title)
                .setView(sv)
                .setPositiveButton(primary, (d, w) -> {
                    if (e.canRetryWithoutLogin && retry != null) retry.run();
                    else if (e.needsNewLogin && relogin != null) relogin.run();
                })
                .setNegativeButton("Copiar diagnóstico", (d, w) -> copy(c, e.report()));
        if (e.helpUrl != null) b.setNeutralButton(e.helpLabel != null ? e.helpLabel : "Abrir página oficial", null);
        AlertDialog dlg = b.create();
        dlg.show();
        if (e.helpUrl != null) {
            Button n = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (n != null) n.setOnClickListener(v -> Tools.openURL(a, e.helpUrl));
        }
    }

    public static void copy(Context c, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("JAVA CRAFT X — diagnóstico", text));
            Toast.makeText(c, "Diagnóstico copiado (sem tokens)", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }
}
