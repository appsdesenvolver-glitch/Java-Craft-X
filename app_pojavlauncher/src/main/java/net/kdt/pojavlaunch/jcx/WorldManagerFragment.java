package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** ADVANCED WORLD MANAGER: lista, versão, backup/restauração, importação, duplicar, renomear, excluir. */
public class WorldManagerFragment extends Fragment {
    public static final String TAG = "JCX_WORLDS";

    private LinearLayout mBody;
    private TextView mStatus;
    private volatile boolean mBusy;

    private final ActivityResultLauncher<String> mImportLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImportPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JcxUi.Screen s = JcxUi.screen(requireContext(), "World Manager", "Mundos do perfil: " + JcxPaths.profileName());
        mBody = s.body;
        reload();
        return s.root;
    }

    private File savesDir() { return JcxPaths.sub("saves"); }
    private File backupDir() { return JcxPaths.jcxHome("backups/worlds"); }

    private static String stamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()); }

    private void reload() {
        if (!isAdded()) return;
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));

        mStatus = JcxUi.label(c, "Pasta: " + savesDir().getAbsolutePath());
        mBody.addView(mStatus);
        Button imp = JcxUi.button(c, "Importar mundo (.zip)", true);
        imp.setOnClickListener(v -> { if (!mBusy) mImportLauncher.launch("application/zip"); });
        Button restoreAny = JcxUi.button(c, "Restaurar backup", false);
        restoreAny.setOnClickListener(v -> showBackups(null));
        Button openBk = JcxUi.button(c, "Pasta de backups", false);
        openBk.setOnClickListener(v -> {
            try { Tools.openPath(c, backupDir(), false); }
            catch (Throwable t) { Toast.makeText(c, backupDir().getAbsolutePath(), Toast.LENGTH_LONG).show(); }
        });
        mBody.addView(JcxUi.buttonRow(c, imp, restoreAny));
        mBody.addView(JcxUi.buttonRow(c, openBk));
        android.widget.CheckBox auto = new android.widget.CheckBox(c);
        auto.setText("Backup automático ao fim de cada sessão (mantém os " + WorldGuard.KEEP_AUTO + " mais recentes por mundo; backups manuais nunca são apagados)");
        auto.setTextColor(JcxUi.TEXT_DIM);
        auto.setChecked(JcxSession.autoBackupEnabled(c));
        auto.setOnCheckedChangeListener((b, on) -> JcxSession.setAutoBackup(c, on));
        mBody.addView(auto);

        File[] worlds = savesDir().listFiles(File::isDirectory);
        if (worlds == null || worlds.length == 0) {
            mBody.addView(JcxUi.section(c, "Nenhum mundo"));
            mBody.addView(JcxUi.label(c, "Crie um mundo no jogo ou importe um backup .zip."));
            return;
        }
        Arrays.sort(worlds, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        mBody.addView(JcxUi.section(c, worlds.length + " mundo(s)"));
        for (File w : worlds) mBody.addView(worldCard(w));
    }

    private View worldCard(File dir) {
        Context c = requireContext();
        LinearLayout card = JcxUi.card(c, JcxUi.PURPLE);
        LinearLayout top = JcxUi.row(c);
        ImageView icon = new ImageView(c);
        int sz = JcxUi.dp(c, 56);
        icon.setBackground(JcxUi.rounded(JcxUi.GRAPHITE_LIGHT, 0, c, 8));
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File iconFile = new File(dir, "icon.png");
        if (iconFile.isFile()) {
            Bitmap bm = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            if (bm != null) icon.setImageBitmap(bm);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = JcxUi.dp(c, 12);
        top.addView(icon, ilp);
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = JcxUi.text(c, dir.getName(), 16, JcxUi.TEXT, true);
        TextView meta = JcxUi.label(c, "Lendo level.dat…");
        TextView size = JcxUi.label(c, "Tamanho: calculando…");
        info.addView(name);
        info.addView(meta);
        info.addView(size);
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        File levelDat = new File(dir, "level.dat");
        JcxUi.bg(() -> {
            String nameTxt = dir.getName();
            String metaTxt;
            int metaColor = JcxUi.TEXT_DIM;
            try {
                if (!levelDat.isFile()) throw new java.io.IOException("level.dat ausente");
                Nbt.LevelInfo li = Nbt.levelInfo(levelDat);
                if (li.levelName != null && !li.levelName.isEmpty()) nameTxt = li.levelName;
                StringBuilder sb = new StringBuilder();
                sb.append("Versão: ").append(li.versionName != null ? li.versionName : "anterior a 1.9 (não registrada)");
                if (li.gameType != null) sb.append("  •  ").append(gameType(li.gameType));
                if (li.hardcore) sb.append("  •  Hardcore");
                long when = li.lastPlayed > 0 ? li.lastPlayed : dir.lastModified();
                sb.append("\nÚltimo acesso: ").append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(when)));
                sb.append("\nPasta: ").append(dir.getName());
                metaTxt = sb.toString();
            } catch (Exception e) {
                boolean hasOld = new File(dir, "level.dat_old").isFile();
                metaTxt = "⚠ level.dat ilegível (" + e.getMessage() + ") — o mundo pode estar corrompido."
                        + (hasOld ? " Existe level.dat_old: use \"Reparar\"." : "");
                metaColor = JcxUi.BAD;
            }
            long bytes = JcxFiles.dirSize(dir);
            final String fn = nameTxt, fm = metaTxt;
            final int fc = metaColor;
            JcxUi.ui(() -> {
                name.setText(fn);
                meta.setText(fm);
                meta.setTextColor(fc);
                size.setText("Tamanho: " + JcxUi.size(bytes));
            });
        });

        Button backup = JcxUi.button(c, "Backup", true);
        backup.setOnClickListener(v -> doBackup(dir));
        Button restore = JcxUi.button(c, "Restaurar", false);
        restore.setOnClickListener(v -> showBackups(dir.getName()));
        Button dup = JcxUi.button(c, "Duplicar", false);
        dup.setOnClickListener(v -> doDuplicate(dir));
        Button ren = JcxUi.button(c, "Renomear", false);
        ren.setOnClickListener(v -> doRename(dir));
        Button repair = JcxUi.button(c, "Reparar", false, JcxUi.WARN);
        repair.setOnClickListener(v -> doRepair(dir));
        Button del = JcxUi.button(c, "Excluir", false, JcxUi.BAD);
        del.setOnClickListener(v -> doDelete(dir));
        card.addView(JcxUi.buttonRow(c, backup, restore, dup));
        card.addView(JcxUi.buttonRow(c, ren, repair, del));
        return card;
    }

    private static String gameType(int t) {
        switch (t) {
            case 0: return "Sobrevivência";
            case 1: return "Criativo";
            case 2: return "Aventura";
            case 3: return "Espectador";
            default: return "Modo " + t;
        }
    }

    private void busy(String msg) {
        mBusy = true;
        if (mStatus != null) { mStatus.setText(msg); mStatus.setTextColor(JcxUi.CYAN); }
    }

    private void done(String msg, boolean ok) {
        JcxUi.ui(() -> {
            mBusy = false;
            if (!isAdded()) return;
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            reload();
            if (mStatus != null) { mStatus.setText(msg); mStatus.setTextColor(ok ? JcxUi.GOOD : JcxUi.BAD); }
        });
    }

    private boolean guard() {
        if (mBusy) {
            Toast.makeText(requireContext(), "Aguarde a operação atual terminar", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void doBackup(File dir) {
        if (guard()) return;
        File out = new File(backupDir(), dir.getName() + "__" + stamp() + ".zip");
        long need = JcxFiles.dirSize(dir);
        long free = JcxPaths.freeBytes(backupDir());
        if (free >= 0 && free < need + 50L * 1024 * 1024) {
            Toast.makeText(requireContext(), "Espaço insuficiente para o backup (" + JcxUi.size(need) + " necessários)", Toast.LENGTH_LONG).show();
            return;
        }
        busy("Criando backup de " + dir.getName() + "…");
        JcxUi.bg(() -> {
            try {
                JcxFiles.zipDir(dir, out);
                done("Backup criado: " + out.getName() + " (" + JcxUi.size(out.length()) + ")", true);
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                new File(out.getAbsolutePath() + ".part").delete();
                done("Falha no backup: " + e.getMessage(), false);
            }
        });
    }

    private void showBackups(@Nullable String worldFolder) {
        if (guard()) return;
        Context c = requireContext();
        File[] all = backupDir().listFiles((d, n) -> n.endsWith(".zip"));
        List<File> list = new ArrayList<>();
        if (all != null) for (File f : all) {
            if (worldFolder == null || f.getName().startsWith(worldFolder + "__")) list.add(f);
        }
        if (list.isEmpty()) {
            Toast.makeText(c, worldFolder == null ? "Nenhum backup encontrado" : "Nenhum backup deste mundo", Toast.LENGTH_LONG).show();
            return;
        }
        java.util.Collections.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).getName() + "  (" + JcxUi.size(list.get(i).length()) + ")";
        new AlertDialog.Builder(c)
                .setTitle("Restaurar backup")
                .setItems(names, (d, which) -> restoreZip(list.get(which), stripStamp(list.get(which).getName())))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String stripStamp(String zipName) {
        String n = zipName.endsWith(".zip") ? zipName.substring(0, zipName.length() - 4) : zipName;
        int i = n.lastIndexOf("__");
        return i > 0 ? n.substring(0, i) : n;
    }

    /** Restaura sempre numa pasta NOVA — nunca sobrescreve um mundo existente. */
    private void restoreZip(File zip, String baseName) {
        if (guard()) return;
        busy("Restaurando " + zip.getName() + "…");
        JcxUi.bg(() -> {
            File target = null;
            try {
                String prefix = JcxFiles.findWorldPrefix(zip);
                if (prefix == null) throw new java.io.IOException("o ZIP não contém level.dat (não é um mundo do Minecraft)");
                String safe = JcxPaths.safeFileName(baseName);
                if (safe == null) safe = "Mundo";
                String name = JcxFiles.uniqueName(savesDir(), safe + " (restaurado)");
                target = new File(savesDir(), name);
                JcxFiles.unzipSafe(zip, prefix, target);
                Nbt.levelInfo(new File(target, "level.dat")); // valida
                done("Mundo restaurado como \"" + name + "\"", true);
            } catch (Exception e) {
                if (target != null) JcxFiles.deleteRecursive(target);
                done("Falha ao restaurar: " + e.getMessage(), false);
            }
        });
    }

    private void onImportPicked(@Nullable Uri uri) {
        if (uri == null || !isAdded()) return;
        Context c = requireContext().getApplicationContext();
        busy("Importando…");
        JcxUi.bg(() -> {
            File tmp = new File(c.getCacheDir(), "jcx_import_" + System.currentTimeMillis() + ".zip");
            try {
                try (InputStream in = c.getContentResolver().openInputStream(uri);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                    if (in == null) throw new java.io.IOException("não foi possível abrir o arquivo");
                    JcxFiles.copyStream(in, out, null);
                }
                // copia o original para a pasta de backups, para ficar registrado
                File kept = new File(backupDir(), "importado__" + stamp() + ".zip");
                JcxFiles.copyFile(tmp, kept);
                done("Arquivo copiado. Extraindo…", true);
                JcxUi.ui(() -> restoreZip(kept, "Mundo importado"));
            } catch (Exception e) {
                done("Falha ao importar: " + e.getMessage(), false);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        });
    }

    private void doDuplicate(File dir) {
        if (guard()) return;
        String name = JcxFiles.uniqueName(savesDir(), dir.getName() + " (cópia)");
        File target = new File(savesDir(), name);
        busy("Duplicando " + dir.getName() + "…");
        JcxUi.bg(() -> {
            try {
                JcxFiles.copyDir(dir, target);
                //noinspection ResultOfMethodCallIgnored
                new File(target, "session.lock").delete();
                File ld = new File(target, "level.dat");
                if (ld.isFile()) {
                    try {
                        Nbt.LevelInfo li = Nbt.levelInfo(ld);
                        Nbt.setLevelName(ld, (li.levelName != null ? li.levelName : dir.getName()) + " (cópia)");
                    } catch (Exception ignored) { /* mantém o nome antigo se o NBT não puder ser regravado */ }
                }
                done("Mundo duplicado: " + name, true);
            } catch (Exception e) {
                JcxFiles.deleteRecursive(target);
                done("Falha ao duplicar: " + e.getMessage(), false);
            }
        });
    }

    private void doRename(File dir) {
        if (guard()) return;
        Context c = requireContext();
        EditText input = new EditText(c);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        String current = dir.getName();
        try { Nbt.LevelInfo li = Nbt.levelInfo(new File(dir, "level.dat")); if (li.levelName != null) current = li.levelName; }
        catch (Exception ignored) {}
        input.setText(current);
        int p = JcxUi.dp(c, 20);
        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(p, p / 2, p, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(c)
                .setTitle("Renomear mundo")
                .setMessage("Altera o nome exibido no jogo e o nome da pasta.")
                .setView(wrap)
                .setPositiveButton("Renomear", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    String folder = JcxPaths.safeFileName(newName);
                    if (folder == null) { Toast.makeText(c, "Nome inválido", Toast.LENGTH_SHORT).show(); return; }
                    busy("Renomeando…");
                    JcxUi.bg(() -> {
                        try {
                            File ld = new File(dir, "level.dat");
                            if (ld.isFile()) Nbt.setLevelName(ld, newName);
                            File target = dir;
                            if (!folder.equals(dir.getName())) {
                                target = new File(savesDir(), JcxFiles.uniqueName(savesDir(), folder));
                                if (!dir.renameTo(target)) throw new java.io.IOException("a pasta está em uso");
                            }
                            done("Renomeado para \"" + newName + "\" (pasta " + target.getName() + ")", true);
                        } catch (Exception e) {
                            done("Falha ao renomear: " + e.getMessage(), false);
                        }
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doRepair(File dir) {
        if (guard()) return;
        Context c = requireContext();
        File ld = new File(dir, "level.dat");
        File old = new File(dir, "level.dat_old");
        boolean ok = true;
        try { Nbt.levelInfo(ld); } catch (Exception e) { ok = false; }
        if (ok) {
            Toast.makeText(c, "level.dat está legível — nenhum reparo necessário", Toast.LENGTH_LONG).show();
            return;
        }
        if (!old.isFile()) {
            Toast.makeText(c, "Não há level.dat_old para recuperar. Restaure um backup.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(c)
                .setTitle("Reparar " + dir.getName())
                .setMessage("Substituir o level.dat ilegível pelo level.dat_old (a última versão salva antes). O arquivo danificado será mantido como level.dat.corrompido.")
                .setPositiveButton("Reparar", (d, w) -> {
                    try {
                        Nbt.levelInfo(old);
                        if (ld.exists()) JcxFiles.copyFile(ld, new File(dir, "level.dat.corrompido"));
                        JcxFiles.copyFile(old, ld);
                        done("level.dat recuperado a partir do level.dat_old", true);
                    } catch (Exception e) {
                        done("level.dat_old também está ilegível: " + e.getMessage(), false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doDelete(File dir) {
        if (guard()) return;
        Context c = requireContext();
        new AlertDialog.Builder(c)
                .setTitle("Excluir \"" + dir.getName() + "\"?")
                .setMessage("O mundo será apagado permanentemente deste aparelho. Recomendo fazer um backup antes.")
                .setPositiveButton("Excluir", (d, w) -> new AlertDialog.Builder(c)
                        .setTitle("Confirmar exclusão")
                        .setMessage("Esta ação não pode ser desfeita. Excluir \"" + dir.getName() + "\"?")
                        .setPositiveButton("Sim, excluir", (d2, w2) -> {
                            busy("Excluindo…");
                            JcxUi.bg(() -> {
                                boolean okDel = JcxFiles.deleteRecursive(dir);
                                done(okDel ? "Mundo excluído" : "Não foi possível excluir tudo (arquivo em uso?)", okDel);
                            });
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show())
                .setNeutralButton("Backup primeiro", (d, w) -> doBackup(dir))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
