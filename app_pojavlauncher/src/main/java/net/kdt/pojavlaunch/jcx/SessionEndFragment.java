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
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tela exibida quando o launcher detecta que uma partida terminou: tempo de sessão,
 * mundos gravados (com verificação real do level.dat), backups automáticos e possíveis falhas.
 */
public class SessionEndFragment extends Fragment {
    public static final String TAG = "JCX_SESSION_END";
    private static final String A_START = "start", A_END = "end", A_PROFILE = "profile", A_VERSION = "version",
            A_DIR = "dir", A_DEMO = "demo";

    private LinearLayout mBody;

    public static Bundle args(JcxSession.Info i) {
        Bundle b = new Bundle();
        b.putLong(A_START, i.start);
        b.putLong(A_END, i.end);
        b.putString(A_PROFILE, i.profileName);
        b.putString(A_VERSION, i.versionId);
        b.putString(A_DIR, i.gameDir);
        b.putBoolean(A_DEMO, i.demo);
        return b;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle a = requireArguments();
        boolean demo = a.getBoolean(A_DEMO);
        JcxUi.Screen s = JcxUi.screen(requireContext(), demo ? "Sessão de demonstração encerrada" : "Sessão encerrada",
                "Perfil " + a.getString(A_PROFILE) + " • " + a.getString(A_VERSION));
        mBody = s.body;
        JcxSession.Info i = new JcxSession.Info();
        i.start = a.getLong(A_START);
        i.end = a.getLong(A_END);
        i.profileName = a.getString(A_PROFILE);
        i.versionId = a.getString(A_VERSION);
        i.gameDir = a.getString(A_DIR);
        i.demo = demo;
        analyze(i);
        return s.root;
    }

    private void analyze(JcxSession.Info i) {
        Context c = requireContext();
        long minutes = Math.max(0, (i.end - i.start) / 60000L);
        LinearLayout head = JcxUi.card(c, JcxUi.CYAN);
        head.addView(JcxUi.text(c, minutes + " min de sessão", 20, JcxUi.TEXT, true));
        DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);
        head.addView(JcxUi.label(c, "Início " + df.format(new Date(i.start)) + " • fim (último registro do log) " + df.format(new Date(i.end))));
        mBody.addView(head);
        TextView working = JcxUi.label(c, "Verificando mundos gravados nesta sessão…");
        mBody.addView(working);
        boolean auto = JcxSession.autoBackupEnabled(c);
        JcxUi.bg(() -> {
            File saves = new File(i.gameDir, "saves");
            List<WorldGuard.WorldState> worlds = WorldGuard.modifiedSince(saves, i.start);
            File backupDir = JcxPaths.jcxHome("backups/worlds");
            if (auto) for (WorldGuard.WorldState w : worlds)
                w.backupResult = WorldGuard.autoBackup(w, backupDir, JcxPaths.freeBytes(backupDir));
            JcxSession.record(i, worlds);
            List<CrashAnalyzer.Finding> problems = new ArrayList<>();
            try {
                File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
                if (log.isFile() && log.lastModified() >= i.start) {
                    CrashAnalyzer.Result r = new CrashAnalyzer.Result();
                    CrashAnalyzer.analyze(JcxFiles.readTail(log, 1024 * 1024), log.getName(), r);
                    for (CrashAnalyzer.Finding f : r.findings) if (f.level != CrashAnalyzer.Level.LOW) problems.add(f);
                }
            } catch (Exception ignored) {}
            JcxUi.ui(() -> { if (isAdded()) show(i, worlds, problems, auto, working); });
        });
    }

    private void show(JcxSession.Info i, List<WorldGuard.WorldState> worlds, List<CrashAnalyzer.Finding> problems, boolean auto, TextView working) {
        Context c = requireContext();
        mBody.removeView(working);
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        mBody.addView(JcxUi.section(c, worlds.isEmpty() ? "Mundos" : worlds.size() + " mundo(s) gravado(s)"));
        if (worlds.isEmpty()) {
            mBody.addView(JcxUi.label(c, "Nenhuma gravação de mundo foi detectada nesta sessão (por exemplo, se você ficou só no menu)."));
        }
        for (WorldGuard.WorldState w : worlds) {
            LinearLayout card = JcxUi.card(c, w.levelOk ? JcxUi.GOOD : JcxUi.BAD);
            card.addView(JcxUi.text(c, w.name, 16, JcxUi.TEXT, true));
            TextView st = JcxUi.label(c, w.levelOk
                    ? "Gravação detectada em " + df.format(new Date(w.lastWrite)) + " • level.dat íntegro"
                    : "⚠ level.dat ilegível (" + w.error + "). Use World Manager → Reparar ou restaure um backup.");
            st.setTextColor(w.levelOk ? JcxUi.GOOD : JcxUi.BAD);
            card.addView(st);
            if (w.sessionLockLeft)
                card.addView(JcxUi.label(c, "O jogo foi encerrado sem fechar o mundo (session.lock ficou). Pode faltar o progresso desde o último salvamento automático do jogo."));
            if (i.demo && w.dayTimeTicks >= 0)
                card.addView(JcxUi.label(c, String.format(Locale.ROOT, "Tempo do mundo de demonstração: %.1f dia(s) de jogo (o limite é aplicado pelo próprio Minecraft).", w.dayTimeTicks / 24000.0)));
            card.addView(JcxUi.label(c, "Backup automático: " + (auto ? w.backupResult : "desativado (ative no World Manager)")));
            mBody.addView(card);
        }

        if (!problems.isEmpty()) {
            LinearLayout pc = JcxUi.card(c, JcxUi.WARN);
            pc.addView(JcxUi.text(c, "A sessão pode ter terminado por erro", 15, JcxUi.WARN, true));
            for (CrashAnalyzer.Finding f : problems) pc.addView(JcxUi.label(c, "• Possível causa: " + f.title));
            Button an = JcxUi.button(c, "Abrir Crash Analyzer", false, JcxUi.WARN);
            an.setOnClickListener(v -> Tools.swapFragment(requireActivity(), CrashAnalyzerFragment.class, CrashAnalyzerFragment.TAG, null));
            pc.addView(JcxUi.buttonRow(c, an));
            mBody.addView(pc);
        }

        if (i.demo) {
            LinearLayout info = JcxUi.card(c, JcxUi.PURPLE);
            info.addView(JcxUi.text(c, "Versão de demonstração", 15, JcxUi.TEXT, true));
            info.addView(JcxUi.label(c, "A demo oficial tem limite de tempo controlado pelo próprio Minecraft; o JAVA CRAFT X não altera esse limite. "
                    + "Seu mundo de demonstração continua guardado neste aparelho. Para jogar sem limite, entre com uma conta Microsoft que tenha o Minecraft: Java Edition — a mesma instalação passa a abrir a versão completa."));
            mBody.addView(info);
        }

        Button home = JcxUi.button(c, "Voltar ao início", true);
        home.setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        Button worldsBtn = JcxUi.button(c, "Gerenciar mundos", false);
        worldsBtn.setOnClickListener(v -> Tools.swapFragment(requireActivity(), WorldManagerFragment.class, WorldManagerFragment.TAG, null));
        mBody.addView(JcxUi.buttonRow(c, home, worldsBtn));
        if (!auto && !worlds.isEmpty()) {
            Button bk = JcxUi.button(c, "Fazer backup agora", false);
            bk.setOnClickListener(v -> {
                bk.setEnabled(false);
                bk.setText("Fazendo backup…");
                File backupDir = JcxPaths.jcxHome("backups/worlds");
                JcxUi.bg(() -> {
                    StringBuilder sb = new StringBuilder();
                    for (WorldGuard.WorldState w : worlds) sb.append(w.name).append(": ").append(WorldGuard.autoBackup(w, backupDir, JcxPaths.freeBytes(backupDir))).append('\n');
                    JcxUi.ui(() -> { if (isAdded()) { bk.setText("Backup concluído"); mBody.addView(JcxUi.label(requireContext(), sb.toString().trim())); } });
                });
            });
            mBody.addView(JcxUi.buttonRow(c, bk));
        }
    }
}
