package com.bg7yoz.ft8cn.liveshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveShareControllerTest {
    @Test
    public void locationCadenceNeverSendsBeforeMinimumInterval() {
        LiveShareController.LocationCadence cadence =
                new LiveShareController.LocationCadence();
        cadence.recordSent(1_000L, 0.0, 0.0, true);

        assertFalse(cadence.shouldSend(60_999L, 0.001, 0.0));
        assertTrue(cadence.shouldSend(61_000L, 0.0, 0.0));
    }

    @Test
    public void locationCadenceSendsFirstFixImmediately() {
        LiveShareController.LocationCadence cadence =
                new LiveShareController.LocationCadence();

        assertTrue(cadence.shouldSend(1L, 0.0, 0.0));
    }

    @Test
    public void gridOnlyPositionDoesNotAdvanceGpsCadence() {
        LiveShareController.LocationCadence cadence =
                new LiveShareController.LocationCadence();

        cadence.recordSent(1_000L, 12.0, 34.0, false);

        assertTrue(cadence.shouldSend(1_001L, 0.0, 0.0));
    }

    @Test
    public void httpFailuresHaveRequiredTerminalBehavior() {
        assertEquals(
                "error: authentication failed; uploads paused",
                LiveShareController.terminalStatusForHttpCode(401));
        assertEquals(
                "error: session unavailable (404)",
                LiveShareController.terminalStatusForHttpCode(404));
        assertEquals(
                "error: session unavailable (409)",
                LiveShareController.terminalStatusForHttpCode(409));
        assertEquals(
                "error: session unavailable (410)",
                LiveShareController.terminalStatusForHttpCode(410));
        assertNull(LiveShareController.terminalStatusForHttpCode(500));
    }
}
