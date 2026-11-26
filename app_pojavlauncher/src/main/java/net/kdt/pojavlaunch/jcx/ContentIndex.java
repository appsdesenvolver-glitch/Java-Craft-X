package net.kdt.pojavlaunch.jcx;

import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registro (por pasta) do que foi instalado pelo JAVA CRAFT X: arquivo → projeto/versão do Modrinth. */
public final class ContentIndex {
    public static final class Entry {
        public String file, projectId, versionId, title, versionNumber;
    }

    private final File mFile;
    public final Map<String, Entry> entries = new LinkedHashMap<>();

    public ContentIndex(File folder) {
        mFile = new File(folder, ".jcx-index.json");
        load();
    }

    /** Remove o sufixo ".disabled" para usar como chave. */
    public static String key(String fileName) {
        return fileName.endsWith(".disabled") ? fileName.substring(0, fileName.length() - 9) : fileName;
    }

    private void load() {
        entries.clear();
        if (!mFile.isFile()) return;
        try {
            JSONObject o = new JSONObject(JcxFiles.readSmall(mFile));
            JSONObject files = o.optJSONObject("files");
            if (files == null) return;
            Iterator<String> it = files.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject e = files.getJSONObject(k);
                Entry en = new Entry();
                en.file = k;
                en.projectId = e.optString("projectId", null);
                en.versionId = e.optString("versionId", null);
                en.title = e.optString("title", k);
                en.versionNumber = e.optString("versionNumber", "");
                entries.put(k, en);
            }
        } catch (Exception ignored) {
            // índice corrompido: recomeça vazio (os arquivos continuam na pasta)
        }
    }

    public synchronized void save() {
        try {
            JSONObject files = new JSONObject();
            for (Entry e : entries.values()) {
                JSONObject j = new JSONObject();
                j.put("projectId", e.projectId);
                j.put("versionId", e.versionId);
                j.put("title", e.title);
                j.put("versionNumber", e.versionNumber);
                files.put(e.file, j);
            }
            JSONObject root = new JSONObject();
            root.put("generator", "JAVA CRAFT X");
            root.put("files", files);
            JcxFiles.writeText(mFile, root.toString(2));
        } catch (Exception ignored) {}
    }

    public Entry byProject(String projectId) {
        if (projectId == null) return null;
        for (Entry e : entries.values()) if (projectId.equals(e.projectId)) return e;
        return null;
    }

    public void put(String file, String projectId, String versionId, String title, String versionNumber) {
        Entry e = new Entry();
        e.file = key(file);
        e.projectId = projectId;
        e.versionId = versionId;
        e.title = title;
        e.versionNumber = versionNumber;
        entries.put(e.file, e);
    }

    public void remove(String file) { entries.remove(key(file)); }

    /** Remove entradas cujo arquivo não existe mais. */
    public void prune(File folder) {
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            String f = it.next().getKey();
            if (!new File(folder, f).exists() && !new File(folder, f + ".disabled").exists()) it.remove();
        }
    }
}
