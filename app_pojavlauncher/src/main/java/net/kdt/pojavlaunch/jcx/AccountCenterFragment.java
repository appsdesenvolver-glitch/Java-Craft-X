package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ACCOUNT CENTER: contas salvas, tipo de licença (jogo completo / Demo oficial / local),
 * estado da sessão, login pendente, último diagnóstico e explicação de Conta Microsoft x
 * Perfil Xbox x Assinatura x Licença x Demo.
 */
public class AccountCenterFragment extends Fragment {
    public static final String TAG = "JCX_ACCOUNT_CENTER";

    private LinearLayout mBody;
    private final ExtraListener<Boolean> mChanged = (k, v) -> { JcxUi.ui(this::rebuild); return false; };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JcxUi.Screen s = JcxUi.screen(requireContext(), "Central de Contas", "Sessão, licença, Demo oficial e diagnóstico");
        mBody = s.body;
        rebuild();
        return s.root;
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.addExtraListener(JcxAccounts.EXTRA_ACCOUNTS_CHANGED, mChanged);
        rebuild();
    }

    @Override
    public void onPause() {
        super.onPause();
        ExtraCore.removeExtraListenerFromValue(JcxAccounts.EXTRA_ACCOUNTS_CHANGED, mChanged);
    }

    private List<MinecraftAccount> accounts() {
        List<MinecraftAccount> out = new ArrayList<>();
        File dir = new File(Tools.DIR_ACCOUNT_NEW);
        String[] names = dir.list();
        if (names != null) for (String n : names) {
            if (!n.endsWith(".json")) continue;
            MinecraftAccount a = MinecraftAccount.load(n.substring(0, n.length() - 5));
            if (a != null) out.add(a);
        }
        return out;
    }

    private void rebuild() {
        if (!isAdded() || mBody == null) return;
        Context c = requireContext();
        // mantém título/subtítulo/linha (3 primeiras views)
        while (mBody.getChildCount() > 3) mBody.removeViewAt(3);

        MinecraftAccount current = JcxAccounts.current(c);
        List<MinecraftAccount> list = accounts();

        // Login pendente
        if (JcxAuthStore.hasPending()) {
            LinearLayout p = JcxUi.card(c, JcxUi.WARN);
            p.addView(JcxUi.text(c, "Login pendente", 16, JcxUi.WARN, true));
            p.addView(JcxUi.label(c, "Você já autorizou a Microsoft, mas o login parou numa etapa seguinte (por exemplo, faltava o perfil Xbox). Depois de resolver, toque em Tentar de novo — sem digitar a senha."));
            Button retry = JcxUi.button(c, "Tentar de novo", true);
            retry.setOnClickListener(v -> { ExtraCore.setValue(JcxAccounts.EXTRA_RETRY_LOGIN, true); toast("Tentando de novo…"); });
            Button drop = JcxUi.button(c, "Descartar", false, JcxUi.BAD);
            drop.setOnClickListener(v -> { JcxAuthStore.clearPending(); rebuild(); });
            p.addView(JcxUi.buttonRow(c, retry, drop));
            mBody.addView(p);
        }

        // Contas
        mBody.addView(JcxUi.section(c, list.isEmpty() ? "Nenhuma conta salva" : "Contas salvas (" + list.size() + ")"));
        if (list.isEmpty()) mBody.addView(JcxUi.label(c, JcxAccounts.isGuest()
                ? "Modo convidado ativo: você usa as abas do launcher, mas para iniciar o Minecraft é preciso entrar com uma conta Microsoft."
                : "Adicione uma conta Microsoft abaixo."));
        boolean hasDemo = false;
        for (MinecraftAccount a : list) {
            boolean sel = current != null && current.username.equals(a.username);
            int accent = a.isDemo() ? JcxUi.CYAN : a.isLocal() ? JcxUi.TEXT_DIM : JcxUi.GOOD;
            if (a.isDemo()) hasDemo = true;
            LinearLayout card = JcxUi.card(c, sel ? JcxUi.PURPLE : accent);
            String name = a.isDemo() ? "Demo oficial (jogador \"Player\")" : a.username;
            card.addView(JcxUi.text(c, (sel ? "● " : "") + name, 16, JcxUi.TEXT, true));
            TextView kind = JcxUi.label(c, a.isDemo() ? "Conta Microsoft sem o Minecraft Java → Demo oficial (~100 min, só um jogador, sem servidores)"
                    : a.isLocal() ? "Perfil local (offline, não entra em servidores online)"
                    : "Conta Microsoft com Minecraft: Java Edition → jogo completo");
            kind.setTextColor(accent);
            card.addView(kind);
            card.addView(JcxUi.label(c, JcxAccounts.sessionState(a)));
            List<View> btns = new ArrayList<>();
            if (!sel) {
                Button use = JcxUi.button(c, a.isDemo() ? "Jogar a Demo" : "Usar", true);
                use.setOnClickListener(v -> {
                    ExtraCore.setValue(JcxAccounts.EXTRA_SELECT_ACCOUNT, a.username);
                    Tools.backToMainMenu(requireActivity());
                });
                btns.add(use);
            }
            if (a.isMicrosoft) {
                Button ref = JcxUi.button(c, "Renovar sessão", false);
                ref.setOnClickListener(v -> {
                    if (!Tools.isOnline(c)) { toast("Sem internet"); return; }
                    ExtraCore.setValue(JcxAccounts.EXTRA_REFRESH_ACCOUNT, a.username);
                    toast("Renovando sem pedir senha…");
                });
                btns.add(ref);
            }
            Button rm = JcxUi.button(c, "Remover", false, JcxUi.BAD);
            rm.setOnClickListener(v -> confirmRemove(a));
            btns.add(rm);
            card.addView(JcxUi.buttonRow(c, btns.toArray(new View[0])));
            mBody.addView(card);
        }

        // Ações
        mBody.addView(JcxUi.section(c, "Adicionar"));
        Button ms = JcxUi.button(c, "Conta Microsoft", true);
        ms.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        Button other = JcxUi.button(c, "Outra conta Microsoft", false);
        other.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, MicrosoftLoginFragment.freshArgs()));
        mBody.addView(JcxUi.buttonRow(c, ms, other));
        mBody.addView(JcxUi.label(c, "A sessão da página oficial da Microsoft fica salva no app: se você marcar \"Manter conectado\", as próximas autorizações pedem só um toque. \"Outra conta\" limpa essa sessão."));
        Button guest = JcxUi.button(c, JcxAccounts.isGuest() ? "Sair do modo convidado" : "Modo convidado", false, JcxUi.TEXT_DIM);
        guest.setOnClickListener(v -> { JcxAccounts.setGuest(!JcxAccounts.isGuest()); rebuild(); });
        mBody.addView(JcxUi.buttonRow(c, guest));

        // Demo oficial
        mBody.addView(JcxUi.section(c, "Demo oficial gratuita"));
        LinearLayout demo = JcxUi.card(c, JcxUi.CYAN);
        demo.addView(JcxUi.label(c, "É a única forma oficial e gratuita de jogar o Minecraft: Java Edition. Ela é liberada pela Mojang para contas Microsoft que NÃO possuem o jogo: o launcher detecta isso sozinho no login e cria a conta \"Demo oficial\"."));
        demo.addView(JcxUi.label(c, "Limites (definidos pela Mojang, não alterados pelo launcher): cerca de 100 minutos / 5 dias de jogo por mundo, modo Sobrevivência, sem comandos e sem servidores online (LAN permitida)."));
        demo.addView(JcxUi.label(c, hasDemo ? "Você já tem uma conta Demo salva: toque em \"Jogar a Demo\" nela."
                : "Para ver a Demo: entre com uma conta Microsoft que não tenha o Minecraft Java (use \"Outra conta Microsoft\" se a sua já tiver o jogo)."));
        mBody.addView(demo);

        // Último diagnóstico
        JSONObject err = JcxAuthStore.lastError();
        if (err != null) {
            mBody.addView(JcxUi.section(c, "Último erro de login"));
            LinearLayout e = JcxUi.card(c, JcxUi.BAD);
            e.addView(JcxUi.label(c, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(err.optLong("at")))));
            TextView rep = JcxUi.text(c, err.optString("report"), 12, JcxUi.TEXT, false);
            rep.setTextIsSelectable(true);
            e.addView(rep);
            Button copy = JcxUi.button(c, "Copiar diagnóstico", false);
            copy.setOnClickListener(v -> JcxAuthDialog.copy(c, err.optString("report")));
            e.addView(JcxUi.buttonRow(c, copy));
            mBody.addView(e);
        } else {
            JSONObject ok = JcxAuthStore.lastSuccess();
            if (ok != null) mBody.addView(JcxUi.label(c, "Último login concluído: " + ok.optString("user").replace("Demo.Player", "Demo oficial") + " em "
                    + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(ok.optLong("at")))));
        }

        // Explicação
        mBody.addView(JcxUi.section(c, "Entenda o login"));
        LinearLayout ex = JcxUi.card(c, JcxUi.PURPLE);
        ex.addView(JcxUi.label(c, "• Conta Microsoft: seu e-mail/Outlook/Hotmail. O login é feito na página oficial da Microsoft; o launcher nunca vê nem guarda sua senha."));
        ex.addView(JcxUi.label(c, "• Perfil Xbox: um gamertag ligado à conta Microsoft. É GRATUITO e obrigatório para o Minecraft Java (o erro \"falta perfil Xbox\" se resolve criando um)."));
        ex.addView(JcxUi.label(c, "• Assinatura Xbox (Game Pass): paga e opcional. Só é necessária se você quiser jogar o Minecraft pelo Game Pass em vez de comprar."));
        ex.addView(JcxUi.label(c, "• Licença do Minecraft Java: a compra do \"Minecraft: Java & Bedrock Edition for PC\" (ou o Game Pass PC). Só o Bedrock de celular/console NÃO inclui o Java."));
        ex.addView(JcxUi.label(c, "• Demo oficial: o que contas sem a licença Java recebem, gratuitamente, com os limites da Mojang."));
        ex.addView(JcxUi.label(c, "Os tokens das contas ficam cifrados no Android Keystore do aparelho e são renovados automaticamente."));
        mBody.addView(ex);
    }

    private void confirmRemove(MinecraftAccount a) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remover conta?")
                .setMessage("A conta \"" + (a.isDemo() ? "Demo oficial" : a.username) + "\" sai do launcher. Seus mundos NÃO são apagados.")
                .setPositiveButton("Remover", (d, w) -> {
                    View sp = requireActivity().findViewById(R.id.account_spinner);
                    if (sp instanceof mcAccountSpinner) ((mcAccountSpinner) sp).removeAccountByName(a.username);
                    else //noinspection ResultOfMethodCallIgnored
                        new File(Tools.DIR_ACCOUNT_NEW, a.username + ".json").delete();
                    rebuild();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String s) {
        android.widget.Toast.makeText(requireContext(), s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
