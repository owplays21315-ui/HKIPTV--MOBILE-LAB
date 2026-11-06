package com.hkiptv.lab.player

import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.max
import kotlin.math.min

enum class PlayerVisualState { PREPARING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }
enum class PlayerControlAction { PLAY, PAUSE }

object PlayerUiLogic {
    const val SEEK_INTERVAL_MS = 10_000L
    const val CONTROLS_TIMEOUT_MS = 4_000L

    fun seekBack(positionMs: Long): Long = max(0L, positionMs - SEEK_INTERVAL_MS)

    fun seekForward(positionMs: Long, durationMs: Long): Long =
        validSeek(positionMs + SEEK_INTERVAL_MS, durationMs)

    fun validSeek(positionMs: Long, durationMs: Long): Long {
        val nonNegative = max(0L, positionMs)
        return if (hasKnownDuration(durationMs)) min(nonNegative, durationMs) else nonNegative
    }

    fun progress(positionMs: Long, durationMs: Long): Float =
        if (hasKnownDuration(durationMs)) (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat() else 0f

    fun buffered(bufferedPositionMs: Long, durationMs: Long): Float =
        if (hasKnownDuration(durationMs)) (bufferedPositionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat() else 0f

    fun hasKnownDuration(durationMs: Long): Boolean = durationMs > 0 && durationMs != C.TIME_UNSET

    fun keepControlsVisible(isPlaying: Boolean, isSeeking: Boolean, hasError: Boolean): Boolean =
        !isPlaying || isSeeking || hasError

    fun playPauseAction(isPlaying: Boolean): PlayerControlAction =
        if (isPlaying) PlayerControlAction.PAUSE else PlayerControlAction.PLAY

    fun visualState(playbackState: Int, isPlaying: Boolean, hasError: Boolean): PlayerVisualState = when {
        hasError -> PlayerVisualState.ERROR
        playbackState == Player.STATE_BUFFERING -> PlayerVisualState.BUFFERING
        playbackState == Player.STATE_ENDED -> PlayerVisualState.ENDED
        playbackState == Player.STATE_READY && isPlaying -> PlayerVisualState.PLAYING
        playbackState == Player.STATE_READY -> PlayerVisualState.PAUSED
        else -> PlayerVisualState.PREPARING
    }

    fun formatTime(milliseconds: Long): String {
        if (milliseconds < 0 || milliseconds == C.TIME_UNSET) return "--:--"
        val totalSeconds = milliseconds / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }
}

data class FullscreenRestoration(val originalOrientation: Int) {
    fun orientationToRestore(): Int = originalOrientation
}

enum class PlayerLifecycleAction { NONE, PAUSE }

object PlayerLifecyclePolicy {
    fun onStop(isPlaying: Boolean): PlayerLifecycleAction =
        if (isPlaying) PlayerLifecycleAction.PAUSE else PlayerLifecycleAction.NONE
}
