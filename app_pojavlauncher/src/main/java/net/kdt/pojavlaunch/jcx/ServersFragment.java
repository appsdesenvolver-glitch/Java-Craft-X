package net.kdt.pojavlaunch.jcx;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * X-MULTIPLAYER: edita a lista de servidores do próprio Minecraft (servers.dat do perfil)
 * e consulta o status público de cada servidor. A entrada no servidor acontece no jogo,
 * com a autenticação normal da conta.
 */
public class ServersFragment extends Fragment {
    public static final String TAG = "JCX_SERVERS";

    private LinearLayout mBody;

    private static final class Server {
        String name, ip;
        Nbt.Tag raw;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JcxUi.Screen s = JcxUi.screen(requireContext(), "Multiplayer", "Servidores do perfil " + JcxPaths.profileName());
        mBody = s.body;
        build();
        return s.root;
    }

    private File serversFile() { return new File(JcxPaths.gameDir(), "servers.dat"); }

    private List<Server> load() throws Exception {
        List<Server> list = new ArrayList<>();
        File f = serversFile();
        if (!f.isFile()) return list;
        Nbt.Root r = Nbt.readRaw(f);
        Nbt.Tag servers = r.tag.get("servers");
        if (servers == null || servers.type != Nbt.LIST) return list;
        for (Nbt.Tag t : ((Nbt.ListTag) servers.value).items) {
            if (t.type != Nbt.COMPOUND) continue;
            Server s = new Server();
            s.raw = t;
            s.name = t.get("name") != null ? t.get("name").asString() : "Servidor";
            s.ip = t.get("ip") != null ? t.get("ip").asString() : "";
            list.add(s);
        }
        return list;
    }

    private void save(List<Server> list) throws Exception {
        File f = serversFile();
        Nbt.Root r;
        if (f.isFile()) {
            r = Nbt.readRaw(f);
            JcxFiles.copyFile(f, new File(f.getParentFile(), "servers.dat_jcxbak"));
        } else {
            r = new Nbt.Root();
            r.name = "";
            r.tag = new Nbt.Tag(Nbt.COMPOUND, new LinkedHashMap<String, Nbt.Tag>());
        }
        Nbt.ListTag lt = new Nbt.ListTag();
        lt.elementType = Nbt.COMPOUND;
        for (Server s : list) {
            if (s.raw == null) s.raw = new Nbt.Tag(Nbt.COMPOUND, new LinkedHashMap<String, Nbt.Tag>());
            s.raw.compound().put("name", new Nbt.Tag(Nbt.STRING, s.name));
            s.raw.compound().put("ip", new Nbt.Tag(Nbt.STRING, s.ip));
            lt.items.add(s.raw);
        }
        r.tag.compound().put("servers", new Nbt.Tag(Nbt.LIST, lt));
        Nbt.writeRaw(f, r);
    }

    private void build() {
        if (!isAdded()) return;
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));
        LinearLayout info = JcxUi.card(c, JcxUi.CYAN);
        info.addView(JcxUi.label(c, "Esta é a mesma lista do menu Multijogador do Minecraft. Para entrar, toque em JOGAR e escolha o servidor no jogo. "
                + "Servidores online exigem conta Microsoft com o jogo; perfis locais só entram em servidores em modo offline. Jogos LAN aparecem automaticamente no jogo."));
        mBody.addView(info);
        Button add = JcxUi.button(c, "Adicionar servidor", true);
        add.setOnClickListener(v -> edit(null, -1));
        Button pingAll = JcxUi.button(c, "Verificar todos", false);
        mBody.addView(JcxUi.buttonRow(c, add, pingAll));

        List<Server> list;
        try {
            list = load();
        } catch (Exception e) {
            TextView t = JcxUi.label(c, "servers.dat ilegível: " + e.getMessage() + ". Nada foi alterado.");
            t.setTextColor(JcxUi.BAD);
            mBody.addView(t);
            return;
        }
        if (list.isEmpty()) {
            mBody.addView(JcxUi.label(c, "Nenhum servidor salvo neste perfil."));
            return;
        }
        List<TextView> statusViews = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) mBody.addView(card(list, i, statusViews));
        pingAll.setOnClickListener(v -> { for (int i = 0; i < list.size(); i++) ping(list.get(i).ip, statusViews.get(i)); });
    }

    private View card(List<Server> list, int index, List<TextView> statusViews) {
        Context c = requireContext();
        Server s = list.get(index);
        LinearLayout card = JcxUi.card(c, index == 0 ? JcxUi.CYAN : JcxUi.PURPLE);
        LinearLayout top = JcxUi.row(c);
        ImageView icon = new ImageView(c);
        int sz = JcxUi.dp(c, 44);
        icon.setBackground(JcxUi.rounded(JcxUi.GRAPHITE_LIGHT, 0, c, 8));
        Nbt.Tag iconTag = s.raw != null ? s.raw.get("icon") : null;
        if (iconTag != null && iconTag.asString() != null) {
            try {
                byte[] png = Base64.decode(iconTag.asString(), Base64.DEFAULT);
                Bitmap bm = BitmapFactory.decodeByteArray(png, 0, png.length);
                if (bm != null) icon.setImageBitmap(bm);
            } catch (Exception ignored) {}
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = JcxUi.dp(c, 10);
        top.addView(icon, ilp);
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(JcxUi.text(c, (index == 0 ? "★ " : "") + s.name, 15, JcxUi.TEXT, true));
        txt.addView(JcxUi.label(c, s.ip));
        TextView status = JcxUi.label(c, "Status: toque em Verificar");
        statusViews.add(status);
        txt.addView(status);
        top.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        Button check = JcxUi.button(c, "Verificar", false);
        check.setOnClickListener(v -> ping(s.ip, status));
        Button fav = JcxUi.button(c, index == 0 ? "No topo" : "Topo/Favorito", false);
        fav.setEnabled(index != 0);
        fav.setOnClickListener(v -> {
            list.remove(index);
            list.add(0, s);
            persist(list, "\"" + s.name + "\" agora é o primeiro da lista");
        });
        Button play = JcxUi.button(c, "Jogar", true);
        play.setOnClickListener(v -> {
            if (index != 0) { list.remove(index); list.add(0, s); persist(list, null); }
            Toast.makeText(c, "Abra Multijogador no jogo: \"" + s.name + "\" é o primeiro da lista", Toast.LENGTH_LONG).show();
            net.kdt.pojavlaunch.Tools.backToMainMenu(requireActivity());
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
        card.addView(JcxUi.buttonRow(c, check, fav, play));
        Button edit = JcxUi.button(c, "Editar", false);
        edit.setOnClickListener(v -> edit(list, index));
        Button copy = JcxUi.button(c, "Copiar IP", false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("servidor", s.ip));
            Toast.makeText(c, "Endereço copiado", Toast.LENGTH_SHORT).show();
        });
        Button del = JcxUi.button(c, "Remover", false, JcxUi.BAD);
        del.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Remover \"" + s.name + "\"?")
                .setPositiveButton("Remover", (d, w) -> { list.remove(index); persist(list, "Servidor removido"); })
                .setNegativeButton(android.R.string.cancel, null).show());
        card.addView(JcxUi.buttonRow(c, edit, copy, del));
        return card;
    }

    private void persist(List<Server> list, @Nullable String msg) {
        try {
            save(list);
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Falha ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        build();
    }

    private void ping(String ip, TextView status) {
        status.setText("Status: conectando…");
        status.setTextColor(JcxUi.CYAN);
        JcxUi.bg(() -> {
            String txt;
            int color;
            try {
                ServerPing.Status st = ServerPing.ping(ip, 6000);
                txt = String.format(Locale.ROOT, "Online • %d/%d jogadores • %s%s\n%s", st.online, st.max,
                        st.versionName != null ? st.versionName : "?", st.pingMs >= 0 ? " • " + st.pingMs + " ms" : "",
                        st.motd == null ? "" : st.motd);
                color = JcxUi.GOOD;
            } catch (java.net.UnknownHostException e) {
                txt = "Endereço não encontrado (DNS). Confira o IP; registros SRV não são consultados aqui.";
                color = JcxUi.BAD;
            } catch (java.net.SocketTimeoutException e) {
                txt = "Sem resposta (tempo esgotado). Servidor offline ou bloqueado na sua rede.";
                color = JcxUi.WARN;
            } catch (Exception e) {
                txt = "Não foi possível consultar: " + e.getMessage();
                color = JcxUi.BAD;
            }
            final String ft = txt;
            final int fc = color;
            JcxUi.ui(() -> { status.setText(ft); status.setTextColor(fc); });
        });
    }

    private void edit(@Nullable List<Server> list, int index) {
        Context c = requireContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = JcxUi.dp(c, 20);
        box.setPadding(p, p / 2, p, 0);
        EditText name = new EditText(c);
        name.setHint("Nome");
        name.setSingleLine(true);
        EditText ip = new EditText(c);
        ip.setHint("Endereço (ex.: play.exemplo.com ou 192.168.0.10:25565)");
        ip.setSingleLine(true);
        ip.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (list != null && index >= 0) { name.setText(list.get(index).name); ip.setText(list.get(index).ip); }
        box.addView(name);
        box.addView(ip);
        new AlertDialog.Builder(c).setTitle(index >= 0 ? "Editar servidor" : "Adicionar servidor").setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String a = ip.getText().toString().trim().replace(" ", "");
                    if (a.isEmpty() || a.length() > 255 || a.contains("/")) {
                        Toast.makeText(c, "Endereço inválido", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (n.isEmpty()) n = "Servidor Minecraft";
                    try {
                        List<Server> l = list != null ? list : load();
                        if (index >= 0) { l.get(index).name = n; l.get(index).ip = a; }
                        else { Server s = new Server(); s.name = n; s.ip = a; l.add(s); }
                        persist(l, "Servidor salvo");
                    } catch (Exception e) {
                        Toast.makeText(c, "Falha: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
