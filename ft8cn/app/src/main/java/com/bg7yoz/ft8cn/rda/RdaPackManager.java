package com.bg7yoz.ft8cn.rda;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Download / install / delete RDA GeoJSON packs into {@code filesDir/rda}.
 * Built-in APK packs are not counted toward {@link #MAX_DOWNLOADED_PACKS}.
 */
public final class RdaPackManager {
    private static final String TAG = "RdaPackManager";

    /** Packs catalog on GitHub (raw). */
    public static final String DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/cure76/ft8cn-rda-packs/main/catalog.json";

    public static final int MAX_DOWNLOADED_PACKS = 3;
    private static final String LOCAL_INDEX = "local_index.json";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 60000;

    private RdaPackManager() {
    }

    public static File getRdaDir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), "rda");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create " + dir);
        }
        return dir;
    }

    public static File localIndexFile(Context context) {
        return new File(getRdaDir(context), LOCAL_INDEX);
    }

    public static Catalog fetchCatalog() throws Exception {
        return fetchCatalog(DEFAULT_CATALOG_URL);
    }

    public static Catalog fetchCatalog(String catalogUrl) throws Exception {
        String text = httpGetString(catalogUrl);
        JSONObject root = new JSONObject(text);
        Catalog catalog = new Catalog();
        catalog.version = root.optInt("version", 1);
        catalog.baseUrl = root.optString("base_url", "");
        if (!catalog.baseUrl.isEmpty() && !catalog.baseUrl.endsWith("/")) {
            catalog.baseUrl = catalog.baseUrl + "/";
        }
        catalog.maxDownloaded = root.optInt("max_downloaded_packs", MAX_DOWNLOADED_PACKS);
        JSONArray arr = root.optJSONArray("packs");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                PackInfo p = new PackInfo();
                p.id = o.optString("id", "");
                p.name = o.optString("name", p.id);
                p.file = o.optString("file", "");
                p.sha256 = o.optString("sha256", "").toLowerCase(Locale.US);
                p.bytes = o.optLong("bytes", 0);
                p.featureCount = o.optInt("feature_count", 0);
                p.packVersion = o.optInt("pack_version", 1);
                p.builtinInApk = o.optBoolean("builtin_in_apk", false);
                JSONArray prefixes = o.optJSONArray("codes_prefix");
                if (prefixes != null) {
                    for (int j = 0; j < prefixes.length(); j++) {
                        p.codesPrefix.add(prefixes.optString(j, ""));
                    }
                }
                if (!p.id.isEmpty() && !p.file.isEmpty()) {
                    catalog.packs.add(p);
                }
            }
        }
        return catalog;
    }

    public static List<InstalledPack> getInstalled(Context context) {
        List<InstalledPack> out = new ArrayList<>();
        try {
            File f = localIndexFile(context);
            if (!f.exists()) {
                return out;
            }
            JSONObject root = new JSONObject(readFile(f));
            JSONArray arr = root.optJSONArray("packs");
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                InstalledPack p = new InstalledPack();
                p.id = o.optString("id", "");
                p.name = o.optString("name", "");
                p.file = o.optString("file", "");
                p.sha256 = o.optString("sha256", "").toLowerCase(Locale.US);
                p.packVersion = o.optInt("pack_version", 1);
                p.installedAt = o.optLong("installed_at", 0);
                if (!p.id.isEmpty()) {
                    out.add(p);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read local_index", e);
        }
        return out;
    }

    public static int downloadedCount(Context context) {
        return getInstalled(context).size();
    }

    public static boolean isInstalled(Context context, String packId) {
        for (InstalledPack p : getInstalled(context)) {
            if (p.id.equals(packId)) {
                return true;
            }
        }
        return false;
    }

    public static InstalledPack findInstalled(Context context, String packId) {
        for (InstalledPack p : getInstalled(context)) {
            if (p.id.equals(packId)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Download and install a catalog pack. Builtin APK packs are skipped (already present).
     *
     * @throws PackLimitException if already at max downloaded packs
     */
    public static void installPack(Context context, Catalog catalog, PackInfo pack) throws Exception {
        if (pack == null || pack.id == null || pack.id.isEmpty()) {
            throw new IllegalArgumentException("invalid pack");
        }
        if (pack.builtinInApk) {
            Log.i(TAG, "Skip install of builtin pack " + pack.id);
            return;
        }
        Context app = context.getApplicationContext();
        if (isInstalled(app, pack.id)) {
            // Re-install / update: replace file, still one slot.
            deletePack(app, pack.id, false);
        } else if (downloadedCount(app) >= MAX_DOWNLOADED_PACKS) {
            throw new PackLimitException(MAX_DOWNLOADED_PACKS);
        }

        String url = resolveUrl(catalog.baseUrl, pack.file);
        File dir = getRdaDir(app);
        String localName = pack.id + ".geojson";
        File tmp = new File(dir, localName + ".tmp");
        File dest = new File(dir, localName);

        httpDownloadToFile(url, tmp);
        String digest = sha256Hex(tmp);
        if (pack.sha256 != null && !pack.sha256.isEmpty()
                && !pack.sha256.equalsIgnoreCase(digest)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new SecurityException("SHA-256 mismatch for " + pack.id
                    + " expected=" + pack.sha256 + " got=" + digest);
        }
        if (dest.exists() && !dest.delete()) {
            Log.w(TAG, "Could not replace " + dest);
        }
        if (!tmp.renameTo(dest)) {
            copyFile(tmp, dest);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }

        List<InstalledPack> list = getInstalled(app);
        InstalledPack entry = new InstalledPack();
        entry.id = pack.id;
        entry.name = pack.name != null ? pack.name : "";
        entry.file = localName;
        entry.sha256 = digest.toLowerCase(Locale.US);
        entry.packVersion = pack.packVersion;
        entry.installedAt = System.currentTimeMillis();
        list.add(entry);
        writeLocalIndex(app, list);
        RdaLookup.getInstance().reload(app);
        Log.i(TAG, "Installed RDA pack " + pack.id);
    }

    public static void deletePack(Context context, String packId) throws Exception {
        deletePack(context, packId, true);
    }

    private static void deletePack(Context context, String packId, boolean reload) throws Exception {
        Context app = context.getApplicationContext();
        List<InstalledPack> list = getInstalled(app);
        InstalledPack found = null;
        for (InstalledPack p : list) {
            if (p.id.equals(packId)) {
                found = p;
                break;
            }
        }
        if (found == null) {
            return;
        }
        File geo = new File(getRdaDir(app), found.file);
        if (geo.exists() && !geo.delete()) {
            Log.w(TAG, "Could not delete " + geo);
        }
        list.remove(found);
        writeLocalIndex(app, list);
        if (reload) {
            RdaLookup.getInstance().reload(app);
        }
        Log.i(TAG, "Deleted RDA pack " + packId);
    }

    private static void writeLocalIndex(Context context, List<InstalledPack> packs) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray arr = new JSONArray();
        for (InstalledPack p : packs) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            if (p.name != null && !p.name.isEmpty()) {
                o.put("name", p.name);
            }
            o.put("file", p.file);
            o.put("sha256", p.sha256);
            o.put("pack_version", p.packVersion);
            o.put("installed_at", p.installedAt);
            arr.put(o);
        }
        root.put("packs", arr);
        File f = localIndexFile(context);
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        if (f.exists() && !f.delete()) {
            Log.w(TAG, "Could not replace local_index");
        }
        if (!tmp.renameTo(f)) {
            copyFile(tmp, f);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    static String resolveUrl(String baseUrl, String file) {
        if (file.startsWith("http://") || file.startsWith("https://")) {
            return file;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("missing catalog base_url");
        }
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        if (file.startsWith("/")) {
            return base + file.substring(1);
        }
        return base + file;
    }

    private static String httpGetString(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new Exception("HTTP " + code + " empty body for " + urlStr);
            }
            String body = readStream(in);
            if (code >= 400) {
                throw new Exception("HTTP " + code + " for " + urlStr + ": " + body);
            }
            return body;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void httpDownloadToFile(String urlStr, File dest) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new Exception("HTTP " + code + " downloading " + urlStr);
            }
            in = new BufferedInputStream(conn.getInputStream());
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static String readFile(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            return readStream(in);
        } finally {
            in.close();
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
            out.close();
        }
    }

    static String sha256Hex(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte b : dig) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    public static final class Catalog {
        public int version;
        public String baseUrl = "";
        public int maxDownloaded = MAX_DOWNLOADED_PACKS;
        public final List<PackInfo> packs = new ArrayList<>();
    }

    public static final class PackInfo {
        public String id = "";
        public String name = "";
        public String file = "";
        public String sha256 = "";
        public long bytes;
        public int featureCount;
        public int packVersion = 1;
        public boolean builtinInApk;
        public final List<String> codesPrefix = new ArrayList<>();
    }

    public static final class InstalledPack {
        public String id = "";
        /** Display name from catalog at install time (may be empty for older installs). */
        public String name = "";
        public String file = "";
        public String sha256 = "";
        public int packVersion = 1;
        public long installedAt;

        public String displayName() {
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
            if (id == null || id.isEmpty()) {
                return "";
            }
            return id.replace('_', ' ');
        }
    }

    public static final class PackLimitException extends Exception {
        public final int limit;

        public PackLimitException(int limit) {
            super("Downloaded RDA pack limit reached: " + limit);
            this.limit = limit;
        }
    }
}
