package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JAVA CRAFT X — componentes visuais próprios (cartões, botões, títulos),
 * criados em código para manter a identidade preto / roxo / azul neon / ciano.
 */
public final class JcxUi {
    public static final int BLACK = 0xFF05050A;
    public static final int GRAPHITE = 0xFF14141F;
    public static final int GRAPHITE_LIGHT = 0xFF1E1E2E;
    public static final int PURPLE = 0xFF8B2CFF;
    public static final int BLUE = 0xFF2F6BFF;
    public static final int CYAN = 0xFF00E5FF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFA9B4D0;
    public static final int GOOD = 0xFF3DFFA8;
    public static final int WARN = 0xFFFFB020;
    public static final int BAD = 0xFFFF4D6D;

    private static final ExecutorService BG = Executors.newFixedThreadPool(3);
    private static volatile Handler sMain; // criado sob demanda (mantém size()/count() testáveis na JVM)

    private JcxUi() {}

    public static void bg(Runnable r) { BG.execute(r); }
    public static void ui(Runnable r) {
        Handler h = sMain;
        if (h == null) { h = new Handler(Looper.getMainLooper()); sMain = h; }
        h.post(r);
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Tela padrão: fundo JCX, título com barra de destaque e corpo rolável. */
    public static final class Screen {
        public final ScrollView root;
        public final LinearLayout body;
        Screen(ScrollView root, LinearLayout body) { this.root = root; this.body = body; }
    }

    public static Screen screen(Context c, String title, String subtitle) {
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.jcx_background);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        body.setPadding(p, p, p, p * 2);
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = text(c, title.toUpperCase(Locale.ROOT), 22, TEXT, true);
        t.setLetterSpacing(0.08f);
        body.addView(t);
        if (subtitle != null) {
            TextView s = text(c, subtitle, 12, CYAN, false);
            s.setLetterSpacing(0.04f);
            body.addView(s);
        }
        View line = new View(c);
        line.setBackgroundResource(R.drawable.jcx_header_line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 2));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 12);
        body.addView(line, lp);
        return new Screen(scroll, body);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            Typeface tf = null;
            try { tf = ResourcesCompat.getFont(c, R.font.noto_sans_bold); } catch (Throwable ignored) {}
            t.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    public static TextView label(Context c, CharSequence s) {
        TextView t = text(c, s, 13, TEXT_DIM, false);
        t.setPadding(0, dp(c, 2), 0, dp(c, 2));
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.ROOT), 12, PURPLE, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(c, 14), 0, dp(c, 6));
        return t;
    }

    public static GradientDrawable rounded(int fill, int stroke, Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    /** Cartão grafite com borda na cor de destaque. */
    public static LinearLayout card(Context c, int accent) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        l.setBackground(rounded(GRAPHITE, (accent & 0x00FFFFFF) | 0x66000000, c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private static Drawable buttonBg(Context c, boolean primary, int accent) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable normal;
        GradientDrawable pressed;
        if (primary) {
            normal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{PURPLE, BLUE});
            normal.setCornerRadius(dp(c, 12));
            normal.setStroke(dp(c, 1), 0x8000E5FF);
            pressed = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF5A12B8, 0xFF1E47B8});
            pressed.setCornerRadius(dp(c, 12));
            pressed.setStroke(dp(c, 2), CYAN);
        } else {
            normal = rounded(GRAPHITE_LIGHT, (accent & 0x00FFFFFF) | 0x99000000, c, 12);
            pressed = rounded(0xFF2A2A40, accent, c, 12);
        }
        GradientDrawable disabled = rounded(0xFF22222E, 0x33FFFFFF, c, 12);
        s.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static Button button(Context c, String label, boolean primary) {
        return button(c, label, primary, CYAN);
    }

    public static Button button(Context c, String label, boolean primary, int accent) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setBackground(buttonBg(c, primary, accent));
        int ph = dp(c, 12);
        b.setPadding(ph, 0, ph, 0);
        b.setMinHeight(dp(c, 42));
        b.setMinimumHeight(dp(c, 42));
        b.setStateListAnimator(null);
        return b;
    }

    /** Adiciona botões lado a lado com peso igual. */
    public static LinearLayout buttonRow(Context c, View... views) {
        LinearLayout r = row(c);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(c, 8);
            r.addView(views[i], lp);
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(c, 8);
        r.setLayoutParams(rp);
        return r;
    }

    public static ProgressBar progress(Context c) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(1000);
        p.setIndeterminate(false);
        return p;
    }

    public static ProgressBar spinner(Context c) {
        ProgressBar p = new ProgressBar(c);
        p.setIndeterminate(true);
        return p;
    }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    public static String count(long n) {
        if (n >= 1000000) return String.format(Locale.ROOT, "%.1fM", n / 1000000.0);
        if (n >= 1000) return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        return String.valueOf(n);
    }
}
