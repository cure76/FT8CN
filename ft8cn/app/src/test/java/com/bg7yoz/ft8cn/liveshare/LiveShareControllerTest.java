package com.bg7yoz.ft8cn.liveshare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveShareControllerTest {
    @Test
    public void locationCadenceNeverSendsBeforeMinimumInterval() {
        long lastSentAt = 1_000L;

        assertFalse(LiveShareController.shouldSendLocation(lastSentAt, 60_999L));
        assertTrue(LiveShareController.shouldSendLocation(lastSentAt, 61_000L));
    }

    @Test
    public void locationCadenceSendsFirstFixImmediately() {
        assertTrue(LiveShareController.shouldSendLocation(0L, 1L));
    }
}
