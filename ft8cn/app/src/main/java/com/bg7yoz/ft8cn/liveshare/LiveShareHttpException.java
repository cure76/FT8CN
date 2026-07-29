package com.bg7yoz.ft8cn.liveshare;

import java.io.IOException;

/** An HTTP response from the live-share API outside the successful 2xx range. */
public final class LiveShareHttpException extends IOException {
    private final int code;
    private final String body;

    public LiveShareHttpException(int code, String body) {
        super("Live share HTTP " + code
                + (body == null || body.isEmpty() ? "" : ": " + body));
        this.code = code;
        this.body = body == null ? "" : body;
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return body;
    }
}
