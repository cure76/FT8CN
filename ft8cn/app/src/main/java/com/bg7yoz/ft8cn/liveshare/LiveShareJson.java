package com.bg7yoz.ft8cn.liveshare;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class LiveShareJson {
    private LiveShareJson() {
    }

    public static String isoUtc(long epochMs) {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMs));
    }

    public static JSONObject position(
            String grid,
            String rda,
            Double lat,
            Double lon,
            Integer freqHz,
            String recordedAtIso,
            String clientEventId) {
        try {
            JSONObject body = new JSONObject();
            body.put("grid", upper(grid));
            body.put("recorded_at", recordedAtIso);
            putOptional(body, "rda", rda);
            putOptional(body, "lat", lat);
            putOptional(body, "lon", lon);
            putOptional(body, "freq_hz", freqHz);
            putOptional(body, "client_event_id", clientEventId);
            return body;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to create position JSON", e);
        }
    }

    public static JSONObject qso(
            String callsign,
            String rstSent,
            String rstRcvd,
            String myGrid,
            String theirGrid,
            String rda,
            Integer freqHz,
            String qsoAtIso,
            Double lat,
            Double lon,
            String clientEventId) {
        try {
            JSONObject body = new JSONObject();
            body.put("callsign", upper(callsign));
            body.put("my_grid", upper(myGrid));
            body.put("qso_at", qsoAtIso);
            putOptional(body, "rst_sent", rstSent);
            putOptional(body, "rst_rcvd", rstRcvd);
            putOptional(body, "their_grid", upper(theirGrid));
            putOptional(body, "rda", rda);
            putOptional(body, "freq_hz", freqHz);
            putOptional(body, "lat", lat);
            putOptional(body, "lon", lon);
            putOptional(body, "client_event_id", clientEventId);
            return body;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to create QSO JSON", e);
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.US);
    }

    private static void putOptional(JSONObject body, String key, Object value)
            throws JSONException {
        if (value != null) {
            body.put(key, value);
        }
    }
}
