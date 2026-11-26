package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** UNIVERSAL VERSION HUB: catálogo oficial, filtros, instalação real pelo motor, perfis e matriz de testes. */
public class VersionHubFragment extends Fragment {
    public static final String TAG = "JCX_VERSION_HUB";

    private static final String[][] FILTERS = {
            {"all", "Todas"}, {"installed", "Instaladas"}, {"not_installed", "Não instaladas"}, {"release", "Releases"},
            {"snapshot", "Snapshots"}, {"historic", "Históricas"}, {"favorites", "Favoritas"},
            {"compatible", "Compatíveis"}, {"incompatible", "Incompatíveis"}};

    private VersionCatalog.Catalog mCatalog;
    private final List<VersionCatalog.Entry> mShown = new ArrayList<>();
    private String mFilter = "release";
    private String mQuery = "";
    private Set<String> mFavorites;
    private Adapter mAdapter;
    private TextView mHeader;
    private LinearLayout mChips;
    private int mDeviceRam;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        mDeviceRam = Tools.getTotalDeviceMemory(c);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(net.kdt.pojavlaunch.R.drawable.jcx_background);
        int p = JcxUi.dp(c, 16);
        root.setPadding(p, p, p, 0);
        TextView title = JcxUi.text(c, "VERSION HUB", 22, JcxUi.TEXT, true);
        title.setLetterSpacing(0.08f);
        root.addView(title);
        mHeader = JcxUi.text(c, "Consultando o manifesto oficial da Mojang…", 12, JcxUi.CYAN, false);
        root.addView(mHeader);

        EditText search = new EditText(c);
        search.setHint("Pesquisar versão (ex.: 1.20.1, 24w, b1.7, a1.2)");
        search.setSingleLine(true);
        search.setTextColor(JcxUi.TEXT);
        search.setHintTextColor(JcxUi.TEXT_DIM);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { mQuery = s.toString().trim().toLowerCase(Locale.ROOT); applyFilter(); }
        });
        root.addView(search);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        mChips = JcxUi.row(c);
        hs.addView(mChips);
        root.addView(hs);
        buildChips();

        RecyclerView rv = new RecyclerView(c);
        rv.setLayoutManager(new LinearLayoutManager(c));
        rv.setHasFixedSize(true);
        rv.setItemAnimator(null);
        mAdapter = new Adapter();
        rv.setAdapter(mAdapter);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rlp.topMargin = JcxUi.dp(c, 8);
        root.addView(rv, rlp);

        mFavorites = VersionCatalog.favorites(c);
        load();
        return root;
    }

    private void buildChips() {
        Context c = requireContext();
        mChips.removeAllViews();
        for (String[] f : FILTERS) {
            boolean on = f[0].equals(mFilter);
            Button b = JcxUi.button(c, f[1], on, on ? JcxUi.CYAN : JcxUi.PURPLE);
            b.setOnClickListener(v -> { mFilter = f[0]; buildChips(); applyFilter(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = JcxUi.dp(c, 6);
            lp.topMargin = JcxUi.dp(c, 6);
            mChips.addView(b, lp);
        }
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        JcxUi.bg(() -> {
            VersionCatalog.Catalog cat = null;
            String err = null;
            try { cat = VersionCatalog.load(app); } catch (Exception e) { err = e.getMessage(); }
            final VersionCatalog.Catalog fc = cat;
            final String fe = err;
            JcxUi.ui(() -> {
                if (!isAdded()) return;
                if (fc == null) { mHeader.setText("Não foi possível carregar o catálogo: " + fe); mHeader.setTextColor(JcxUi.BAD); return; }
                mCatalog = fc;
                int installed = 0;
                for (VersionCatalog.Entry e : fc.entries) if (VersionCatalog.installState(e.id) == 2) installed++;
                StringBuilder sb = new StringBuilder();
                sb.append(fc.entries.size()).append(" versões • última release ").append(fc.latestRelease)
                        .append(" • último snapshot ").append(fc.latestSnapshot).append(" • ").append(installed).append(" instalada(s)");
                if (fc.fromCache) sb.append("\nSem internet: mostrando o catálogo salvo.");
                if (!fc.newSinceLastVisit.isEmpty()) sb.append("\nNOVO desde a última visita: ").append(fc.newSinceLastVisit);
                mHeader.setText(sb.toString());
                mHeader.setTextColor(fc.newSinceLastVisit.isEmpty() ? JcxUi.CYAN : JcxUi.GOOD);
                applyFilter();
            });
        });
    }

    private boolean matches(VersionCatalog.Entry e) {
        if (!mQuery.isEmpty() && !e.id.toLowerCase(Locale.ROOT).contains(mQuery)) return false;
        String cat = e.category();
        String[] rec = VersionCatalog.testRecord(requireContext(), e.id);
        switch (mFilter) {
            case "installed": return VersionCatalog.installState(e.id) > 0;
            case "not_installed": return VersionCatalog.installState(e.id) == 0;
            case "release": return "release".equals(cat);
            case "snapshot": return cat.equals("snapshot") || cat.equals("pre-release") || cat.equals("release-candidate") || cat.equals("experimental");
            case "historic": return cat.startsWith("old_");
            case "favorites": return mFavorites.contains(e.id);
            case "compatible": return rec != null && ("COMPATIBLE".equals(rec[0]) || "LIMITED".equals(rec[0]));
            case "incompatible": return rec != null && "UNSUPPORTED".equals(rec[0]);
            default: return true;
        }
    }

    private void applyFilter() {
        if (mCatalog == null || !isAdded()) return;
        mShown.clear();
        // fixa as mais recentes no topo
        for (VersionCatalog.Entry e : mCatalog.entries) {
            if ((e.id.equals(mCatalog.latestRelease) || e.id.equals(mCatalog.latestSnapshot)) && matches(e)) mShown.add(e);
        }
        for (VersionCatalog.Entry e : mCatalog.entries) {
            if (e.id.equals(mCatalog.latestRelease) || e.id.equals(mCatalog.latestSnapshot)) continue;
            if (matches(e)) mShown.add(e);
        }
        mAdapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ lista
    private final class Holder extends RecyclerView.ViewHolder {
        final TextView title, sub, state;
        Holder(LinearLayout v, TextView title, TextView sub, TextView state) { super(v); this.title = title; this.sub = sub; this.state = state; }
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout card = JcxUi.card(c, JcxUi.PURPLE);
            card.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            ((RecyclerView.LayoutParams) card.getLayoutParams()).bottomMargin = JcxUi.dp(c, 8);
            TextView t = JcxUi.text(c, "", 16, JcxUi.TEXT, true);
            TextView s = JcxUi.label(c, "");
            TextView st = JcxUi.label(c, "");
            card.addView(t);
            card.addView(s);
            card.addView(st);
            return new Holder(card, t, s, st);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            VersionCatalog.Entry e = mShown.get(position);
            String badge = e.id.equals(mCatalog.latestRelease) ? "  • LATEST RELEASE" : e.id.equals(mCatalog.latestSnapshot) ? "  • LATEST SNAPSHOT" : "";
            h.title.setText((mFavorites.contains(e.id) ? "★ " : "") + e.id + badge);
            String date = e.releaseTime != null && e.releaseTime.length() >= 10 ? e.releaseTime.substring(0, 10) : "";
            h.sub.setText(VersionCatalog.categoryLabel(e.category()) + (date.isEmpty() ? "" : "  •  " + date));
            int st = VersionCatalog.installState(e.id);
            String[] rec = VersionCatalog.testRecord(h.itemView.getContext(), e.id);
            String compat = rec != null ? rec[0] : "UNKNOWN";
            h.state.setText((st == 2 ? "Instalada" : st == 1 ? "Instalação incompleta" : "Não instalada") + "  •  " + compat);
            h.state.setTextColor(st == 2 ? JcxUi.GOOD : st == 1 ? JcxUi.WARN : JcxUi.TEXT_DIM);
            h.itemView.setOnClickListener(v -> openDetails(e));
        }

        @Override public int getItemCount() { return mShown.size(); }
    }

    // ------------------------------------------------------------------ detalhes
    private void openDetails(VersionCatalog.Entry e) {
        Context c = requireContext();
        AlertDialog wait = new AlertDialog.Builder(c).setTitle(e.id).setMessage("Lendo metadados oficiais…").show();
        JcxUi.bg(() -> {
            VersionCatalog.Details d = null;
            String err = null;
            try { d = VersionCatalog.details(e); } catch (Exception ex) { err = ex.getMessage(); }
            final VersionCatalog.Details fd = d;
            final String fe = err;
            JcxUi.ui(() -> {
                wait.dismiss();
                if (isAdded()) showDetails(e, fd, fe);
            });
        });
    }

    private void showDetails(VersionCatalog.Entry e, @Nullable VersionCatalog.Details d, @Nullable String err) {
        Context c = requireContext();
        VersionCatalog.Verdict v = VersionCatalog.classify(c, e, d, mDeviceRam);
        int st = VersionCatalog.installState(e.id);
        StringBuilder sb = new StringBuilder();
        sb.append("Tipo: ").append(VersionCatalog.categoryLabel(e.category())).append('\n');
        if (e.releaseTime != null && !e.releaseTime.isEmpty()) sb.append("Publicação: ").append(e.releaseTime.replace('T', ' ').replace("+00:00", " UTC")).append('\n');
        if (d != null) {
            sb.append("Java necessário: ").append(d.javaMajor).append('\n');
            if (d.clientSize > 0) sb.append("Client JAR: ").append(JcxUi.size(d.clientSize)).append('\n');
            if (d.assetsSize > 0) sb.append("Assets: ").append(JcxUi.size(d.assetsSize)).append(" (índice ").append(d.assetIndex).append(")\n");
            if (d.libraries >= 0) sb.append("Bibliotecas: ").append(d.libraries).append('\n');
            if (d.inheritsFrom != null && !d.inheritsFrom.isEmpty()) sb.append("Baseada em: ").append(d.inheritsFrom).append('\n');
        } else if (err != null) {
            sb.append("Metadados indisponíveis: ").append(err).append('\n');
        }
        sb.append("Instalação: ").append(st == 2 ? "instalada" : st == 1 ? "incompleta (use Verificar/Reparar)" : "não instalada").append('\n');
        sb.append("\nCompatibilidade: ").append(VersionCatalog.label(v.compat)).append('\n');
        for (String n : v.notes) sb.append("• ").append(n).append('\n');

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = JcxUi.dp(c, 20);
        box.setPadding(p, p / 2, p, 0);
        TextView info = JcxUi.label(c, sb.toString());
        info.setTextColor(JcxUi.TEXT);
        box.addView(info);
        CheckBox isolate = new CheckBox(c);
        isolate.setText("Instância isolada (pasta própria para mundos, mods e configurações)");
        isolate.setChecked(true);
        box.addView(isolate);
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(box);

        AlertDialog dlg = new AlertDialog.Builder(c).setTitle("Minecraft " + e.id).setView(sv)
                .setNegativeButton("Fechar", null).create();
        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.VERTICAL);
        Button profile = JcxUi.button(c, "Criar perfil e selecionar", true);
        profile.setEnabled(v.compat != VersionCatalog.Compat.UNSUPPORTED);
        profile.setOnClickListener(x -> { createProfile(e.id, isolate.isChecked()); dlg.dismiss(); });
        Button install = JcxUi.button(c, st == 2 ? "Verificar / Reparar arquivos" : "Instalar agora", false);
        install.setEnabled(v.compat != VersionCatalog.Compat.UNSUPPORTED && e.inManifest || st > 0);
        install.setOnClickListener(x -> { dlg.dismiss(); install(e, d); });
        Button fav = JcxUi.button(c, mFavorites.contains(e.id) ? "Remover dos favoritos" : "Favoritar", false);
        fav.setOnClickListener(x -> { VersionCatalog.toggleFavorite(c, e.id); mFavorites = VersionCatalog.favorites(c); applyFilter(); dlg.dismiss(); });
        Button test = JcxUi.button(c, "Registrar resultado de teste", false);
        test.setOnClickListener(x -> { dlg.dismiss(); recordTest(e); });
        Button remove = JcxUi.button(c, "Remover instalação", false, JcxUi.BAD);
        remove.setEnabled(st > 0);
        remove.setOnClickListener(x -> { dlg.dismiss(); remove(e.id); });
        actions.addView(JcxUi.buttonRow(c, profile));
        actions.addView(JcxUi.buttonRow(c, install, fav));
        actions.addView(JcxUi.buttonRow(c, test, remove));
        box.addView(actions);
        dlg.show();
    }

    private void createProfile(String id, boolean isolated) {
        Context c = requireContext();
        try {
            LauncherProfiles.load();
            MinecraftProfile p = new MinecraftProfile();
            p.name = "Minecraft " + id;
            p.lastVersionId = id;
            if (isolated) {
                String folder = JcxPaths.safeFileName(id);
                p.gameDir = Tools.LAUNCHERPROFILES_RTPREFIX + "instances/" + (folder != null ? folder : "instancia");
                //noinspection ResultOfMethodCallIgnored
                Tools.getGameDirPath(p).mkdirs();
            }
            String key = LauncherProfiles.getFreeProfileKey();
            LauncherProfiles.mainProfileJson.profiles.put(key, p);
            LauncherProfiles.write();
            LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key).apply();
            Toast.makeText(c, "Perfil \"" + p.name + "\" criado e selecionado. Toque em JOGAR para instalar e iniciar.", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(c, "Falha ao criar perfil: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void install(VersionCatalog.Entry e, @Nullable VersionCatalog.Details d) {
        Context c = requireContext();
        if (!Tools.hasOnlineProfile() && !Tools.isDemoProfile(c)) {
            new AlertDialog.Builder(c).setTitle("Conta necessária")
                    .setMessage("Os arquivos do Minecraft são baixados dos servidores da Mojang para contas Microsoft. Adicione sua conta (ou use o modo Demo oficial) e tente de novo.")
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        if (d != null && d.clientSize > 0) {
            long need = d.clientSize + Math.max(0, d.assetsSize) + 150L * 1024 * 1024;
            long free = JcxPaths.freeBytes(new File(Tools.DIR_GAME_HOME));
            if (free >= 0 && free < need) {
                Toast.makeText(c, "Espaço insuficiente: precisa de ~" + JcxUi.size(need) + ", livre " + JcxUi.size(free), Toast.LENGTH_LONG).show();
                return;
            }
        }
        mHeader.setText("Instalando " + e.id + "… (progresso na barra inferior)");
        mHeader.setTextColor(JcxUi.CYAN);
        String id = AsyncMinecraftDownloader.normalizeVersionId(e.id);
        new MinecraftDownloader().start(requireActivity(), AsyncMinecraftDownloader.getListedVersion(id), id,
                new AsyncMinecraftDownloader.DoneListener() {
                    @Override public void onDownloadDone() {
                        JcxUi.ui(() -> {
                            if (!isAdded()) return;
                            boolean ok = VersionCatalog.installState(e.id) == 2;
                            mHeader.setText(ok ? e.id + " instalada e verificada. Crie/selecione um perfil e toque em JOGAR."
                                    : e.id + ": downloads concluídos, mas arquivos principais não encontrados.");
                            mHeader.setTextColor(ok ? JcxUi.GOOD : JcxUi.WARN);
                            applyFilter();
                        });
                    }
                    @Override public void onDownloadFailed(Throwable t) {
                        JcxUi.ui(() -> {
                            if (!isAdded()) return;
                            mHeader.setText("Falha ao instalar " + e.id + ": " + t.getMessage());
                            mHeader.setTextColor(JcxUi.BAD);
                        });
                    }
                });
    }

    private void remove(String id) {
        Context c = requireContext();
        List<String> users = new ArrayList<>();
        try {
            LauncherProfiles.load();
            for (Map.Entry<String, MinecraftProfile> en : LauncherProfiles.mainProfileJson.profiles.entrySet())
                if (id.equals(en.getValue().lastVersionId)) users.add(en.getValue().name != null ? en.getValue().name : en.getKey());
        } catch (Exception ignored) {}
        new AlertDialog.Builder(c).setTitle("Remover " + id + "?")
                .setMessage("Apaga a pasta versions/" + id + ". Mundos, mods, bibliotecas e assets compartilhados NÃO são apagados."
                        + (users.isEmpty() ? "" : "\n\nUsada pelos perfis: " + users + " (eles precisarão reinstalar ao jogar)."))
                .setPositiveButton("Remover", (dd, w) -> {
                    boolean ok = JcxFiles.deleteRecursive(VersionCatalog.versionDir(id));
                    Toast.makeText(c, ok ? id + " removida" : "Não foi possível remover tudo", Toast.LENGTH_LONG).show();
                    applyFilter();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void recordTest(VersionCatalog.Entry e) {
        Context c = requireContext();
        String[] labels = {"Funcionou (COMPATIBLE)", "Funcionou com problemas (LIMITED)", "Não funcionou (UNSUPPORTED)", "Apagar registro"};
        new AlertDialog.Builder(c).setTitle("Resultado do teste de " + e.id)
                .setItems(labels, (d, w) -> {
                    if (w == 3) {
                        c.getSharedPreferences("jcx_versions", Context.MODE_PRIVATE).edit().remove("test_" + e.id).apply();
                    } else {
                        VersionCatalog.Compat r = w == 0 ? VersionCatalog.Compat.COMPATIBLE : w == 1 ? VersionCatalog.Compat.LIMITED : VersionCatalog.Compat.UNSUPPORTED;
                        VersionCatalog.recordTest(c, e.id, r, JcxPaths.renderer(), "");
                    }
                    applyFilter();
                    Toast.makeText(c, "Matriz de compatibilidade atualizada", Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}
