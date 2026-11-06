package com.hkiptv.lab.player

import android.content.pm.ActivityInfo
import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class PlayerUiLogicTest {
    @Test fun playPauseStateMatchesPlayback() {
        assertEquals(PlayerVisualState.PLAYING, PlayerUiLogic.visualState(Player.STATE_READY, isPlaying = true, hasError = false))
        assertEquals(PlayerVisualState.PAUSED, PlayerUiLogic.visualState(Player.STATE_READY, isPlaying = false, hasError = false))
        assertEquals(PlayerControlAction.PAUSE, PlayerUiLogic.playPauseAction(true))
        assertEquals(PlayerControlAction.PLAY, PlayerUiLogic.playPauseAction(false))
    }

    @Test fun rewindTenSecondsNeverBecomesNegative() {
        assertEquals(15_000L, PlayerUiLogic.seekBack(25_000L))
        assertEquals(0L, PlayerUiLogic.seekBack(4_000L))
    }

    @Test fun forwardTenSecondsUsesDurationBoundary() {
        assertEquals(35_000L, PlayerUiLogic.seekForward(25_000L, 60_000L))
    }

    @Test fun seekStaysInsideDuration() {
        assertEquals(60_000L, PlayerUiLogic.validSeek(80_000L, 60_000L))
    }

    @Test fun seekNearEndStopsAtEnd() {
        assertEquals(60_000L, PlayerUiLogic.seekForward(58_000L, 60_000L))
    }

    @Test fun unknownDurationDoesNotCreateProgress() {
        assertFalse(PlayerUiLogic.hasKnownDuration(C.TIME_UNSET))
        assertEquals(0f, PlayerUiLogic.progress(5_000L, C.TIME_UNSET))
        assertEquals("--:--", PlayerUiLogic.formatTime(C.TIME_UNSET))
        assertEquals(15_000L, PlayerUiLogic.seekForward(5_000L, C.TIME_UNSET))
    }

    @Test fun progressAndBufferAreClamped() {
        assertEquals(.5f, PlayerUiLogic.progress(30_000L, 60_000L))
        assertEquals(1f, PlayerUiLogic.buffered(80_000L, 60_000L))
    }

    @Test fun bufferingHasDedicatedState() {
        assertEquals(PlayerVisualState.BUFFERING, PlayerUiLogic.visualState(Player.STATE_BUFFERING, isPlaying = false, hasError = false))
    }

    @Test fun errorHasDedicatedState() {
        assertEquals(PlayerVisualState.ERROR, PlayerUiLogic.visualState(Player.STATE_READY, isPlaying = true, hasError = true))
    }

    @Test fun controlsCanAutoHideOnlyDuringPlayback() {
        assertFalse(PlayerUiLogic.keepControlsVisible(isPlaying = true, isSeeking = false, hasError = false))
    }

    @Test fun controlsStayVisibleWhilePaused() {
        assertTrue(PlayerUiLogic.keepControlsVisible(isPlaying = false, isSeeking = false, hasError = false))
    }

    @Test fun controlsStayVisibleDuringSeek() {
        assertTrue(PlayerUiLogic.keepControlsVisible(isPlaying = true, isSeeking = true, hasError = false))
    }

    @Test fun orientationRestorationUsesCapturedValue() {
        val state = FullscreenRestoration(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, state.orientationToRestore())
    }

    @Test fun lifecyclePausesOnlyActivePlayback() {
        assertEquals(PlayerLifecycleAction.PAUSE, PlayerLifecyclePolicy.onStop(true))
        assertEquals(PlayerLifecycleAction.NONE, PlayerLifecyclePolicy.onStop(false))
    }
}
