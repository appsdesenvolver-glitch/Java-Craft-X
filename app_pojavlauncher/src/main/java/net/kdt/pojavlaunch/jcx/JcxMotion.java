package net.kdt.pojavlaunch.jcx;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Animações do JAVA CRAFT X: curtas, executadas uma vez e na GPU (camada de hardware),
 * sem loops — nada fica animando enquanto o jogo roda.
 */
public final class JcxMotion {
    private JcxMotion() {}

    /** Entrada escalonada dos tiles do dashboard (≈ 0,4 s no total). */
    public static void entrance(ViewGroup container) {
        if (container == null) return;
        float dy = JcxUi.dp(container.getContext(), 14);
        int n = 0;
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(n, 12) * 22L)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .withLayer()
                    .start();
            n++;
        }
    }

    /** Leve "afundar" ao tocar (sem bloquear o clique). */
    @SuppressLint("ClickableViewAccessibility")
    public static void pressFeedback(View v) {
        if (v == null) return;
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
    }

    /** Linha de estado do dashboard com dados reais do perfil selecionado. */
    public static String statusLine() {
        String v = JcxPaths.versionId();
        String mode = XPerformance.currentMode();
        return "Perfil: " + JcxPaths.profileName()
                + (v != null ? "  •  " + v : "")
                + "  •  " + JcxPaths.rendererLabel(JcxPaths.renderer())
                + "  •  Heap " + LauncherPreferences.PREF_RAM_ALLOCATION + " MB"
                + (mode != null ? "  •  " + mode : "");
    }
}
