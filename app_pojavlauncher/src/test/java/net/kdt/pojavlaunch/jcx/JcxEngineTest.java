package net.kdt.pojavlaunch.jcx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.LinkedHashMap;

/** Testes do Version Hub, multiplayer (servers.dat + protocolo de status). */
public class JcxEngineTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void serverAddressSplit() {
        String[] a = ServerPing.split("play.exemplo.com");
        assertEquals("play.exemplo.com", a[0]);
        assertEquals("25565", a[1]);
        a = ServerPing.split("192.168.0.10:25570");
        assertEquals("192.168.0.10", a[0]);
        assertEquals("25570", a[1]);
        a = ServerPing.split("[::1]:25566");
        assertEquals("::1", a[0]);
        assertEquals("25566", a[1]);
        assertEquals("25565", ServerPing.split("host:99999")[1]);
    }

    @Test public void varIntRoundTrip() throws Exception {
        int[] values = {0, 1, 127, 128, 255, 25565, 2097151, Integer.MAX_VALUE, -1};
        for (int v : values) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ServerPing.writeVarInt(new DataOutputStream(bo), v);
            assertEquals(v, ServerPing.readVarInt(new ByteArrayInputStream(bo.toByteArray())));
        }
    }

    @Test public void serverStatusParse() throws Exception {
        String json = "{\"version\":{\"name\":\"Paper 1.20.4\",\"protocol\":765},\"players\":{\"max\":100,\"online\":7},"
                + "\"description\":{\"text\":\"§aBem-vindo \",\"extra\":[{\"text\":\"ao servidor\"}]}}";
        ServerPing.Status st = ServerPing.parse(json);
        assertEquals("Paper 1.20.4", st.versionName);
        assertEquals(765, st.protocol);
        assertEquals(7, st.online);
        assertEquals(100, st.max);
        assertEquals("Bem-vindo ao servidor", st.motd);
    }

    @Test public void serversDatRoundTrip() throws Exception {
        Nbt.Root r = new Nbt.Root();
        r.name = "";
        LinkedHashMap<String, Nbt.Tag> server = new LinkedHashMap<>();
        server.put("name", new Nbt.Tag(Nbt.STRING, "Meu servidor"));
        server.put("ip", new Nbt.Tag(Nbt.STRING, "mc.exemplo.com"));
        server.put("acceptTextures", new Nbt.Tag(Nbt.BYTE, (byte) 1));
        Nbt.ListTag list = new Nbt.ListTag();
        list.elementType = Nbt.COMPOUND;
        list.items.add(new Nbt.Tag(Nbt.COMPOUND, server));
        LinkedHashMap<String, Nbt.Tag> root = new LinkedHashMap<>();
        root.put("servers", new Nbt.Tag(Nbt.LIST, list));
        r.tag = new Nbt.Tag(Nbt.COMPOUND, root);
        File f = new File(tmp.getRoot(), "servers.dat");
        Nbt.writeRaw(f, r);
        Nbt.Root back = Nbt.readRaw(f);
        Nbt.Tag s0 = ((Nbt.ListTag) back.tag.get("servers").value).items.get(0);
        assertEquals("mc.exemplo.com", s0.get("ip").asString());
        assertEquals(Long.valueOf(1), s0.get("acceptTextures").asLong());
    }

    @Test public void versionManifestParseAndCategories() throws Exception {
        String json = "{\"latest\":{\"release\":\"1.21.4\",\"snapshot\":\"25w02a\"},\"versions\":["
                + "{\"id\":\"25w02a\",\"type\":\"snapshot\",\"url\":\"https://piston-meta.mojang.com/x.json\",\"releaseTime\":\"2025-01-08T12:00:00+00:00\",\"sha1\":\"aa\"},"
                + "{\"id\":\"1.21.4\",\"type\":\"release\",\"url\":\"u\",\"releaseTime\":\"2024-12-03T10:12:57+00:00\"},"
                + "{\"id\":\"1.21.4-rc1\",\"type\":\"snapshot\",\"url\":\"u\",\"releaseTime\":\"2024-11-25T00:00:00+00:00\"},"
                + "{\"id\":\"1.21.4-pre1\",\"type\":\"snapshot\",\"url\":\"u\",\"releaseTime\":\"2024-11-13T00:00:00+00:00\"},"
                + "{\"id\":\"b1.7.3\",\"type\":\"old_beta\",\"url\":\"u\",\"releaseTime\":\"2011-07-08T00:00:00+00:00\"},"
                + "{\"id\":\"rd-132211\",\"type\":\"old_alpha\",\"url\":\"u\",\"releaseTime\":\"2009-05-13T00:00:00+00:00\"}]}";
        VersionCatalog.Catalog c = VersionCatalog.parse(json);
        assertEquals("1.21.4", c.latestRelease);
        assertEquals("25w02a", c.latestSnapshot);
        assertEquals(6, c.entries.size());
        assertEquals("snapshot", c.entries.get(0).category());
        assertEquals("release", c.entries.get(1).category());
        assertEquals("release-candidate", c.entries.get(2).category());
        assertEquals("pre-release", c.entries.get(3).category());
        assertEquals("old_beta", c.entries.get(4).category());
        assertEquals("old_alpha", c.entries.get(5).category());
        assertTrue(c.entries.get(0).inManifest);
    }

    @Test public void versionCompare() {
        assertTrue(EngineFragment.compare("1.21.5", "1.21.4") > 0);
        assertTrue(EngineFragment.compare("1.20.1", "1.21.4") < 0);
        assertEquals(0, EngineFragment.compare("1.21.4", "1.21.4"));
        assertTrue(EngineFragment.compare("1.21", "1.20.6") > 0);
    }
}
