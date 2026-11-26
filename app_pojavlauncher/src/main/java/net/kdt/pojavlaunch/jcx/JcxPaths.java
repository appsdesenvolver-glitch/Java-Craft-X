package net.kdt.pojavlaunch.jcx;

import android.os.StatFs;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.Locale;

/** Resolve o perfil/instalação selecionado e suas pastas isoladas. */
public final class JcxPaths {
    private JcxPaths() {}

    public static MinecraftProfile currentProfile() {
        try {
            LauncherProfiles.load();
            String id = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            if (id == null || LauncherProfiles.mainProfileJson == null || LauncherProfiles.mainProfileJson.profiles == null) return null;
            return LauncherProfiles.mainProfileJson.profiles.get(id);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String profileName() {
        MinecraftProfile p = currentProfile();
        if (p == null) return "(nenhum perfil)";
        if (p.name != null && !p.name.trim().isEmpty()) return p.name;
        return p.lastVersionId != null ? p.lastVersionId : "(sem nome)";
    }

    public static String versionId() {
        MinecraftProfile p = currentProfile();
        return p == null ? null : p.lastVersionId;
    }

    /** Pasta do jogo do perfil atual (cada perfil pode ter a sua). */
    public static File gameDir() {
        MinecraftProfile p = currentProfile();
        File f = (p == null) ? new File(Tools.DIR_GAME_NEW) : Tools.getGameDirPath(p);
        return f;
    }

    public static File sub(String name) {
        File f = new File(gameDir(), name);
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    public static File jcxHome(String name) {
        File f = new File(Tools.DIR_GAME_HOME, "jcx/" + name);
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    /** Versão vanilla do Minecraft do perfil (resolve inheritsFrom de loaders). */
    public static String mcVersion() {
        String id = versionId();
        if (id == null || id.startsWith("latest") || "Unknown".equals(id)) return null;
        try {
            String v = Tools.getSelectedVanillaMcVer();
            if (v != null && !v.trim().isEmpty()) return v.trim();
        } catch (Throwable ignored) {}
        return guessMcVersion(id);
    }

    /** Extrai algo como 1.20.1 de "fabric-loader-0.15.11-1.20.1". */
    static String guessMcVersion(String id) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(1\\.\\d{1,2}(?:\\.\\d{1,2})?)").matcher(id);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    /** Loader do perfil no formato do Modrinth: fabric, quilt, forge, neoforge ou null (vanilla/OptiFine). */
    public static String loader() {
        String id = versionId();
        if (id == null) return null;
        String l = id.toLowerCase(Locale.ROOT);
        if (l.contains("neoforge")) return "neoforge";
        if (l.contains("quilt")) return "quilt";
        if (l.contains("fabric")) return "fabric";
        if (l.contains("forge")) return "forge";
        return null;
    }

    public static boolean hasOptiFine() {
        String id = versionId();
        if (id != null && id.toLowerCase(Locale.ROOT).contains("optifine")) return true;
        return modPresent("optifine");
    }

    public static boolean modPresent(String namePart) {
        File mods = new File(gameDir(), "mods");
        File[] list = mods.listFiles();
        if (list == null) return false;
        String n = namePart.toLowerCase(Locale.ROOT);
        for (File f : list) {
            String fn = f.getName().toLowerCase(Locale.ROOT);
            if (fn.endsWith(".jar") && fn.contains(n)) return true;
        }
        return false;
    }

    public static String renderer() {
        MinecraftProfile p = currentProfile();
        if (p != null && p.pojavRendererName != null) return p.pojavRendererName;
        return "opengles_mobileglues";
    }

    public static String rendererLabel(String r) {
        if (r == null) return "?";
        if (r.equals("opengles2")) return "Krypton (NG-GL4ES)";
        if (r.equals("opengles_mobileglues")) return "MobileGlues";
        if (r.contains("zink")) return "Zink (Vulkan)";
        if (r.contains("ltw")) return "LTW";
        return r;
    }

    public static long freeBytes(File dir) {
        try {
            StatFs s = new StatFs(dir.getAbsolutePath());
            return s.getAvailableBytes();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Nome de arquivo seguro: sem separadores, sem "..", sem caracteres reservados. */
    public static String safeFileName(String name) {
        if (name == null) return null;
        String n = name.replace('\\', '_').replace('/', '_').replaceAll("[:*?\"<>|\\x00-\\x1F]", "_").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) return null;
        if (n.length() > 120) n = n.substring(n.length() - 120);
        return n;
    }
}
