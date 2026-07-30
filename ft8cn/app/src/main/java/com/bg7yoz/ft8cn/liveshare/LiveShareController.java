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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
    private final LocationCadence locationCadence = new LocationCadence();
    /** Dedup QSO uploads for the current Start→Stop share session. */
    private final Set<String> sharedQsoEventIds = new HashSet<>();
    private final Set<String> sharedQsoContactKeys = new HashSet<>();

    private LiveShareQueue queue;
    private boolean stopping;
    private volatile boolean uploadsPaused;
    private long lifecycleGeneration;
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
        if (stopping) {
            postStatus("stopping");
            return;
        }
        if (!isConfigured() || queue == null) {
            postStatus("error");
            return;
        }
        lifecycleGeneration++;
        uploadsPaused = false;
        sharedQsoEventIds.clear();
        sharedQsoContactKeys.clear();
        frequencyHz = GeneralVariables.band
                + Math.round(GeneralVariables.getBaseFrequency());
        GeneralVariables.liveShareSharing = true;
        postStatus("sharing");
        enqueuePosition(
                GeneralVariables.getMyMaidenheadGrid(),
                GeneralVariables.currentMyRdaCode,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLatitude : null,
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null,
                false);
        flushExecutor.execute(this::flushQueueBestEffort);
    }

    public synchronized void stop() {
        if (!GeneralVariables.liveShareSharing || stopping) {
            return;
        }
        stopping = true;
        final long stopGeneration = ++lifecycleGeneration;
        postStatus("stopping");
        flushExecutor.execute(() -> {
            boolean stopped = false;
            try {
                stopped = flushBestEffortThenStop(
                        this::flushQueue,
                        () -> LiveShareClient.stop(
                                GeneralVariables.getLiveShareApiBaseUrl(),
                                GeneralVariables.myCallsign,
                                GeneralVariables.getLiveShareApiKey(),
                                GeneralVariables.getLiveShareSessionToken()),
                        this::handleUploadFailure);
            } finally {
                boolean completedCurrentStop = false;
                synchronized (LiveShareController.this) {
                    if (lifecycleGeneration == stopGeneration) {
                        GeneralVariables.liveShareSharing = false;
                        stopping = false;
                        sharedQsoEventIds.clear();
                        sharedQsoContactKeys.clear();
                        completedCurrentStop = true;
                    }
                }
                if (stopped && completedCurrentStop) {
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
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null,
                false);
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
                GeneralVariables.hasLastKnownLocation ? GeneralVariables.lastKnownLongitude : null,
                false);
    }

    public synchronized void onLocation(double lat, double lon) {
        long now = System.currentTimeMillis();
        if (!isSharing() || !locationCadence.shouldSend(now, lat, lon)) {
            return;
        }
        enqueuePosition(
                GeneralVariables.getMyMaidenheadGrid(),
                GeneralVariables.currentMyRdaCode,
                lat,
                lon,
                true);
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
        String eventId = clientEventIdForQso(record);
        String contactKey = contactKeyForQso(record);
        if (sharedQsoEventIds.contains(eventId) || sharedQsoContactKeys.contains(contactKey)) {
            return;
        }
        long qsoFrequency = record.getBandFreq() + Math.max(0, record.getWavFrequency());
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
        sharedQsoEventIds.add(eventId);
        sharedQsoContactKeys.add(contactKey);
        enqueue(LiveShareEventType.QSO, eventId, body);
    }

    /**
     * Stable id so retries / repeated 73 completions hash to the same server row.
     * Prefer DB row id when present; otherwise callsign + QSO start + band.
     */
    static String clientEventIdForQso(QSLRecord record) {
        if (record.id >= 0) {
            return "qso-" + record.id;
        }
        String call = record.getToCallsign() == null
                ? ""
                : record.getToCallsign().trim().toUpperCase(Locale.US);
        String date = blankStatic(record.getQso_date()) ? "00000000" : record.getQso_date();
        String timeOn = blankStatic(record.getTime_on()) ? "000000" : record.getTime_on();
        return "qso-" + call + "-" + date + "-" + timeOn + "-" + record.getBandFreq();
    }

    static String contactKeyForQso(QSLRecord record) {
        String call = record.getToCallsign() == null
                ? ""
                : record.getToCallsign().trim().toUpperCase(Locale.US);
        return call + "|" + record.getBandFreq();
    }

    private static boolean blankStatic(String value) {
        return value == null || value.trim().isEmpty();
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
            Double lon,
            boolean gpsPosition) {
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
        locationCadence.recordSent(now, lat, lon, gpsPosition);
        enqueue(LiveShareEventType.POSITION, eventId, body);
    }

    private void enqueue(LiveShareEventType type, String eventId, JSONObject body) {
        queue.enqueue(type, eventId, body.toString());
        postStatus("sharing (" + queue.pendingCount() + ")");
        flushExecutor.execute(this::flushQueueBestEffort);
    }

    private void flushQueue() throws IOException {
        if (queue == null || uploadsPaused
                || !GeneralVariables.liveShareSharing || !isConfigured()) {
            return;
        }
        int[] droppedEvents = {0};
        int[] droppedCode = {0};
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

            @Override
            public void onDroppedClientPayload(int code, int eventCount) {
                droppedEvents[0] += eventCount;
                droppedCode[0] = code;
            }
        });
        if (droppedEvents[0] > 0) {
            postStatus("sharing (" + pending + "); dropped " + droppedEvents[0]
                    + " invalid event(s) (HTTP " + droppedCode[0] + ")");
        } else {
            postStatus("sharing (" + pending + ")");
        }
    }

    private void flushQueueBestEffort() {
        try {
            flushQueue();
        } catch (IOException error) {
            handleUploadFailure(error);
        }
    }

    static boolean flushBestEffortThenStop(
            IoAction flush,
            IoAction stop,
            UploadFailureHandler failureHandler) {
        try {
            flush.run();
        } catch (IOException error) {
            failureHandler.handle(error);
        }
        try {
            stop.run();
            return true;
        } catch (IOException error) {
            failureHandler.handle(error);
            return false;
        }
    }

    private void handleUploadFailure(IOException error) {
        if (error instanceof LiveShareHttpException) {
            int code = ((LiveShareHttpException) error).getCode();
            String terminalStatus = terminalStatusForHttpCode(code);
            if (terminalStatus != null) {
                uploadsPaused = true;
                if (code != 401) {
                    GeneralVariables.liveShareSharing = false;
                }
                postStatus(terminalStatus);
                return;
            }
        }
        postStatus("error");
    }

    static String terminalStatusForHttpCode(int code) {
        if (code == 401) {
            return "error: authentication failed; uploads paused";
        }
        if (code == 404 || code == 409 || code == 410) {
            return "error: session unavailable (" + code + ")";
        }
        return null;
    }

    interface IoAction {
        void run() throws IOException;
    }

    interface UploadFailureHandler {
        void handle(IOException error);
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

    static final class LocationCadence {
        private long lastSentAt;
        private Double lastLatitude;
        private Double lastLongitude;

        boolean shouldSend(long now, double latitude, double longitude) {
            if (lastSentAt == 0L) {
                return true;
            }
            long elapsed = now - lastSentAt;
            if (elapsed < MIN_LOCATION_INTERVAL_MS) {
                return false;
            }
            return distanceMeters(lastLatitude, lastLongitude, latitude, longitude) >= 100.0
                    || elapsed >= MIN_LOCATION_INTERVAL_MS;
        }

        void recordSent(
                long now,
                Double latitude,
                Double longitude,
                boolean gpsPosition) {
            if (!gpsPosition || latitude == null || longitude == null) {
                return;
            }
            lastSentAt = now;
            lastLatitude = latitude;
            lastLongitude = longitude;
        }

        private static double distanceMeters(
                Double fromLatitude,
                Double fromLongitude,
                double toLatitude,
                double toLongitude) {
            if (fromLatitude == null || fromLongitude == null) {
                return Double.POSITIVE_INFINITY;
            }
            double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
            double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
            double fromRadians = Math.toRadians(fromLatitude);
            double toRadians = Math.toRadians(toLatitude);
            double a = Math.sin(latitudeDelta / 2.0) * Math.sin(latitudeDelta / 2.0)
                    + Math.cos(fromRadians) * Math.cos(toRadians)
                    * Math.sin(longitudeDelta / 2.0) * Math.sin(longitudeDelta / 2.0);
            return 6_371_000.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        }
    }
}
