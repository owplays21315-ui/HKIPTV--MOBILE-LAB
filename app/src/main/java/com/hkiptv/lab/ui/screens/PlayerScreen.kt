package com.hkiptv.lab.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hkiptv.lab.data.model.MediaItem as CatalogItem
import com.hkiptv.lab.data.model.MediaSource
import com.hkiptv.lab.player.*
import kotlinx.coroutines.delay

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PlayerScreen(source: MediaSource?, content: CatalogItem?, experimentalHttpPlayback: Boolean, back: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val observer = remember(source, experimentalHttpPlayback) { PlaybackNetworkObserver() }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var diagnostic by remember(source) { mutableStateOf<PlaybackDiagnostic?>(null) }
    var showDiagnostic by remember(source) { mutableStateOf(false) }
    var stage by remember(source) { mutableStateOf("preparação") }
    var visualState by remember(source) { mutableStateOf(PlayerVisualState.PREPARING) }
    var controlsVisible by remember(source) { mutableStateOf(true) }
    var interactionVersion by remember(source) { mutableIntStateOf(0) }
    var isSeeking by remember(source) { mutableStateOf(false) }
    var seekPositionMs by remember(source) { mutableLongStateOf(0L) }
    var positionMs by remember(source) { mutableLongStateOf(0L) }
    var durationMs by remember(source) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember(source) { mutableLongStateOf(0L) }
    var lifecycleActive by remember { mutableStateOf(true) }

    val player = remember(source, experimentalHttpPlayback) {
        source?.let { selected ->
            val httpClient = PlaybackHttpClient.create(selected, observer, PlaybackRedirectPolicy(experimentalHttpEnabled = experimentalHttpPlayback))
            val dataSource = OkHttpDataSource.Factory(httpClient)
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSource)).build().apply {
                setMediaItem(MediaItem.Builder().setUri(selected.url).apply {
                    PlaybackMimeType.forFormat(selected.format)?.let(::setMimeType)
                }.build())
                addListener(object : Player.Listener {
                    override fun onPlayerError(playbackError: PlaybackException) {
                        val status = playbackError.findInvalidResponse()?.responseCode
                        val currentStage = if (currentPosition <= 0) "abertura" else "reprodução"
                        val details = PlaybackDiagnostics.snapshot(selected, observer.snapshot(), currentStage, status, failed = true, playbackErrorCode = playbackError.errorCode)
                        diagnostic = details
                        error = PlaybackDiagnostics.userMessage(details)
                        visualState = PlayerVisualState.ERROR
                        controlsVisible = true
                        details.hops.forEach { hop ->
                            Log.e("HKPlayerHop", "hop=" + hop.number + "; status=" + hop.status + "; protocol=" + hop.protocol + "; hostChanged=" + hop.hostChanged + "; location=" + hop.locationPresent + "; stop=" + (hop.stopReason?.name ?: "none"))
                        }
                        Log.e("HKPlayer", "provider=" + details.provider + "; stage=" + details.stage + "; format=" + details.format + "; initialStatus=" + (details.initialStatus ?: "n/a") + "; finalStatus=" + (details.finalStatus ?: "n/a") + "; redirects=" + details.redirectCount + "; hostChanged=" + details.crossHostRedirect + "; completed=" + details.redirectCompleted + "; category=" + details.category)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        visualState = PlayerUiLogic.visualState(state, isPlaying, error != null)
                        stage = when (state) {
                            Player.STATE_BUFFERING -> if (currentPosition <= 0) "abertura" else "buffering"
                            Player.STATE_READY -> "reprodução"
                            Player.STATE_ENDED -> "concluído"
                            else -> stage
                        }
                        if (visualState != PlayerVisualState.PLAYING) controlsVisible = true
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        if (playbackState == Player.STATE_READY) visualState = if (playing) PlayerVisualState.PLAYING else PlayerVisualState.PAUSED
                        if (!playing) controlsVisible = true
                    }
                })
                prepare()
                playWhenReady = true
            }
        }
    }

    fun revealControls() {
        controlsVisible = true
        interactionVersion++
    }

    DisposableEffect(player, lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> lifecycleActive = true
                Lifecycle.Event.ON_STOP -> {
                    lifecycleActive = false
                    if (PlayerLifecyclePolicy.onStop(player?.isPlaying == true) == PlayerLifecycleAction.PAUSE) player?.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player?.release()
        }
    }

    PlayerFullscreenEffect(activity, view, originalOrientation)
    BackHandler { if (showDiagnostic) showDiagnostic = false else back() }

    LaunchedEffect(controlsVisible, visualState, isSeeking, error, interactionVersion) {
        if (controlsVisible && visualState == PlayerVisualState.PLAYING && !isSeeking && error == null) {
            delay(PlayerUiLogic.CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(player, visualState, lifecycleActive, isSeeking) {
        if (player == null) return@LaunchedEffect
        fun updateProgress() {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        }
        updateProgress()
        while (lifecycleActive && visualState == PlayerVisualState.PLAYING && !isSeeking) {
            delay(500)
            updateProgress()
        }
    }

    if (showDiagnostic && source != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlaybackDiagnosticScreen(diagnostic ?: PlaybackDiagnostics.snapshot(source, observer.snapshot(), stage), onBack = { showDiagnostic = false })
        }
        return
    }

    val title = content?.title ?: source?.name ?: "Reprodução"
    val subtitle = content?.year?.toString()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier.matchParentSize().pointerInput(visualState, isSeeking, error, controlsVisible) {
                detectTapGestures(onTap = {
                    if (controlsVisible && visualState == PlayerVisualState.PLAYING && !isSeeking && error == null) controlsVisible = false
                    else revealControls()
                })
            }
        )
        if (player == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhuma fonte selecionada", color = Color.White)
                Button(onClick = back, modifier = Modifier.padding(top = 12.dp)) { Text("Voltar") }
            }
        } else {
            PlayerControlsOverlay(
                visible = controlsVisible, state = visualState, title = title, subtitle = subtitle,
                positionMs = positionMs, durationMs = durationMs, bufferedPositionMs = bufferedPositionMs,
                isSeeking = isSeeking, seekPositionMs = seekPositionMs, onBack = back,
                onPlayPause = {
                    revealControls()
                    when (PlayerUiLogic.playPauseAction(player.isPlaying)) {
                        PlayerControlAction.PLAY -> player.play()
                        PlayerControlAction.PAUSE -> player.pause()
                    }
                },
                onReplay = { player.seekTo(0L); player.play(); error = null; revealControls() },
                onRewind = { player.seekTo(PlayerUiLogic.seekBack(player.currentPosition)); revealControls() },
                onForward = { player.seekTo(PlayerUiLogic.seekForward(player.currentPosition, player.duration)); revealControls() },
                onSeekStart = {
                    if (!isSeeking) { isSeeking = true; seekPositionMs = player.currentPosition.coerceAtLeast(0L) }
                    revealControls()
                },
                onSeekChange = { seekPositionMs = PlayerUiLogic.validSeek(it, player.duration); revealControls() },
                onSeekFinished = {
                    player.seekTo(PlayerUiLogic.validSeek(seekPositionMs, player.duration))
                    positionMs = seekPositionMs
                    isSeeking = false
                    revealControls()
                }
            )
            if (visualState == PlayerVisualState.PREPARING || visualState == PlayerVisualState.BUFFERING) PlayerLoadingIndicator(Modifier.fillMaxSize())
            error?.let { message ->
                PlayerErrorOverlay(
                    message = message,
                    onRetry = {
                        error = null
                        diagnostic = null
                        visualState = PlayerVisualState.PREPARING
                        player.prepare()
                        player.playWhenReady = true
                        revealControls()
                    },
                    onDiagnostic = {
                        diagnostic = PlaybackDiagnostics.snapshot(requireNotNull(source), observer.snapshot(), stage, failed = true)
                        showDiagnostic = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlayerFullscreenEffect(activity: Activity?, view: android.view.View, originalOrientation: Int) {
    DisposableEffect(activity, view, originalOrientation) {
        if (activity == null) return@DisposableEffect onDispose { }
        val restoration = FullscreenRestoration(originalOrientation)
        val window = activity.window
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode else null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            if (activity.isChangingConfigurations) return@onDispose
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutoutMode }
            }
            activity.requestedOrientation = restoration.orientationToRestore()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Throwable.findInvalidResponse(): HttpDataSource.InvalidResponseCodeException? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current
        current = current.cause
    }
    return null
}
