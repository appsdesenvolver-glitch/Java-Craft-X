package net.kdt.pojavlaunch.jcx;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Cliente da API pública do Modrinth (https://docs.modrinth.com). Somente HTTPS, sem chave. */
public final class Modrinth {
    private static final String API = "https://api.modrinth.com/v2";
    private Modrinth() {}

    public static final class Hit {
        public String projectId, slug, title, author, description, iconUrl, license, projectType;
        public long downloads;
        public List<String> categories = new ArrayList<>();
        public List<String> versions = new ArrayList<>();
    }

    public static final class VFile {
        public String url, filename, sha1, sha512;
        public long size;
        public boolean primary;
    }

    public static final class Dependency {
        public String projectId, versionId, type; // required, optional, incompatible, embedded
    }

    public static final class Version {
        public String id, projectId, name, versionNumber, versionType, datePublished;
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public List<VFile> files = new ArrayList<>();
        public List<Dependency> dependencies = new ArrayList<>();

        public VFile primaryFile() {
            for (VFile f : files) if (f.primary) return f;
            return files.isEmpty() ? null : files.get(0);
        }
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; }
    }

    private static List<String> strings(JSONArray a) {
        List<String> l = new ArrayList<>();
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    private static String jsonList(List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        return a.toString();
    }

    /**
     * @param projectType mod, shader, resourcepack, modpack
     * @param index relevance, downloads, follows, newest, updated
     */
    public static List<Hit> search(String query, String projectType, String mcVersion, List<String> loaders,
                                   String category, String index, int offset, int limit) throws IOException {
        JSONArray facets = new JSONArray();
        facets.put(new JSONArray().put("project_type:" + projectType));
        if (mcVersion != null) facets.put(new JSONArray().put("versions:" + mcVersion));
        if (loaders != null && !loaders.isEmpty()) {
            JSONArray or = new JSONArray();
            for (String l : loaders) or.put("categories:" + l);
            facets.put(or);
        }
        if (category != null) facets.put(new JSONArray().put("categories:" + category));
        String url = API + "/search?limit=" + limit + "&offset=" + offset
                + "&index=" + enc(index == null ? "relevance" : index)
                + "&facets=" + enc(facets.toString())
                + (query != null && !query.trim().isEmpty() ? "&query=" + enc(query.trim()) : "");
        try {
            JSONObject o = new JSONObject(JcxDownloader.getText(url));
            JSONArray hits = o.getJSONArray("hits");
            List<Hit> out = new ArrayList<>();
            for (int i = 0; i < hits.length(); i++) {
                JSONObject h = hits.getJSONObject(i);
                Hit hit = new Hit();
                hit.projectId = h.optString("project_id");
                hit.slug = h.optString("slug");
                hit.title = h.optString("title");
                hit.author = h.optString("author");
                hit.description = h.optString("description");
                hit.iconUrl = h.optString("icon_url", null);
                if ("null".equals(hit.iconUrl) || (hit.iconUrl != null && hit.iconUrl.isEmpty())) hit.iconUrl = null;
                hit.license = h.optString("license");
                hit.projectType = h.optString("project_type");
                hit.downloads = h.optLong("downloads");
                hit.categories = strings(h.optJSONArray("display_categories"));
                if (hit.categories.isEmpty()) hit.categories = strings(h.optJSONArray("categories"));
                hit.versions = strings(h.optJSONArray("versions"));
                out.add(hit);
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Resposta inválida do Modrinth", e);
        }
    }

    private static Version parseVersion(JSONObject v) throws JSONException {
        Version ver = new Version();
        ver.id = v.getString("id");
        ver.projectId = v.optString("project_id");
        ver.name = v.optString("name");
        ver.versionNumber = v.optString("version_number");
        ver.versionType = v.optString("version_type");
        ver.datePublished = v.optString("date_published");
        ver.gameVersions = strings(v.optJSONArray("game_versions"));
        ver.loaders = strings(v.optJSONArray("loaders"));
        JSONArray files = v.optJSONArray("files");
        if (files != null) for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            VFile vf = new VFile();
            vf.url = f.getString("url");
            vf.filename = f.getString("filename");
            vf.size = f.optLong("size", -1);
            vf.primary = f.optBoolean("primary", false);
            JSONObject hashes = f.optJSONObject("hashes");
            if (hashes != null) { vf.sha1 = hashes.optString("sha1", null); vf.sha512 = hashes.optString("sha512", null); }
            ver.files.add(vf);
        }
        JSONArray deps = v.optJSONArray("dependencies");
        if (deps != null) for (int i = 0; i < deps.length(); i++) {
            JSONObject d = deps.getJSONObject(i);
            Dependency dep = new Dependency();
            dep.projectId = d.isNull("project_id") ? null : d.optString("project_id", null);
            dep.versionId = d.isNull("version_id") ? null : d.optString("version_id", null);
            dep.type = d.optString("dependency_type");
            ver.dependencies.add(dep);
        }
        return ver;
    }

    /** Versões do projeto filtradas (mais recente primeiro). Filtros nulos = sem filtro. */
    public static List<Version> versions(String projectId, String mcVersion, List<String> loaders) throws IOException {
        StringBuilder url = new StringBuilder(API + "/project/" + enc(projectId) + "/version");
        String sep = "?";
        if (mcVersion != null) {
            List<String> gv = new ArrayList<>();
            gv.add(mcVersion);
            url.append(sep).append("game_versions=").append(enc(jsonList(gv)));
            sep = "&";
        }
        if (loaders != null && !loaders.isEmpty()) url.append(sep).append("loaders=").append(enc(jsonList(loaders)));
        try {
            JSONArray a = new JSONArray(JcxDownloader.getText(url.toString()));
            List<Version> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(parseVersion(a.getJSONObject(i)));
            return out;
        } catch (JSONException e) {
            throw new IOException("Resposta inválida do Modrinth", e);
        }
    }

    public static Version version(String versionId) throws IOException {
        try {
            return parseVersion(new JSONObject(JcxDownloader.getText(API + "/version/" + enc(versionId))));
        } catch (JSONException e) {
            throw new IOException("Resposta inválida do Modrinth", e);
        }
    }

    /** Título de um projeto (para mostrar dependências). */
    public static String projectTitle(String projectId) {
        try {
            JSONObject o = new JSONObject(JcxDownloader.getText(API + "/project/" + enc(projectId)));
            return o.optString("title", projectId);
        } catch (Exception e) {
            return projectId;
        }
    }

    /** Escolhe a melhor versão: prefere "release", senão a mais recente. */
    public static Version best(List<Version> list) {
        if (list == null || list.isEmpty()) return null;
        for (Version v : list) if ("release".equals(v.versionType) && v.primaryFile() != null) return v;
        for (Version v : list) if (v.primaryFile() != null) return v;
        return null;
    }
}
