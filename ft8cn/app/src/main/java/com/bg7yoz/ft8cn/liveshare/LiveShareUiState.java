package com.bg7yoz.ft8cn.liveshare;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure UI mapping for the main-screen live-share controls. */
public final class LiveShareUiState {
    private static final Pattern QUEUE_PATTERN = Pattern.compile("sharing \\((\\d+)\\)");
    private static final Pattern HTTP_CODE_PATTERN = Pattern.compile("\\((\\d{3})\\)");

    public enum Kind {
        IDLE,
        SHARING,
        QUEUE,
        STOPPING,
        UPLOADS_PAUSED,
        SESSION_UNAVAILABLE,
        ERROR
    }

    public final Kind kind;
    public final int queueCount;
    public final int httpCode;
    public final boolean actionInFlight;
    public final boolean shouldNotify;

    private LiveShareUiState(
            Kind kind,
            int queueCount,
            int httpCode,
            boolean actionInFlight,
            boolean shouldNotify) {
        this.kind = kind;
        this.queueCount = queueCount;
        this.httpCode = httpCode;
        this.actionInFlight = actionInFlight;
        this.shouldNotify = shouldNotify;
    }

    public static boolean hasVisibleControls(
            boolean enabled,
            String apiBaseUrl,
            String apiKey,
            String sessionToken) {
        return enabled
                && !blank(apiBaseUrl)
                && !blank(apiKey)
                && !blank(sessionToken);
    }

    public static LiveShareUiState fromControllerStatus(String status) {
        String value = status == null ? "" : status.trim();
        if ("stopping".equals(value)) {
            return state(Kind.STOPPING, 0, 0, true, false);
        }
        if (value.contains("authentication failed")
                || value.contains("uploads paused")) {
            return state(Kind.UPLOADS_PAUSED, 0, 401, false, true);
        }
        if (value.startsWith("error: session unavailable")) {
            Matcher codeMatcher = HTTP_CODE_PATTERN.matcher(value);
            int code = codeMatcher.find() ? Integer.parseInt(codeMatcher.group(1)) : 0;
            return state(Kind.SESSION_UNAVAILABLE, 0, code, false, true);
        }
        Matcher queueMatcher = QUEUE_PATTERN.matcher(value);
        if (queueMatcher.matches()) {
            int count = Integer.parseInt(queueMatcher.group(1));
            return state(count == 0 ? Kind.SHARING : Kind.QUEUE,
                    count, 0, false, false);
        }
        if ("sharing".equals(value)) {
            return state(Kind.SHARING, 0, 0, false, false);
        }
        if ("idle".equals(value)) {
            return state(Kind.IDLE, 0, 0, false, false);
        }
        return state(Kind.ERROR, 0, 0, false, value.startsWith("error"));
    }

    private static LiveShareUiState state(
            Kind kind,
            int queueCount,
            int httpCode,
            boolean actionInFlight,
            boolean shouldNotify) {
        return new LiveShareUiState(
                kind, queueCount, httpCode, actionInFlight, shouldNotify);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
