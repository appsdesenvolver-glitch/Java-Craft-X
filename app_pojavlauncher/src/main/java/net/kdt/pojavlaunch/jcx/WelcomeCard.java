package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.text.DateFormat;
import java.util.Date;

/** Cartão "Bem-vindo de volta" no dashboard: só aparece se o último mundo jogado ainda existir. */
public final class WelcomeCard {
    private static final String TAG = "jcx_welcome_card";
    private WelcomeCard() {}

    public static void bind(View root, FragmentActivity activity) {
        if (root == null) return;
        View news = root.findViewById(R.id.news_button);
        View status = root.findViewById(R.id.jcx_status);
        if (news == null || status == null || !(news.getParent() instanceof ConstraintLayout)) return;
        ConstraintLayout parent = (ConstraintLayout) news.getParent();
        View old = parent.findViewWithTag(TAG);
        String[] last = JcxSession.lastWorld();
        if (last == null) {
            if (old != null) {
                parent.removeView(old);
                anchorNews(news, R.id.jcx_status);
            }
            return;
        }
        if (old != null) parent.removeView(old);
        Context c = root.getContext();
        LinearLayout card = JcxUi.card(c, JcxUi.GOOD);
        card.setTag(TAG);
        card.setId(View.generateViewId());
        card.addView(JcxUi.text(c, "BEM-VINDO DE VOLTA", 12, JcxUi.GOOD, true));
        card.addView(JcxUi.text(c, last[0], 17, JcxUi.TEXT, true));
        long when = 0;
        try { when = Long.parseLong(last[3]); } catch (NumberFormatException ignored) {}
        card.addView(JcxUi.label(c, "Seu mundo continua salvo neste aparelho" + (when > 0 ? " • última sessão " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(when)) : "")));
        Button cont = JcxUi.button(c, "Continuar", true);
        cont.setOnClickListener(v -> {
            Toast.makeText(c, "Em Um jogador, \"" + last[0] + "\" é o mundo mais recente da lista", Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
        Button worlds = JcxUi.button(c, "Mundos", false);
        worlds.setOnClickListener(v -> Tools.swapFragment(activity, WorldManagerFragment.class, WorldManagerFragment.TAG, null));
        card.addView(JcxUi.buttonRow(c, cont, worlds));

        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topToBottom = R.id.jcx_status;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        int m = JcxUi.dp(c, 12);
        lp.setMargins(m, JcxUi.dp(c, 8), m, JcxUi.dp(c, 4));
        parent.addView(card, lp);
        anchorNews(news, card.getId());
    }

    private static void anchorNews(View news, int aboveId) {
        ConstraintLayout.LayoutParams nlp = (ConstraintLayout.LayoutParams) news.getLayoutParams();
        nlp.topToBottom = aboveId;
        news.setLayoutParams(nlp);
    }
}
