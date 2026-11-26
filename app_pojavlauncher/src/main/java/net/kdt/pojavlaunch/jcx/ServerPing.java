package net.kdt.pojavlaunch.jcx;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Consulta de status de servidor Minecraft Java (protocolo "Server List Ping", 1.7+).
 * Só lê informações públicas (MOTD, versão, jogadores, latência) — não autentica nem entra no servidor.
 */
public final class ServerPing {
    private ServerPing() {}

    public static final class Status {
        public String motd, versionName;
        public int protocol, online, max;
        public long pingMs;
    }

    /** Separa "host:porta" (porta padrão 25565; aceita IPv6 entre colchetes). */
    public static String[] split(String address) {
        String a = address.trim();
        String host = a;
        int port = 25565;
        if (a.startsWith("[")) {
            int end = a.indexOf(']');
            host = a.substring(1, Math.max(1, end));
            if (end > 0 && a.length() > end + 2 && a.charAt(end + 1) == ':') port = parsePort(a.substring(end + 2));
        } else {
            int i = a.lastIndexOf(':');
            if (i > 0 && a.indexOf(':') == i) { host = a.substring(0, i); port = parsePort(a.substring(i + 1)); }
        }
        return new String[]{host, String.valueOf(port)};
    }

    private static int parsePort(String s) {
        try { int p = Integer.parseInt(s.trim()); return (p > 0 && p < 65536) ? p : 25565; } catch (NumberFormatException e) { return 25565; }
    }

    public static Status ping(String address, int timeoutMs) throws IOException {
        String[] hp = split(address);
        String host = hp[0];
        int port = Integer.parseInt(hp[1]);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            writeVarInt(h, 0x00);
            writeVarInt(h, 47);
            writeString(h, host);
            h.writeShort(port);
            writeVarInt(h, 1);
            sendPacket(out, hs.toByteArray());
            sendPacket(out, new byte[]{0x00});

            readVarInt(in); // tamanho
            int id = readVarInt(in);
            if (id != 0x00) throw new IOException("Resposta inesperada do servidor");
            int len = readVarInt(in);
            if (len <= 0 || len > 1024 * 1024) throw new IOException("Resposta inválida do servidor");
            byte[] json = new byte[len];
            in.readFully(json);

            Status st = parse(new String(json, StandardCharsets.UTF_8));

            long t0 = System.nanoTime();
            ByteArrayOutputStream pp = new ByteArrayOutputStream();
            DataOutputStream p = new DataOutputStream(pp);
            writeVarInt(p, 0x01);
            p.writeLong(t0);
            sendPacket(out, pp.toByteArray());
            try {
                readVarInt(in);
                if (readVarInt(in) == 0x01) in.readLong();
                st.pingMs = (System.nanoTime() - t0) / 1_000_000L;
            } catch (IOException e) {
                st.pingMs = -1;
            }
            return st;
        }
    }

    static Status parse(String json) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            Status st = new Status();
            JSONObject v = o.optJSONObject("version");
            if (v != null) { st.versionName = v.optString("name"); st.protocol = v.optInt("protocol"); }
            JSONObject pl = o.optJSONObject("players");
            if (pl != null) { st.online = pl.optInt("online"); st.max = pl.optInt("max"); }
            Object d = o.opt("description");
            StringBuilder sb = new StringBuilder();
            text(d, sb);
            st.motd = sb.toString().replaceAll("§.", "").trim();
            return st;
        } catch (Exception e) {
            throw new IOException("Status do servidor ilegível");
        }
    }

    private static void text(Object c, StringBuilder sb) {
        if (c == null) return;
        if (c instanceof String) { sb.append((String) c); return; }
        if (c instanceof JSONObject) {
            JSONObject o = (JSONObject) c;
            sb.append(o.optString("text", ""));
            JSONArray extra = o.optJSONArray("extra");
            if (extra != null) for (int i = 0; i < extra.length(); i++) text(extra.opt(i), sb);
        } else if (c instanceof JSONArray) {
            JSONArray a = (JSONArray) c;
            for (int i = 0; i < a.length(); i++) text(a.opt(i), sb);
        }
    }

    private static void sendPacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    static int readVarInt(InputStream in) throws IOException {
        int value = 0, pos = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("Conexão encerrada pelo servidor");
            value |= (b & 0x7F) << pos;
            if ((b & 0x80) == 0) return value;
            pos += 7;
            if (pos >= 35) throw new IOException("VarInt grande demais");
        }
    }
}
