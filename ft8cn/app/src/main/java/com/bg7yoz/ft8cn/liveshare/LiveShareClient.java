package com.bg7yoz.ft8cn.liveshare;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class LiveShareClient {
    private static final int TIMEOUT_MS = 15_000;
    private static final int ERROR_SNIPPET_LIMIT = 512;

    private LiveShareClient() {
    }

    public static int health(String apiBase) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = open(apiBase, "/health");
            connection.setRequestMethod("GET");
            return connection.getResponseCode();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void postPosition(
            String apiBase,
            String callsign,
            String apiKey,
            String token,
            JSONObject body) throws IOException {
        post(apiBase, callsign, apiKey, sessionPath(token, "/positions"), body);
    }

    public static void postQso(
            String apiBase,
            String callsign,
            String apiKey,
            String token,
            JSONObject body) throws IOException {
        post(apiBase, callsign, apiKey, sessionPath(token, "/qsos"), body);
    }

    public static void postPositionBatch(
            String apiBase,
            String callsign,
            String apiKey,
            String token,
            JSONArray items) throws IOException {
        post(apiBase, callsign, apiKey, sessionPath(token, "/positions/batch"),
                batchBody(items));
    }

    public static void postQsoBatch(
            String apiBase,
            String callsign,
            String apiKey,
            String token,
            JSONArray items) throws IOException {
        post(apiBase, callsign, apiKey, sessionPath(token, "/qsos/batch"),
                batchBody(items));
    }

    public static void stop(
            String apiBase,
            String callsign,
            String apiKey,
            String token) throws IOException {
        post(apiBase, callsign, apiKey, sessionPath(token, "/stop"), null);
    }

    private static JSONObject batchBody(JSONArray items) {
        try {
            return new JSONObject().put("items", items);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to create batch JSON", e);
        }
    }

    private static String sessionPath(String token, String suffix) {
        return "/api/v1/sessions/" + token + suffix;
    }

    private static void post(
            String apiBase,
            String callsign,
            String apiKey,
            String path,
            JSONObject body) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = open(apiBase, path);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-FT8CN-Callsign", callsign);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] json = body != null
                    ? body.toString().getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(json.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json);
            }

            int code = connection.getResponseCode();
            throwForHttpError(code, readSnippet(connection.getErrorStream()));
            drain(connection.getInputStream());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static void throwForHttpError(int code, String body) throws LiveShareHttpException {
        if (code < 200 || code >= 300) {
            throw new LiveShareHttpException(code, body);
        }
    }

    private static HttpURLConnection open(String apiBase, String path) throws IOException {
        String base = apiBase == null ? "" : apiBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        HttpURLConnection connection =
                (HttpURLConnection) new URL(base + path).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        return connection;
    }

    private static void drain(InputStream stream) throws IOException {
        if (stream == null) {
            return;
        }
        try (InputStream in = stream) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                // discard
            }
        }
    }

    private static String readSnippet(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            int next;
            while (response.length() < ERROR_SNIPPET_LIMIT && (next = reader.read()) != -1) {
                response.append((char) next);
            }
            return response.toString();
        }
    }
}
