package net.kdt.pojavlaunch.jcx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Leitor/gravador mínimo do formato NBT (level.dat do Minecraft Java, GZIP).
 * Preserva todos os tipos para que regravar o arquivo não perca dados.
 */
public final class Nbt {
    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
            BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    /** Limite de profundidade para arquivos malformados. */
    private static final int MAX_DEPTH = 512;

    public static final class Tag {
        public byte type;
        public Object value; // Byte, Short, Integer, Long, Float, Double, byte[], String, ListTag, LinkedHashMap<String,Tag>, int[], long[]
        public Tag(byte type, Object value) { this.type = type; this.value = value; }

        @SuppressWarnings("unchecked")
        public Map<String, Tag> compound() { return type == COMPOUND ? (Map<String, Tag>) value : null; }

        public Tag get(String key) {
            Map<String, Tag> m = compound();
            return m == null ? null : m.get(key);
        }

        public String asString() { return type == STRING ? (String) value : null; }

        public Long asLong() {
            if (value instanceof Number) return ((Number) value).longValue();
            return null;
        }
    }

    public static final class ListTag {
        public byte elementType;
        public final List<Tag> items = new ArrayList<>();
    }

    public static final class Root {
        public String name;
        public Tag tag;
    }

    private Nbt() {}

    public static Root readGzip(File f) throws IOException {
        return read(f, true);
    }

    /** servers.dat e alguns outros arquivos usam NBT sem compressão. */
    public static Root readRaw(File f) throws IOException {
        return read(f, false);
    }

    private static Root read(File f, boolean gzip) throws IOException {
        java.io.InputStream base = new FileInputStream(f);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(gzip ? new GZIPInputStream(base) : base))) {
            byte type = in.readByte();
            if (type != COMPOUND) throw new IOException("NBT raiz inválido (tipo " + type + ")");
            Root r = new Root();
            r.name = in.readUTF();
            r.tag = readPayload(in, type, 0);
            return r;
        }
    }

    public static void writeGzip(File f, Root root) throws IOException {
        write(f, root, true);
    }

    public static void writeRaw(File f, Root root) throws IOException {
        write(f, root, false);
    }

    private static void write(File f, Root root, boolean gzip) throws IOException {
        File tmp = new File(f.getAbsolutePath() + ".jcxtmp");
        java.io.OutputStream base = new FileOutputStream(tmp);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(gzip ? new GZIPOutputStream(base) : base))) {
            out.writeByte(COMPOUND);
            out.writeUTF(root.name == null ? "" : root.name);
            writePayload(out, root.tag);
        }
        // valida antes de substituir
        read(tmp, gzip);
        if (f.exists() && !f.delete()) throw new IOException("Não foi possível substituir " + f.getName());
        if (!tmp.renameTo(f)) throw new IOException("Não foi possível gravar " + f.getName());
    }

    private static Tag readPayload(DataInputStream in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT profundo demais");
        switch (type) {
            case BYTE: return new Tag(type, in.readByte());
            case SHORT: return new Tag(type, in.readShort());
            case INT: return new Tag(type, in.readInt());
            case LONG: return new Tag(type, in.readLong());
            case FLOAT: return new Tag(type, in.readFloat());
            case DOUBLE: return new Tag(type, in.readDouble());
            case BYTE_ARRAY: {
                int n = checkLen(in.readInt());
                byte[] b = new byte[n];
                in.readFully(b);
                return new Tag(type, b);
            }
            case STRING: return new Tag(type, in.readUTF());
            case LIST: {
                ListTag l = new ListTag();
                l.elementType = in.readByte();
                int n = checkLen(in.readInt());
                for (int i = 0; i < n; i++) l.items.add(readPayload(in, l.elementType, depth + 1));
                return new Tag(type, l);
            }
            case COMPOUND: {
                LinkedHashMap<String, Tag> m = new LinkedHashMap<>();
                while (true) {
                    byte t = in.readByte();
                    if (t == END) break;
                    String name = in.readUTF();
                    m.put(name, readPayload(in, t, depth + 1));
                }
                return new Tag(type, m);
            }
            case INT_ARRAY: {
                int n = checkLen(in.readInt());
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return new Tag(type, a);
            }
            case LONG_ARRAY: {
                int n = checkLen(in.readInt());
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return new Tag(type, a);
            }
            default:
                throw new IOException("Tipo NBT desconhecido: " + type);
        }
    }

    private static int checkLen(int n) throws IOException {
        if (n < 0 || n > 64 * 1024 * 1024) throw new IOException("Tamanho NBT inválido: " + n);
        return n;
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Tag t) throws IOException {
        switch (t.type) {
            case BYTE: out.writeByte((Byte) t.value); break;
            case SHORT: out.writeShort((Short) t.value); break;
            case INT: out.writeInt((Integer) t.value); break;
            case LONG: out.writeLong((Long) t.value); break;
            case FLOAT: out.writeFloat((Float) t.value); break;
            case DOUBLE: out.writeDouble((Double) t.value); break;
            case BYTE_ARRAY: {
                byte[] b = (byte[]) t.value;
                out.writeInt(b.length);
                out.write(b);
                break;
            }
            case STRING: out.writeUTF((String) t.value); break;
            case LIST: {
                ListTag l = (ListTag) t.value;
                out.writeByte(l.items.isEmpty() && l.elementType == 0 ? END : l.elementType);
                out.writeInt(l.items.size());
                for (Tag i : l.items) writePayload(out, i);
                break;
            }
            case COMPOUND: {
                Map<String, Tag> m = (Map<String, Tag>) t.value;
                for (Map.Entry<String, Tag> e : m.entrySet()) {
                    out.writeByte(e.getValue().type);
                    out.writeUTF(e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(END);
                break;
            }
            case INT_ARRAY: {
                int[] a = (int[]) t.value;
                out.writeInt(a.length);
                for (int v : a) out.writeInt(v);
                break;
            }
            case LONG_ARRAY: {
                long[] a = (long[]) t.value;
                out.writeInt(a.length);
                for (long v : a) out.writeLong(v);
                break;
            }
            default:
                throw new IOException("Tipo NBT desconhecido: " + t.type);
        }
    }

    /** Resumo de um level.dat. */
    public static final class LevelInfo {
        public String levelName;
        public String versionName;
        public long lastPlayed;
        public boolean hardcore;
        public Integer gameType;
    }

    public static LevelInfo levelInfo(File levelDat) throws IOException {
        Root r = readGzip(levelDat);
        Tag data = r.tag.get("Data");
        if (data == null) throw new IOException("level.dat sem o bloco Data");
        LevelInfo li = new LevelInfo();
        Tag n = data.get("LevelName");
        li.levelName = n != null ? n.asString() : null;
        Tag v = data.get("Version");
        if (v != null && v.get("Name") != null) li.versionName = v.get("Name").asString();
        Tag lp = data.get("LastPlayed");
        if (lp != null && lp.asLong() != null) li.lastPlayed = lp.asLong();
        Tag hc = data.get("hardcore");
        if (hc != null && hc.asLong() != null) li.hardcore = hc.asLong() != 0;
        Tag gt = data.get("GameType");
        if (gt != null && gt.asLong() != null) li.gameType = gt.asLong().intValue();
        return li;
    }

    /** Altera Data.LevelName (nome exibido no jogo), mantendo uma cópia de segurança. */
    public static void setLevelName(File levelDat, String newName) throws IOException {
        Root r = readGzip(levelDat);
        Tag data = r.tag.get("Data");
        if (data == null || data.compound() == null) throw new IOException("level.dat sem o bloco Data");
        JcxFiles.copyFile(levelDat, new File(levelDat.getParentFile(), "level.dat_jcxbak"));
        data.compound().put("LevelName", new Tag(STRING, newName));
        writeGzip(levelDat, r);
    }
}
