package net.kdt.pojavlaunch.jcx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Testes dos componentes puros (sem Android) do JAVA CRAFT X. */
public class JcxCoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File makeWorld(File dir, String name, String version) throws IOException {
        Nbt.Root root = new Nbt.Root();
        root.name = "";
        LinkedHashMap<String, Nbt.Tag> data = new LinkedHashMap<>();
        data.put("LevelName", new Nbt.Tag(Nbt.STRING, name));
        LinkedHashMap<String, Nbt.Tag> ver = new LinkedHashMap<>();
        ver.put("Name", new Nbt.Tag(Nbt.STRING, version));
        data.put("Version", new Nbt.Tag(Nbt.COMPOUND, ver));
        data.put("LastPlayed", new Nbt.Tag(Nbt.LONG, 1700000000000L));
        data.put("GameType", new Nbt.Tag(Nbt.INT, 1));
        Nbt.ListTag pos = new Nbt.ListTag();
        pos.elementType = Nbt.DOUBLE;
        pos.items.add(new Nbt.Tag(Nbt.DOUBLE, 1.5));
        data.put("Pos", new Nbt.Tag(Nbt.LIST, pos));
        data.put("Seeds", new Nbt.Tag(Nbt.LONG_ARRAY, new long[]{1, 2, 3}));
        LinkedHashMap<String, Nbt.Tag> top = new LinkedHashMap<>();
        top.put("Data", new Nbt.Tag(Nbt.COMPOUND, data));
        root.tag = new Nbt.Tag(Nbt.COMPOUND, top);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        Nbt.writeGzip(new File(dir, "level.dat"), root);
        File region = new File(dir, "region");
        assertTrue(region.mkdirs());
        try (FileWriter w = new FileWriter(new File(region, "r.0.0.mca"))) { w.write("chunk"); }
        return dir;
    }

    @Test public void nbtReadsLevelInfo() throws Exception {
        File w = makeWorld(new File(tmp.getRoot(), "w"), "Mundo A", "1.20.1");
        Nbt.LevelInfo li = Nbt.levelInfo(new File(w, "level.dat"));
        assertEquals("Mundo A", li.levelName);
        assertEquals("1.20.1", li.versionName);
        assertEquals(1700000000000L, li.lastPlayed);
        assertEquals(Integer.valueOf(1), li.gameType);
    }

    @Test public void nbtRenameKeepsOtherDataAndBackup() throws Exception {
        File w = makeWorld(new File(tmp.getRoot(), "w"), "Antigo", "1.19.4");
        File ld = new File(w, "level.dat");
        Nbt.setLevelName(ld, "Novo Nome");
        assertEquals("Novo Nome", Nbt.levelInfo(ld).levelName);
        assertEquals("1.19.4", Nbt.levelInfo(ld).versionName);
        assertTrue(new File(w, "level.dat_jcxbak").isFile());
        Nbt.Root r = Nbt.readGzip(ld);
        assertNotNull(r.tag.get("Data").get("Pos"));
        assertEquals(3, ((long[]) r.tag.get("Data").get("Seeds").value).length);
    }

    @Test(expected = IOException.class)
    public void nbtRejectsGarbage() throws Exception {
        File f = tmp.newFile("level.dat");
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(new byte[]{1, 2, 3, 4}); }
        Nbt.levelInfo(f);
    }

    @Test public void backupAndRestoreWorld() throws Exception {
        File w = makeWorld(new File(tmp.getRoot(), "saves/Mundo"), "Mundo", "1.16.5");
        File zip = new File(tmp.getRoot(), "bk/Mundo__1.zip");
        JcxFiles.zipDir(w, zip);
        assertTrue(zip.isFile());
        String prefix = JcxFiles.findWorldPrefix(zip);
        assertEquals("Mundo/", prefix);
        File restored = new File(tmp.getRoot(), "restored");
        JcxFiles.unzipSafe(zip, prefix, restored);
        assertEquals("Mundo", Nbt.levelInfo(new File(restored, "level.dat")).levelName);
        assertTrue(new File(restored, "region/r.0.0.mca").isFile());
    }

    @Test public void zipSlipIsBlocked() throws Exception {
        File evil = new File(tmp.getRoot(), "evil.zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(evil))) {
            z.putNextEntry(new ZipEntry("../../escape.txt"));
            z.write(1);
            z.closeEntry();
        }
        try {
            JcxFiles.unzipSafe(evil, "", new File(tmp.getRoot(), "out"));
            fail("ZIP Slip não foi bloqueado");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("insegura"));
        }
        assertFalse(new File(tmp.getRoot().getParentFile(), "escape.txt").exists());
    }

    @Test public void zipWithoutWorldHasNoPrefix() throws Exception {
        File z = new File(tmp.getRoot(), "notworld.zip");
        try (ZipOutputStream o = new ZipOutputStream(new FileOutputStream(z))) {
            o.putNextEntry(new ZipEntry("readme.txt"));
            o.write(1);
            o.closeEntry();
        }
        assertNull(JcxFiles.findWorldPrefix(z));
    }

    @Test public void hashMatchesKnownSha1() throws Exception {
        File f = tmp.newFile("abc.txt");
        try (FileOutputStream o = new FileOutputStream(f)) { o.write("abc".getBytes("UTF-8")); }
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", JcxFiles.hash(f, "SHA-1"));
    }

    @Test public void crashAnalyzerFindsKnownCauses() {
        String log = "Launching Minecraft\nSetting user: Steve\nLWJGL Version 3.3.3\n"
                + "java.lang.OutOfMemoryError: Java heap space\n"
                + "Mixin apply for mod examplemod failed examplemod.mixins.json\n"
                + "java.lang.UnsupportedClassVersionError: Foo has been compiled by a more recent version of the Java Runtime\n";
        CrashAnalyzer.Result r = new CrashAnalyzer.Result();
        CrashAnalyzer.analyze(log, "latestlog.txt", r);
        boolean oom = false, mixin = false, java = false;
        for (CrashAnalyzer.Finding f : r.findings) {
            if (f.id.equals("oom")) { oom = true; assertEquals(4, f.line); }
            if (f.id.equals("mixin")) mixin = true;
            if (f.id.equals("java_version")) java = true;
        }
        assertTrue(oom && mixin && java);
        assertEquals("Inicialização do Minecraft", r.stage);
    }

    @Test public void crashAnalyzerDoesNotInventCauses() {
        CrashAnalyzer.Result r = new CrashAnalyzer.Result();
        CrashAnalyzer.analyze("[Render thread/INFO]: Stopping!\n", "latestlog.txt", r);
        assertTrue(r.findings.isEmpty());
    }

    @Test public void xperformanceHeapLimitsAreSafe() {
        assertEquals(1024, XPerformance.safeMaxHeap(2048, true));
        assertEquals(1248, XPerformance.safeMaxHeap(2048, false));
        assertEquals(3072, XPerformance.safeMaxHeap(4096, false));
        assertTrue(XPerformance.recommendedHeap(2048, false) <= XPerformance.safeMaxHeap(2048, false));
        assertTrue(XPerformance.recommendedHeap(3000, false) <= XPerformance.safeMaxHeap(3000, false));
    }

    @Test public void mcVersionGuessFromLoaderIds() {
        assertEquals("1.20.1", JcxPaths.guessMcVersion("fabric-loader-0.15.11-1.20.1"));
        assertEquals("1.20.1", JcxPaths.guessMcVersion("1.20.1-forge-47.2.0"));
        assertEquals("1.16.5", JcxPaths.guessMcVersion("1.16.5"));
    }

    @Test public void safeFileNameStripsTraversal() {
        assertEquals("_.._evil.jar", JcxPaths.safeFileName("/../evil.jar"));
        assertNull(JcxPaths.safeFileName(".."));
        assertEquals("sodium-0.5.jar", JcxPaths.safeFileName("sodium-0.5.jar"));
    }
}
