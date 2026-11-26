package net.kdt.pojavlaunch.jcx;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * DOWNLOAD ENGINE do JAVA CRAFT X: HTTPS obrigatório, retomada (Range), repetição com espera,
 * cancelamento, progresso/velocidade reais, verificação de espaço e de hash.
 */
public final class JcxDownloader {
    public static final String USER_AGENT = "appsdesenvolver-glitch/Java-Craft-X/1.1.0 (github.com/appsdesenvolver-glitch/Java-Craft-X)";

    public interface Listener {
        /** @param total -1 se desconhecido; speed em bytes/s */
        void onProgress(long done, long total, long speed);
    }

    public static final class Cancelled extends IOException {
        public Cancelled() { super("Download cancelado"); }
    }

    private volatile boolean mCancelled;

    public void cancel() { mCancelled = true; }
    public boolean isCancelled() { return mCancelled; }

    /** Só permite HTTPS em hosts esperados. */
    public static void checkUrl(String url, String... allowedHostSuffixes) throws IOException {
        URL u = new URL(url);
        if (!"https".equalsIgnoreCase(u.getProtocol())) throw new IOException("URL recusada (não é HTTPS): " + url);
        String host = u.getHost().toLowerCase(Locale.ROOT);
        if (allowedHostSuffixes.length == 0) return;
        for (String s : allowedHostSuffixes) {
            if (host.equals(s) || host.endsWith("." + s)) return;
        }
        throw new IOException("Fonte não autorizada: " + host);
    }

    /**
     * Baixa para {@code dest}. Usa {@code dest.part} para retomar. Verifica sha1/sha512 quando informado.
     */
    public void download(String url, File dest, long expectedSize, String sha1, String sha512, Listener l) throws IOException {
        File part = new File(dest.getAbsolutePath() + ".part");
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Não foi possível criar " + parent);
        if (expectedSize > 0) {
            long free = JcxPaths.freeBytes(parent != null ? parent : dest);
            if (free >= 0 && free < expectedSize + 20L * 1024 * 1024)
                throw new IOException("Armazenamento insuficiente: precisa de " + JcxUi.size(expectedSize) + ", livre " + JcxUi.size(free));
        }
        IOException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            if (mCancelled) throw new Cancelled();
            try {
                fetch(url, part, expectedSize, l);
                last = null;
                break;
            } catch (Cancelled c) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                throw c;
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(800L * attempt); } catch (InterruptedException ie) { throw new InterruptedIOException(); }
            }
        }
        if (last != null) throw new IOException("Falha após 4 tentativas: " + last.getMessage(), last);

        if (expectedSize > 0 && part.length() != expectedSize) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("Tamanho diferente do esperado (" + part.length() + " ≠ " + expectedSize + ")");
        }
        if (sha512 != null && !sha512.isEmpty()) {
            String h = JcxFiles.hash(part, "SHA-512");
            if (!h.equalsIgnoreCase(sha512)) { part.delete(); throw new IOException("Verificação SHA-512 falhou — arquivo descartado"); }
        } else if (sha1 != null && !sha1.isEmpty()) {
            String h = JcxFiles.hash(part, "SHA-1");
            if (!h.equalsIgnoreCase(sha1)) { part.delete(); throw new IOException("Verificação SHA-1 falhou — arquivo descartado"); }
        }
        if (dest.exists() && !dest.delete()) throw new IOException("Não foi possível substituir " + dest.getName());
        if (!part.renameTo(dest)) throw new IOException("Não foi possível mover o download para " + dest.getName());
    }

    private void fetch(String url, File part, long expectedSize, Listener l) throws IOException {
        long have = part.exists() ? part.length() : 0;
        if (expectedSize > 0 && have > expectedSize) { part.delete(); have = 0; }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", USER_AGENT);
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        int code = c.getResponseCode();
        boolean append;
        if (code == HttpURLConnection.HTTP_PARTIAL) append = true;
        else if (code == HttpURLConnection.HTTP_OK) { append = false; have = 0; }
        else if (code == 416 && expectedSize > 0 && have == expectedSize) { c.disconnect(); return; }
        else { c.disconnect(); throw new IOException("HTTP " + code); }
        // garante que o redirecionamento final continua em HTTPS
        if (!"https".equalsIgnoreCase(c.getURL().getProtocol())) { c.disconnect(); throw new IOException("Redirecionamento inseguro recusado"); }
        long total = expectedSize > 0 ? expectedSize : (c.getContentLength() > 0 ? c.getContentLength() + have : -1);
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             FileOutputStream out = new FileOutputStream(part, append)) {
            byte[] buf = new byte[64 * 1024];
            long done = have;
            long windowStart = System.currentTimeMillis();
            long windowBytes = 0;
            long speed = 0;
            int r;
            while ((r = in.read(buf)) != -1) {
                if (mCancelled) throw new Cancelled();
                out.write(buf, 0, r);
                done += r;
                windowBytes += r;
                long now = System.currentTimeMillis();
                if (now - windowStart >= 500) {
                    speed = windowBytes * 1000 / Math.max(1, now - windowStart);
                    windowStart = now;
                    windowBytes = 0;
                    if (l != null) l.onProgress(done, total, speed);
                }
            }
            if (l != null) l.onProgress(done, total, speed);
        } finally {
            c.disconnect();
        }
    }

    /** GET simples para APIs JSON (com User-Agent e repetição). */
    public static String getText(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code == 429) throw new IOException("Muitas requisições (limite da API). Tente em instantes.");
                if (code >= 400) throw new IOException("HTTP " + code);
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    JcxFiles.copyStream(in, bo, null);
                    return bo.toString("UTF-8");
                }
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(600L * attempt); } catch (InterruptedException ie) { throw new InterruptedIOException(); }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw last;
    }

    public static String speed(long bps) {
        return JcxUi.size(bps) + "/s";
    }
}
