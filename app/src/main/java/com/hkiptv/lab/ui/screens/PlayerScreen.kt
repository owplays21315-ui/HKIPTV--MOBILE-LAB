package com.hkiptv.lab.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.data.model.SourceFormat

@SuppressLint("UnsafeOptInUsageError")
@Composable fun PlayerScreen(source: MediaSource?, back: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember(source) {
        source?.let {
            val dataSource = DefaultHttpDataSource.Factory().setUserAgent("HK-IPTV-Mobile-Lab/0.1.0").setDefaultRequestProperties(it.headers)
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
                val mime = if (it.format == SourceFormat.HLS) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
                setMediaItem(MediaItem.Builder().setUri(it.url).setMimeType(mime).build())
                addListener(object : Player.Listener {
                    override fun onPlayerError(playbackError: PlaybackException) {
                        error = "Falha ao reproduzir (${playbackError.errorCodeName})"
                        Log.e("HKPlayer", "playback error code=${playbackError.errorCodeName}; format=${it.format}")
                    }
                    override fun onPlaybackStateChanged(state: Int) { Log.d("HKPlayer", "state=$state; format=${it.format}") }
                })
                prepare(); playWhenReady = true
            }
        }
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }; Text(source?.name ?: "Player", style = MaterialTheme.typography.titleLarge) }
        if (player == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhuma fonte selecionada") }
        else AndroidView(factory = { PlayerView(it).apply { this.player = player; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } }, modifier = Modifier.fillMaxSize())
    }
    error?.let { AlertDialog(onDismissRequest = { error = null }, confirmButton = { TextButton({ error = null }) { Text("OK") } }, title = { Text("Erro de reprodução") }, text = { Text(it) }) }
}
