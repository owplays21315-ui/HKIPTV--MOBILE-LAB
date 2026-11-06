package com.hkiptv.lab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hkiptv.lab.player.PlayerUiLogic
import com.hkiptv.lab.player.PlayerVisualState

private val HkRed = Color(0xFFE11D2E)
private val HkSurfaceRaised = Color(0xFF1B1B20)
private val SecondaryText = Color(0xFFB6B6BD)

@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    state: PlayerVisualState,
    title: String,
    subtitle: String?,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isSeeking: Boolean,
    seekPositionMs: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekFinished: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(92.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .82f), Color.Transparent)))
            )
            Box(
                Modifier.fillMaxWidth().height(190.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
            )
            PlayerTopBar(onBack, Modifier.align(Alignment.TopCenter))
            PlayerCenterControls(state, onPlayPause, onReplay, onRewind, onForward, Modifier.align(Alignment.Center))
            PlayerBottomControls(
                title, subtitle, positionMs, durationMs, bufferedPositionMs, isSeeking, seekPositionMs,
                onSeekStart, onSeekChange, onSeekFinished,
                Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PlayerTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
        Text("HK IPTV", color = Color.White.copy(alpha = .88f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun PlayerCenterControls(
    state: PlayerVisualState,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        ControlButton(Icons.Default.Replay10, "Retroceder 10 segundos", onRewind)
        val ended = state == PlayerVisualState.ENDED
        val playing = state == PlayerVisualState.PLAYING || state == PlayerVisualState.BUFFERING
        ControlButton(
            icon = when { ended -> Icons.Default.Replay; playing -> Icons.Default.Pause; else -> Icons.Default.PlayArrow },
            description = when { ended -> "Reproduzir novamente"; playing -> "Pausar"; else -> "Reproduzir" },
            onClick = if (ended) onReplay else onPlayPause,
            prominent = true
        )
        ControlButton(Icons.Default.Forward10, "Avançar 10 segundos", onForward)
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    prominent: Boolean = false
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(if (prominent) 58.dp else 48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (prominent) Color.White.copy(alpha = .2f) else Color.Black.copy(alpha = .38f),
            contentColor = Color.White
        )
    ) { Icon(icon, description, Modifier.size(if (prominent) 32.dp else 26.dp)) }
}

@Composable
private fun PlayerBottomControls(
    title: String,
    subtitle: String?,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isSeeking: Boolean,
    seekPositionMs: Long,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = SecondaryText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        PlayerProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            isSeeking = isSeeking,
            seekPositionMs = seekPositionMs,
            onSeekStart = onSeekStart,
            onSeekChange = onSeekChange,
            onSeekFinished = onSeekFinished
        )
        val shownPosition = if (isSeeking) seekPositionMs else positionMs
        Text(
            PlayerUiLogic.formatTime(shownPosition) + " / " + PlayerUiLogic.formatTime(durationMs),
            color = Color.White.copy(alpha = .9f), fontSize = 12.sp
        )
    }
}

@Composable
fun PlayerProgressBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isSeeking: Boolean,
    seekPositionMs: Long,
    onSeekStart: () -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekFinished: () -> Unit
) {
    val knownDuration = PlayerUiLogic.hasKnownDuration(durationMs)
    val value = (if (isSeeking) seekPositionMs else positionMs).coerceAtLeast(0L)
    Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
            val end = size.width
            drawLine(Color.White.copy(alpha = .22f), Offset.Zero, Offset(end, 0f), strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = .48f), Offset.Zero, Offset(end * PlayerUiLogic.buffered(bufferedPositionMs, durationMs), 0f), strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(HkRed, Offset.Zero, Offset(end * PlayerUiLogic.progress(value, durationMs), 0f), strokeWidth = size.height, cap = StrokeCap.Round)
        }
        Slider(
            value = if (knownDuration) value.coerceAtMost(durationMs).toFloat() else 0f,
            onValueChange = { onSeekStart(); onSeekChange(it.toLong()) },
            onValueChangeFinished = onSeekFinished,
            enabled = knownDuration,
            valueRange = 0f..if (knownDuration) durationMs.toFloat() else 1f,
            colors = SliderDefaults.colors(
                thumbColor = HkRed,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.White.copy(alpha = .35f),
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
fun PlayerLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = HkRed, strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
    }
}

@Composable
fun PlayerErrorOverlay(message: String, onRetry: () -> Unit, onDiagnostic: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = .68f)), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = HkSurfaceRaised),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, null, tint = HkRed, modifier = Modifier.size(34.dp))
                Text("Não foi possível reproduzir", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text(message, color = SecondaryText, modifier = Modifier.padding(top = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDiagnostic) { Text("Diagnóstico") }
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = HkRed)) { Text("Tentar novamente") }
                }
            }
        }
    }
}
