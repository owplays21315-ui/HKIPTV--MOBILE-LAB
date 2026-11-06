package com.hkiptv.lab.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.data.model.SourceFormat
import com.hkiptv.lab.player.*

@SuppressLint("UnsafeOptInUsageError")
@Composable fun PlayerScreen(source: MediaSource?, back: () -> Unit) {
    val context = LocalContext.current
    val observer = remember(source) { PlaybackNetworkObserver() }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var diagnostic by remember(source) { mutableStateOf<PlaybackDiagnostic?>(null) }
    var showDiagnostic by remember(source) { mutableStateOf(false) }
    var stage by remember(source) { mutableStateOf("preparação") }
    val player = remember(source) {
        source?.let { selected ->
            val httpClient = PlaybackHttpClient.create(selected, observer)
            val dataSource = OkHttpDataSource.Factory(httpClient)
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
                val item = MediaItem.Builder().setUri(selected.url).apply {
                    PlaybackMimeType.forFormat(selected.format)?.let(::setMimeType)
                }.build()
                setMediaItem(item)
                addListener(object : Player.Listener {
                    override fun onPlayerError(playbackError: PlaybackException) {
                        val status = playbackError.findInvalidResponse()?.responseCode
                        val currentStage = if (currentPosition <= 0) "abertura" else "reprodução"
                        val details = PlaybackDiagnostics.snapshot(selected, observer.snapshot(), currentStage, status, failed = true, playbackErrorCode = playbackError.errorCode)
                        diagnostic = details; error = PlaybackDiagnostics.userMessage(details)
                        Log.e("HKPlayer", "provider=${details.provider}; stage=${details.stage}; format=${details.format}; initialStatus=${details.initialStatus ?: "n/a"}; finalStatus=${details.finalStatus ?: "n/a"}; redirects=${details.redirectCount}; hostChanged=${details.crossHostRedirect}; completed=${details.redirectCompleted}; category=${details.category}")
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        stage = when (state) { Player.STATE_BUFFERING -> if (currentPosition <= 0) "abertura" else "buffering"; Player.STATE_READY -> "reprodução"; Player.STATE_ENDED -> "concluído"; else -> stage }
                    }
                })
                prepare(); playWhenReady = true
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    if (showDiagnostic && source != null) {
        PlaybackDiagnosticScreen(diagnostic ?: PlaybackDiagnostics.snapshot(source, observer.snapshot(), stage), onBack = { showDiagnostic = false })
    } else Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text(source?.name ?: "Player", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (source != null) IconButton({ diagnostic = PlaybackDiagnostics.snapshot(source, observer.snapshot(), stage); showDiagnostic = true }) { Icon(Icons.Default.Info, "Diagnóstico técnico") }
        }
        if (player == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma fonte selecionada") }
        else AndroidView(factory = { PlayerView(it).apply { this.player = player; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } }, modifier = Modifier.fillMaxSize())
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } }, dismissButton = { TextButton({ error = null; showDiagnostic = true }) { Text("Ver diagnóstico") } }, title = { Text("Erro de reprodução") }, text = { Text(message) }) }
}

private fun Throwable.findInvalidResponse(): HttpDataSource.InvalidResponseCodeException? {
    var current: Throwable? = this
    while (current != null) { if (current is HttpDataSource.InvalidResponseCodeException) return current; current = current.cause }
    return null
}
