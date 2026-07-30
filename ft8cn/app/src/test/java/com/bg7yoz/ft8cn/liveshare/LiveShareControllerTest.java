package com.bg7yoz.ft8cn.liveshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bg7yoz.ft8cn.log.QSLRecord;

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

    @Test
    public void qsoClientEventIdIsStableWithoutDatabaseRowId() {
        QSLRecord first = new QSLRecord(
                1_700_000_000_000L,
                1_700_000_015_000L,
                "RN3AOE",
                "KO85",
                "ba5an",
                "PM00",
                -16,
                -17,
                "FT8",
                21_074_000L,
                681);
        QSLRecord second = new QSLRecord(
                1_700_000_000_000L,
                1_700_000_075_000L,
                "RN3AOE",
                "KO85",
                "BA5AN",
                "PM00",
                -16,
                -17,
                "FT8",
                21_074_000L,
                681);

        String firstId = LiveShareController.clientEventIdForQso(first);
        String secondId = LiveShareController.clientEventIdForQso(second);

        assertEquals(firstId, secondId);
        assertTrue(firstId.startsWith("qso-BA5AN-"));
        assertFalse(firstId.contains("qso-" + java.util.UUID.randomUUID()));
        assertEquals(
                LiveShareController.contactKeyForQso(first),
                LiveShareController.contactKeyForQso(second));
    }

    @Test
    public void qsoClientEventIdUsesDatabaseRowWhenPresent() {
        QSLRecord record = new QSLRecord(
                1_700_000_000_000L,
                1_700_000_015_000L,
                "RN3AOE",
                "KO85",
                "R1ABC",
                "KO86",
                -10,
                -12,
                "FT8",
                7_074_000L,
                0);
        record.id = 42L;
        assertEquals("qso-42", LiveShareController.clientEventIdForQso(record));
    }
}
