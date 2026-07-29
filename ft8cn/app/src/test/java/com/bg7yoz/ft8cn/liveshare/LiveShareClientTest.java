package com.bg7yoz.ft8cn.liveshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class LiveShareClientTest {
    @Test
    public void nonSuccessResponseBecomesTypedHttpException() {
        try {
            LiveShareClient.throwForHttpError(409, "{\"error\":\"already stopped\"}");
            fail("Expected LiveShareHttpException");
        } catch (LiveShareHttpException error) {
            assertEquals(409, error.getCode());
            assertEquals("{\"error\":\"already stopped\"}", error.getBody());
        }
    }

    @Test
    public void successfulResponseDoesNotThrow() throws Exception {
        LiveShareClient.throwForHttpError(204, "");
    }
}
