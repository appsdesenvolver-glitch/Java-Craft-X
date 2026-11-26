package net.kdt.pojavlaunch.jcx;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Tela do CRASH ANALYZER: lê latestlog.txt e o crash-report mais recente do perfil. */
public class CrashAnalyzerFragment extends Fragment {
    public static final String TAG = "JCX_CRASH";
    private LinearLayout mBody;
    private String mReport;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        JcxUi.Screen s = JcxUi.screen(requireContext(), "Crash Analyzer", "Diagnóstico a partir dos logs reais da última execução");
        mBody = s.body;
        run();
        return s.root;
    }

    private void run() {
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));
        mBody.addView(JcxUi.spinner(c));
        JcxUi.bg(() -> {
            List<File> sources = new ArrayList<>();
            File latest = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if (latest.isFile()) sources.add(latest);
            File crashDir = new File(JcxPaths.gameDir(), "crash-reports");
            File[] crashes = crashDir.listFiles((d, n) -> n.endsWith(".txt"));
            File newest = null;
            if (crashes != null) for (File f : crashes) if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
            if (newest != null) sources.add(newest);
            File gameLog = new File(JcxPaths.gameDir(), "logs/latest.log");
            if (gameLog.isFile()) sources.add(gameLog);

            CrashAnalyzer.Result result = new CrashAnalyzer.Result();
            StringBuilder files = new StringBuilder();
            for (File f : sources) {
                try {
                    String text = JcxFiles.readTail(f, 3 * 1024 * 1024);
                    CrashAnalyzer.analyze(text, f.getName(), result);
                    files.append("• ").append(f.getName()).append(" (").append(JcxUi.size(f.length())).append(", ")
                            .append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(f.lastModified())))
                            .append(")\n");
                } catch (Exception e) {
                    files.append("• ").append(f.getName()).append(": não foi possível ler (").append(e.getMessage()).append(")\n");
                }
            }
            final String fFiles = files.toString();
            final boolean none = sources.isEmpty();
            JcxUi.ui(() -> { if (isAdded()) show(result, fFiles, none); });
        });
    }

    private void show(CrashAnalyzer.Result r, String files, boolean noLogs) {
        Context c = requireContext();
        mBody.removeViews(3, Math.max(0, mBody.getChildCount() - 3));
        StringBuilder rep = new StringBuilder();
        rep.append("JAVA CRAFT X — Relatório do Crash Analyzer\n")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date())).append("\n")
                .append("Perfil: ").append(JcxPaths.profileName()).append(" (").append(JcxPaths.versionId()).append(")\n")
                .append("Renderizador: ").append(JcxPaths.rendererLabel(JcxPaths.renderer())).append("\n")
                .append("Heap configurado: ").append(net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_RAM_ALLOCATION).append(" MB\n");

        LinearLayout src = JcxUi.card(c, JcxUi.CYAN);
        src.addView(JcxUi.text(c, "Arquivos analisados", 15, JcxUi.TEXT, true));
        if (noLogs) {
            src.addView(JcxUi.label(c, "Nenhum log encontrado. Inicie o jogo pelo menos uma vez."));
            mBody.addView(src);
            return;
        }
        src.addView(JcxUi.label(c, files.trim()));
        if (r.stage != null) src.addView(JcxUi.label(c, "Última etapa detectada (estimativa): " + r.stage));
        if (r.mcVersion != null) src.addView(JcxUi.label(c, "Minecraft no crash-report: " + r.mcVersion));
        if (r.javaVersion != null) src.addView(JcxUi.label(c, "Java: " + r.javaVersion));
        mBody.addView(src);
        rep.append("\nArquivos:\n").append(files).append("Etapa estimada: ").append(r.stage).append("\n");

        if (r.findings.isEmpty()) {
            LinearLayout card = JcxUi.card(c, JcxUi.GOOD);
            card.addView(JcxUi.text(c, "Nenhum padrão de erro conhecido", 15, JcxUi.GOOD, true));
            card.addView(JcxUi.label(c, "Não há informação suficiente para apontar uma causa. Isso não garante que não houve erro: compartilhe o log completo se o jogo fechou."));
            mBody.addView(card);
            rep.append("\nNenhum padrão de erro conhecido encontrado. Informação insuficiente para um diagnóstico.\n");
        } else {
            mBody.addView(JcxUi.section(c, r.findings.size() + " possível(is) causa(s)"));
            for (CrashAnalyzer.Finding f : r.findings) {
                int color = f.level == CrashAnalyzer.Level.HIGH ? JcxUi.BAD : f.level == CrashAnalyzer.Level.MEDIUM ? JcxUi.WARN : JcxUi.TEXT_DIM;
                LinearLayout card = JcxUi.card(c, color);
                card.addView(JcxUi.text(c, f.title, 15, color, true));
                card.addView(JcxUi.label(c, CrashAnalyzer.levelLabel(f.level)));
                TextView ex = JcxUi.label(c, f.explanation);
                ex.setTextColor(JcxUi.TEXT);
                card.addView(ex);
                TextView ev = JcxUi.label(c, "Evidência (" + f.source + ", linha " + f.line + "):\n" + f.evidence);
                ev.setTypeface(android.graphics.Typeface.MONOSPACE);
                ev.setTextSize(11);
                card.addView(ev);
                TextView fix = JcxUi.label(c, "Sugestão: " + f.fix);
                fix.setTextColor(JcxUi.CYAN);
                card.addView(fix);
                mBody.addView(card);
                rep.append("\n[").append(CrashAnalyzer.levelLabel(f.level)).append("] ").append(f.title)
                        .append("\n  ").append(f.explanation)
                        .append("\n  Evidência: ").append(f.source).append(":").append(f.line).append(" ").append(f.evidence)
                        .append("\n  Sugestão: ").append(f.fix).append("\n");
            }
        }
        rep.append("\nObservação: os itens acima são hipóteses baseadas em padrões do log, não diagnósticos confirmados.\n");
        mReport = rep.toString();

        Button share = JcxUi.button(c, "Exportar relatório", true);
        share.setOnClickListener(v -> export());
        Button log = JcxUi.button(c, "Compartilhar log", false);
        log.setOnClickListener(v -> Tools.shareLog(c));
        Button again = JcxUi.button(c, "Analisar de novo", false);
        again.setOnClickListener(v -> run());
        mBody.addView(JcxUi.buttonRow(c, share, log));
        mBody.addView(JcxUi.buttonRow(c, again));
    }

    private void export() {
        Context c = requireContext();
        if (mReport == null) return;
        File out = new File(JcxPaths.jcxHome("reports"), "crash-report-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".txt");
        try {
            JcxFiles.writeText(out, mReport);
        } catch (Exception e) {
            Toast.makeText(c, "Não foi possível salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "JAVA CRAFT X — relatório de diagnóstico");
        send.putExtra(Intent.EXTRA_TEXT, mReport.length() > 60000 ? mReport.substring(0, 60000) : mReport);
        try {
            startActivity(Intent.createChooser(send, "Exportar relatório"));
        } catch (Exception e) {
            Toast.makeText(c, "Relatório salvo em " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }
}
