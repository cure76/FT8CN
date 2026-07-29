package com.bg7yoz.ft8cn.liveshare;

import android.database.sqlite.SQLiteDatabase;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.log.QSLRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the runtime live-share state and translates application events into queued API events.
 */
public final class LiveShareController {
    private static final long MIN_LOCATION_INTERVAL_MS = 60_000L;
    private static final Pattern RDA_PATTERN =
            Pattern.compile("\\bRDA:\\s*([A-Za-z]{2}-\\d{1,3})\\b");
    private static final LiveShareController INSTANCE = new LiveShareController();

    private final ExecutorService flushExecutor =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "live-share-flush"));

    private LiveShareQueue queue;
    private boolean stopping;
    private long lastSentLocationAt;
    private Double lastSentLatitude;
    private Double lastSentLongitude;
    private long frequencyHz;

    private LiveShareController() {
    }

    public static LiveShareController get() {
        return INSTANCE;
    }

    /** Must be called once after the application database has been opened. */
    public synchronized void initialize(SQLiteDatabase database) {
        if (queue == null && database != null) {
            queue = new LiveShareQueue(new LiveShareQueueStore(database));
        }
    }

    public synchronized void start() {
        if (!isConfigured() || queue == null) {
            postStatus("error");
            return;
        }
        stopping = false;
        GeneralVariables.liveShareSharing = true;
        postStatus("sharing");
        enqueuePosition(
                GeneralVariables.getMyMaidenheadGrid(),
                GeneralVariables.currentMyRdaCode,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLatitude : null,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null);
        flushExecutor.execute(this::flushQueue);
    }

    public synchronized void stop() {
        if (!GeneralVariables.liveShareSharing || stopping) {
            return;
        }
        stopping = true;
        postStatus("stopping");
        flushExecutor.execute(() -> {
            boolean stopped = false;
            try {
                flushQueue();
                LiveShareClient.stop(
                        GeneralVariables.getLiveShareApiBaseUrl(),
                        GeneralVariables.myCallsign,
                        GeneralVariables.getLiveShareApiKey(),
                        GeneralVariables.getLiveShareSessionToken());
                stopped = true;
            } catch (IOException ignored) {
                postStatus("error");
            } finally {
                synchronized (LiveShareController.this) {
                    GeneralVariables.liveShareSharing = false;
                    stopping = false;
                }
                if (stopped) {
                    postStatus("idle");
                }
            }
        });
    }

    public synchronized void onGridOrRdaChanged(String grid, String rda) {
        if (!isSharing()) {
            return;
        }
        enqueuePosition(
                grid,
                rda,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLatitude : null,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null);
    }

    public synchronized void onFrequencyHz(long freqHz) {
        frequencyHz = Math.max(0L, freqHz);
        if (!isSharing()) {
            return;
        }
        enqueuePosition(
                GeneralVariables.getMyMaidenheadGrid(),
                GeneralVariables.currentMyRdaCode,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLatitude : null,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null);
    }

    public synchronized void onLocation(double lat, double lon) {
        if (!isSharing() || !shouldSendLocation(lastSentLocationAt, System.currentTimeMillis())) {
            return;
        }
        enqueuePosition(
                GeneralVariables.getMyMaidenheadGrid(),
                GeneralVariables.currentMyRdaCode,
                lat,
                lon);
    }

    public synchronized void onQsoCompleted(QSLRecord record) {
        if (!isSharing() || record == null || blank(record.getToCallsign())) {
            return;
        }
        String myGrid = blank(record.getMyMaidenGrid())
                ? GeneralVariables.getMyMaidenheadGrid()
                : record.getMyMaidenGrid();
        if (blank(myGrid)) {
            return;
        }
        long qsoFrequency = record.getBandFreq() + Math.max(0, record.getWavFrequency());
        String eventId = record.id >= 0
                ? "qso-" + record.id
                : "qso-" + UUID.randomUUID();
        String rda = rdaFromComment(record.getComment());
        JSONObject body = LiveShareJson.qso(
                record.getToCallsign(),
                Integer.toString(record.getSendReport()),
                Integer.toString(record.getReceivedReport()),
                myGrid,
                record.getToMaidenGrid(),
                rda != null ? rda : GeneralVariables.currentMyRdaCode,
                asFrequencyHz(qsoFrequency),
                qsoTime(record),
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLatitude : null,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null,
                eventId);
        enqueue(LiveShareEventType.QSO, eventId, body);
    }

    static boolean shouldSendLocation(long lastSentAt, long now) {
        return lastSentAt == 0L || now - lastSentAt >= MIN_LOCATION_INTERVAL_MS;
    }

    private boolean isSharing() {
        return GeneralVariables.enableLiveShare && GeneralVariables.liveShareSharing && !stopping
                && queue != null;
    }

    private boolean isConfigured() {
        return GeneralVariables.enableLiveShare
                && !blank(GeneralVariables.getLiveShareApiBaseUrl())
                && !blank(GeneralVariables.getLiveShareApiKey())
                && !blank(GeneralVariables.getLiveShareSessionToken())
                && !blank(GeneralVariables.myCallsign);
    }

    private void enqueuePosition(
            String grid,
            String rda,
            Double lat,
            Double lon) {
        if (blank(grid)) {
            return;
        }
        long now = System.currentTimeMillis();
        String eventId = "position-" + UUID.randomUUID();
        JSONObject body = LiveShareJson.position(
                grid,
                rda,
                lat,
                lon,
                asFrequencyHz(frequencyHz),
                LiveShareJson.isoUtc(now),
                eventId);
        lastSentLocationAt = now;
        lastSentLatitude = lat;
        lastSentLongitude = lon;
        enqueue(LiveShareEventType.POSITION, eventId, body);
    }

    private void enqueue(LiveShareEventType type, String eventId, JSONObject body) {
        queue.enqueue(type, eventId, body.toString());
        postStatus("sharing (" + queue.pendingCount() + ")");
        flushExecutor.execute(this::flushQueue);
    }

    private void flushQueue() {
        if (queue == null || !GeneralVariables.liveShareSharing || !isConfigured()) {
            return;
        }
        try {
            int pending = queue.flush(new LiveShareQueue.LiveShareClientSink() {
                @Override
                public void sendPositions(java.util.List<String> jsonBodies) throws IOException {
                    LiveShareClient.postPositionBatch(
                            GeneralVariables.getLiveShareApiBaseUrl(),
                            GeneralVariables.myCallsign,
                            GeneralVariables.getLiveShareApiKey(),
                            GeneralVariables.getLiveShareSessionToken(),
                            jsonArray(jsonBodies));
                }

                @Override
                public void sendQsos(java.util.List<String> jsonBodies) throws IOException {
                    LiveShareClient.postQsoBatch(
                            GeneralVariables.getLiveShareApiBaseUrl(),
                            GeneralVariables.myCallsign,
                            GeneralVariables.getLiveShareApiKey(),
                            GeneralVariables.getLiveShareSessionToken(),
                            jsonArray(jsonBodies));
                }
            });
            postStatus("sharing (" + pending + ")");
        } catch (IOException ignored) {
            postStatus("error");
        }
    }

    private static JSONArray jsonArray(java.util.List<String> bodies) throws IOException {
        JSONArray items = new JSONArray();
        try {
            for (String body : bodies) {
                items.put(new JSONObject(body));
            }
            return items;
        } catch (JSONException e) {
            throw new IOException("Unable to read queued live-share event", e);
        }
    }

    private static Integer asFrequencyHz(long value) {
        return value > 0L && value <= Integer.MAX_VALUE ? (int) value : null;
    }

    private static String qsoTime(QSLRecord record) {
        String date = record.getQso_date_off();
        String time = record.getTime_off();
        if (!blank(date) && !blank(time)) {
            SimpleDateFormat parser = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            parser.setLenient(false);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                Date parsed = parser.parse(date + time);
                if (parsed != null) {
                    return LiveShareJson.isoUtc(parsed.getTime());
                }
            } catch (ParseException ignored) {
                // Fall back to the local completion time when an imported record is incomplete.
            }
        }
        return LiveShareJson.isoUtc(System.currentTimeMillis());
    }

    private static String rdaFromComment(String comment) {
        if (comment == null) {
            return null;
        }
        Matcher matcher = RDA_PATTERN.matcher(comment);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.US) : null;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void postStatus(String status) {
        GeneralVariables.mutableLiveShareStatus.postValue(status);
    }
}
