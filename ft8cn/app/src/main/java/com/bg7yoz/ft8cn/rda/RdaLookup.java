package com.bg7yoz.ft8cn.rda;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline RDA lookup from GeoJSON packs in assets/rda and downloaded filesDir/rda.
 * Built-in: Moscow + Moscow Oblast (MA-*, MO-*).
 */
public final class RdaLookup {
    private static final String TAG = "RdaLookup";
    private static final String INDEX_PATH = "rda/index.json";

    private static final RdaLookup INSTANCE = new RdaLookup();

    private final Object lock = new Object();
    private final List<RdaFeature> features = new ArrayList<>();
    private volatile boolean loaded = false;
    private volatile boolean loadAttempted = false;

    public static RdaLookup getInstance() {
        return INSTANCE;
    }

    private RdaLookup() {
    }

    /** Load packs from assets + downloaded cache. Safe to call repeatedly. */
    public void ensureLoaded(Context context) {
        if (loaded) {
            return;
        }
        synchronized (lock) {
            if (loaded || loadAttempted) {
                return;
            }
            loadAttempted = true;
            loadLocked(context.getApplicationContext());
        }
    }

    /** Force reload after pack install/delete. */
    public void reload(Context context) {
        synchronized (lock) {
            loaded = false;
            loadAttempted = true;
            features.clear();
            loadLocked(context.getApplicationContext());
        }
    }

    private void loadLocked(Context app) {
        try {
            List<RdaFeature> parsed = new ArrayList<>();
            Set<String> downloadedIds = new HashSet<>();

            // Downloaded packs first (priority over same id in assets).
            for (RdaPackManager.InstalledPack inst : RdaPackManager.getInstalled(app)) {
                File geo = new File(RdaPackManager.getRdaDir(app), inst.file);
                if (!geo.isFile()) {
                    Log.w(TAG, "Missing downloaded pack file: " + geo);
                    continue;
                }
                parseGeoJson(readFile(geo), parsed);
                downloadedIds.add(inst.id);
            }

            JSONObject index = new JSONObject(readAsset(app, INDEX_PATH));
            JSONArray packs = index.optJSONArray("packs");
            if (packs != null) {
                for (int i = 0; i < packs.length(); i++) {
                    JSONObject pack = packs.getJSONObject(i);
                    if (!pack.optBoolean("enabled", true)) {
                        continue;
                    }
                    String id = pack.optString("id", "");
                    if (!id.isEmpty() && downloadedIds.contains(id)) {
                        continue;
                    }
                    String file = pack.optString("file", "");
                    if (file.isEmpty()) {
                        continue;
                    }
                    parseGeoJson(readAsset(app, "rda/" + file), parsed);
                }
            }

            // Smaller polygons first (town inside district).
            Collections.sort(parsed, Comparator.comparingDouble(f -> f.bboxArea));
            features.clear();
            features.addAll(parsed);
            loaded = true;
            Log.i(TAG, "Loaded " + features.size() + " RDA polygons"
                    + " (downloaded=" + downloadedIds.size() + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load RDA packs", e);
            loaded = true;
        }
    }

    public boolean isReady() {
        return loaded && !features.isEmpty();
    }

    public int featureCount() {
        return features.size();
    }

    /**
     * @return RDA code or "" if unknown / not covered
     */
    public String lookup(double latitude, double longitude) {
        if (!loaded || features.isEmpty()) {
            return "";
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return "";
        }
        for (int i = 0; i < features.size(); i++) {
            RdaFeature f = features.get(i);
            if (!f.bboxContains(latitude, longitude)) {
                continue;
            }
            if (f.contains(latitude, longitude)) {
                return f.code;
            }
        }
        return "";
    }

    private static void parseGeoJson(String text, List<RdaFeature> out) throws Exception {
        JSONObject root = new JSONObject(text);
        JSONArray feats = root.optJSONArray("features");
        if (feats == null) {
            return;
        }
        for (int i = 0; i < feats.length(); i++) {
            JSONObject feat = feats.getJSONObject(i);
            JSONObject props = feat.optJSONObject("properties");
            JSONObject geom = feat.optJSONObject("geometry");
            if (props == null || geom == null) {
                continue;
            }
            String code = props.optString("rda_code", "").trim().toUpperCase(Locale.US);
            if (code.isEmpty()) {
                continue;
            }
            String type = geom.optString("type", "");
            JSONArray coordinates = geom.optJSONArray("coordinates");
            if (coordinates == null) {
                continue;
            }
            List<Polygon> polygons = new ArrayList<>();
            if ("Polygon".equals(type)) {
                Polygon p = readPolygon(coordinates);
                if (p != null) {
                    polygons.add(p);
                }
            } else if ("MultiPolygon".equals(type)) {
                for (int p = 0; p < coordinates.length(); p++) {
                    Polygon poly = readPolygon(coordinates.getJSONArray(p));
                    if (poly != null) {
                        polygons.add(poly);
                    }
                }
            }
            if (!polygons.isEmpty()) {
                out.add(new RdaFeature(code, polygons));
            }
        }
    }

    private static Polygon readPolygon(JSONArray polygonCoords) throws Exception {
        if (polygonCoords.length() < 1) {
            return null;
        }
        double[][] exterior = readRing(polygonCoords.getJSONArray(0));
        if (exterior == null) {
            return null;
        }
        List<double[][]> holes = new ArrayList<>();
        for (int r = 1; r < polygonCoords.length(); r++) {
            double[][] hole = readRing(polygonCoords.getJSONArray(r));
            if (hole != null) {
                holes.add(hole);
            }
        }
        return new Polygon(exterior, holes);
    }

    private static double[][] readRing(JSONArray ringJson) throws Exception {
        int n = ringJson.length();
        if (n < 3) {
            return null;
        }
        double[][] ring = new double[n][2];
        for (int i = 0; i < n; i++) {
            JSONArray pt = ringJson.getJSONArray(i);
            ring[i][0] = pt.getDouble(0); // lon
            ring[i][1] = pt.getDouble(1); // lat
        }
        return ring;
    }

    private static String readAsset(Context context, String path) throws Exception {
        InputStream in = context.getAssets().open(path);
        try {
            return readStream(in);
        } finally {
            in.close();
        }
    }

    private static String readFile(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            return readStream(in);
        } finally {
            in.close();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(Math.max(1024, in.available()));
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    /** Ray-casting. Ring points are [lon, lat]. */
    static boolean pointInRing(double lat, double lon, double[][] ring) {
        boolean inside = false;
        int n = ring.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i][0], yi = ring[i][1];
            double xj = ring[j][0], yj = ring[j][1];
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    static final class Polygon {
        final double[][] exterior;
        final List<double[][]> holes;

        Polygon(double[][] exterior, List<double[][]> holes) {
            this.exterior = exterior;
            this.holes = holes;
        }

        boolean contains(double lat, double lon) {
            if (!pointInRing(lat, lon, exterior)) {
                return false;
            }
            for (int i = 0; i < holes.size(); i++) {
                if (pointInRing(lat, lon, holes.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    static final class RdaFeature {
        final String code;
        final List<Polygon> polygons;
        final double minLat, maxLat, minLon, maxLon;
        final double bboxArea;

        RdaFeature(String code, List<Polygon> polygons) {
            this.code = code;
            this.polygons = polygons;
            double minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;
            for (Polygon poly : polygons) {
                for (double[] pt : poly.exterior) {
                    double lon = pt[0], lat = pt[1];
                    if (lat < minLa) minLa = lat;
                    if (lat > maxLa) maxLa = lat;
                    if (lon < minLo) minLo = lon;
                    if (lon > maxLo) maxLo = lon;
                }
            }
            this.minLat = minLa;
            this.maxLat = maxLa;
            this.minLon = minLo;
            this.maxLon = maxLo;
            this.bboxArea = Math.max(0, (maxLa - minLa) * (maxLo - minLo));
        }

        boolean bboxContains(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }

        boolean contains(double lat, double lon) {
            for (int i = 0; i < polygons.size(); i++) {
                if (polygons.get(i).contains(lat, lon)) {
                    return true;
                }
            }
            return false;
        }
    }
}
