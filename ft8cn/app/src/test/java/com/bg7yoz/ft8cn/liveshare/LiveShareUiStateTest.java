package com.bg7yoz.ft8cn.liveshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveShareUiStateTest {
    @Test
    public void controlsRequireEnabledAndCompleteCredentials() {
        assertTrue(LiveShareUiState.hasVisibleControls(
                true, "https://api.example", "key", "token"));
        assertFalse(LiveShareUiState.hasVisibleControls(
                false, "https://api.example", "key", "token"));
        assertFalse(LiveShareUiState.hasVisibleControls(
                true, " ", "key", "token"));
        assertFalse(LiveShareUiState.hasVisibleControls(
                true, "https://api.example", "", "token"));
        assertFalse(LiveShareUiState.hasVisibleControls(
                true, "https://api.example", "key", null));
    }

    @Test
    public void sharingQueueStatusSeparatesActiveAndQueuedStates() {
        assertEquals(
                LiveShareUiState.Kind.SHARING,
                LiveShareUiState.fromControllerStatus("sharing (0)").kind);
        LiveShareUiState queued =
                LiveShareUiState.fromControllerStatus("sharing (7)");
        assertEquals(LiveShareUiState.Kind.QUEUE, queued.kind);
        assertEquals(7, queued.queueCount);
    }

    @Test
    public void authenticationFailureIsClearlyPaused() {
        LiveShareUiState state = LiveShareUiState.fromControllerStatus(
                "error: authentication failed; uploads paused");

        assertEquals(LiveShareUiState.Kind.UPLOADS_PAUSED, state.kind);
        assertTrue(state.shouldNotify);
    }

    @Test
    public void unavailableSessionErrorsRequestNotification() {
        LiveShareUiState state = LiveShareUiState.fromControllerStatus(
                "error: session unavailable (410)");

        assertEquals(LiveShareUiState.Kind.SESSION_UNAVAILABLE, state.kind);
        assertEquals(410, state.httpCode);
        assertTrue(state.shouldNotify);
    }

    @Test
    public void stoppingDisablesActionUntilControllerCompletes() {
        assertTrue(LiveShareUiState.fromControllerStatus("stopping").actionInFlight);
        assertFalse(LiveShareUiState.fromControllerStatus("idle").actionInFlight);
    }
}
