package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * MOD CENTER / SHADER LAB / RESOURCE PACKS — conteúdo do Modrinth instalado dentro do launcher,
 * com verificação de versão, loader, dependências, hash e espaço antes de instalar.
 */
public class ContentLabFragment extends Fragment {
    public static final String TAG = "JCX_CONTENT_LAB";
    public static final String ARG_KIND = "kind";
    public static final String KIND_MOD = "mod";
    public static final String KIND_SHADER = "shader";
    public static final String KIND_RESOURCEPACK = "resourcepack";

    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(60);

    private String mKind;
    private LinearLayout mBody;
    private LinearLayout mList;
    private EditText mQuery;
    private CheckBox mOnlyMine;
    private Button mMore;
    private String mCategory;
    private String mSort = "relevance";
    private int mOffset;
    private boolean mShowingInstalled;
    private int mGeneration;

    public static Bundle args(String kind) {
        Bundle b = new Bundle();
        b.putString(ARG_KIND, kind);
        return b;
    }

    private String title() {
        if (KIND_SHADER.equals(mKind)) return "Shader Lab";
        if (KIND_RESOURCEPACK.equals(mKind)) return "Resource Packs";
        return "Mod Center";
    }

    private String folderName() {
        if (KIND_SHADER.equals(mKind)) return "shaderpacks";
        if (KIND_RESOURCEPACK.equals(mKind)) return "resourcepacks";
        return "mods";
    }

    private File folder() { return JcxPaths.sub(folderName()); }

    /** Loaders aceitos pelo perfil atual no formato do Modrinth (somente para mods). */
    private List<String> modLoaders() {
        String l = JcxPaths.loader();
        List<String> out = new ArrayList<>();
        if (l == null) return out;
        out.add(l);
        if ("quilt".equals(l)) out.add("fabric"); // Quilt carrega mods Fabric
        return out;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mKind = getArguments() != null ? getArguments().getString(ARG_KIND, KIND_MOD) : KIND_MOD;
        Context c = requireContext();
        JcxUi.Screen s = JcxUi.screen(c, title(), "Conteúdo do Modrinth • perfil " + JcxPaths.profileName());
        mBody = s.body;
        buildHeader();
        return s.root;
    }

    // ------------------------------------------------------------------ cabeçalho
    private void buildHeader() {
        Context c = requireContext();
        String mc = JcxPaths.mcVersion();
        LinearLayout status = JcxUi.card(c, JcxUi.CYAN);
        status.addView(JcxUi.label(c, "Minecraft: " + (mc != null ? mc : "desconhecida (instale/selecione uma versão)")
                + "  •  Loader: " + (JcxPaths.loader() != null ? JcxPaths.loader() : "nenhum (vanilla)")
                + "  •  Renderizador: " + JcxPaths.rendererLabel(JcxPaths.renderer())));
        for (String[] w : compatibilityNotes()) {
            TextView t = JcxUi.label(c, w[1]);
            t.setTextColor("bad".equals(w[0]) ? JcxUi.BAD : "warn".equals(w[0]) ? JcxUi.WARN : JcxUi.GOOD);
            status.addView(t);
        }
        mBody.addView(status);

        Button explore = JcxUi.button(c, "Explorar", true);
        Button installed = JcxUi.button(c, "Instalados", false);
        explore.setOnClickListener(v -> { mShowingInstalled = false; showExplore(); });
        installed.setOnClickListener(v -> { mShowingInstalled = true; showInstalled(); });
        mBody.addView(JcxUi.buttonRow(c, explore, installed));

        mList = new LinearLayout(c);
        mList.setOrientation(LinearLayout.VERTICAL);
        mBody.addView(mList);
        showExplore();
    }

    /** Avisos honestos de compatibilidade. Cada item: {nível, texto}. */
    private List<String[]> compatibilityNotes() {
        List<String[]> l = new ArrayList<>();
        String r = JcxPaths.renderer();
        if (KIND_MOD.equals(mKind)) {
            if (JcxPaths.loader() == null)
                l.add(new String[]{"bad", "Este perfil não tem mod loader. Crie um perfil Fabric, Quilt, Forge ou NeoForge (editar perfil → versão) para instalar mods."});
            else l.add(new String[]{"good", "Somente versões publicadas para " + JcxPaths.loader() + " e para a sua versão do Minecraft serão oferecidas."});
            if (JcxPaths.modPresent("sodium"))
                l.add(new String[]{"warn", "Sodium detectado: tem problemas conhecidos com alguns renderizadores Android."});
        } else if (KIND_SHADER.equals(mKind)) {
            boolean iris = JcxPaths.modPresent("iris");
            boolean of = JcxPaths.hasOptiFine();
            if (!iris && !of)
                l.add(new String[]{"bad", "Iris ou OptiFine não encontrado no perfil: shaders não serão carregados. Instale o Iris pelo Mod Center (perfil Fabric/Quilt)."});
            else l.add(new String[]{"warn", (iris ? "Iris" : "OptiFine") + " detectado. A compatibilidade de cada shader depende do renderizador e da GPU — não é confirmada."});
            if ("opengles2".equals(r))
                l.add(new String[]{"bad", "Krypton (GL4ES) não oferece os recursos de OpenGL que shaders exigem. Use MobileGlues ou Zink no perfil."});
            else l.add(new String[]{"warn", "Shaders de PC são pesados: em aparelhos de entrada espere falhas ou FPS muito baixo."});
        } else {
            l.add(new String[]{"good", "Resource packs não dependem de loader. Compatibilidade de versão conforme os metadados do autor."});
        }
        return l;
    }

    // ------------------------------------------------------------------ explorar
    private void showExplore() {
        Context c = requireContext();
        mList.removeAllViews();
        LinearLayout searchRow = JcxUi.row(c);
        mQuery = new EditText(c);
        mQuery.setHint("Pesquisar " + title().toLowerCase(Locale.ROOT) + "…");
        mQuery.setSingleLine(true);
        mQuery.setInputType(InputType.TYPE_CLASS_TEXT);
        mQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mQuery.setTextColor(JcxUi.TEXT);
        mQuery.setHintTextColor(JcxUi.TEXT_DIM);
        mQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                newSearch();
                return true;
            }
            return false;
        });
        Button go = JcxUi.button(c, "Buscar", true);
        go.setOnClickListener(v -> newSearch());
        searchRow.addView(mQuery, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.leftMargin = JcxUi.dp(c, 8);
        searchRow.addView(go, gp);
        mList.addView(searchRow);

        Button cat = JcxUi.button(c, mCategory == null ? "Categoria: todas" : "Categoria: " + mCategory, false);
        cat.setOnClickListener(v -> chooseCategory());
        Button sort = JcxUi.button(c, "Ordem: " + sortLabel(mSort), false);
        sort.setOnClickListener(v -> chooseSort());
        mList.addView(JcxUi.buttonRow(c, cat, sort));

        mOnlyMine = new CheckBox(c);
        String mc = JcxPaths.mcVersion();
        mOnlyMine.setText(mc != null ? "Somente compatíveis com Minecraft " + mc : "Versão do perfil desconhecida");
        mOnlyMine.setTextColor(JcxUi.TEXT_DIM);
        mOnlyMine.setChecked(mc != null);
        mOnlyMine.setEnabled(mc != null);
        mOnlyMine.setOnCheckedChangeListener((b, checked) -> newSearch());
        mList.addView(mOnlyMine);

        LinearLayout results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setTag("results");
        mList.addView(results);
        mMore = JcxUi.button(c, "Carregar mais", false);
        mMore.setVisibility(View.GONE);
        mMore.setOnClickListener(v -> loadPage(false));
        mList.addView(JcxUi.buttonRow(c, mMore));
        newSearch();
    }

    private static String sortLabel(String s) {
        switch (s) {
            case "downloads": return "mais baixados";
            case "updated": return "atualizados";
            case "newest": return "novos";
            default: return "relevância";
        }
    }

    private String[] categories() {
        if (KIND_SHADER.equals(mKind)) return new String[]{"potato", "low", "medium", "high", "vanilla-like", "semi-realistic", "realistic", "cartoon", "fantasy", "atmosphere", "shadows"};
        if (KIND_RESOURCEPACK.equals(mKind)) return new String[]{"8x-", "16x", "32x", "64x", "vanilla-like", "simplistic", "realistic", "themed", "decoration", "gui", "font", "audio", "combat", "tweaks"};
        return new String[]{"optimization", "utility", "library", "decoration", "adventure", "technology", "magic", "storage", "worldgen", "mobs", "food", "equipment", "transportation", "social", "game-mechanics"};
    }

    private void chooseCategory() {
        String[] cats = categories();
        String[] items = new String[cats.length + 1];
        items[0] = "todas";
        System.arraycopy(cats, 0, items, 1, cats.length);
        new AlertDialog.Builder(requireContext()).setTitle("Categoria")
                .setItems(items, (d, w) -> { mCategory = w == 0 ? null : cats[w - 1]; showExplore(); })
                .show();
    }

    private void chooseSort() {
        String[] keys = {"relevance", "downloads", "updated", "newest"};
        String[] labels = {"relevância", "mais baixados", "atualizados", "novos"};
        new AlertDialog.Builder(requireContext()).setTitle("Ordenar por")
                .setItems(labels, (d, w) -> { mSort = keys[w]; showExplore(); })
                .show();
    }

    private LinearLayout results() {
        return (LinearLayout) mList.findViewWithTag("results");
    }

    private void newSearch() {
        mOffset = 0;
        LinearLayout r = results();
        if (r != null) r.removeAllViews();
        loadPage(true);
    }

    private void loadPage(boolean fresh) {
        if (!isAdded()) return;
        Context c = requireContext();
        LinearLayout r = results();
        if (r == null) return;
        final int gen = ++mGeneration;
        ProgressBar sp = JcxUi.spinner(c);
        r.addView(sp);
        mMore.setVisibility(View.GONE);
        final String q = mQuery.getText().toString();
        final String mc = (mOnlyMine != null && mOnlyMine.isChecked()) ? JcxPaths.mcVersion() : null;
        final List<String> loaders = KIND_MOD.equals(mKind) ? modLoaders() : null;
        final String cat = mCategory;
        final String sort = mSort;
        final int offset = mOffset;
        JcxUi.bg(() -> {
            List<Modrinth.Hit> hits = null;
            String err = null;
            try {
                hits = Modrinth.search(q, mKind, mc, loaders, cat, sort, offset, 20);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final List<Modrinth.Hit> fh = hits;
            final String fe = err;
            JcxUi.ui(() -> {
                if (!isAdded() || gen != mGeneration) return;
                r.removeView(sp);
                if (fe != null) {
                    TextView t = JcxUi.label(c, "Sem conexão com o Modrinth: " + fe);
                    t.setTextColor(JcxUi.BAD);
                    r.addView(t);
                    return;
                }
                if (fh.isEmpty() && offset == 0) r.addView(JcxUi.label(c, "Nenhum resultado com esses filtros."));
                for (Modrinth.Hit h : fh) r.addView(hitCard(h));
                mOffset += fh.size();
                mMore.setVisibility(fh.size() == 20 ? View.VISIBLE : View.GONE);
            });
        });
    }

    private View hitCard(Modrinth.Hit h) {
        Context c = requireContext();
        LinearLayout card = JcxUi.card(c, JcxUi.BLUE);
        LinearLayout top = JcxUi.row(c);
        ImageView icon = new ImageView(c);
        int sz = JcxUi.dp(c, 52);
        icon.setBackground(JcxUi.rounded(JcxUi.GRAPHITE_LIGHT, 0, c, 10));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = JcxUi.dp(c, 12);
        top.addView(icon, ilp);
        loadIcon(h.iconUrl, icon);
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(JcxUi.text(c, h.title, 15, JcxUi.TEXT, true));
        info.addView(JcxUi.label(c, "por " + h.author + "  •  " + JcxUi.count(h.downloads) + " downloads"
                + (h.license != null && !h.license.isEmpty() ? "  •  " + h.license : "")));
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);
        TextView d = JcxUi.label(c, h.description);
        d.setTextColor(JcxUi.TEXT);
        card.addView(d);
        if (!h.categories.isEmpty()) {
            TextView cats = JcxUi.label(c, "Categorias: " + join(h.categories, ", "));
            cats.setTextSize(11);
            card.addView(cats);
        }
        Button inst = JcxUi.button(c, "Detalhes e instalação", true);
        inst.setOnClickListener(v -> prepareInstall(h.projectId, h.title, h.license, null));
        card.addView(JcxUi.buttonRow(c, inst));
        return card;
    }

    private static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(sep); sb.append(l.get(i)); }
        return sb.toString();
    }

    private void loadIcon(String url, ImageView into) {
        if (url == null) return;
        Bitmap cached = ICONS.get(url);
        if (cached != null) { into.setImageBitmap(cached); return; }
        into.setTag(url);
        JcxUi.bg(() -> {
            try {
                JcxDownloader.checkUrl(url, "modrinth.com");
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                con.setRequestProperty("User-Agent", JcxDownloader.USER_AGENT);
                Bitmap bm;
                try (InputStream in = con.getInputStream()) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    bm = BitmapFactory.decodeStream(in, null, o);
                } finally {
                    con.disconnect();
                }
                if (bm == null) return;
                if (bm.getWidth() > 128) bm = Bitmap.createScaledBitmap(bm, 128, 128 * bm.getHeight() / Math.max(1, bm.getWidth()), true);
                ICONS.put(url, bm);
                final Bitmap fb = bm;
                JcxUi.ui(() -> { if (url.equals(into.getTag())) into.setImageBitmap(fb); });
            } catch (Exception ignored) {
                // ícone é opcional
            }
        });
    }

    // ------------------------------------------------------------------ plano de instalação
    private static final class PlanItem {
        Modrinth.Version version;
        Modrinth.VFile file;
        String title;
        boolean dependency;
        String replacesFile; // atualização: arquivo antigo a remover
    }

    private static final class Plan {
        final List<PlanItem> items = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> blockers = new ArrayList<>();
        String license;
        boolean versionConfirmed = true;
    }

    /** Busca versão compatível e dependências em segundo plano, depois mostra o resumo. */
    private void prepareInstall(String projectId, String title, String license, @Nullable String replacingFile) {
        Context c = requireContext();
        AlertDialog wait = new AlertDialog.Builder(c).setTitle(title).setMessage("Verificando versões, loader e dependências…").setCancelable(false).show();
        final String mc = JcxPaths.mcVersion();
        final List<String> loaders = KIND_MOD.equals(mKind) ? modLoaders() : null;
        JcxUi.bg(() -> {
            Plan plan = new Plan();
            plan.license = license;
            try {
                if (KIND_MOD.equals(mKind) && (loaders == null || loaders.isEmpty())) {
                    plan.blockers.add("O perfil atual é vanilla. Mods precisam de Fabric, Quilt, Forge ou NeoForge.");
                } else if (KIND_MOD.equals(mKind) && mc == null) {
                    plan.blockers.add("Não foi possível identificar a versão do Minecraft do perfil. Instale a versão e tente novamente.");
                } else {
                    List<Modrinth.Version> vs = Modrinth.versions(projectId, mc, loaders);
                    if (vs.isEmpty() && !KIND_MOD.equals(mKind)) {
                        // shaders/resource packs às vezes não listam todas as versões do jogo
                        vs = Modrinth.versions(projectId, null, null);
                        plan.versionConfirmed = false;
                    }
                    Modrinth.Version best = Modrinth.best(vs);
                    if (best == null) {
                        plan.blockers.add("Nenhuma versão publicada para Minecraft " + mc + (loaders != null ? " + " + join(loaders, "/") : "") + ".");
                    } else {
                        PlanItem main = new PlanItem();
                        main.version = best;
                        main.file = best.primaryFile();
                        main.title = title;
                        main.replacesFile = replacingFile;
                        ContentIndex idx = new ContentIndex(folder());
                        ContentIndex.Entry existing = idx.byProject(projectId);
                        if (existing != null && replacingFile == null) {
                            if (best.id.equals(existing.versionId)) plan.blockers.add("Já instalado nesta versão (" + existing.versionNumber + ").");
                            else { main.replacesFile = existing.file; plan.warnings.add("Já existe outra versão instalada (" + existing.versionNumber + "): será substituída."); }
                        }
                        plan.items.add(main);
                        if (!plan.versionConfirmed && mc != null)
                            plan.warnings.add("O autor não marcou compatibilidade com Minecraft " + mc + ". Pode não funcionar.");
                        if (KIND_MOD.equals(mKind)) resolveDeps(best, mc, loaders, idx, plan, 0);
                        if (KIND_SHADER.equals(mKind)) {
                            for (String[] n : compatibilityNotes()) if (!"good".equals(n[0])) plan.warnings.add(n[1]);
                            if (!best.loaders.isEmpty()) plan.warnings.add("Feito para: " + join(best.loaders, ", "));
                        }
                    }
                }
            } catch (Exception e) {
                plan.blockers.add("Erro ao consultar o Modrinth: " + e.getMessage());
            }
            JcxUi.ui(() -> {
                wait.dismiss();
                if (isAdded()) showPlan(title, plan);
            });
        });
    }

    private void resolveDeps(Modrinth.Version v, String mc, List<String> loaders, ContentIndex idx, Plan plan, int depth) throws Exception {
        if (depth > 4) return;
        for (Modrinth.Dependency d : v.dependencies) {
            if ("incompatible".equals(d.type)) {
                if (d.projectId != null && idx.byProject(d.projectId) != null)
                    plan.warnings.add("CONFLITO: " + v.name + " é incompatível com " + idx.byProject(d.projectId).title + ", que está instalado.");
                continue;
            }
            if (!"required".equals(d.type)) continue;
            String pid = d.projectId;
            if (pid != null && idx.byProject(pid) != null) continue;
            boolean planned = false;
            for (PlanItem p : plan.items) if (p.version.projectId.equals(pid)) planned = true;
            if (planned) continue;
            Modrinth.Version dv = null;
            if (d.versionId != null) {
                dv = Modrinth.version(d.versionId);
                pid = dv.projectId;
                if (idx.byProject(pid) != null) continue;
            } else if (pid != null) {
                dv = Modrinth.best(Modrinth.versions(pid, mc, loaders));
            }
            String depTitle = pid != null ? Modrinth.projectTitle(pid) : "?";
            if (dv == null || dv.primaryFile() == null) {
                plan.blockers.add("Dependência obrigatória \"" + depTitle + "\" sem versão para Minecraft " + mc + ".");
                continue;
            }
            PlanItem it = new PlanItem();
            it.version = dv;
            it.file = dv.primaryFile();
            it.title = depTitle;
            it.dependency = true;
            plan.items.add(it);
            resolveDeps(dv, mc, loaders, idx, plan, depth + 1);
        }
    }

    private void showPlan(String title, Plan plan) {
        Context c = requireContext();
        StringBuilder sb = new StringBuilder();
        String mc = JcxPaths.mcVersion();
        sb.append("Minecraft do perfil: ").append(mc != null ? mc : "?").append('\n');
        if (KIND_MOD.equals(mKind)) sb.append("Loader: ").append(JcxPaths.loader() != null ? JcxPaths.loader() : "nenhum").append('\n');
        if (plan.license != null && !plan.license.isEmpty()) sb.append("Licença: ").append(plan.license).append('\n');
        long total = 0;
        if (!plan.items.isEmpty()) {
            sb.append("\nArquivos (destino: ").append(folderName()).append("/):\n");
            for (PlanItem p : plan.items) {
                long sz = p.file.size > 0 ? p.file.size : 0;
                total += sz;
                sb.append(p.dependency ? "  + dependência: " : "  • ").append(p.title).append(" ").append(p.version.versionNumber)
                        .append("\n     ").append(p.file.filename).append(" (").append(JcxUi.size(sz)).append(")")
                        .append("\n     jogo: ").append(shortList(p.version.gameVersions)).append(" • ").append(join(p.version.loaders, ", ")).append('\n');
            }
            sb.append("\nTotal: ").append(JcxUi.size(total)).append(" • verificação ").append("SHA-512/SHA-1").append('\n');
        }
        for (String w : plan.warnings) sb.append("\n⚠ ").append(w);
        for (String b : plan.blockers) sb.append("\n✖ ").append(b);
        AlertDialog.Builder b = new AlertDialog.Builder(c).setTitle(title).setMessage(sb.toString())
                .setNegativeButton(android.R.string.cancel, null);
        if (plan.blockers.isEmpty() && !plan.items.isEmpty())
            b.setPositiveButton(plan.warnings.isEmpty() ? "Instalar" : "Instalar mesmo assim", (d, w) -> runPlan(plan));
        b.show();
    }

    private static String shortList(List<String> l) {
        if (l.size() <= 4) return join(l, ", ");
        return l.get(0) + " … " + l.get(l.size() - 1) + " (" + l.size() + " versões)";
    }

    // ------------------------------------------------------------------ download
    private void runPlan(Plan plan) {
        Context c = requireContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = JcxUi.dp(c, 20);
        box.setPadding(p, p / 2, p, 0);
        TextView label = JcxUi.label(c, "Preparando…");
        ProgressBar bar = JcxUi.progress(c);
        box.addView(label);
        box.addView(bar);
        JcxDownloader dl = new JcxDownloader();
        AlertDialog dlg = new AlertDialog.Builder(c).setTitle("Download Engine").setView(box).setCancelable(false)
                .setNegativeButton("Cancelar", (d, w) -> dl.cancel()).show();
        File dir = folder();
        JcxUi.bg(() -> {
            ContentIndex idx = new ContentIndex(dir);
            String result;
            boolean ok = true;
            int n = 0;
            try {
                for (PlanItem it : plan.items) {
                    n++;
                    final int idxN = n;
                    String safe = JcxPaths.safeFileName(it.file.filename);
                    if (safe == null) throw new java.io.IOException("Nome de arquivo inválido: " + it.file.filename);
                    JcxDownloader.checkUrl(it.file.url, "modrinth.com");
                    File dest = new File(dir, safe);
                    dl.download(it.file.url, dest, it.file.size, it.file.sha1, it.file.sha512, (done, total, speed) -> JcxUi.ui(() -> {
                        label.setText(String.format(Locale.ROOT, "(%d/%d) %s\n%s / %s  •  %s", idxN, plan.items.size(), it.title,
                                JcxUi.size(done), total > 0 ? JcxUi.size(total) : "?", JcxDownloader.speed(speed)));
                        if (total > 0) bar.setProgress((int) (done * 1000 / total));
                    }));
                    if (it.replacesFile != null && !it.replacesFile.equals(safe)) {
                        //noinspection ResultOfMethodCallIgnored
                        new File(dir, it.replacesFile).delete();
                        //noinspection ResultOfMethodCallIgnored
                        new File(dir, it.replacesFile + ".disabled").delete();
                        idx.remove(it.replacesFile);
                    }
                    idx.put(safe, it.version.projectId, it.version.id, it.title, it.version.versionNumber);
                    idx.save();
                }
                result = plan.items.size() == 1 ? "Instalado: " + plan.items.get(0).title
                        : "Instalados " + plan.items.size() + " arquivos (incluindo dependências)";
            } catch (JcxDownloader.Cancelled e) {
                ok = false;
                result = "Download cancelado";
            } catch (Exception e) {
                ok = false;
                result = "Falha: " + e.getMessage();
            }
            final String fr = result;
            final boolean fok = ok;
            JcxUi.ui(() -> {
                dlg.dismiss();
                if (!isAdded()) return;
                Toast.makeText(requireContext(), fr, Toast.LENGTH_LONG).show();
                if (!fok) new AlertDialog.Builder(requireContext()).setTitle(title()).setMessage(fr + "\n\nVocê pode tentar novamente: downloads parciais são retomados.").setPositiveButton(android.R.string.ok, null).show();
                if (mShowingInstalled) showInstalled();
            });
        });
    }

    // ------------------------------------------------------------------ instalados
    private boolean isContentFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.startsWith(".") || n.endsWith(".part")) return false;
        if (KIND_MOD.equals(mKind)) return n.endsWith(".jar") || n.endsWith(".jar.disabled");
        return f.isDirectory() || n.endsWith(".zip") || n.endsWith(".zip.disabled");
    }

    private void showInstalled() {
        Context c = requireContext();
        mList.removeAllViews();
        File dir = folder();
        ContentIndex idx = new ContentIndex(dir);
        idx.prune(dir);
        idx.save();
        File[] files = dir.listFiles();
        List<File> list = new ArrayList<>();
        if (files != null) for (File f : files) if (isContentFile(f)) list.add(f);
        Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        mList.addView(JcxUi.label(c, list.size() + " item(ns) em " + dir.getAbsolutePath()));
        if (list.isEmpty()) return;
        // conflito simples: mesmo projeto em dois arquivos
        for (File f : list) mList.addView(installedCard(f, idx));
    }

    private View installedCard(File f, ContentIndex idx) {
        Context c = requireContext();
        boolean disabled = f.getName().endsWith(".disabled");
        ContentIndex.Entry e = idx.entries.get(ContentIndex.key(f.getName()));
        LinearLayout card = JcxUi.card(c, disabled ? JcxUi.TEXT_DIM : JcxUi.PURPLE);
        card.addView(JcxUi.text(c, e != null ? e.title : f.getName(), 15, disabled ? JcxUi.TEXT_DIM : JcxUi.TEXT, true));
        card.addView(JcxUi.label(c, f.getName() + "  •  " + JcxUi.size(JcxFiles.dirSize(f))
                + (e != null ? "  •  versão " + e.versionNumber : "  •  instalado manualmente")
                + (disabled ? "  •  DESATIVADO" : "")));
        List<View> buttons = new ArrayList<>();
        if (!f.isDirectory()) {
            Button toggle = JcxUi.button(c, disabled ? "Ativar" : "Desativar", false);
            toggle.setOnClickListener(v -> {
                File target = disabled ? new File(f.getParentFile(), ContentIndex.key(f.getName())) : new File(f.getAbsolutePath() + ".disabled");
                if (target.exists() || !f.renameTo(target)) Toast.makeText(c, "Não foi possível alterar o arquivo", Toast.LENGTH_SHORT).show();
                showInstalled();
            });
            buttons.add(toggle);
        }
        if (e != null && e.projectId != null) {
            Button upd = JcxUi.button(c, "Atualizar", false);
            upd.setOnClickListener(v -> checkUpdate(e));
            buttons.add(upd);
        }
        Button del = JcxUi.button(c, "Remover", false, JcxUi.BAD);
        del.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle("Remover " + f.getName() + "?")
                .setMessage(KIND_MOD.equals(mKind) ? "Outros mods podem depender deste. Prefira \"Desativar\" se tiver dúvida." : "O arquivo será apagado.")
                .setPositiveButton("Remover", (d, w) -> {
                    JcxFiles.deleteRecursive(f);
                    idx.remove(f.getName());
                    idx.save();
                    showInstalled();
                })
                .setNegativeButton(android.R.string.cancel, null).show());
        buttons.add(del);
        card.addView(JcxUi.buttonRow(c, buttons.toArray(new View[0])));
        return card;
    }

    private void checkUpdate(ContentIndex.Entry e) {
        Context c = requireContext();
        Toast.makeText(c, "Procurando atualização de " + e.title + "…", Toast.LENGTH_SHORT).show();
        final String mc = JcxPaths.mcVersion();
        final List<String> loaders = KIND_MOD.equals(mKind) ? modLoaders() : null;
        JcxUi.bg(() -> {
            String msg;
            Modrinth.Version best = null;
            try {
                best = Modrinth.best(Modrinth.versions(e.projectId, mc, loaders));
                if (best == null) msg = "Nenhuma versão compatível encontrada.";
                else if (best.id.equals(e.versionId)) msg = e.title + " já está na versão mais recente compatível (" + e.versionNumber + ").";
                else msg = null;
            } catch (Exception ex) {
                msg = "Erro: " + ex.getMessage();
            }
            final String fm = msg;
            JcxUi.ui(() -> {
                if (!isAdded()) return;
                if (fm != null) Toast.makeText(c, fm, Toast.LENGTH_LONG).show();
                else prepareInstall(e.projectId, e.title, null, e.file);
            });
        });
    }

    @SuppressWarnings("unused")
    private static List<String> list(String... s) { return new ArrayList<>(Arrays.asList(s)); }
}
