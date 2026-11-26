package net.kdt.pojavlaunch.jcx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.List;

/** Testes da proteção de mundos: detecção de gravação, integridade, backup automático e rotação. */
public class WorldGuardTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File world(File saves, String folder, String name, long timeTicks) throws Exception {
        File w = new File(saves, folder);
        assertTrue(w.mkdirs());
        LinkedHashMap<String, Nbt.Tag> data = new LinkedHashMap<>();
        data.put("LevelName", new Nbt.Tag(Nbt.STRING, name));
        data.put("Time", new Nbt.Tag(Nbt.LONG, timeTicks));
        LinkedHashMap<String, Nbt.Tag> top = new LinkedHashMap<>();
        top.put("Data", new Nbt.Tag(Nbt.COMPOUND, data));
        Nbt.Root r = new Nbt.Root();
        r.name = "";
        r.tag = new Nbt.Tag(Nbt.COMPOUND, top);
        Nbt.writeGzip(new File(w, "level.dat"), r);
        File region = new File(w, "region");
        assertTrue(region.mkdirs());
        try (FileWriter fw = new FileWriter(new File(region, "r.0.0.mca"))) { fw.write("chunk"); }
        return w;
    }

    @Test public void detectsOnlyWorldsWrittenDuringSession() throws Exception {
        File saves = tmp.newFolder("saves");
        File old = world(saves, "Antigo", "Antigo", 0);
        File cur = world(saves, "MeuMundo", "Meu Mundo", 48000);
        long sessionStart = System.currentTimeMillis() - 60_000;
        // mundo antigo: tudo gravado antes da sessão
        for (File f : new File[]{new File(old, "level.dat"), new File(old, "region"), new File(old, "region/r.0.0.mca")})
            assertTrue(f.setLastModified(sessionStart - 3_600_000));
        List<WorldGuard.WorldState> m = WorldGuard.modifiedSince(saves, sessionStart);
        assertEquals(1, m.size());
        assertEquals("Meu Mundo", m.get(0).name);
        assertTrue(m.get(0).levelOk);
        assertEquals(48000, m.get(0).dayTimeTicks);
        assertTrue(cur.isDirectory());
    }

    @Test public void corruptedWorldIsReportedAndNotBackedUp() throws Exception {
        File saves = tmp.newFolder("saves");
        File w = world(saves, "Quebrado", "Quebrado", 0);
        try (FileOutputStream o = new FileOutputStream(new File(w, "level.dat"))) { o.write(new byte[]{9, 9, 9}); }
        WorldGuard.WorldState s = WorldGuard.inspect(w, System.currentTimeMillis());
        assertFalse(s.levelOk);
        File bk = tmp.newFolder("bk");
        String r = WorldGuard.autoBackup(s, bk, -1);
        assertTrue(r.startsWith("não feito"));
        String[] files = bk.list();
        assertEquals(0, files == null ? 0 : files.length);
    }

    @Test public void autoBackupSkipsWhenNoSpace() throws Exception {
        File saves = tmp.newFolder("saves");
        WorldGuard.WorldState s = WorldGuard.inspect(world(saves, "A", "A", 0), System.currentTimeMillis());
        String r = WorldGuard.autoBackup(s, tmp.newFolder("bk"), 1024);
        assertTrue(r.contains("espaço insuficiente"));
    }

    @Test public void autoBackupCreatesRestorableZip() throws Exception {
        File saves = tmp.newFolder("saves");
        WorldGuard.WorldState s = WorldGuard.inspect(world(saves, "Casa", "Casa", 0), System.currentTimeMillis());
        File bk = tmp.newFolder("bk");
        String r = WorldGuard.autoBackup(s, bk, -1);
        assertTrue(r, r.startsWith("feito"));
        File[] zips = bk.listFiles();
        assertEquals(1, zips.length);
        assertTrue(zips[0].getName().startsWith("Casa__auto-"));
        assertEquals("Casa/", JcxFiles.findWorldPrefix(zips[0]));
    }

    @Test public void pruneKeepsNewestAutoAndNeverTouchesManual() throws Exception {
        File bk = tmp.newFolder("bk");
        for (int i = 1; i <= 8; i++) assertTrue(new File(bk, "Casa__auto-2026010" + i + "-120000.zip").createNewFile());
        assertTrue(new File(bk, "Casa__20260101-120000.zip").createNewFile()); // manual
        assertTrue(new File(bk, "Outro__auto-20260101-120000.zip").createNewFile());
        assertEquals(3, WorldGuard.pruneAuto(bk, "Casa", 5));
        assertTrue(new File(bk, "Casa__20260101-120000.zip").exists());
        assertTrue(new File(bk, "Outro__auto-20260101-120000.zip").exists());
        assertTrue(new File(bk, "Casa__auto-20260108-120000.zip").exists());
        assertFalse(new File(bk, "Casa__auto-20260101-120000.zip").exists());
    }
}
