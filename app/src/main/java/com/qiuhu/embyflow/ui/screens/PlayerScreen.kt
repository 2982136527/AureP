package com.qiuhu.embyflow.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.StatsDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qiuhu.embyflow.data.emby.EmbyPlaybackInfoField
import com.qiuhu.embyflow.data.emby.EmbyPlaybackSource
import com.qiuhu.embyflow.data.emby.EmbyPlaybackStreamOption
import com.qiuhu.embyflow.data.emby.EmbySubtitleTrack
import com.qiuhu.embyflow.data.settings.AppSettings
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_COMPATIBILITY
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_STANDARD
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_SYSTEM
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

private val PlayerAccentColor = Color(0xFFF0E7DA)
private val PlayerPanelColor = Color(0xCC101010)

@Composable
fun PlayerScreen(
    mediaId: String,
    title: String,
    source: EmbyPlaybackSource,
    initialResumePositionMs: Long,
    settings: AppSettings,
    onClose: (Long, Long) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val landscapeNavBarRightPadding = if (isLandscapeLayout) {
        with(density) { WindowInsets.navigationBars.getRight(this, layoutDirection).toDp() }
    } else {
        0.dp
    }
    val runtimeProfile = remember(settings.playerMode) {
        settings.toPlayerRuntimeProfile()
    }
    val autoSubtitleTracks = remember(source, settings.subtitleMode) {
        source.subtitleTracks.applySubtitleStrategy(settings.subtitleMode)
    }
    val streamOptions = remember(source.streamOptions, source.streamUrl) {
        if (source.streamOptions.isNotEmpty()) {
            source.streamOptions
        } else {
            listOf(
                EmbyPlaybackStreamOption(
                    id = "default",
                    label = "当前链路",
                    description = "使用当前播放地址。",
                    streamUrl = source.streamUrl,
                    lockedByServer = true,
                ),
            )
        }
    }
    var selectedSubtitleIndex by rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    var selectedStreamOptionId by rememberSaveable(source.selectedStreamOptionId, source.streamUrl) {
        mutableStateOf(source.selectedStreamOptionId ?: streamOptions.firstOrNull()?.id)
    }
    var playbackSpeed by rememberSaveable {
        mutableFloatStateOf(1f)
    }
    var isLandscapeFullscreen by rememberSaveable {
        mutableStateOf(false)
    }
    var scaleMode by rememberSaveable {
        mutableStateOf(PlayerScaleMode.Fit)
    }
    var controlsVisible by rememberSaveable {
        mutableStateOf(true)
    }
    var controlsLocked by rememberSaveable {
        mutableStateOf(false)
    }
    var showTrackSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var showRuntimeSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var showSubtitleSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var resumePositionMs by rememberSaveable(mediaId) {
        mutableLongStateOf(initialResumePositionMs.coerceAtLeast(0L))
    }
    var resumePlayWhenReady by rememberSaveable(mediaId) {
        mutableStateOf(true)
    }
    var currentPositionMs by remember {
        mutableLongStateOf(0L)
    }
    var bufferedPositionMs by remember {
        mutableLongStateOf(0L)
    }
    var durationMs by remember {
        mutableLongStateOf(0L)
    }
    var bitrateEstimateBitsPerSecond by remember {
        mutableLongStateOf(0L)
    }
    var isPlaying by remember {
        mutableStateOf(true)
    }
    var isBuffering by remember {
        mutableStateOf(true)
    }
    var sliderPositionMs by remember {
        mutableFloatStateOf(0f)
    }
    var isScrubbing by remember {
        mutableStateOf(false)
    }
    var playerSurfaceWidthPx by remember {
        mutableFloatStateOf(1f)
    }
    var playerSurfaceHeightPx by remember {
        mutableFloatStateOf(1f)
    }
    var gestureOverlayState by remember {
        mutableStateOf<PlayerGestureOverlayState?>(null)
    }
    var gestureActive by remember {
        mutableStateOf(false)
    }
    val activity = remember(context) {
        context.findActivity()
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val viewConfiguration = LocalViewConfiguration.current
    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }
    val activeStreamOption = remember(streamOptions, selectedStreamOptionId) {
        streamOptions.firstOrNull { it.id == selectedStreamOptionId } ?: streamOptions.first()
    }

    val activeSubtitleTracks = remember(source, autoSubtitleTracks, selectedSubtitleIndex) {
        when (selectedSubtitleIndex) {
            null -> autoSubtitleTracks
            SUBTITLE_OFF -> emptyList()
            else -> source.subtitleTracks
                .filter { it.index == selectedSubtitleIndex }
                .map { it.copy(isDefault = true) }
        }
    }
    val subtitleChoiceLabel = remember(source.subtitleTracks, autoSubtitleTracks, selectedSubtitleIndex) {
        formatSubtitleChoiceLabel(
            selectedSubtitleIndex = selectedSubtitleIndex,
            sourceTracks = source.subtitleTracks,
            autoTracks = autoSubtitleTracks,
        )
    }
    val streamChoiceLabel = remember(activeStreamOption) {
        activeStreamOption.label
    }
    val playbackTrafficTracker = remember(activeStreamOption.id, activeStreamOption.streamUrl) {
        PlaybackTrafficTracker()
    }
    var resolvedPlaybackUrl by rememberSaveable(activeStreamOption.id, activeStreamOption.streamUrl) {
        mutableStateOf(activeStreamOption.streamUrl)
    }
    val mediaItem = remember(activeStreamOption.streamUrl, activeSubtitleTracks) {
        PlayerMediaItem.Builder()
            .setUri(activeStreamOption.streamUrl)
            .setSubtitleConfigurations(
                activeSubtitleTracks.map { subtitle ->
                    PlayerMediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                        .setMimeType(subtitle.mimeType)
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .setSelectionFlags(if (subtitle.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                },
            )
            .build()
    }
    val exoPlayer = remember(context, mediaItem, runtimeProfile, activeStreamOption.requestHeaders) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(runtimeProfile.decoderFallback)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                runtimeProfile.minBufferMs,
                runtimeProfile.maxBufferMs,
                runtimeProfile.bufferForPlaybackMs,
                runtimeProfile.bufferForPlaybackAfterRebufferMs,
            )
            .build()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(activeStreamOption.requestHeaders)
        val trackingDataSourceFactory = TrackingDataSourceFactory(
            upstream = httpDataSourceFactory,
            tracker = playbackTrafficTracker,
            onOpenedUri = { openedUri ->
                val candidate = openedUri.toString().trim()
                if (candidate.isBlank() || candidate.isSubtitleLikeUri()) return@TrackingDataSourceFactory
                mainHandler.post {
                    if (
                        resolvedPlaybackUrl == activeStreamOption.streamUrl ||
                        resolvedPlaybackUrl == candidate
                    ) {
                        resolvedPlaybackUrl = candidate
                    }
                }
            },
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(trackingDataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(runtimeProfile.seekBackMs)
            .setSeekForwardIncrementMs(runtimeProfile.seekForwardMs)
            .build().apply {
                setMediaItem(mediaItem)
                prepare()
                if (resumePositionMs > 0L) {
                    seekTo(resumePositionMs)
                }
                playbackParameters = PlaybackParameters(playbackSpeed)
                playWhenReady = resumePlayWhenReady
            }
    }

    fun revealControls() {
        controlsVisible = true
    }

    fun capturePlaybackState() {
        resumePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        resumePlayWhenReady = exoPlayer.playWhenReady
    }

    fun maybeUpdateResolvedPlaybackUrl(
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        if (!shouldTrackResolvedPlaybackUrl(mediaLoadData)) return
        val candidate = loadEventInfo.uri.toString().trim()
        if (candidate.isBlank()) return
        if (
            resolvedPlaybackUrl == activeStreamOption.streamUrl ||
            resolvedPlaybackUrl == candidate
        ) {
            resolvedPlaybackUrl = candidate
        }
    }

    fun toggleSheets(target: PlayerSheet?) {
        showTrackSheet = target == PlayerSheet.Track
        showRuntimeSheet = target == PlayerSheet.Runtime
        if (target != PlayerSheet.Track) {
            showSubtitleSheet = false
        }
        revealControls()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                durationMs = duration.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                if (!isScrubbing) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
            }
        }
        val analyticsListener = object : AnalyticsListener {
            override fun onLoadStarted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
            ) {
                maybeUpdateResolvedPlaybackUrl(loadEventInfo, mediaLoadData)
            }

            override fun onLoadCompleted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData,
            ) {
                maybeUpdateResolvedPlaybackUrl(loadEventInfo, mediaLoadData)
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.addAnalyticsListener(analyticsListener)
        onDispose {
            capturePlaybackState()
            exoPlayer.removeListener(listener)
            exoPlayer.removeAnalyticsListener(analyticsListener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(activeStreamOption.id) {
        bitrateEstimateBitsPerSecond = 0L
    }

    LaunchedEffect(exoPlayer, playbackTrafficTracker) {
        var previousBytesRead = playbackTrafficTracker.totalBytesRead()
        var previousSampleTimeMs = SystemClock.elapsedRealtime()
        var smoothedBitrateBitsPerSecond = 0L
        var lastNonZeroSampleTimeMs = previousSampleTimeMs
        while (true) {
            val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            durationMs = duration.coerceAtLeast(0L)
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            val nowMs = SystemClock.elapsedRealtime()
            val totalBytesRead = playbackTrafficTracker.totalBytesRead()
            val bytesDelta = (totalBytesRead - previousBytesRead).coerceAtLeast(0L)
            val timeDeltaMs = (nowMs - previousSampleTimeMs).coerceAtLeast(1L)
            if (bytesDelta > 0L) {
                val instantBitrate = bytesDelta * 8_000L / timeDeltaMs
                smoothedBitrateBitsPerSecond = when {
                    smoothedBitrateBitsPerSecond <= 0L -> instantBitrate
                    else -> ((smoothedBitrateBitsPerSecond * 0.58) + (instantBitrate * 0.42)).toLong()
                }
                bitrateEstimateBitsPerSecond = smoothedBitrateBitsPerSecond
                lastNonZeroSampleTimeMs = nowMs
            } else if (nowMs - lastNonZeroSampleTimeMs > 1_500L) {
                smoothedBitrateBitsPerSecond = 0L
                bitrateEstimateBitsPerSecond = 0L
            }
            previousBytesRead = totalBytesRead
            previousSampleTimeMs = nowMs
            delay(250)
        }
    }

    LaunchedEffect(exoPlayer, playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    DisposableEffect(activity, isLandscapeFullscreen) {
        activity?.window?.let { window ->
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            activity.requestedOrientation = if (isLandscapeFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            if (isLandscapeFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            activity?.window?.let { window ->
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(controlsVisible, controlsLocked, isPlaying, showTrackSheet, showRuntimeSheet, showSubtitleSheet) {
        if (
            controlsVisible &&
            !controlsLocked &&
            isPlaying &&
            !showTrackSheet &&
            !showRuntimeSheet &&
            !showSubtitleSheet
        ) {
            delay(3200)
            controlsVisible = false
        }
    }

    LaunchedEffect(gestureOverlayState, gestureActive) {
        if (!gestureActive && gestureOverlayState != null) {
            delay(650)
            if (!gestureActive) {
                gestureOverlayState = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    setResizeMode(scaleMode.resizeMode)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = exoPlayer
                }
            },
            update = { playerView ->
                playerView.setResizeMode(scaleMode.resizeMode)
                playerView.setBackgroundColor(android.graphics.Color.BLACK)
                playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
                playerView.player = exoPlayer
            },
        )

        AnimatedVisibility(
            visible = controlsVisible && !controlsLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            PlayerStatusBadge(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(
                        start = 18.dp,
                        top = 14.dp,
                        end = 18.dp + landscapeNavBarRightPadding,
                        bottom = 14.dp,
                    ),
                bitrateLabel = formatBitrate(bitrateEstimateBitsPerSecond),
                isBuffering = isBuffering,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    playerSurfaceWidthPx = size.width.toFloat().coerceAtLeast(1f)
                    playerSurfaceHeightPx = size.height.toFloat().coerceAtLeast(1f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!controlsLocked) {
                        controlsVisible = !controlsVisible
                        if (!controlsVisible) {
                            showTrackSheet = false
                            showRuntimeSheet = false
                            showSubtitleSheet = false
                        }
                    }
                }
                .pointerInput(
                    controlsLocked,
                    playerSurfaceWidthPx,
                    playerSurfaceHeightPx,
                    durationMs,
                    viewConfiguration.touchSlop,
                ) {
                    if (controlsLocked) return@pointerInput
                    var gestureMode: PlayerGestureMode? = null
                    var horizontalDrag = 0f
                    var verticalDrag = 0f
                    var isLeftPanel = false
                    var startBrightness = 0.5f
                    var maxVolume = 1
                    var startVolume = 0
                    var seekBasePosition = 0L

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            gestureMode = null
                            horizontalDrag = 0f
                            verticalDrag = 0f
                            isLeftPanel = startOffset.x < playerSurfaceWidthPx / 2f
                            startBrightness = activity?.resolvePlayerBrightness() ?: 0.5f
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            seekBasePosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                            gestureActive = true
                            gestureOverlayState = null
                        },
                        onDrag = { _, dragAmount ->
                            horizontalDrag += dragAmount.x
                            verticalDrag += dragAmount.y

                            if (gestureMode == null && max(abs(horizontalDrag), abs(verticalDrag)) >= viewConfiguration.touchSlop) {
                                gestureMode = if (abs(horizontalDrag) >= abs(verticalDrag)) {
                                    PlayerGestureMode.Seek
                                } else if (isLeftPanel) {
                                    PlayerGestureMode.Brightness
                                } else {
                                    PlayerGestureMode.Volume
                                }
                            }

                            when (gestureMode) {
                                PlayerGestureMode.Seek -> {
                                    val seekRangeMs = calculateSeekGestureRange(durationMs)
                                    val targetPosition = (
                                        seekBasePosition + (horizontalDrag / playerSurfaceWidthPx * seekRangeMs)
                                    ).roundToLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
                                    sliderPositionMs = targetPosition.toFloat()
                                    isScrubbing = true
                                    gestureOverlayState = buildSeekGestureOverlay(
                                        targetPositionMs = targetPosition,
                                        basePositionMs = seekBasePosition,
                                        durationMs = durationMs,
                                    )
                                }

                                PlayerGestureMode.Brightness -> {
                                    val brightness = (startBrightness - verticalDrag / playerSurfaceHeightPx)
                                        .coerceIn(0.05f, 1f)
                                    activity?.applyPlayerBrightness(brightness)
                                    gestureOverlayState = PlayerGestureOverlayState(
                                        icon = Icons.Rounded.Brightness6,
                                        title = "亮度 ${formatPercent(brightness)}",
                                        detail = "左侧上下滑动",
                                        progress = brightness,
                                    )
                                }

                                PlayerGestureMode.Volume -> {
                                    val targetVolume = (
                                        startVolume - verticalDrag / playerSurfaceHeightPx * maxVolume
                                    ).roundToInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        targetVolume,
                                        0,
                                    )
                                    val volumeFraction = targetVolume / maxVolume.toFloat()
                                    gestureOverlayState = PlayerGestureOverlayState(
                                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                        title = "音量 ${formatPercent(volumeFraction)}",
                                        detail = "右侧上下滑动",
                                        progress = volumeFraction,
                                    )
                                }

                                null -> Unit
                            }
                        },
                        onDragEnd = {
                            if (gestureMode == PlayerGestureMode.Seek) {
                                isScrubbing = false
                                exoPlayer.seekTo(sliderPositionMs.toLong())
                            }
                            gestureActive = false
                        },
                        onDragCancel = {
                            if (gestureMode == PlayerGestureMode.Seek) {
                                isScrubbing = false
                                sliderPositionMs = currentPositionMs.toFloat()
                            }
                            gestureActive = false
                        },
                    )
                },
        )

        AnimatedVisibility(
            visible = gestureOverlayState != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            gestureOverlayState?.let { state ->
                PlayerGestureOverlay(
                    state = state,
                )
            }
        }

        AnimatedVisibility(
            visible = isBuffering && gestureOverlayState == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            PlayerLoadingOverlay(
                label = "正在缓冲",
                detail = formatBitrate(bitrateEstimateBitsPerSecond)?.let { "实时码率 $it" },
            )
        }

        if (controlsLocked) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x66202020))
                    .padding(vertical = 10.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerOverlayIconButton(
                    icon = Icons.Rounded.LockOpen,
                    contentDescription = "解锁控制",
                    onClick = {
                        controlsLocked = false
                        revealControls()
                    },
                )
            }
        } else {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PlayerTopActions(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        onClose = {
                            onClose(
                                exoPlayer.currentPosition.coerceAtLeast(0L),
                                (exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: durationMs).coerceAtLeast(0L),
                            )
                        },
                        onToggleLock = {
                            controlsLocked = true
                            controlsVisible = false
                            showTrackSheet = false
                            showRuntimeSheet = false
                            showSubtitleSheet = false
                        },
                    )

                    PlayerSidePills(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = landscapeNavBarRightPadding),
                        subtitleChoiceLabel = subtitleChoiceLabel,
                        playbackSpeed = playbackSpeed,
                        isLandscapeFullscreen = isLandscapeFullscreen,
                        onToggleSubtitleSheet = {
                            showTrackSheet = false
                            showRuntimeSheet = false
                            showSubtitleSheet = !showSubtitleSheet
                        },
                        onToggleFullscreen = {
                            isLandscapeFullscreen = !isLandscapeFullscreen
                            if (isLandscapeFullscreen) {
                                scaleMode = PlayerScaleMode.Fit
                            }
                            revealControls()
                        },
                        onDecreaseSpeed = {
                            playbackSpeed = (playbackSpeed - 0.25f).coerceAtLeast(0.5f)
                            revealControls()
                        },
                        onIncreaseSpeed = {
                            playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(2.0f)
                            revealControls()
                        },
                    )

                    AnimatedVisibility(
                        visible = showTrackSheet || showRuntimeSheet || showSubtitleSheet,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .playerBottomOverlayInsets(isLandscapeFullscreen)
                            .padding(
                                start = 24.dp,
                                top = 24.dp,
                                end = 24.dp + landscapeNavBarRightPadding,
                                bottom = if (isLandscapeLayout) 96.dp else 188.dp,
                            ),
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
                    ) {
                        when {
                            showSubtitleSheet -> PlayerSubtitleSheet(
                                subtitleOptions = buildSubtitleOptions(
                                    sourceTracks = source.subtitleTracks,
                                    autoTracks = autoSubtitleTracks,
                                    selectedSubtitleIndex = selectedSubtitleIndex,
                                ),
                                onSelectSubtitle = { targetIndex ->
                                    capturePlaybackState()
                                    selectedSubtitleIndex = targetIndex
                                    showSubtitleSheet = false
                                    revealControls()
                                },
                            )

                            showTrackSheet -> PlayerTrackSheet(
                                infoLine = source.infoLine.ifBlank { "当前媒体流" },
                                title = title,
                                subtitleChoiceLabel = subtitleChoiceLabel,
                                runtimeLabel = runtimeProfile.label,
                                streamChoiceLabel = streamChoiceLabel,
                                bitrateLabel = formatBitrate(bitrateEstimateBitsPerSecond),
                                infoFields = source.infoFields,
                            )

                            showRuntimeSheet -> PlayerRuntimeSheet(
                                runtimeLabel = runtimeProfile.label,
                                streamOptions = streamOptions,
                                selectedStreamOptionId = activeStreamOption.id,
                                onSelectStreamOption = { optionId ->
                                    if (optionId == activeStreamOption.id) return@PlayerRuntimeSheet
                                    capturePlaybackState()
                                    selectedStreamOptionId = optionId
                                    revealControls()
                                },
                                bitrateLabel = formatBitrate(bitrateEstimateBitsPerSecond),
                                entryPlaybackUrl = activeStreamOption.streamUrl,
                                currentPlaybackUrl = resolvedPlaybackUrl,
                            )
                        }
                    }

                    PlayerBottomControls(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .playerBottomOverlayInsets(isLandscapeFullscreen || isLandscapeLayout)
                            .padding(
                                start = 24.dp,
                                top = if (isLandscapeFullscreen || isLandscapeLayout) 4.dp else 22.dp,
                                end = 24.dp + landscapeNavBarRightPadding,
                                bottom = if (isLandscapeFullscreen || isLandscapeLayout) 0.dp else 22.dp,
                            ),
                        currentPositionMs = if (isScrubbing) sliderPositionMs.toLong() else currentPositionMs,
                        durationMs = durationMs,
                        bufferedPositionMs = bufferedPositionMs,
                        isPlaying = isPlaying,
                        isLandscapeCompact = isLandscapeFullscreen || isLandscapeLayout,
                        onScrubStart = {
                            isScrubbing = true
                            sliderPositionMs = currentPositionMs.toFloat()
                            revealControls()
                        },
                        onScrub = { value ->
                            sliderPositionMs = value
                        },
                        onScrubStop = {
                            isScrubbing = false
                            exoPlayer.seekTo(sliderPositionMs.toLong())
                            revealControls()
                        },
                        onOpenInfoSheet = {
                            if (showTrackSheet) {
                                toggleSheets(null)
                            } else {
                                toggleSheets(PlayerSheet.Track)
                            }
                        },
                        onSeekBack = {
                            exoPlayer.seekBack()
                            revealControls()
                        },
                        onPlayPause = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                            revealControls()
                        },
                        onSeekForward = {
                            exoPlayer.seekForward()
                            revealControls()
                        },
                        onOpenRuntime = {
                            if (showRuntimeSheet) {
                                toggleSheets(null)
                            } else {
                                toggleSheets(PlayerSheet.Runtime)
                            }
                        },
                    )
                }
            }
        }
    }
}

private enum class PlayerSheet {
    Track,
    Runtime,
}

private enum class PlayerScaleMode(
    val label: String,
    val resizeMode: Int,
) {
    Fit(
        label = "完整显示",
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    ),
    Fill(
        label = "铺满画面",
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    ),
    ;

    fun next(): PlayerScaleMode = when (this) {
        Fit -> Fill
        Fill -> Fit
    }
}

private data class PlayerRuntimeProfile(
    val label: String,
    val decoderFallback: Boolean,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val seekBackMs: Long,
    val seekForwardMs: Long,
)

private data class SubtitleOption(
    val title: String,
    val subtitle: String?,
    val targetIndex: Int?,
    val selected: Boolean,
)

private enum class PlayerGestureMode {
    Seek,
    Brightness,
    Volume,
}

private data class PlayerGestureOverlayState(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val progress: Float? = null,
)

private const val SUBTITLE_OFF = -1

private fun Modifier.playerBottomOverlayInsets(
    isLandscapeFullscreen: Boolean,
): Modifier = if (isLandscapeFullscreen) this else navigationBarsPadding()

@Composable
private fun PlayerTopActions(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onToggleLock: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(PlayerPanelColor)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerOverlayIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onClose,
        )
        PlayerOverlayIconButton(
            icon = Icons.Rounded.Lock,
            contentDescription = "锁定控制",
            onClick = onToggleLock,
        )
    }
}

@Composable
private fun PlayerSidePills(
    modifier: Modifier = Modifier,
    subtitleChoiceLabel: String,
    playbackSpeed: Float,
    isLandscapeFullscreen: Boolean,
    onToggleSubtitleSheet: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onDecreaseSpeed: () -> Unit,
    onIncreaseSpeed: () -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PlayerPanelColor)
                .padding(vertical = 10.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerOverlayIconButton(
                icon = Icons.Rounded.Subtitles,
                contentDescription = subtitleChoiceLabel,
                onClick = onToggleSubtitleSheet,
            )
            PlayerOverlayIconButton(
                icon = Icons.Rounded.AspectRatio,
                contentDescription = if (isLandscapeFullscreen) "退出横屏全屏" else "进入横屏全屏",
                onClick = onToggleFullscreen,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(PlayerPanelColor)
                .padding(vertical = 10.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerOverlayIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "加速",
                onClick = onIncreaseSpeed,
            )
            Text(
                text = formatSpeed(playbackSpeed),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            PlayerOverlayIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "减速",
                onClick = onDecreaseSpeed,
            )
        }
    }
}

@Composable
private fun PlayerBottomControls(
    modifier: Modifier = Modifier,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isLandscapeCompact: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubStop: () -> Unit,
    onOpenInfoSheet: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onOpenRuntime: () -> Unit,
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progressValue = currentPositionMs.coerceIn(0L, safeDuration).toFloat()
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val compact = isLandscapeCompact

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE0101010))
            .padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 4.dp else 14.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
    ) {
        PlayerProgressBar(
            progressValue = progressValue,
            bufferedFraction = bufferedFraction,
            durationMs = safeDuration,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onScrubStop = onScrubStop,
            modifier = Modifier.fillMaxWidth(),
            trackHeight = if (compact) 10.dp else 18.dp,
        )

        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlaybackTime(currentPositionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(
                    text = "-${formatPlaybackTime((durationMs - currentPositionMs).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerBottomButton(
                icon = Icons.Rounded.Info,
                contentDescription = "媒体信息",
                containerSize = if (compact) 38.dp else 42.dp,
                iconSize = if (compact) 20.dp else 22.dp,
                onClick = onOpenInfoSheet,
            )
            PlayerBottomButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "后退10秒",
                containerSize = if (compact) 38.dp else 42.dp,
                iconSize = if (compact) 20.dp else 22.dp,
                onClick = onSeekBack,
            )
            PlayerBottomButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                containerSize = if (compact) 48.dp else 56.dp,
                iconSize = if (compact) 24.dp else 28.dp,
                onClick = onPlayPause,
            )
            PlayerBottomButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "前进10秒",
                containerSize = if (compact) 38.dp else 42.dp,
                iconSize = if (compact) 20.dp else 22.dp,
                onClick = onSeekForward,
            )
            PlayerBottomButton(
                icon = Icons.Rounded.PlayCircle,
                contentDescription = "播放方式",
                containerSize = if (compact) 38.dp else 42.dp,
                iconSize = if (compact) 20.dp else 22.dp,
                onClick = onOpenRuntime,
            )
        }
    }
}

@Composable
private fun PlayerProgressBar(
    progressValue: Float,
    bufferedFraction: Float,
    durationMs: Long,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubStop: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 18.dp,
) {
    val progressFraction = (progressValue / durationMs.toFloat()).coerceIn(0f, 1f)
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val handleSize = 8.dp

    fun valueFromPosition(positionX: Float): Float {
        val fraction = (positionX / trackWidthPx).coerceIn(0f, 1f)
        return fraction * durationMs.toFloat()
    }

    Box(
        modifier = modifier
            .height(trackHeight)
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(durationMs, trackWidthPx) {
                detectTapGestures { offset ->
                    if (durationMs > 0L) {
                        onScrubStart()
                        onScrub(valueFromPosition(offset.x))
                        onScrubStop()
                    }
                }
            }
            .pointerInput(durationMs, trackWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0L) {
                            onScrubStart()
                            onScrub(valueFromPosition(offset.x))
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (durationMs > 0L) {
                            onScrub(valueFromPosition(change.position.x))
                        }
                    },
                    onDragEnd = {
                        if (durationMs > 0L) {
                            onScrubStop()
                        }
                    },
                    onDragCancel = {
                        if (durationMs > 0L) {
                            onScrubStop()
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.24f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.92f)),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = with(density) {
                        ((trackWidthPx * progressFraction).toDp() - handleSize / 2)
                            .coerceAtLeast(0.dp)
                    },
                )
                .size(handleSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun PlayerTrackSheet(
    infoLine: String,
    title: String,
    subtitleChoiceLabel: String,
    runtimeLabel: String,
    streamChoiceLabel: String,
    bitrateLabel: String?,
    infoFields: List<EmbyPlaybackInfoField>,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xEE111111))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "播放信息",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (infoLine.isNotBlank()) {
            Text(
                text = infoLine,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f),
            )
        }
        PlayerInfoFieldRow(label = "当前字幕", value = subtitleChoiceLabel)
        PlayerInfoFieldRow(label = "播放策略", value = runtimeLabel)
        PlayerInfoFieldRow(label = "串流方式", value = streamChoiceLabel)
        bitrateLabel?.let { PlayerInfoFieldRow(label = "实时码率", value = it) }
        infoFields.forEach { field ->
            PlayerInfoFieldRow(label = field.label, value = field.value)
        }
    }
}

@Composable
private fun PlayerRuntimeSheet(
    runtimeLabel: String,
    streamOptions: List<EmbyPlaybackStreamOption>,
    selectedStreamOptionId: String,
    onSelectStreamOption: (String) -> Unit,
    bitrateLabel: String?,
    entryPlaybackUrl: String,
    currentPlaybackUrl: String,
) {
    val actualPlaybackUrl = currentPlaybackUrl.ifBlank { entryPlaybackUrl }
    val redirected = actualPlaybackUrl != entryPlaybackUrl
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .heightIn(max = 440.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xEE111111))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "播放方式",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        PlayerInfoFieldRow(label = "播放策略", value = runtimeLabel)
        bitrateLabel?.let { PlayerInfoFieldRow(label = "实时码率", value = it) }
        PlayerInfoFieldRow(
            label = if (redirected) "当前直链" else "当前链接",
            value = actualPlaybackUrl,
            multiline = true,
        )
        if (redirected) {
            PlayerInfoFieldRow(
                label = "请求入口",
                value = entryPlaybackUrl,
                multiline = true,
            )
        }
        Text(
            text = runtimeModeDescription(runtimeLabel),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f),
        )
        Text(
            text = "串流方式",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.88f),
            fontWeight = FontWeight.SemiBold,
        )
        streamOptions.forEach { option ->
            StreamOptionRow(
                option = option,
                selected = option.id == selectedStreamOptionId,
                onClick = { onSelectStreamOption(option.id) },
            )
        }
    }
}

@Composable
private fun PlayerSubtitleSheet(
    subtitleOptions: List<SubtitleOption>,
    onSelectSubtitle: (Int?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xEE111111))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        subtitleOptions.forEach { option ->
            SubtitleOptionRow(
                option = option,
                onClick = { onSelectSubtitle(option.targetIndex) },
            )
        }
    }
}

@Composable
private fun PlayerSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x66242424)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StreamOptionRow(
    option: EmbyPlaybackStreamOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    Color.White.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.04f)
                },
            )
            .clickable(enabled = !option.lockedByServer && !selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (selected) PlayerAccentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selected) "选" else "流",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (option.lockedByServer) {
                    "${option.description} 当前源不支持切换其他串流方式。"
                } else {
                    option.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun PlayerInfoFieldRow(
    label: String,
    value: String,
    multiline: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.52f),
            modifier = Modifier.widthIn(min = 72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = if (multiline) 4 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayerStatusBadge(
    modifier: Modifier = Modifier,
    bitrateLabel: String?,
    isBuffering: Boolean,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PlayerPanelColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(active = isBuffering)
        Text(
            text = when {
                isBuffering && bitrateLabel != null -> "缓冲中 · 实时码率 $bitrateLabel"
                isBuffering -> "缓冲中 · 正在测量码率"
                bitrateLabel != null -> "实时码率 $bitrateLabel"
                else -> "实时码率 获取中"
            },
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerGestureOverlay(
    state: PlayerGestureOverlayState,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 220.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xD20B0B0C))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = state.icon,
                contentDescription = null,
                tint = PlayerAccentColor,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.detail,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f),
        )
        state.progress?.let { progress ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(PlayerAccentColor),
                )
            }
        }
    }
}

@Composable
private fun PlayerLoadingOverlay(
    label: String,
    detail: String?,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 224.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xD20B0B0C))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayerLoadingIndicator(
            modifier = Modifier.size(68.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = detail ?: "正在建立流连接",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f),
        )
    }
}

@Composable
private fun PlayerLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "player-loader")
    val haloScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loader-halo-scale",
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loader-halo-alpha",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .clip(CircleShape)
                .background(PlayerAccentColor),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val barScale by transition.animateFloat(
                    initialValue = 0.65f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 760,
                            delayMillis = index * 110,
                            easing = FastOutSlowInEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "loader-bar-$index",
                )
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height((16f + 14f * barScale).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (index == 1) {
                                PlayerAccentColor.copy(alpha = 0.96f)
                            } else {
                                Color.White.copy(alpha = 0.84f - index * 0.14f)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(
    active: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "bitrate-dot")
    val alpha by transition.animateFloat(
        initialValue = if (active) 0.42f else 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 900 else 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bitrate-dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (active) {
                    PlayerAccentColor.copy(alpha = alpha)
                } else {
                    Color.White.copy(alpha = alpha)
                },
            ),
    )
}

@Composable
private fun SubtitleOptionRow(
    option: SubtitleOption,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (option.selected) Color(0x33FFFFFF) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.selected) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlayerOverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
        )
    }
}

@Composable
private fun PlayerBottomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerSize: androidx.compose.ui.unit.Dp = 42.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier = Modifier
            .size(containerSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun buildSubtitleOptions(
    sourceTracks: List<EmbySubtitleTrack>,
    autoTracks: List<EmbySubtitleTrack>,
    selectedSubtitleIndex: Int?,
): List<SubtitleOption> {
    val autoLabel = autoTracks.firstOrNull { it.isDefault }?.label ?: "服务端自动匹配"

    return buildList {
        add(
            SubtitleOption(
                title = "自动",
                subtitle = autoLabel,
                targetIndex = null,
                selected = selectedSubtitleIndex == null,
            ),
        )
        add(
            SubtitleOption(
                title = "关闭字幕",
                subtitle = "不加载任何字幕轨",
                targetIndex = SUBTITLE_OFF,
                selected = selectedSubtitleIndex == SUBTITLE_OFF,
            ),
        )
        sourceTracks.forEach { track ->
            add(
                SubtitleOption(
                    title = track.label,
                    subtitle = buildString {
                        append(track.language ?: "未标记语言")
                        if (track.isExternal) {
                            append(" · 外挂")
                        }
                    },
                    targetIndex = track.index,
                    selected = selectedSubtitleIndex == track.index,
                ),
            )
        }
    }
}

private fun formatSubtitleChoiceLabel(
    selectedSubtitleIndex: Int?,
    sourceTracks: List<EmbySubtitleTrack>,
    autoTracks: List<EmbySubtitleTrack>,
): String {
    return when (selectedSubtitleIndex) {
        null -> autoTracks.firstOrNull { it.isDefault }?.label ?: "自动"
        SUBTITLE_OFF -> "关闭字幕"
        else -> sourceTracks.firstOrNull { it.index == selectedSubtitleIndex }?.label ?: "字幕"
    }
}

private fun formatPlaybackTime(valueMs: Long): String {
    val totalSeconds = (valueMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatBitrate(bitsPerSecond: Long): String? {
    if (bitsPerSecond <= 0L) return null
    val megaBits = bitsPerSecond / 1_000_000.0
    return if (megaBits >= 1.0) {
        String.format(Locale.US, "%.1f Mbps", megaBits)
    } else {
        String.format(Locale.US, "%.0f Kbps", bitsPerSecond / 1_000.0)
    }
}

private fun formatPercent(value: Float): String =
    "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"

private fun calculateSeekGestureRange(durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (durationMs / 12f).coerceIn(45_000f, 600_000f)
}

private fun shouldTrackResolvedPlaybackUrl(
    mediaLoadData: MediaLoadData,
): Boolean {
    if (mediaLoadData.trackType == C.TRACK_TYPE_TEXT) {
        return false
    }
    return mediaLoadData.dataType == C.DATA_TYPE_MEDIA || mediaLoadData.dataType == C.DATA_TYPE_MANIFEST
}

private fun String.isSubtitleLikeUri(): Boolean {
    val normalized = lowercase(Locale.US)
    return normalized.contains("/subtitles/") ||
        normalized.endsWith(".srt") ||
        normalized.endsWith(".ass") ||
        normalized.endsWith(".ssa") ||
        normalized.endsWith(".vtt") ||
        normalized.endsWith(".ttml")
}

private class TrackingDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val tracker: PlaybackTrafficTracker,
    private val onOpenedUri: (Uri) -> Unit,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val statsDataSource = StatsDataSource(upstream.createDataSource())
        return object : DataSource by statsDataSource {
            private var shouldTrackBytes = false

            override fun open(dataSpec: DataSpec): Long {
                val bytesToRead = statsDataSource.open(dataSpec)
                val openedUri = statsDataSource.lastOpenedUri ?: dataSpec.uri
                shouldTrackBytes = !openedUri.toString().isSubtitleLikeUri()
                if (shouldTrackBytes) {
                    tracker.onOpenedUri(openedUri)
                    onOpenedUri(openedUri)
                }
                return bytesToRead
            }

            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                val bytesRead = statsDataSource.read(target, offset, length)
                if (shouldTrackBytes && bytesRead > 0) {
                    tracker.onBytesRead(bytesRead)
                }
                return bytesRead
            }
        }
    }
}

private class PlaybackTrafficTracker {
    private val totalBytesRead = AtomicLong(0L)

    fun onOpenedUri(uri: Uri) {
        if (uri.toString().isSubtitleLikeUri()) return
    }

    fun onBytesRead(bytesRead: Int) {
        if (bytesRead > 0) {
            totalBytesRead.addAndGet(bytesRead.toLong())
        }
    }

    fun totalBytesRead(): Long = totalBytesRead.get()
}

private fun buildSeekGestureOverlay(
    targetPositionMs: Long,
    basePositionMs: Long,
    durationMs: Long,
): PlayerGestureOverlayState {
    val deltaMs = targetPositionMs - basePositionMs
    val forward = deltaMs >= 0L

    return PlayerGestureOverlayState(
        icon = if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
        title = "${if (forward) "快进" else "后退"} ${formatPlaybackTime(abs(deltaMs))}",
        detail = "${formatPlaybackTime(targetPositionMs)} / ${formatPlaybackTime(durationMs.coerceAtLeast(0L))}",
        progress = durationMs.takeIf { it > 0L }?.let { targetPositionMs / it.toFloat() },
    )
}

private fun formatSpeed(value: Float): String = String.format(Locale.US, "%.2fx", value)

private fun runtimeModeDescription(label: String): String = when (label) {
    PLAYER_MODE_SYSTEM -> "更贴近系统播放器，启动快，直解优先，遇到挑片源时兼容性会更敏感。"
    PLAYER_MODE_COMPATIBILITY -> "兼容性最高，遇到特殊封装或奇怪片源时优先用这个，代价是更偏保守。"
    else -> "均衡模式，兼顾启动速度、兼容性和日常播放稳定性。"
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.resolvePlayerBrightness(): Float {
    val current = window.attributes.screenBrightness
    if (current in 0f..1f) {
        return current.coerceIn(0.05f, 1f)
    }

    val systemBrightness = runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)

    return (systemBrightness / 255f).coerceIn(0.05f, 1f)
}

private fun Activity.applyPlayerBrightness(value: Float) {
    val params = window.attributes
    params.screenBrightness = value.coerceIn(0.05f, 1f)
    window.attributes = params
}

private fun AppSettings.toPlayerRuntimeProfile(): PlayerRuntimeProfile = when (playerMode) {
    PLAYER_MODE_COMPATIBILITY -> PlayerRuntimeProfile(
        label = PLAYER_MODE_COMPATIBILITY,
        decoderFallback = true,
        minBufferMs = 18_000,
        maxBufferMs = 72_000,
        bufferForPlaybackMs = 1_800,
        bufferForPlaybackAfterRebufferMs = 3_200,
        seekBackMs = 5_000L,
        seekForwardMs = 12_000L,
    )

    PLAYER_MODE_SYSTEM -> PlayerRuntimeProfile(
        label = PLAYER_MODE_SYSTEM,
        decoderFallback = false,
        minBufferMs = 6_000,
        maxBufferMs = 24_000,
        bufferForPlaybackMs = 700,
        bufferForPlaybackAfterRebufferMs = 1_300,
        seekBackMs = 4_000L,
        seekForwardMs = 8_000L,
    )

    else -> PlayerRuntimeProfile(
        label = PLAYER_MODE_STANDARD,
        decoderFallback = true,
        minBufferMs = 12_000,
        maxBufferMs = 48_000,
        bufferForPlaybackMs = 1_200,
        bufferForPlaybackAfterRebufferMs = 2_200,
        seekBackMs = 5_000L,
        seekForwardMs = 10_000L,
    )
}

private fun List<EmbySubtitleTrack>.applySubtitleStrategy(strategy: String): List<EmbySubtitleTrack> {
    if (isEmpty()) return this

    val preferred = when (strategy) {
        "双语优先" -> firstOrNull { track ->
            val text = "${track.label} ${track.language.orEmpty()}".lowercase()
            text.contains("双") || text.contains("中") || text.contains("zh")
        } ?: firstOrNull { it.isDefault }

        "原语言优先" -> firstOrNull { track ->
            val language = track.language.orEmpty().lowercase()
            language.isNotBlank() && language != "zh" && language != "chi" && language != "zho"
        } ?: firstOrNull { !it.isExternal } ?: firstOrNull { it.isDefault }

        "仅外挂字幕" -> firstOrNull { it.isExternal }
        "关闭自动匹配" -> null
        else -> firstOrNull { it.isDefault }
    }

    return map { track ->
        track.copy(
            isDefault = preferred?.index == track.index,
        )
    }
}

@Composable
fun PlayerLoadingScreen(
    title: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PlayerPanelColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "准备播放",
                style = MaterialTheme.typography.labelLarge,
                color = PlayerAccentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        PlayerLoadingIndicator(
            modifier = Modifier
                .padding(top = 18.dp)
                .size(88.dp),
        )
        Text(
            text = "正在准备播放",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "媒体源、字幕与解码能力匹配中",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}
