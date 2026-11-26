package net.kdt.pojavlaunch.jcx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Operações de arquivo seguras: ZIP com proteção contra ZIP Slip, cópia, hash. */
public final class JcxFiles {
    private JcxFiles() {}

    /** Limite de extração para evitar "zip bombs" (8 GB). */
    private static final long MAX_EXTRACT = 8L * 1024 * 1024 * 1024;

    public interface Progress { void onProgress(long done); }

    public static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        File[] list = f.listFiles();
        if (list != null) for (File c : list) s += dirSize(c);
        return s;
    }

    public static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) for (File c : list) {
                // não segue links simbólicos para fora da pasta
                try {
                    if (!c.getCanonicalPath().startsWith(f.getCanonicalPath())) { c.delete(); continue; }
                } catch (IOException ignored) {}
                deleteRecursive(c);
            }
        }
        return f.delete();
    }

    public static void copyStream(InputStream in, OutputStream out, Progress p) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long done = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
            done += r;
            if (p != null) p.onProgress(done);
        }
    }

    public static void copyFile(File src, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        try (InputStream in = new BufferedInputStream(new FileInputStream(src));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dst))) {
            copyStream(in, out, null);
        }
        //noinspection ResultOfMethodCallIgnored
        dst.setLastModified(src.lastModified());
    }

    public static void copyDir(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Não foi possível criar " + dst);
            File[] list = src.listFiles();
            if (list != null) for (File c : list) copyDir(c, new File(dst, c.getName()));
        } else {
            copyFile(src, dst);
        }
    }

    /** Compacta a pasta inteira; as entradas começam com o nome da pasta. */
    public static void zipDir(File dir, File outZip) throws IOException {
        File parent = outZip.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        File tmp = new File(outZip.getAbsolutePath() + ".part");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            zipAdd(zos, dir, dir.getName());
        }
        if (outZip.exists()) //noinspection ResultOfMethodCallIgnored
            outZip.delete();
        if (!tmp.renameTo(outZip)) throw new IOException("Falha ao finalizar " + outZip.getName());
    }

    private static void zipAdd(ZipOutputStream zos, File f, String path) throws IOException {
        if (f.isDirectory()) {
            zos.putNextEntry(new ZipEntry(path + "/"));
            zos.closeEntry();
            File[] list = f.listFiles();
            if (list != null) for (File c : list) zipAdd(zos, c, path + "/" + c.getName());
            return;
        }
        // "session.lock" fica travado enquanto o mundo está aberto e não é necessário no backup
        if (f.getName().equals("session.lock")) return;
        ZipEntry e = new ZipEntry(path);
        e.setTime(f.lastModified());
        zos.putNextEntry(e);
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            copyStream(in, zos, null);
        }
        zos.closeEntry();
    }

    /**
     * Procura dentro do ZIP o caminho da pasta que contém level.dat (raiz do mundo).
     * Retorna "" se level.dat está na raiz, o prefixo com "/" no fim, ou null se não houver mundo.
     */
    public static String findWorldPrefix(File zip) throws IOException {
        String best = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName().replace('\\', '/');
                if (n.equals("level.dat") || n.endsWith("/level.dat")) {
                    String prefix = n.substring(0, n.length() - "level.dat".length());
                    if (best == null || prefix.length() < best.length()) best = prefix;
                }
            }
        }
        return best;
    }

    /**
     * Extrai somente as entradas sob {@code prefix} para {@code target}.
     * Bloqueia ZIP Slip (entradas com ".." ou caminhos absolutos que escapariam do destino).
     */
    public static void unzipSafe(File zip, String prefix, File target) throws IOException {
        if (!target.exists() && !target.mkdirs()) throw new IOException("Não foi possível criar " + target);
        String root = target.getCanonicalPath() + File.separator;
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName().replace('\\', '/');
                if (prefix != null && !prefix.isEmpty()) {
                    if (!n.startsWith(prefix)) continue;
                    n = n.substring(prefix.length());
                }
                if (n.isEmpty()) continue;
                File out = new File(target, n);
                String canon = out.getCanonicalPath();
                if (!canon.startsWith(root)) throw new IOException("Entrada insegura bloqueada no ZIP: " + e.getName());
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                    continue;
                }
                File p = out.getParentFile();
                if (p != null) //noinspection ResultOfMethodCallIgnored
                    p.mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int r;
                    while ((r = zis.read(buf)) != -1) {
                        total += r;
                        if (total > MAX_EXTRACT) throw new IOException("ZIP grande demais (limite de 8 GB)");
                        os.write(buf, 0, r);
                    }
                }
                if (e.getTime() > 0) //noinspection ResultOfMethodCallIgnored
                    out.setLastModified(e.getTime());
            }
        }
    }

    public static String hash(File f, String algorithm) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** Lê no máximo os últimos {@code maxBytes} do arquivo (logs podem ser enormes). */
    public static String readTail(File f, int maxBytes) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - maxBytes);
            raf.seek(start);
            byte[] b = new byte[(int) (len - start)];
            raf.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    public static String readSmall(File f) throws IOException {
        return readTail(f, 4 * 1024 * 1024);
    }

    public static void writeText(File f, String s) throws IOException {
        File p = f.getParentFile();
        if (p != null) //noinspection ResultOfMethodCallIgnored
            p.mkdirs();
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(s.getBytes(StandardCharsets.UTF_8));
        }
        if (f.exists() && !f.delete()) throw new IOException("Não foi possível substituir " + f.getName());
        if (!tmp.renameTo(f)) throw new IOException("Não foi possível gravar " + f.getName());
    }

    public static String uniqueName(File parent, String base) {
        String b = base;
        File f = new File(parent, b);
        int i = 2;
        while (f.exists()) {
            b = base + " (" + i++ + ")";
            f = new File(parent, b);
        }
        return b;
    }
}
