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
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Audiotrack
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.StatsDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.datasource.cronet.CronetUtil
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qiuhu.embyflow.BuildConfig
import com.qiuhu.embyflow.data.emby.EmbyAudioTrack
import com.qiuhu.embyflow.data.emby.EmbyPlaybackInfoField
import com.qiuhu.embyflow.data.emby.EmbyPlaybackSessionState
import com.qiuhu.embyflow.data.emby.EmbyPlaybackSource
import com.qiuhu.embyflow.data.emby.EmbyPlaybackStreamOption
import com.qiuhu.embyflow.data.emby.EmbySubtitleTrack
import com.qiuhu.embyflow.data.settings.AppSettings
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_COMPATIBILITY
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_STANDARD
import com.qiuhu.embyflow.data.settings.PLAYER_MODE_SYSTEM
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.isEpisode
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_CHINESE
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_ENGLISH
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_JAPANESE
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_KOREAN
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_SIMPLIFIED_CHINESE
import com.qiuhu.embyflow.data.settings.SUBTITLE_LANGUAGE_PREFERENCE_TRADITIONAL_CHINESE
import com.qiuhu.embyflow.ui.theme.AppTitleFontFamily
import `is`.xyz.mpv.MPVLib
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.chromium.net.CronetEngine
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val PlayerAccentColor = Color(0xFFF0E7DA)
private val PlayerPanelColor = Color(0xCC101010)
private const val PlayerDebugTag = "AurePPlayer"
private const val DualBackendExoCompanionDelayFastMs = 1_400L
private const val DualBackendExoCompanionDelayBalancedMs = 1_800L
private const val DualBackendExoCompanionDelayCompatibilityMs = 2_400L

private object PlaybackBackendMemory {
    private val rememberedBackends = linkedMapOf<String, PlayerBackendKind>()

    @Synchronized
    fun get(key: String): PlayerBackendKind? = rememberedBackends[key]

    @Synchronized
    fun remember(key: String, backend: PlayerBackendKind) {
        rememberedBackends[key] = backend
        while (rememberedBackends.size > 160) {
            val oldestKey = rememberedBackends.entries.firstOrNull()?.key ?: break
            rememberedBackends.remove(oldestKey)
        }
    }

    @Synchronized
    fun forget(key: String, backend: PlayerBackendKind) {
        if (rememberedBackends[key] == backend) {
            rememberedBackends.remove(key)
        }
    }
}

private object PlayerHttpDataSourceFactoryProvider {
    private const val NetworkTimeoutMs = 30_000
    private val cronetExecutor = Executors.newCachedThreadPool()

    @Volatile
    private var cronetEngine: CronetEngine? = null

    @Volatile
    private var cronetBuildAttempted: Boolean = false

    fun create(
        context: Context,
        requestHeaders: Map<String, String>,
    ): HttpDataSource.Factory {
        val normalizedHeaders = requestHeaders
            .mapKeys { (name, _) -> name.trim() }
            .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }

        val fallbackFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(NetworkTimeoutMs)
            .setReadTimeoutMs(NetworkTimeoutMs)
            .setUserAgent(playerHttpUserAgent())
            .setDefaultRequestProperties(normalizedHeaders)

        val activeCronetEngine = getCronetEngine(context.applicationContext) ?: return fallbackFactory

        return CronetDataSource.Factory(activeCronetEngine, cronetExecutor)
            .setUserAgent(playerHttpUserAgent())
            .setConnectionTimeoutMs(NetworkTimeoutMs)
            .setReadTimeoutMs(NetworkTimeoutMs)
            .setResetTimeoutOnRedirects(true)
            .setDefaultRequestProperties(normalizedHeaders)
            .setFallbackFactory(fallbackFactory)
    }

    @Synchronized
    private fun getCronetEngine(context: Context): CronetEngine? {
        if (cronetBuildAttempted) {
            return cronetEngine
        }
        cronetBuildAttempted = true
        cronetEngine = runCatching {
            CronetUtil.buildCronetEngine(
                context,
                playerHttpUserAgent(),
                false,
            )
        }.getOrNull()
        if (cronetEngine != null) {
            Log.i(PlayerDebugTag, "Cronet engine enabled for Exo data source")
        } else {
            Log.w(PlayerDebugTag, "Cronet unavailable, fallback to default Exo HTTP stack")
        }
        return cronetEngine
    }
}

private fun playerHttpUserAgent(): String = "AureP/${BuildConfig.VERSION_NAME} (Android)"

private sealed interface RedirectProbeState {
    data object Idle : RedirectProbeState
    data object Loading : RedirectProbeState
    data class Hit(
        val code: Int,
        val location: String,
    ) : RedirectProbeState

    data class NoRedirect(
        val code: Int,
    ) : RedirectProbeState

    data class Skipped(
        val reason: String,
    ) : RedirectProbeState

    data class Failed(
        val message: String,
    ) : RedirectProbeState
}

@Composable
fun PlayerScreen(
    media: MediaItem,
    mediaId: String,
    title: String,
    source: EmbyPlaybackSource,
    initialResumePositionMs: Long,
    settings: AppSettings,
    onPlaybackStarted: (EmbyPlaybackSessionState) -> Unit,
    onPlaybackProgress: (EmbyPlaybackSessionState, String) -> Unit,
    onPlaybackStopped: (EmbyPlaybackSessionState) -> Unit,
    onClose: (Long, Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val landscapeNavBarRightPadding = if (isLandscapeLayout) {
        with(density) { WindowInsets.navigationBars.getRight(this, layoutDirection).toDp() }
    } else {
        0.dp
    }
    val headerLogoUrl = remember(media) {
        if (media.isEpisode) {
            media.seriesTitleLogoUrl?.takeIf { it.isNotBlank() }
                ?: media.titleLogoUrl?.takeIf { it.isNotBlank() }
        } else {
            media.titleLogoUrl?.takeIf { it.isNotBlank() }
        }
    }
    val headerTitle = remember(media, title) {
        when {
            media.isEpisode -> media.seriesName.ifBlank { title }
            media.title.isNotBlank() -> media.title
            else -> title
        }
    }
    val episodeTitle = remember(media, title) {
        if (!media.isEpisode) {
            null
        } else {
            media.title
                .takeIf { it.isNotBlank() && it != media.seriesName }
                ?: title.takeIf { it.isNotBlank() && it != media.seriesName }
        }
    }
    val runtimeProfile = remember(settings.playerMode) {
        settings.toPlayerRuntimeProfile()
    }
    val mpvAvailable = remember { MPVLib.isAvailable }
    val sourceAudioTracks = remember(source.audioTracks) {
        source.audioTracks.map { it.toPlayerAudioTrack() }
    }
    val sourceSubtitleTracks = remember(source.subtitleTracks) {
        source.subtitleTracks.map { it.toPlayerSubtitleTrack() }
    }
    var vlcRuntimeAudioTracks by remember(mediaId) {
        mutableStateOf(emptyList<VlcRuntimeAudioTrack>())
    }
    var vlcRuntimeSubtitleTracks by remember(mediaId) {
        mutableStateOf(emptyList<VlcRuntimeSubtitleTrack>())
    }
    var mpvRuntimeAudioTracks by remember(mediaId) {
        mutableStateOf(emptyList<MpvRuntimeAudioTrack>())
    }
    var mpvRuntimeSubtitleTracks by remember(mediaId) {
        mutableStateOf(emptyList<MpvRuntimeSubtitleTrack>())
    }
    var exoRuntimeAudioTracks by remember(mediaId) {
        mutableStateOf(emptyList<ExoRuntimeAudioTrack>())
    }
    var exoRuntimeSubtitleTracks by remember(mediaId) {
        mutableStateOf(emptyList<ExoRuntimeSubtitleTrack>())
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
    var selectedSubtitleKey by rememberSaveable(mediaId) {
        mutableStateOf<String?>(null)
    }
    var selectedAudioKey by rememberSaveable(mediaId) {
        mutableStateOf<String?>(null)
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
    var showAudioSheet by rememberSaveable {
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
    var failedStreamOptionIds by remember(mediaId) {
        mutableStateOf(emptySet<String>())
    }
    var autoFallbackInProgress by remember(mediaId) {
        mutableStateOf(false)
    }
    var forceVlcCompatibilityBackend by rememberSaveable(mediaId) {
        mutableStateOf(false)
    }
    var forceExoStandardBackend by rememberSaveable(mediaId) {
        mutableStateOf(false)
    }
    var forceMpvBackend by rememberSaveable(mediaId) {
        mutableStateOf(false)
    }
    var attemptedBackendKinds by rememberSaveable(mediaId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var compatibilityPlaybackUrl by remember(mediaId) {
        mutableStateOf<String?>(null)
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
    LaunchedEffect(mpvAvailable) {
        if (!mpvAvailable) {
            Log.w(
                PlayerDebugTag,
                "libmpv unavailable, fallback to remaining backends reason=${MPVLib.loadErrorMessage.orEmpty()}",
            )
        }
    }
    LaunchedEffect(mediaId, activeStreamOption.id) {
        vlcRuntimeSubtitleTracks = emptyList()
        vlcRuntimeAudioTracks = emptyList()
        mpvRuntimeSubtitleTracks = emptyList()
        mpvRuntimeAudioTracks = emptyList()
        exoRuntimeSubtitleTracks = emptyList()
        exoRuntimeAudioTracks = emptyList()
        attemptedBackendKinds = emptySet()
    }
    var redirectProbeState by remember(activeStreamOption.id, activeStreamOption.streamUrl) {
        mutableStateOf<RedirectProbeState>(RedirectProbeState.Idle)
    }
    var hasRenderedFirstFrame by remember(activeStreamOption.id) {
        mutableStateOf(false)
    }
    var playbackStartedReported by rememberSaveable(
        mediaId,
        source.mediaSourceId,
        source.playSessionId,
    ) {
        mutableStateOf(false)
    }
    var playbackStoppedReported by rememberSaveable(
        mediaId,
        source.mediaSourceId,
        source.playSessionId,
    ) {
        mutableStateOf(false)
    }

    val streamChoiceLabel = remember(activeStreamOption) {
        activeStreamOption.label
    }
    val sourceContainer = remember(source.container, source.infoFields) {
        source.container?.takeIf { it.isNotBlank() }
            ?: source.infoFields.firstOrNull { it.label == "封装" }?.value.orEmpty()
    }
    val sourceVideoCodec = remember(source.videoCodec, source.infoFields) {
        source.videoCodec?.takeIf { it.isNotBlank() }
            ?: source.infoFields.firstOrNull { it.label == "视频编码" }?.value.orEmpty()
    }
    val sourceAudioCodec = remember(source.audioCodec, source.infoFields) {
        source.audioCodec?.takeIf { it.isNotBlank() }
            ?: source.infoFields.firstOrNull { it.label == "音频编码" }?.value.orEmpty()
    }
    val sourceVideoRange = remember(source.videoRange) {
        source.videoRange.orEmpty()
    }
    val sourceExtendedVideoType = remember(source.extendedVideoType) {
        source.extendedVideoType.orEmpty()
    }
    val sourceLegacyCompatibilityRisk = remember(
        sourceContainer,
        sourceVideoCodec,
    ) {
        shouldPreferCompatibilityBackend(
            container = sourceContainer,
            videoCodec = sourceVideoCodec,
        )
    }
    val sourceCompatibilityStreamHint = remember(
        activeStreamOption.id,
        activeStreamOption.streamUrl,
        source.infoLine,
    ) {
        shouldPreferCompatibilityBackendForStreamOption(
            optionId = activeStreamOption.id,
            streamUrl = activeStreamOption.streamUrl,
            infoLine = source.infoLine,
        )
    }
    val sourceRequiresCompatibilityBackend = sourceLegacyCompatibilityRisk || sourceCompatibilityStreamHint
    val sourceHdrCompatibilityRisk = remember(
        sourceVideoRange,
        sourceExtendedVideoType,
        source.bitDepth,
        source.title,
        title,
        source.infoLine,
        activeStreamOption.streamUrl,
    ) {
        shouldPreferMpvBackend(
            videoRange = sourceVideoRange,
            extendedVideoType = sourceExtendedVideoType,
            bitDepth = source.bitDepth,
            title = listOf(source.title, title, source.infoLine, activeStreamOption.streamUrl)
                .joinToString(separator = " "),
        )
    }
    val mpvHwdecOption = remember(sourceHdrCompatibilityRisk) {
        buildMpvHwdecOption(
            hdrRisk = sourceHdrCompatibilityRisk,
        )
    }
    val mpvEnableHdrToneMapping = remember(sourceHdrCompatibilityRisk) {
        sourceHdrCompatibilityRisk
    }
    val backendMemoryKey = remember(
        source.mediaSourceId,
        activeStreamOption.id,
        sourceContainer,
        sourceVideoCodec,
        sourceAudioCodec,
    ) {
        buildBackendMemoryKey(
            mediaSourceId = source.mediaSourceId,
            streamOptionId = activeStreamOption.id,
            container = sourceContainer,
            videoCodec = sourceVideoCodec,
            audioCodec = sourceAudioCodec,
        )
    }
    val rememberedBackendKind = PlaybackBackendMemory.get(backendMemoryKey)
    val automaticBackendOrder = remember(
        runtimeProfile.label,
        mpvAvailable,
        sourceLegacyCompatibilityRisk,
        sourceHdrCompatibilityRisk,
        sourceContainer,
        sourceVideoCodec,
        sourceAudioCodec,
        source.isRemote,
        source.infoLine,
    ) {
        buildAutomaticBackendOrder(
            runtimeMode = runtimeProfile.label,
            legacyRisk = sourceLegacyCompatibilityRisk,
            hdrRisk = sourceHdrCompatibilityRisk,
            container = sourceContainer,
            videoCodec = sourceVideoCodec,
            audioCodec = sourceAudioCodec,
            isRemote = source.isRemote,
            infoLine = source.infoLine,
        ).filterAvailableBackends(mpvAvailable)
    }
    val preferredBackendKind = when {
        forceVlcCompatibilityBackend -> PlayerBackendKind.Vlc
        forceExoStandardBackend -> PlayerBackendKind.Exo
        forceMpvBackend && mpvAvailable -> PlayerBackendKind.Mpv
        rememberedBackendKind == PlayerBackendKind.Mpv && !mpvAvailable -> automaticBackendOrder.first()
        rememberedBackendKind == PlayerBackendKind.Mpv && sourceHdrCompatibilityRisk -> automaticBackendOrder.first()
        rememberedBackendKind == PlayerBackendKind.Exo && sourceHdrCompatibilityRisk -> automaticBackendOrder.first()
        rememberedBackendKind != null -> rememberedBackendKind
        else -> automaticBackendOrder.first()
    }
    val experimentalDualBackendRace = remember(
        forceVlcCompatibilityBackend,
        forceExoStandardBackend,
        forceMpvBackend,
        preferredBackendKind,
        runtimeProfile.label,
        sourceHdrCompatibilityRisk,
        sourceContainer,
        sourceVideoCodec,
        sourceAudioCodec,
        source.infoLine,
    ) {
        !forceVlcCompatibilityBackend &&
            !forceExoStandardBackend &&
            !forceMpvBackend &&
            preferredBackendKind == PlayerBackendKind.Vlc &&
            !sourceHdrCompatibilityRisk &&
            runtimeProfile.label != PLAYER_MODE_SYSTEM &&
            shouldUseDualBackendRace(
                container = sourceContainer,
                videoCodec = sourceVideoCodec,
                audioCodec = sourceAudioCodec,
                infoLine = source.infoLine,
            )
    }
    val exoCompanionDelayMs = remember(runtimeProfile.label) {
        companionArmDelayMs(runtimeProfile.label)
    }
    val initialBackendKind = if (experimentalDualBackendRace) {
        PlayerBackendKind.Vlc
    } else {
        preferredBackendKind
    }
    var exoCompanionArmed by rememberSaveable(
        mediaId,
        activeStreamOption.id,
        experimentalDualBackendRace,
        preferredBackendKind.name,
    ) {
        mutableStateOf(!experimentalDualBackendRace && preferredBackendKind == PlayerBackendKind.Exo)
    }
    val shouldCreateExoPlayer = when {
        experimentalDualBackendRace -> exoCompanionArmed
        preferredBackendKind == PlayerBackendKind.Exo -> true
        else -> false
    }
    var activeBackendKindName by rememberSaveable(
        mediaId,
        activeStreamOption.id,
        initialBackendKind.name,
        experimentalDualBackendRace,
    ) {
        mutableStateOf(initialBackendKind.name)
    }
    var raceResolved by rememberSaveable(
        mediaId,
        activeStreamOption.id,
        experimentalDualBackendRace,
    ) {
        mutableStateOf(!experimentalDualBackendRace)
    }
    var exoFirstFrameAtMs by remember(activeStreamOption.id) {
        mutableLongStateOf(0L)
    }
    var vlcFirstFrameAtMs by remember(activeStreamOption.id) {
        mutableLongStateOf(0L)
    }
    var mpvFirstFrameAtMs by remember(activeStreamOption.id) {
        mutableLongStateOf(0L)
    }
    val activeBackendKind = remember(activeBackendKindName) {
        PlayerBackendKind.valueOf(activeBackendKindName)
    }
    val activeBackendLabel = remember(activeBackendKind) {
        when (activeBackendKind) {
            PlayerBackendKind.Exo -> "Exo"
            PlayerBackendKind.Mpv -> "MPV"
            PlayerBackendKind.Vlc -> "VLC"
        }
    }
    val raceInProgress = experimentalDualBackendRace && !raceResolved
    val runtimeSubtitleTracks = remember(
        activeBackendKind,
        vlcRuntimeSubtitleTracks,
        mpvRuntimeSubtitleTracks,
        exoRuntimeSubtitleTracks,
    ) {
        when (activeBackendKind) {
            PlayerBackendKind.Vlc -> vlcRuntimeSubtitleTracks.map { it.toPlayerSubtitleTrack() }
            PlayerBackendKind.Mpv -> mpvRuntimeSubtitleTracks.map { it.toPlayerSubtitleTrack() }
            PlayerBackendKind.Exo -> exoRuntimeSubtitleTracks.map { it.toPlayerSubtitleTrack() }
        }
    }
    val runtimeAudioTracks = remember(
        activeBackendKind,
        vlcRuntimeAudioTracks,
        mpvRuntimeAudioTracks,
        exoRuntimeAudioTracks,
    ) {
        when (activeBackendKind) {
            PlayerBackendKind.Vlc -> vlcRuntimeAudioTracks.map { it.toPlayerAudioTrack() }
            PlayerBackendKind.Mpv -> mpvRuntimeAudioTracks.map { it.toPlayerAudioTrack() }
            PlayerBackendKind.Exo -> exoRuntimeAudioTracks.map { it.toPlayerAudioTrack() }
        }
    }
    val availableSubtitleTracks = remember(sourceSubtitleTracks, runtimeSubtitleTracks) {
        mergeSubtitleTracks(
            sourceTracks = sourceSubtitleTracks,
            runtimeTracks = runtimeSubtitleTracks,
        )
    }
    val availableAudioTracks = remember(sourceAudioTracks, runtimeAudioTracks) {
        mergeAudioTracks(
            sourceTracks = sourceAudioTracks,
            runtimeTracks = runtimeAudioTracks,
        )
    }
    val autoSubtitleTracks = remember(
        availableSubtitleTracks,
        settings.embeddedSubtitleLanguage,
        settings.externalSubtitleLanguage,
    ) {
        availableSubtitleTracks.resolveAutomaticSubtitleSelection(
            embeddedLanguagePreference = settings.embeddedSubtitleLanguage,
            externalLanguagePreference = settings.externalSubtitleLanguage,
        )
    }
    val autoAudioTracks = remember(availableAudioTracks) {
        availableAudioTracks.resolveAutomaticAudioSelection()
    }
    val activeSubtitleTracks = remember(availableSubtitleTracks, autoSubtitleTracks, selectedSubtitleKey) {
        when (selectedSubtitleKey) {
            null -> autoSubtitleTracks
            SUBTITLE_OFF -> emptyList()
            else -> availableSubtitleTracks
                .filter { it.key == selectedSubtitleKey }
                .map { it.copy(isDefault = true) }
        }
    }
    val activeAudioTrack = remember(availableAudioTracks, autoAudioTracks, selectedAudioKey) {
        when (selectedAudioKey) {
            null -> autoAudioTracks.firstOrNull()
            else -> availableAudioTracks.firstOrNull { it.key == selectedAudioKey }
        }
    }
    val sourceExternalSubtitleTracks = remember(sourceSubtitleTracks) {
        sourceSubtitleTracks.filter { it.isExternal && !it.url.isNullOrBlank() && !it.mimeType.isNullOrBlank() }
    }
    val audioChoiceLabel = remember(availableAudioTracks, autoAudioTracks, selectedAudioKey) {
        formatAudioChoiceLabel(
            selectedAudioKey = selectedAudioKey,
            sourceTracks = availableAudioTracks,
            autoTracks = autoAudioTracks,
        )
    }
    val vlcExternalSubtitleTracks = remember(sourceExternalSubtitleTracks) {
        sourceExternalSubtitleTracks.mapNotNull { track ->
            val url = track.url ?: return@mapNotNull null
            VlcExternalSubtitleTrack(
                label = track.label,
                url = url,
                isDefault = track.isDefault,
            )
        }
    }
    val mpvExternalSubtitleTracks = remember(sourceExternalSubtitleTracks) {
        sourceExternalSubtitleTracks.mapNotNull { track ->
            val url = track.url ?: return@mapNotNull null
            MpvExternalSubtitleTrack(
                label = track.label,
                url = url,
                isDefault = track.isDefault,
            )
        }
    }
    val subtitleChoiceLabel = remember(availableSubtitleTracks, autoSubtitleTracks, selectedSubtitleKey) {
        formatSubtitleChoiceLabel(
            selectedSubtitleKey = selectedSubtitleKey,
            sourceTracks = availableSubtitleTracks,
            autoTracks = autoSubtitleTracks,
        )
    }
    val vlcForceSoftwareDecode = remember(
        sourceContainer,
        sourceVideoCodec,
        preferredBackendKind,
        experimentalDualBackendRace,
    ) {
        (preferredBackendKind == PlayerBackendKind.Vlc || experimentalDualBackendRace) &&
            shouldForceVlcSoftwareDecode(
            container = sourceContainer,
            videoCodec = sourceVideoCodec,
        )
    }
    val promoteRaceBackend: (PlayerBackendKind, String) -> Unit = { backendKind, trigger ->
        if (!experimentalDualBackendRace) {
            Unit
        } else if (raceResolved && activeBackendKind == backendKind) {
            Unit
        } else {
            Log.i(
                PlayerDebugTag,
                "dual-race select title=$title mediaId=$mediaId backend=$backendKind trigger=$trigger option=${activeStreamOption.id}",
            )
            activeBackendKindName = backendKind.name
            raceResolved = true
            hasRenderedFirstFrame = when (backendKind) {
                PlayerBackendKind.Exo -> exoFirstFrameAtMs > 0L
                PlayerBackendKind.Mpv -> false
                PlayerBackendKind.Vlc -> vlcFirstFrameAtMs > 0L
            }
            bitrateEstimateBitsPerSecond = 0L
        }
    }
    val registerBackendFirstFrame: (PlayerBackendKind, Long?) -> Unit = { backendKind, renderTimeMs ->
        val nowMs = SystemClock.elapsedRealtime()
        when (backendKind) {
            PlayerBackendKind.Exo -> {
                if (exoFirstFrameAtMs == 0L) {
                    exoFirstFrameAtMs = nowMs
                }
            }

            PlayerBackendKind.Mpv -> {
                if (mpvFirstFrameAtMs == 0L) {
                    mpvFirstFrameAtMs = nowMs
                }
            }

            PlayerBackendKind.Vlc -> {
                if (vlcFirstFrameAtMs == 0L) {
                    vlcFirstFrameAtMs = nowMs
                }
            }
        }

        if (experimentalDualBackendRace) {
            val winner = when {
                exoFirstFrameAtMs > 0L && vlcFirstFrameAtMs > 0L ->
                    if (exoFirstFrameAtMs <= vlcFirstFrameAtMs) PlayerBackendKind.Exo else PlayerBackendKind.Vlc
                backendKind == PlayerBackendKind.Exo && vlcFirstFrameAtMs == 0L -> PlayerBackendKind.Exo
                backendKind == PlayerBackendKind.Vlc && exoFirstFrameAtMs == 0L -> PlayerBackendKind.Vlc
                else -> null
            }
            if (winner != null) {
                PlaybackBackendMemory.remember(backendMemoryKey, winner)
                promoteRaceBackend(
                    winner,
                    renderTimeMs?.let { "first-frame-$it" } ?: "first-frame",
                )
            }
        } else if (activeBackendKind == backendKind) {
            PlaybackBackendMemory.remember(backendMemoryKey, backendKind)
            hasRenderedFirstFrame = true
            isBuffering = false
        }
    }
    val playbackTrafficTracker = remember(
        activeStreamOption.id,
        activeStreamOption.streamUrl,
        preferredBackendKind,
        experimentalDualBackendRace,
    ) {
        PlaybackTrafficTracker()
    }
    var resolvedPlaybackUrl by rememberSaveable(activeStreamOption.id, activeStreamOption.streamUrl) {
        mutableStateOf(activeStreamOption.streamUrl)
    }
    val mediaItem = remember(
        activeStreamOption.streamUrl,
        sourceExternalSubtitleTracks,
        shouldCreateExoPlayer,
    ) {
        if (!shouldCreateExoPlayer) {
            null
        } else {
            PlayerMediaItem.Builder()
                .setUri(activeStreamOption.streamUrl)
                .setSubtitleConfigurations(
                    sourceExternalSubtitleTracks.mapNotNull { subtitle ->
                        val subtitleUrl = subtitle.url ?: return@mapNotNull null
                        val mimeType = subtitle.mimeType ?: return@mapNotNull null
                        PlayerMediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                            .setMimeType(mimeType)
                            .setLanguage(subtitle.language)
                            .setLabel(subtitle.label)
                            .setSelectionFlags(if (subtitle.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    },
                )
                .build()
        }
    }
    val exoPlayer = if (!shouldCreateExoPlayer) {
        null
    } else {
        remember(
            context,
            mediaItem,
            runtimeProfile,
            activeStreamOption.requestHeaders,
            experimentalDualBackendRace,
            exoCompanionArmed,
        ) {
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
            val httpDataSourceFactory = PlayerHttpDataSourceFactoryProvider.create(
                context = context,
                requestHeaders = activeStreamOption.requestHeaders,
            )
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
                    setMediaItem(requireNotNull(mediaItem))
                    prepare()
                    if (resumePositionMs > 0L) {
                        seekTo(resumePositionMs)
                    }
                    playbackParameters = PlaybackParameters(playbackSpeed)
                    playWhenReady = resumePlayWhenReady
                    volume = if (experimentalDualBackendRace) 0f else 1f
            }
        }
    }
    val mpvPlaybackUrl = remember(
        activeStreamOption.id,
        activeStreamOption.streamUrl,
        resolvedPlaybackUrl,
    ) {
        resolvedPlaybackUrl.takeIf { it.isNotBlank() } ?: activeStreamOption.streamUrl
    }
    val mpvPlayer = if (preferredBackendKind == PlayerBackendKind.Mpv && mpvAvailable) {
        remember(
            context,
            mpvPlaybackUrl,
            mpvExternalSubtitleTracks,
            activeStreamOption.requestHeaders,
            mpvHwdecOption,
            mpvEnableHdrToneMapping,
        ) {
            MpvPlayerSession(
                context = context,
                streamUrl = mpvPlaybackUrl,
                subtitleTracks = mpvExternalSubtitleTracks,
                onSubtitleTracksChanged = { tracks, selectedTrackId ->
                    mainHandler.post {
                        mpvRuntimeSubtitleTracks = tracks
                        if (selectedSubtitleKey == null && selectedTrackId != null) {
                            // keep automatic selection in sync
                        }
                    }
                },
                onAudioTracksChanged = { tracks, _ ->
                    mainHandler.post {
                        mpvRuntimeAudioTracks = tracks
                    }
                },
                requestHeaders = activeStreamOption.requestHeaders,
                hwdecOption = mpvHwdecOption,
                enableHdrToneMapping = mpvEnableHdrToneMapping,
                startPositionMs = resumePositionMs,
                playWhenReady = resumePlayWhenReady,
                initialVolume = 100,
                initialPlaybackSpeed = playbackSpeed,
                seekBackMs = runtimeProfile.seekBackMs,
                seekForwardMs = runtimeProfile.seekForwardMs,
                onFirstFrameRendered = {
                    mainHandler.post {
                        registerBackendFirstFrame(PlayerBackendKind.Mpv, null)
                    }
                },
            )
        }
    } else {
        null
    }
    val vlcPlaybackUrl = remember(
        activeStreamOption.id,
        activeStreamOption.streamUrl,
        compatibilityPlaybackUrl,
    ) {
        compatibilityPlaybackUrl?.takeIf { it.isNotBlank() } ?: activeStreamOption.streamUrl
    }
    val vlcPlayer = if (preferredBackendKind == PlayerBackendKind.Vlc || experimentalDualBackendRace) {
        remember(
            context,
            vlcPlaybackUrl,
            vlcExternalSubtitleTracks,
            activeStreamOption.requestHeaders,
            experimentalDualBackendRace,
            vlcForceSoftwareDecode,
        ) {
            VlcPlayerSession(
                context = context,
                streamUrl = vlcPlaybackUrl,
                subtitleTracks = vlcExternalSubtitleTracks,
                onSubtitleTracksChanged = { tracks, selectedTrackId ->
                    mainHandler.post {
                        vlcRuntimeSubtitleTracks = tracks
                        if (selectedSubtitleKey == null && selectedTrackId != null) {
                            // Keep the runtime-selected track in sync when VLC auto-selects one.
                        }
                    }
                },
                onAudioTracksChanged = { tracks, _ ->
                    mainHandler.post {
                        vlcRuntimeAudioTracks = tracks
                    }
                },
                requestHeaders = activeStreamOption.requestHeaders,
                forceSoftwareDecode = vlcForceSoftwareDecode,
                startPositionMs = resumePositionMs,
                playWhenReady = resumePlayWhenReady,
                initialVolume = if (experimentalDualBackendRace) 0 else 100,
                initialPlaybackSpeed = playbackSpeed,
                seekBackMs = runtimeProfile.seekBackMs,
                seekForwardMs = runtimeProfile.seekForwardMs,
                onFirstFrameRendered = {
                    mainHandler.post {
                        registerBackendFirstFrame(PlayerBackendKind.Vlc, null)
                    }
                },
            )
        }
    } else {
        null
    }

    fun currentPositionSnapshot(): Long = when (activeBackendKind) {
        PlayerBackendKind.Mpv -> mpvPlayer?.currentPositionMs?.coerceAtLeast(0L) ?: 0L
        PlayerBackendKind.Vlc -> vlcPlayer?.currentPositionMs?.coerceAtLeast(0L) ?: 0L
        PlayerBackendKind.Exo -> exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    fun durationSnapshot(): Long = when (activeBackendKind) {
        PlayerBackendKind.Mpv -> mpvPlayer?.durationMs?.coerceAtLeast(0L) ?: 0L
        PlayerBackendKind.Vlc -> vlcPlayer?.durationMs?.coerceAtLeast(0L) ?: 0L
        PlayerBackendKind.Exo -> exoPlayer?.duration?.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    }

    fun bufferedPositionSnapshot(): Long = when (activeBackendKind) {
        PlayerBackendKind.Mpv -> mpvPlayer?.bufferedPositionMs?.coerceAtLeast(0L) ?: 0L
        PlayerBackendKind.Vlc -> vlcPlayer?.bufferedPositionMs?.coerceAtLeast(0L) ?: 0L
        PlayerBackendKind.Exo -> exoPlayer?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
    }

    fun isPlayingSnapshot(): Boolean = when (activeBackendKind) {
        PlayerBackendKind.Mpv -> mpvPlayer?.isPlaying ?: false
        PlayerBackendKind.Vlc -> vlcPlayer?.isPlaying ?: false
        PlayerBackendKind.Exo -> exoPlayer?.isPlaying ?: false
    }

    fun isBufferingSnapshot(): Boolean = when (activeBackendKind) {
        PlayerBackendKind.Mpv -> mpvPlayer?.isBuffering ?: false
        PlayerBackendKind.Vlc -> vlcPlayer?.isBuffering ?: false
        PlayerBackendKind.Exo -> exoPlayer?.playbackState == Player.STATE_BUFFERING
    }

    fun playbackStateSnapshot(): Int = when (activeBackendKind) {
        PlayerBackendKind.Mpv -> when {
            mpvPlayer?.isEnded == true -> Player.STATE_ENDED
            mpvPlayer?.isBuffering == true -> Player.STATE_BUFFERING
            mpvPlayer?.isPlaying == true -> Player.STATE_READY
            else -> Player.STATE_IDLE
        }

        PlayerBackendKind.Vlc -> when {
            vlcPlayer?.isEnded == true -> Player.STATE_ENDED
            vlcPlayer?.isBuffering == true -> Player.STATE_BUFFERING
            vlcPlayer?.isPlaying == true -> Player.STATE_READY
            else -> Player.STATE_IDLE
        }

        PlayerBackendKind.Exo -> exoPlayer?.playbackState ?: Player.STATE_IDLE
    }

    fun shouldExpectPlaybackToStart(): Boolean = if (raceInProgress) {
        (exoPlayer?.playWhenReady ?: false) || (vlcPlayer?.playWhenReadyRequested ?: false)
    } else {
        when (activeBackendKind) {
            PlayerBackendKind.Mpv -> mpvPlayer?.playWhenReadyRequested ?: resumePlayWhenReady
            PlayerBackendKind.Vlc -> vlcPlayer?.playWhenReadyRequested ?: resumePlayWhenReady
            PlayerBackendKind.Exo -> exoPlayer?.playWhenReady ?: resumePlayWhenReady
        }
    }

    fun currentPlaybackSpeedTarget(): Float = playbackSpeed

    fun currentSubtitleStreamIndex(): Int? = when (selectedSubtitleKey) {
        null -> autoSubtitleTracks.firstOrNull()?.serverIndex
        SUBTITLE_OFF -> null
        else -> availableSubtitleTracks.firstOrNull { it.key == selectedSubtitleKey }?.serverIndex
    }

    fun currentAudioStreamIndex(): Int? = when (selectedAudioKey) {
        null -> autoAudioTracks.firstOrNull()?.serverIndex
        else -> availableAudioTracks.firstOrNull { it.key == selectedAudioKey }?.serverIndex
    }

    fun currentPlayMethod(): String = "DirectStream"

    fun buildPlaybackSessionState(
        positionOverrideMs: Long? = null,
    ): EmbyPlaybackSessionState? {
        val playSessionId = source.playSessionId?.takeIf { it.isNotBlank() } ?: return null
        val mediaSourceId = source.mediaSourceId.takeIf { it.isNotBlank() } ?: return null
        val positionMs = (
            positionOverrideMs
                ?: if (isScrubbing) sliderPositionMs.toLong() else currentPositionSnapshot()
            ).coerceAtLeast(0L)
        return EmbyPlaybackSessionState(
            itemId = mediaId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            positionMs = positionMs,
            durationMs = durationSnapshot().coerceAtLeast(0L),
            isPaused = !isPlayingSnapshot(),
            playbackRate = playbackSpeed.toDouble(),
            subtitleStreamIndex = currentSubtitleStreamIndex(),
            audioStreamIndex = currentAudioStreamIndex(),
            playMethod = currentPlayMethod(),
        )
    }

    fun reportPlaybackStartedIfNeeded() {
        if (playbackStartedReported || playbackStoppedReported) return
        val state = buildPlaybackSessionState() ?: return
        playbackStartedReported = true
        onPlaybackStarted(state)
    }

    fun reportPlaybackProgressEvent(
        eventName: String,
        positionOverrideMs: Long? = null,
    ) {
        if (!playbackStartedReported || playbackStoppedReported) return
        buildPlaybackSessionState(positionOverrideMs)?.let { state ->
            onPlaybackProgress(state, eventName)
        }
    }

    DisposableEffect(lifecycleOwner, mediaId, source.mediaSourceId, source.playSessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                reportPlaybackProgressEvent("AppBackground")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun reportPlaybackStoppedIfNeeded() {
        if (!playbackStartedReported || playbackStoppedReported) return
        val state = buildPlaybackSessionState() ?: return
        playbackStoppedReported = true
        onPlaybackStopped(state)
    }

    fun capturePlaybackState() {
        resumePositionMs = if (raceInProgress) {
            max(
                exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                vlcPlayer?.currentPositionMs?.coerceAtLeast(0L) ?: 0L,
            )
        } else {
            currentPositionSnapshot()
        }
        resumePlayWhenReady = shouldExpectPlaybackToStart()
    }

    fun seekToPlayback(positionMs: Long) {
        if (raceInProgress) {
            exoPlayer?.seekTo(positionMs)
            vlcPlayer?.seekTo(positionMs)
        } else {
            when (activeBackendKind) {
                PlayerBackendKind.Mpv -> mpvPlayer?.seekTo(positionMs)
                PlayerBackendKind.Vlc -> vlcPlayer?.seekTo(positionMs)
                PlayerBackendKind.Exo -> exoPlayer?.seekTo(positionMs)
            }
        }
    }

    fun seekBackPlayback() {
        if (raceInProgress) {
            exoPlayer?.seekBack()
            vlcPlayer?.seekBack()
        } else {
            when (activeBackendKind) {
                PlayerBackendKind.Mpv -> mpvPlayer?.seekBack()
                PlayerBackendKind.Vlc -> vlcPlayer?.seekBack()
                PlayerBackendKind.Exo -> exoPlayer?.seekBack()
            }
        }
    }

    fun seekForwardPlayback() {
        if (raceInProgress) {
            exoPlayer?.seekForward()
            vlcPlayer?.seekForward()
        } else {
            when (activeBackendKind) {
                PlayerBackendKind.Mpv -> mpvPlayer?.seekForward()
                PlayerBackendKind.Vlc -> vlcPlayer?.seekForward()
                PlayerBackendKind.Exo -> exoPlayer?.seekForward()
            }
        }
    }

    fun togglePlayPausePlayback() {
        if (raceInProgress) {
            val shouldPause = (exoPlayer?.isPlaying == true) || (vlcPlayer?.isPlaying == true)
            if (shouldPause) {
                exoPlayer?.pause()
                vlcPlayer?.pause()
            } else {
                exoPlayer?.play()
                vlcPlayer?.play()
            }
        } else {
            when (activeBackendKind) {
                PlayerBackendKind.Mpv -> {
                    if (mpvPlayer?.isPlaying == true) {
                        mpvPlayer.pause()
                    } else {
                        mpvPlayer?.play()
                    }
                }

                PlayerBackendKind.Vlc -> {
                    if (vlcPlayer?.isPlaying == true) {
                        vlcPlayer.pause()
                    } else {
                        vlcPlayer?.play()
                    }
                }

                PlayerBackendKind.Exo -> {
                    if (exoPlayer?.isPlaying == true) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer?.play()
                    }
                }
            }
        }
    }

    fun revealControls() {
        controlsVisible = true
    }

    fun switchBackendIfNeeded(
        targetBackend: PlayerBackendKind,
        trigger: String,
    ): Boolean {
        if (experimentalDualBackendRace && !raceResolved) {
            promoteRaceBackend(targetBackend, trigger)
            return true
        }
        if (activeBackendKind == targetBackend || targetBackend.name in attemptedBackendKinds) return false
        Log.w(
            PlayerDebugTag,
            "switch backend title=$title mediaId=$mediaId trigger=$trigger from=$activeBackendKind to=$targetBackend option=${activeStreamOption.id} url=${activeStreamOption.streamUrl}",
        )
        PlaybackBackendMemory.forget(backendMemoryKey, activeBackendKind)
        activeBackendKindName = targetBackend.name
        attemptedBackendKinds = attemptedBackendKinds + targetBackend.name
        compatibilityPlaybackUrl = if (targetBackend == PlayerBackendKind.Vlc) {
            resolvedPlaybackUrl.takeIf { candidate ->
                candidate.isNotBlank() && candidate != activeStreamOption.streamUrl
            }
        } else {
            null
        }
        capturePlaybackState()
        resumePlayWhenReady = true
        forceVlcCompatibilityBackend = targetBackend == PlayerBackendKind.Vlc
        forceExoStandardBackend = targetBackend == PlayerBackendKind.Exo
        forceMpvBackend = targetBackend == PlayerBackendKind.Mpv
        hasRenderedFirstFrame = false
        bitrateEstimateBitsPerSecond = 0L
        autoFallbackInProgress = false
        return true
    }

    fun maybeSwitchBackendAfterFailure(trigger: String): Boolean {
        val targetBackend = automaticBackendOrder.firstOrNull { backend ->
            backend != activeBackendKind && backend.name !in attemptedBackendKinds
        } ?: return false
        return switchBackendIfNeeded(targetBackend, trigger)
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
            showAudioSheet = false
        }
        revealControls()
    }

    BackHandler {
        when {
            controlsLocked -> {
                controlsLocked = false
                revealControls()
            }

            showTrackSheet || showRuntimeSheet || showSubtitleSheet || showAudioSheet -> {
                showTrackSheet = false
                showRuntimeSheet = false
                showSubtitleSheet = false
                showAudioSheet = false
                revealControls()
            }

            else -> {
                onClose(
                    currentPositionSnapshot(),
                    durationSnapshot(),
                )
            }
        }
    }

    fun attemptAutoFallback(trigger: String): Boolean {
        if (autoFallbackInProgress) {
            return true
        }
        val currentIndex = streamOptions.indexOfFirst { it.id == activeStreamOption.id }
        val orderedCandidates = buildList {
            if (currentIndex >= 0) {
                addAll(streamOptions.drop(currentIndex + 1))
                addAll(streamOptions.take(currentIndex))
            } else {
                addAll(streamOptions)
            }
        }.filter { option ->
            option.id != activeStreamOption.id && option.id !in failedStreamOptionIds
        }
        val directPriorityIds = setOf("server-direct", "emby-direct")
        val fallbackCandidates = buildList {
            addAll(orderedCandidates.filter { it.id in directPriorityIds })
            addAll(orderedCandidates.filter { it.id !in directPriorityIds })
        }.distinctBy { it.id }
        val nextOption = fallbackCandidates.firstOrNull() ?: return false

        autoFallbackInProgress = true
        failedStreamOptionIds = failedStreamOptionIds + activeStreamOption.id
        Log.w(
            PlayerDebugTag,
            "auto-fallback title=$title mediaId=$mediaId trigger=$trigger from=${activeStreamOption.id} to=${nextOption.id} entry=${activeStreamOption.streamUrl} resolved=$resolvedPlaybackUrl",
        )
        capturePlaybackState()
        resumePlayWhenReady = true
        selectedStreamOptionId = nextOption.id
        mainHandler.post {
            Toast.makeText(
                context.applicationContext,
                "当前片源起播异常，已切到${nextOption.label}",
                Toast.LENGTH_SHORT,
            ).show()
        }
        return true
    }

    fun snapshotExoRuntimeSubtitleTracks(tracks: Tracks): List<ExoRuntimeSubtitleTrack> {
        return tracks.groups.flatMapIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) {
                return@flatMapIndexed emptyList()
            }
            buildList {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        ExoRuntimeSubtitleTrack(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = format.label
                                ?: format.language
                                ?: format.sampleMimeType
                                ?: "字幕 ${trackIndex + 1}",
                            language = format.language,
                            isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0,
                        ),
                    )
                }
            }
        }
    }

    fun snapshotExoRuntimeAudioTracks(tracks: Tracks): List<ExoRuntimeAudioTrack> {
        return tracks.groups.flatMapIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) {
                return@flatMapIndexed emptyList()
            }
            buildList {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        ExoRuntimeAudioTrack(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            label = format.label
                                ?: format.language
                                ?: format.sampleMimeType
                                ?: "音轨 ${trackIndex + 1}",
                            language = format.language,
                            isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0,
                        ),
                    )
                }
            }
        }
    }

    fun applyExoSubtitleSelection() {
        val player = exoPlayer ?: return
        val selectedTrack = when (selectedSubtitleKey) {
            null -> autoSubtitleTracks.firstOrNull()
            SUBTITLE_OFF -> null
            else -> availableSubtitleTracks.firstOrNull { it.key == selectedSubtitleKey }
        }
        val builder = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (selectedSubtitleKey == SUBTITLE_OFF) {
            player.trackSelectionParameters = builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        val groupIndex = selectedTrack?.exoGroupIndex
        val trackIndex = selectedTrack?.exoTrackIndex
        if (groupIndex != null && trackIndex != null) {
            val group = player.currentTracks.groups.getOrNull(groupIndex)?.mediaTrackGroup
            if (group != null) {
                player.trackSelectionParameters = builder
                    .addOverride(TrackSelectionOverride(group, trackIndex))
                    .build()
                return
            }
        }
        val preferredLanguage = selectedTrack?.language
        player.trackSelectionParameters = if (!preferredLanguage.isNullOrBlank()) {
            builder.setPreferredTextLanguage(preferredLanguage).build()
        } else {
            builder.setSelectUndeterminedTextLanguage(true).build()
        }
    }

    fun applyExoAudioSelection() {
        val player = exoPlayer ?: return
        val selectedTrack = when (selectedAudioKey) {
            null -> autoAudioTracks.firstOrNull()
            else -> availableAudioTracks.firstOrNull { it.key == selectedAudioKey }
        }
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        val groupIndex = selectedTrack?.exoGroupIndex
        val trackIndex = selectedTrack?.exoTrackIndex
        if (groupIndex != null && trackIndex != null) {
            val group = player.currentTracks.groups.getOrNull(groupIndex)?.mediaTrackGroup
            if (group != null) {
                player.trackSelectionParameters = builder
                    .addOverride(TrackSelectionOverride(group, trackIndex))
                    .build()
                return
            }
        }
        val preferredLanguage = selectedTrack?.language
        player.trackSelectionParameters = if (!preferredLanguage.isNullOrBlank()) {
            builder.setPreferredAudioLanguage(preferredLanguage).build()
        } else {
            builder.setPreferredAudioLanguages().build()
        }
    }

    val keepExoSession = exoPlayer != null &&
        (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Exo)
    val keepVlcSession = vlcPlayer != null &&
        (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Vlc)
    val keepMpvSession = mpvPlayer != null &&
        (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Mpv)

    LaunchedEffect(exoPlayer, vlcPlayer, mpvPlayer, experimentalDualBackendRace, raceResolved, activeBackendKind) {
        exoPlayer?.volume = when {
            experimentalDualBackendRace && !raceResolved -> 0f
            activeBackendKind == PlayerBackendKind.Exo -> 1f
            else -> 0f
        }
        mpvPlayer?.setVolume(
            when {
                activeBackendKind == PlayerBackendKind.Mpv -> 100
                else -> 0
            },
        )
        vlcPlayer?.setVolume(
            when {
                experimentalDualBackendRace && !raceResolved -> 0
                activeBackendKind == PlayerBackendKind.Vlc -> 100
                else -> 0
            },
        )
    }

    LaunchedEffect(
        selectedAudioKey,
        activeAudioTrack,
        selectedSubtitleKey,
        activeSubtitleTracks,
        sourceExternalSubtitleTracks,
        exoPlayer,
        mpvPlayer,
        vlcPlayer,
        activeBackendKind,
        raceInProgress,
    ) {
        mpvPlayer?.updateAudioSelection(activeAudioTrack?.mpvTrackId)
        mpvPlayer?.updateSubtitleSelection(
            trackId = activeSubtitleTracks.firstOrNull()?.mpvTrackId,
            disabled = selectedSubtitleKey == SUBTITLE_OFF,
        )
        vlcPlayer?.updateAudioSelection(activeAudioTrack?.vlcTrackId)
        vlcPlayer?.updateSubtitleSelection(
            trackId = activeSubtitleTracks.firstOrNull()?.vlcTrackId,
            disabled = selectedSubtitleKey == SUBTITLE_OFF,
        )
        applyExoAudioSelection()
        applyExoSubtitleSelection()
    }

    if (keepExoSession) {
        DisposableEffect(exoPlayer) {
            val player = exoPlayer
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    if (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Exo) {
                        isPlaying = playing
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Exo) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                        durationMs = duration.coerceAtLeast(0L)
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                        if (!isScrubbing) {
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                        }
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    exoRuntimeAudioTracks = snapshotExoRuntimeAudioTracks(tracks)
                    exoRuntimeSubtitleTracks = snapshotExoRuntimeSubtitleTracks(tracks)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(
                        PlayerDebugTag,
                        "player error title=$title mediaId=$mediaId option=${activeStreamOption.id} entry=${activeStreamOption.streamUrl} resolved=$resolvedPlaybackUrl",
                        error,
                    )
                    if (experimentalDualBackendRace && !raceResolved) {
                        promoteRaceBackend(
                            PlayerBackendKind.Vlc,
                            error.errorCodeName.ifBlank { "exo-error" },
                        )
                        return
                    }
                    if (
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.cause?.javaClass?.simpleName == "UnrecognizedInputFormatException"
                    ) {
                        if (maybeSwitchBackendAfterFailure(error.errorCodeName.ifBlank { "容器不支持" })) {
                            return
                        }
                        return
                    }
                    if (attemptAutoFallback(error.errorCodeName.ifBlank { "播放失败" })) {
                        return
                    }
                    if (activeBackendKind == PlayerBackendKind.Exo &&
                        maybeSwitchBackendAfterFailure(error.errorCodeName.ifBlank { "Exo异常" })
                    ) {
                        return
                    }
                    mainHandler.post {
                        Toast.makeText(
                            context.applicationContext,
                            "当前片源暂时无法播放，请切换播放方式再试",
                            Toast.LENGTH_SHORT,
                        ).show()
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

                    override fun onLoadError(
                        eventTime: AnalyticsListener.EventTime,
                        loadEventInfo: LoadEventInfo,
                        mediaLoadData: MediaLoadData,
                        error: java.io.IOException,
                        wasCanceled: Boolean,
                    ) {
                        Log.w(
                            PlayerDebugTag,
                            "load error title=$title mediaId=$mediaId option=${activeStreamOption.id} canceled=$wasCanceled uri=${loadEventInfo.uri}",
                            error,
                        )
                    }

                    override fun onAudioCodecError(
                        eventTime: AnalyticsListener.EventTime,
                        audioCodecError: Exception,
                    ) {
                        Log.e(
                            PlayerDebugTag,
                            "audio codec error title=$title mediaId=$mediaId option=${activeStreamOption.id} resolved=$resolvedPlaybackUrl",
                            audioCodecError,
                        )
                    }

                    override fun onVideoCodecError(
                        eventTime: AnalyticsListener.EventTime,
                        videoCodecError: Exception,
                    ) {
                        Log.e(
                            PlayerDebugTag,
                            "video codec error title=$title mediaId=$mediaId option=${activeStreamOption.id} resolved=$resolvedPlaybackUrl",
                            videoCodecError,
                        )
                    }

                    override fun onRenderedFirstFrame(
                        eventTime: AnalyticsListener.EventTime,
                        output: Any,
                        renderTimeMs: Long,
                    ) {
                        registerBackendFirstFrame(
                            PlayerBackendKind.Exo,
                            renderTimeMs,
                        )
                        Log.i(
                            PlayerDebugTag,
                            "first-frame title=$title mediaId=$mediaId option=${activeStreamOption.id} resolved=$resolvedPlaybackUrl renderTimeMs=$renderTimeMs backend=Exo",
                        )
                    }
                }
            player.addListener(listener)
            player.addAnalyticsListener(analyticsListener)
            exoRuntimeAudioTracks = snapshotExoRuntimeAudioTracks(player.currentTracks)
            exoRuntimeSubtitleTracks = snapshotExoRuntimeSubtitleTracks(player.currentTracks)
            onDispose {
                capturePlaybackState()
                player.removeListener(listener)
                player.removeAnalyticsListener(analyticsListener)
                player.release()
            }
        }
    }

    if (keepVlcSession) {
        DisposableEffect(vlcPlayer) {
            val player = vlcPlayer
            onDispose {
                capturePlaybackState()
                player.release()
            }
        }
    }

    if (keepMpvSession) {
        DisposableEffect(mpvPlayer) {
            val player = mpvPlayer
            onDispose {
                capturePlaybackState()
                player.release()
            }
        }
    }

    DisposableEffect(mediaId, source.mediaSourceId, source.playSessionId) {
        onDispose {
            reportPlaybackStoppedIfNeeded()
        }
    }

    LaunchedEffect(
        hasRenderedFirstFrame,
        raceInProgress,
        activeBackendKind,
        mediaId,
        source.mediaSourceId,
        source.playSessionId,
    ) {
        if (hasRenderedFirstFrame && !raceInProgress) {
            reportPlaybackStartedIfNeeded()
        }
    }

    LaunchedEffect(
        playbackStartedReported,
        playbackStoppedReported,
        mediaId,
        source.mediaSourceId,
        source.playSessionId,
    ) {
        if (!playbackStartedReported || playbackStoppedReported) {
            return@LaunchedEffect
        }
        while (playbackStartedReported && !playbackStoppedReported) {
            delay(10_000)
            reportPlaybackProgressEvent(eventName = "TimeUpdate")
        }
    }

    LaunchedEffect(activeStreamOption.id) {
        autoFallbackInProgress = false
        attemptedBackendKinds = setOf(initialBackendKind.name)
        hasRenderedFirstFrame = false
        bitrateEstimateBitsPerSecond = 0L
        redirectProbeState = RedirectProbeState.Idle
        resolvedPlaybackUrl = activeStreamOption.streamUrl
        compatibilityPlaybackUrl = null
        activeBackendKindName = initialBackendKind.name
        raceResolved = !experimentalDualBackendRace
        exoFirstFrameAtMs = 0L
        vlcFirstFrameAtMs = 0L
        mpvFirstFrameAtMs = 0L
        Log.i(
            PlayerDebugTag,
            "starting title=$title mediaId=$mediaId option=${activeStreamOption.id} entry=${activeStreamOption.streamUrl}",
        )
    }

    LaunchedEffect(
        experimentalDualBackendRace,
        activeStreamOption.id,
        preferredBackendKind,
        hasRenderedFirstFrame,
        raceResolved,
    ) {
        if (!experimentalDualBackendRace) {
            exoCompanionArmed = preferredBackendKind == PlayerBackendKind.Exo
            return@LaunchedEffect
        }
        if (hasRenderedFirstFrame || raceResolved) {
            return@LaunchedEffect
        }
        exoCompanionArmed = false
        delay(exoCompanionDelayMs)
        if (
            hasRenderedFirstFrame ||
            raceResolved ||
            !shouldExpectPlaybackToStart()
        ) {
            return@LaunchedEffect
        }
        Log.i(
            PlayerDebugTag,
            "arm exo companion title=$title mediaId=$mediaId option=${activeStreamOption.id} delayMs=$exoCompanionDelayMs",
        )
        exoCompanionArmed = true
    }

    LaunchedEffect(activeStreamOption.id, activeStreamOption.streamUrl, activeStreamOption.requestHeaders) {
        val streamUrl = activeStreamOption.streamUrl.trim()
        if (!shouldProbeRedirectState(activeStreamOption.id, streamUrl)) {
            redirectProbeState = RedirectProbeState.Skipped("当前链路不是 302 入口")
            return@LaunchedEffect
        }
        redirectProbeState = RedirectProbeState.Loading
        redirectProbeState = probeRedirectState(
            url = streamUrl,
            requestHeaders = activeStreamOption.requestHeaders,
        )
    }

    LaunchedEffect(
        preferredBackendKind,
        activeBackendKind,
        raceInProgress,
        exoPlayer,
        mpvPlayer,
        vlcPlayer,
        activeStreamOption.id,
    ) {
        val startupTimeoutMs = 12_000L
        delay(startupTimeoutMs)
        if (
            !hasRenderedFirstFrame &&
            shouldExpectPlaybackToStart() &&
            playbackStateSnapshot() != Player.STATE_ENDED
        ) {
            val bytesRead = playbackTrafficTracker.totalBytesRead()
            val bypassManagedFallbackAfterTimeout = shouldBypassManagedFallbackAfterTimeout(
                currentOptionId = activeStreamOption.id,
                currentOptionUrl = activeStreamOption.streamUrl,
                resolvedPlaybackUrl = resolvedPlaybackUrl,
                streamOptions = streamOptions,
            )
            if (bytesRead > 0L) {
                val extraWaitMs = if (bypassManagedFallbackAfterTimeout) 6_000L else 18_000L
                Log.i(
                    PlayerDebugTag,
                    "startup still flowing title=$title mediaId=$mediaId option=${activeStreamOption.id} bytes=$bytesRead extraWaitMs=$extraWaitMs entry=${activeStreamOption.streamUrl} resolved=$resolvedPlaybackUrl",
                )
                delay(extraWaitMs)
                if (
                    hasRenderedFirstFrame ||
                    !shouldExpectPlaybackToStart() ||
                    playbackStateSnapshot() == Player.STATE_ENDED
                ) {
                    return@LaunchedEffect
                }
            }
            if (activeBackendKind == PlayerBackendKind.Exo && sourceRequiresCompatibilityBackend) {
                if (maybeSwitchBackendAfterFailure("老封装起播超时")) {
                    return@LaunchedEffect
                }
            }
            if (activeBackendKind == PlayerBackendKind.Exo && bypassManagedFallbackAfterTimeout) {
                Log.w(
                    PlayerDebugTag,
                    "skip managed fallback after timeout title=$title mediaId=$mediaId option=${activeStreamOption.id} entry=${activeStreamOption.streamUrl} resolved=$resolvedPlaybackUrl",
                )
                if (maybeSwitchBackendAfterFailure("外链直链Exo起播超时")) {
                    return@LaunchedEffect
                }
            }
            Log.w(
                PlayerDebugTag,
                "startup timeout title=$title mediaId=$mediaId option=${activeStreamOption.id} backend=$activeBackendKind race=$raceInProgress state=${playbackStateSnapshot()} bytes=${playbackTrafficTracker.totalBytesRead()} entry=${activeStreamOption.streamUrl} resolved=$resolvedPlaybackUrl",
            )
            if (attemptAutoFallback("起播超时")) {
                return@LaunchedEffect
            }
            if (maybeSwitchBackendAfterFailure("${activeBackendLabel}起播超时")) {
                return@LaunchedEffect
            }
            if (activeBackendKind == PlayerBackendKind.Exo) {
                if (
                    switchBackendIfNeeded(
                        PlayerBackendKind.Vlc,
                        if (sourceRequiresCompatibilityBackend) "老封装起播超时" else "Exo起播超时",
                    )
                ) {
                    return@LaunchedEffect
                }
            }
            mainHandler.post {
                Toast.makeText(
                    context.applicationContext,
                    "当前片源长时间未起播，请切换播放方式再试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(
        activeBackendKind,
        raceInProgress,
        exoPlayer,
        vlcPlayer,
        playbackTrafficTracker,
        activeStreamOption.id,
    ) {
        var previousBytesRead = playbackTrafficTracker.totalBytesRead()
        var previousSampleTimeMs = SystemClock.elapsedRealtime()
        var smoothedBitrateBitsPerSecond = 0L
        var lastNonZeroSampleTimeMs = previousSampleTimeMs
        while (true) {
            mpvPlayer?.syncState()
            vlcPlayer?.syncState()
            if (mpvPlayer?.hasRenderedFirstFrame == true && mpvFirstFrameAtMs == 0L) {
                registerBackendFirstFrame(PlayerBackendKind.Mpv, null)
            }
            if (vlcPlayer?.hasRenderedFirstFrame == true && vlcFirstFrameAtMs == 0L) {
                registerBackendFirstFrame(PlayerBackendKind.Vlc, null)
            }
            if (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Mpv) {
                if (activeBackendKind == PlayerBackendKind.Mpv) {
                    durationMs = durationSnapshot()
                    bufferedPositionMs = bufferedPositionSnapshot()
                    isPlaying = isPlayingSnapshot()
                    isBuffering = isBufferingSnapshot()
                    if (!isScrubbing) {
                        currentPositionMs = currentPositionSnapshot()
                    }
                    bitrateEstimateBitsPerSecond = mpvPlayer?.bitrateEstimateBitsPerSecond?.coerceAtLeast(0L) ?: 0L
                    resolvedPlaybackUrl = mpvPlayer?.resolvedPlaybackUrl?.ifBlank { activeStreamOption.streamUrl }
                        ?: activeStreamOption.streamUrl
                    hasRenderedFirstFrame = mpvFirstFrameAtMs > 0L
                }
                mpvPlayer?.consumePendingError()?.let { errorLabel ->
                    if (attemptAutoFallback(errorLabel)) {
                        Unit
                    } else if (maybeSwitchBackendAfterFailure(errorLabel)) {
                        Unit
                    } else {
                        mainHandler.post {
                            Toast.makeText(
                                context.applicationContext,
                                "当前片源暂时无法播放，请切换播放方式再试",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
            if (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Vlc) {
                if (activeBackendKind == PlayerBackendKind.Vlc) {
                    durationMs = durationSnapshot()
                    bufferedPositionMs = bufferedPositionSnapshot()
                    isPlaying = isPlayingSnapshot()
                    isBuffering = isBufferingSnapshot()
                    if (!isScrubbing) {
                        currentPositionMs = currentPositionSnapshot()
                    }
                    bitrateEstimateBitsPerSecond = vlcPlayer?.bitrateEstimateBitsPerSecond?.coerceAtLeast(0L) ?: 0L
                    resolvedPlaybackUrl = vlcPlayer?.resolvedPlaybackUrl?.ifBlank { activeStreamOption.streamUrl }
                        ?: activeStreamOption.streamUrl
                    hasRenderedFirstFrame = vlcFirstFrameAtMs > 0L
                }
                vlcPlayer?.consumePendingError()?.let { errorLabel ->
                    if (experimentalDualBackendRace && !raceResolved) {
                        promoteRaceBackend(PlayerBackendKind.Exo, errorLabel)
                    } else if (attemptAutoFallback(errorLabel)) {
                        Unit
                    } else if (maybeSwitchBackendAfterFailure(errorLabel)) {
                        Unit
                    } else {
                        mainHandler.post {
                            Toast.makeText(
                                context.applicationContext,
                                "当前片源暂时无法播放，请切换播放方式再试",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }

            val player = exoPlayer
            if (player != null) {
                if (!experimentalDualBackendRace || raceInProgress || activeBackendKind == PlayerBackendKind.Exo) {
                    val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    durationMs = duration.coerceAtLeast(0L)
                    bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                    isPlaying = player.isPlaying
                    isBuffering = player.playbackState == Player.STATE_BUFFERING
                    if (!isScrubbing) {
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                    }
                    hasRenderedFirstFrame = exoFirstFrameAtMs > 0L
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
                    if (activeBackendKind == PlayerBackendKind.Exo || raceInProgress) {
                        bitrateEstimateBitsPerSecond = smoothedBitrateBitsPerSecond
                    }
                    lastNonZeroSampleTimeMs = nowMs
                } else if (nowMs - lastNonZeroSampleTimeMs > 1_500L) {
                    smoothedBitrateBitsPerSecond = 0L
                    if (activeBackendKind == PlayerBackendKind.Exo || raceInProgress) {
                        bitrateEstimateBitsPerSecond = 0L
                    }
                }
                previousBytesRead = totalBytesRead
                previousSampleTimeMs = nowMs
            }
            delay(250)
        }
    }

    LaunchedEffect(activeBackendKind, raceInProgress, exoPlayer, vlcPlayer, mpvPlayer, playbackSpeed) {
        if (raceInProgress) {
            vlcPlayer?.setPlaybackSpeed(currentPlaybackSpeedTarget())
            exoPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeedTarget())
        } else {
            when (activeBackendKind) {
                PlayerBackendKind.Mpv -> mpvPlayer?.setPlaybackSpeed(currentPlaybackSpeedTarget())
                PlayerBackendKind.Vlc -> vlcPlayer?.setPlaybackSpeed(currentPlaybackSpeedTarget())
                PlayerBackendKind.Exo -> exoPlayer?.playbackParameters = PlaybackParameters(currentPlaybackSpeedTarget())
            }
        }
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

    LaunchedEffect(controlsVisible, controlsLocked, isPlaying, showTrackSheet, showRuntimeSheet, showSubtitleSheet, showAudioSheet) {
        if (
            controlsVisible &&
            !controlsLocked &&
            isPlaying &&
            !showTrackSheet &&
            !showRuntimeSheet &&
            !showSubtitleSheet &&
            !showAudioSheet
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

    val exoViewVisibility = if (raceInProgress || activeBackendKind == PlayerBackendKind.Exo) {
        View.VISIBLE
    } else {
        View.GONE
    }
    val vlcViewVisibility = if (raceInProgress || activeBackendKind == PlayerBackendKind.Vlc) {
        View.VISIBLE
    } else {
        View.GONE
    }
    val mpvViewVisibility = if (raceInProgress || activeBackendKind == PlayerBackendKind.Mpv) {
        View.VISIBLE
    } else {
        View.GONE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (keepExoSession) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (raceInProgress) 0f else if (activeBackendKind == PlayerBackendKind.Exo) 1f else 0f
                    },
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        visibility = exoViewVisibility
                        setResizeMode(scaleMode.resizeMode)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        player = requireNotNull(exoPlayer)
                    }
                },
                update = { playerView ->
                    playerView.visibility = exoViewVisibility
                    playerView.setResizeMode(scaleMode.resizeMode)
                    playerView.setBackgroundColor(android.graphics.Color.BLACK)
                    playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
                    playerView.player = exoPlayer
                },
            )
        }

        if (keepVlcSession) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (raceInProgress) 0f else if (activeBackendKind == PlayerBackendKind.Vlc) 1f else 0f
                    },
                factory = { requireNotNull(vlcPlayer).view },
                update = { playerView ->
                    playerView.visibility = vlcViewVisibility
                },
            )
        }

        if (keepMpvSession) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (raceInProgress) 0f else if (activeBackendKind == PlayerBackendKind.Mpv) 1f else 0f
                    },
                factory = { requireNotNull(mpvPlayer).view },
                update = { playerView ->
                    playerView.visibility = mpvViewVisibility
                },
            )
        }

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
                            showAudioSheet = false
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
                            seekBasePosition = currentPositionSnapshot()
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
                                seekToPlayback(sliderPositionMs.toLong())
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
                detail = formatBitrate(bitrateEstimateBitsPerSecond),
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
                    PlayerTopBar(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(
                                start = 18.dp,
                                top = 14.dp,
                                end = 18.dp + landscapeNavBarRightPadding,
                                bottom = 14.dp,
                            ),
                        title = headerTitle,
                        titleLogoUrl = headerLogoUrl,
                        compact = isLandscapeFullscreen || isLandscapeLayout,
                        onClose = {
                            onClose(
                                currentPositionSnapshot(),
                                durationSnapshot(),
                            )
                        },
                    )

                    PlayerSidePills(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = landscapeNavBarRightPadding),
                        subtitleChoiceLabel = subtitleChoiceLabel,
                        audioChoiceLabel = audioChoiceLabel,
                        playbackSpeed = playbackSpeed,
                        isLandscapeFullscreen = isLandscapeFullscreen,
                        onToggleLock = {
                            controlsLocked = true
                            controlsVisible = false
                            showTrackSheet = false
                            showRuntimeSheet = false
                            showSubtitleSheet = false
                            showAudioSheet = false
                        },
                        onToggleSubtitleSheet = {
                            showTrackSheet = false
                            showRuntimeSheet = false
                            showSubtitleSheet = !showSubtitleSheet
                            showAudioSheet = false
                        },
                        onToggleAudioSheet = {
                            showTrackSheet = false
                            showRuntimeSheet = false
                            showAudioSheet = !showAudioSheet
                            showSubtitleSheet = false
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
                            reportPlaybackProgressEvent("PlaybackRateChange")
                            revealControls()
                        },
                        onIncreaseSpeed = {
                            playbackSpeed = (playbackSpeed + 0.25f).coerceAtMost(2.0f)
                            reportPlaybackProgressEvent("PlaybackRateChange")
                            revealControls()
                        },
                    )

                    AnimatedVisibility(
                        visible = showTrackSheet || showRuntimeSheet || showSubtitleSheet || showAudioSheet,
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
                                    sourceTracks = availableSubtitleTracks,
                                    autoTracks = autoSubtitleTracks,
                                    selectedSubtitleKey = selectedSubtitleKey,
                                ),
                                onSelectSubtitle = { targetKey ->
                                    selectedSubtitleKey = targetKey
                                    reportPlaybackProgressEvent("SubtitleTrackChange")
                                    showSubtitleSheet = false
                                    revealControls()
                                },
                            )

                            showAudioSheet -> PlayerSubtitleSheet(
                                subtitleOptions = buildAudioOptions(
                                    sourceTracks = availableAudioTracks,
                                    autoTracks = autoAudioTracks,
                                    selectedAudioKey = selectedAudioKey,
                                ),
                                onSelectSubtitle = { targetKey ->
                                    selectedAudioKey = targetKey
                                    reportPlaybackProgressEvent("AudioTrackChange")
                                    showAudioSheet = false
                                    revealControls()
                                },
                            )

                            showTrackSheet -> PlayerTrackSheet(
                                infoLine = source.infoLine.ifBlank { "当前媒体流" },
                                title = title,
                                subtitleChoiceLabel = subtitleChoiceLabel,
                                audioChoiceLabel = audioChoiceLabel,
                                runtimeLabel = runtimeProfile.label,
                                backendLabel = activeBackendLabel,
                                streamChoiceLabel = streamChoiceLabel,
                                bitrateLabel = formatBitrate(bitrateEstimateBitsPerSecond),
                                redirectProbeState = redirectProbeState,
                                streamOptions = streamOptions,
                                entryPlaybackUrl = activeStreamOption.streamUrl,
                                currentPlaybackUrl = resolvedPlaybackUrl,
                                infoFields = source.infoFields,
                            )

                            showRuntimeSheet -> PlayerRuntimeSheet(
                                runtimeLabel = runtimeProfile.label,
                                backendLabel = activeBackendLabel,
                                streamOptions = streamOptions,
                                selectedStreamOptionId = activeStreamOption.id,
                                onSelectStreamOption = { optionId ->
                                    if (optionId == activeStreamOption.id) return@PlayerRuntimeSheet
                                    capturePlaybackState()
                                    selectedStreamOptionId = optionId
                                    reportPlaybackProgressEvent("QualityChange")
                                    revealControls()
                                },
                                bitrateLabel = formatBitrate(bitrateEstimateBitsPerSecond),
                                redirectProbeState = redirectProbeState,
                                entryPlaybackUrl = activeStreamOption.streamUrl,
                                currentPlaybackUrl = resolvedPlaybackUrl,
                            )
                        }
                    }

                    if (
                        !episodeTitle.isNullOrBlank() &&
                        !showTrackSheet &&
                        !showRuntimeSheet &&
                        !showSubtitleSheet &&
                        !showAudioSheet
                    ) {
                        PlayerEpisodeTitleOverlay(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .playerBottomOverlayInsets(isLandscapeFullscreen || isLandscapeLayout)
                                .padding(
                                    start = 28.dp,
                                    end = 28.dp + landscapeNavBarRightPadding,
                                    bottom = if (isLandscapeFullscreen || isLandscapeLayout) 86.dp else 148.dp,
                                ),
                            title = episodeTitle,
                            compact = isLandscapeFullscreen || isLandscapeLayout,
                        )
                    }

                    PlayerBottomControls(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .playerBottomOverlayInsets(isLandscapeFullscreen || isLandscapeLayout)
                            .padding(
                                start = 24.dp,
                                top = if (isLandscapeFullscreen || isLandscapeLayout) 4.dp else 18.dp,
                                end = 24.dp + landscapeNavBarRightPadding,
                                bottom = if (isLandscapeFullscreen || isLandscapeLayout) 0.dp else 10.dp,
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
                            seekToPlayback(sliderPositionMs.toLong())
                            reportPlaybackProgressEvent("TimeUpdate", sliderPositionMs.toLong())
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
                            seekBackPlayback()
                            reportPlaybackProgressEvent("TimeUpdate")
                            revealControls()
                        },
                        onPlayPause = {
                            val pauseEvent = if (isPlayingSnapshot()) "Pause" else "Unpause"
                            togglePlayPausePlayback()
                            reportPlaybackProgressEvent(pauseEvent)
                            revealControls()
                        },
                        onSeekForward = {
                            seekForwardPlayback()
                            reportPlaybackProgressEvent("TimeUpdate")
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

private enum class PlayerBackendKind {
    Exo,
    Mpv,
    Vlc,
}

private data class PlayerRuntimeProfile(
    val backendKind: PlayerBackendKind,
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
    val targetKey: String?,
    val selected: Boolean,
)

private data class PlayerSubtitleTrack(
    val key: String,
    val serverIndex: Int?,
    val label: String,
    val language: String?,
    val url: String?,
    val mimeType: String?,
    val isDefault: Boolean,
    val isExternal: Boolean,
    val mpvTrackId: Int? = null,
    val vlcTrackId: Int? = null,
    val exoGroupIndex: Int? = null,
    val exoTrackIndex: Int? = null,
)

private data class ExoRuntimeSubtitleTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isDefault: Boolean,
)

private data class PlayerAudioTrack(
    val key: String,
    val serverIndex: Int?,
    val label: String,
    val language: String?,
    val codec: String?,
    val channels: Int?,
    val isDefault: Boolean,
    val mpvTrackId: Int? = null,
    val vlcTrackId: Int? = null,
    val exoGroupIndex: Int? = null,
    val exoTrackIndex: Int? = null,
)

private data class ExoRuntimeAudioTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isDefault: Boolean,
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

private const val SUBTITLE_OFF = "__off__"

private fun Modifier.playerBottomOverlayInsets(
    isLandscapeFullscreen: Boolean,
): Modifier = if (isLandscapeFullscreen) this else navigationBarsPadding()

@Composable
private fun PlayerTopTitleOverlay(
    title: String,
    titleLogoUrl: String?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = if (compact) 180.dp else 240.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (!titleLogoUrl.isNullOrBlank()) {
            AsyncImage(
                model = titleLogoUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 18.dp, max = if (compact) 26.dp else 34.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        } else if (title.isNotBlank()) {
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                }.copy(fontFamily = AppTitleFontFamily),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerEpisodeTitleOverlay(
    title: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.widthIn(max = if (compact) 260.dp else 320.dp),
        style = if (compact) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.titleSmall
        },
        color = Color.White.copy(alpha = 0.94f),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PlayerTopBar(
    modifier: Modifier = Modifier,
    title: String,
    titleLogoUrl: String?,
    compact: Boolean,
    onClose: () -> Unit,
) {
    val reservedEndSpace = if (compact) 96.dp else 124.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(PlayerPanelColor)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            PlayerOverlayIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onClose,
            )
        }

        if (title.isNotBlank() || !titleLogoUrl.isNullOrBlank()) {
            PlayerTopTitleOverlay(
                title = title,
                titleLogoUrl = titleLogoUrl,
                compact = compact,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = reservedEndSpace),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayerSidePills(
    modifier: Modifier = Modifier,
    subtitleChoiceLabel: String,
    audioChoiceLabel: String,
    playbackSpeed: Float,
    isLandscapeFullscreen: Boolean,
    onToggleLock: () -> Unit,
    onToggleSubtitleSheet: () -> Unit,
    onToggleAudioSheet: () -> Unit,
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
                icon = Icons.Rounded.Audiotrack,
                contentDescription = audioChoiceLabel,
                onClick = onToggleAudioSheet,
            )
            PlayerOverlayIconButton(
                icon = Icons.Rounded.Lock,
                contentDescription = "锁定控制",
                onClick = onToggleLock,
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
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xE0101010))
            .padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
    ) {
        PlayerProgressBar(
            progressValue = progressValue,
            bufferedFraction = bufferedFraction,
            durationMs = safeDuration,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onScrubStop = onScrubStop,
            modifier = Modifier.fillMaxWidth(),
            trackHeight = if (compact) 10.dp else 12.dp,
        )

        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlaybackTime(currentPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Text(
                    text = "-${formatPlaybackTime((durationMs - currentPositionMs).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodySmall,
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
                containerSize = if (compact) 38.dp else 34.dp,
                iconSize = if (compact) 20.dp else 18.dp,
                onClick = onOpenInfoSheet,
            )
            PlayerBottomButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "后退10秒",
                containerSize = if (compact) 38.dp else 34.dp,
                iconSize = if (compact) 20.dp else 18.dp,
                onClick = onSeekBack,
            )
            PlayerBottomButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                containerSize = if (compact) 48.dp else 44.dp,
                iconSize = if (compact) 24.dp else 22.dp,
                onClick = onPlayPause,
            )
            PlayerBottomButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "前进10秒",
                containerSize = if (compact) 38.dp else 34.dp,
                iconSize = if (compact) 20.dp else 18.dp,
                onClick = onSeekForward,
            )
            PlayerBottomButton(
                icon = Icons.Rounded.PlayCircle,
                contentDescription = "播放方式",
                containerSize = if (compact) 38.dp else 34.dp,
                iconSize = if (compact) 20.dp else 18.dp,
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
    audioChoiceLabel: String,
    runtimeLabel: String,
    backendLabel: String,
    streamChoiceLabel: String,
    bitrateLabel: String?,
    redirectProbeState: RedirectProbeState,
    streamOptions: List<EmbyPlaybackStreamOption>,
    entryPlaybackUrl: String,
    currentPlaybackUrl: String,
    infoFields: List<EmbyPlaybackInfoField>,
) {
    val routeState = remember(entryPlaybackUrl, currentPlaybackUrl, streamOptions, redirectProbeState) {
        resolvePlaybackRouteState(
            entryPlaybackUrl = entryPlaybackUrl,
            currentPlaybackUrl = currentPlaybackUrl,
            streamOptions = streamOptions,
            redirectProbeState = redirectProbeState,
        )
    }
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
        PlayerInfoFieldRow(label = "当前音轨", value = audioChoiceLabel)
        PlayerInfoFieldRow(label = "当前字幕", value = subtitleChoiceLabel)
        PlayerInfoFieldRow(label = "播放策略", value = runtimeLabel)
        PlayerInfoFieldRow(label = "播放内核", value = backendLabel)
        PlayerInfoFieldRow(label = "串流方式", value = streamChoiceLabel)
        PlayerInfoFieldRow(label = "链路状态", value = routeState.routeLabel)
        PlayerInfoFieldRow(label = "传输路径", value = routeState.transportLabel)
        bitrateLabel?.let { PlayerInfoFieldRow(label = "码率", value = it) }
        routeState.technicalLabel?.let { label ->
            PlayerInfoFieldRow(label = "入口重定向", value = label)
        }
        routeState.currentUrl?.let { currentUrl ->
            PlayerInfoFieldRow(label = "当前链接", value = currentUrl, multiline = true)
        }
        routeState.entryUrl?.takeIf { it != routeState.currentUrl }?.let { entryUrl ->
            PlayerInfoFieldRow(label = "请求入口", value = entryUrl, multiline = true)
        }
        infoFields.forEach { field ->
            PlayerInfoFieldRow(label = field.label, value = field.value)
        }
    }
}

@Composable
private fun PlayerRuntimeSheet(
    runtimeLabel: String,
    backendLabel: String,
    streamOptions: List<EmbyPlaybackStreamOption>,
    selectedStreamOptionId: String,
    onSelectStreamOption: (String) -> Unit,
    bitrateLabel: String?,
    redirectProbeState: RedirectProbeState,
    entryPlaybackUrl: String,
    currentPlaybackUrl: String,
) {
    val routeState = remember(entryPlaybackUrl, currentPlaybackUrl, streamOptions, redirectProbeState) {
        resolvePlaybackRouteState(
            entryPlaybackUrl = entryPlaybackUrl,
            currentPlaybackUrl = currentPlaybackUrl,
            streamOptions = streamOptions,
            redirectProbeState = redirectProbeState,
        )
    }
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
        PlayerInfoFieldRow(label = "播放内核", value = backendLabel)
        PlayerInfoFieldRow(label = "链路状态", value = routeState.routeLabel)
        PlayerInfoFieldRow(label = "传输路径", value = routeState.transportLabel)
        bitrateLabel?.let { PlayerInfoFieldRow(label = "码率", value = it) }
        routeState.technicalLabel?.let { label ->
            PlayerInfoFieldRow(label = "入口重定向", value = label)
        }
        PlayerInfoFieldRow(
            label = if (routeState.isExternalDirect) "当前直链" else "当前链接",
            value = routeState.currentUrl ?: entryPlaybackUrl,
            multiline = true,
        )
        if (routeState.entryUrl != null && routeState.entryUrl != routeState.currentUrl) {
            PlayerInfoFieldRow(
                label = "请求入口",
                value = routeState.entryUrl,
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
    onSelectSubtitle: (String?) -> Unit,
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
                onClick = { onSelectSubtitle(option.targetKey) },
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
                isBuffering && bitrateLabel != null -> "缓冲中 · $bitrateLabel"
                isBuffering -> "缓冲中 · 测量中"
                bitrateLabel != null -> bitrateLabel
                else -> "获取中"
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
    sourceTracks: List<PlayerSubtitleTrack>,
    autoTracks: List<PlayerSubtitleTrack>,
    selectedSubtitleKey: String?,
): List<SubtitleOption> {
    val autoLabel = autoTracks.firstOrNull { it.isDefault }?.label ?: "服务端自动匹配"

    return buildList {
        add(
            SubtitleOption(
                title = "自动",
                subtitle = autoLabel,
                targetKey = null,
                selected = selectedSubtitleKey == null,
            ),
        )
        add(
            SubtitleOption(
                title = "关闭字幕",
                subtitle = "不加载任何字幕轨",
                targetKey = SUBTITLE_OFF,
                selected = selectedSubtitleKey == SUBTITLE_OFF,
            ),
        )
        sourceTracks.forEach { track ->
            add(
                SubtitleOption(
                    title = track.label,
                    subtitle = buildString {
                        append(track.language ?: "未标记语言")
                        append(if (track.isExternal) " · 外挂" else " · 内嵌")
                    },
                    targetKey = track.key,
                    selected = selectedSubtitleKey == track.key,
                ),
            )
        }
    }
}

private fun formatSubtitleChoiceLabel(
    selectedSubtitleKey: String?,
    sourceTracks: List<PlayerSubtitleTrack>,
    autoTracks: List<PlayerSubtitleTrack>,
): String {
    return when (selectedSubtitleKey) {
        null -> autoTracks.firstOrNull { it.isDefault }?.label ?: "自动"
        SUBTITLE_OFF -> "关闭字幕"
        else -> sourceTracks.firstOrNull { it.key == selectedSubtitleKey }?.label ?: "字幕"
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

private fun shouldProbeRedirectState(
    optionId: String,
    streamUrl: String,
): Boolean {
    val normalizedOptionId = optionId.trim().lowercase(Locale.US)
    val normalizedUrl = streamUrl.trim().lowercase(Locale.US)
    return when (normalizedOptionId) {
        "emby-direct" -> normalizedUrl.contains("/videos/") && normalizedUrl.contains("/stream?")
        "server-direct" -> normalizedUrl.contains("/play/") || normalizedUrl.contains("play_source=emby_proxy")
        else -> false
    }
}

private fun shouldBypassManagedFallbackAfterTimeout(
    currentOptionId: String,
    currentOptionUrl: String,
    resolvedPlaybackUrl: String,
    streamOptions: List<EmbyPlaybackStreamOption>,
): Boolean {
    if (!currentOptionId.equals("server-direct", ignoreCase = true)) {
        return false
    }
    val embyDirectOption = streamOptions.firstOrNull { it.id.equals("emby-direct", ignoreCase = true) }
        ?: return false
    val embyHost = embyDirectOption.streamUrl.urlHostOrNull() ?: return false
    val currentHost = resolvedPlaybackUrl.urlHostOrNull()
        ?: currentOptionUrl.urlHostOrNull()
        ?: return false
    return !currentHost.equals(embyHost, ignoreCase = true)
}

private suspend fun probeRedirectState(
    url: String,
    requestHeaders: Map<String, String>,
): RedirectProbeState = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=0-0")
        requestHeaders.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                requestBuilder.header(name, value)
            }
        }
        client.newCall(requestBuilder.get().build()).execute().use { response ->
            val location = response.header("Location").orEmpty().trim()
            when {
                response.code in 300..399 && location.isNotBlank() ->
                    RedirectProbeState.Hit(code = response.code, location = location)
                response.isSuccessful || response.code == 206 ->
                    RedirectProbeState.NoRedirect(code = response.code)
                else ->
                    RedirectProbeState.Failed("HTTP ${response.code}")
            }
        }
    }.getOrElse { error ->
        RedirectProbeState.Failed(error.message ?: "探测失败")
    }
}

private fun RedirectProbeState.statusLabel(): String = when (this) {
    RedirectProbeState.Idle -> "待探测"
    RedirectProbeState.Loading -> "探测中"
    is RedirectProbeState.Hit -> "已命中 302 (HTTP $code)"
    is RedirectProbeState.NoRedirect -> "未重定向 (HTTP $code)"
    is RedirectProbeState.Skipped -> reason
    is RedirectProbeState.Failed -> message
}

private fun RedirectProbeState.redirectLocationOrNull(): String? = when (this) {
    is RedirectProbeState.Hit -> location
    else -> null
}

private data class PlaybackRouteState(
    val routeLabel: String,
    val transportLabel: String,
    val technicalLabel: String?,
    val currentUrl: String?,
    val entryUrl: String?,
    val isExternalDirect: Boolean,
)

private fun resolvePlaybackRouteState(
    entryPlaybackUrl: String,
    currentPlaybackUrl: String,
    streamOptions: List<EmbyPlaybackStreamOption>,
    redirectProbeState: RedirectProbeState,
): PlaybackRouteState {
    val probeLocation = redirectProbeState.redirectLocationOrNull()?.takeIf { it.isNotBlank() }
    val currentUrl = when {
        currentPlaybackUrl.isNotBlank() && currentPlaybackUrl != entryPlaybackUrl -> currentPlaybackUrl
        !probeLocation.isNullOrBlank() -> probeLocation
        currentPlaybackUrl.isNotBlank() -> currentPlaybackUrl
        else -> entryPlaybackUrl
    }.takeIf { it.isNotBlank() }
    val entryUrl = entryPlaybackUrl.takeIf { it.isNotBlank() }
    val embyHost = streamOptions.firstOrNull { it.id.equals("emby-direct", ignoreCase = true) }
        ?.streamUrl
        ?.urlHostOrNull()
        ?: entryUrl?.urlHostOrNull()
    val currentHost = currentUrl?.urlHostOrNull()
    val isExternalDirect = embyHost != null &&
        currentHost != null &&
        !currentHost.equals(embyHost, ignoreCase = true)
    val routeLabel = when {
        currentHost == null -> "待确认"
        embyHost == null -> "直链"
        isExternalDirect -> "直链"
        else -> "中转"
    }
    val transportLabel = when {
        currentHost == null -> "待确认"
        embyHost == null -> "直链"
        isExternalDirect -> "直链"
        else -> "中转"
    }
    val technicalLabel = when (redirectProbeState) {
        is RedirectProbeState.Hit -> "已发生 302"
        is RedirectProbeState.NoRedirect -> "未发生 302"
        is RedirectProbeState.Loading -> "探测中"
        is RedirectProbeState.Failed -> redirectProbeState.message
        is RedirectProbeState.Skipped -> redirectProbeState.reason
        else -> null
    }
    return PlaybackRouteState(
        routeLabel = routeLabel,
        transportLabel = transportLabel,
        technicalLabel = technicalLabel,
        currentUrl = currentUrl,
        entryUrl = entryUrl,
        isExternalDirect = isExternalDirect,
    )
}

private fun String.urlHostOrNull(): String? {
    return runCatching { Uri.parse(this).host?.trim()?.lowercase(Locale.US) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
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

private fun shouldForceVlcSoftwareDecode(
    container: String,
    videoCodec: String,
): Boolean {
    val normalizedContainer = container.trim().lowercase(Locale.US)
    val normalizedCodec = videoCodec.trim().lowercase(Locale.US)
    return normalizedContainer in setOf(
        "avi",
        "flv",
        "gif",
        "mpg",
        "mpeg",
        "rm",
        "rmvb",
        "swf",
        "vob",
        "wmv",
    ) || normalizedCodec in setOf(
        "flv1",
        "gif",
        "mpeg1video",
        "mpeg2video",
        "rv30",
        "rv40",
        "vc1",
        "wmv1",
        "wmv2",
        "wmv3",
    )
}

private fun shouldPreferCompatibilityBackend(
    container: String,
    videoCodec: String,
): Boolean {
    val normalizedContainer = container.trim().lowercase(Locale.US)
    val normalizedCodec = videoCodec.trim().lowercase(Locale.US)
    return normalizedContainer in setOf(
        "3gp",
        "avi",
        "flv",
        "gif",
        "mpg",
        "mpeg",
        "rm",
        "rmvb",
        "swf",
        "ts",
        "vob",
        "wmv",
    ) || normalizedCodec in setOf(
        "flv1",
        "gif",
        "mpeg1video",
        "mpeg2video",
        "rv30",
        "rv40",
        "vc1",
        "wmv1",
        "wmv2",
        "wmv3",
    )
}

private fun shouldPreferCompatibilityBackendForStreamOption(
    optionId: String,
    streamUrl: String,
    infoLine: String,
): Boolean {
    val normalizedOptionId = optionId.trim().lowercase(Locale.US)
    if (normalizedOptionId != "server-direct") return false

    val normalizedUrl = streamUrl.trim().lowercase(Locale.US)
    val normalizedInfoLine = infoLine.trim().lowercase(Locale.US)
    val proxyHint = normalizedUrl.contains("play_source=emby_proxy") ||
        normalizedUrl.contains("content_identity=")
    val remoteHint = normalizedInfoLine.contains("isremote=true") ||
        normalizedInfoLine.contains("strm") ||
        normalizedInfoLine.contains("网络直链")

    return proxyHint || (normalizedUrl.contains("/play/") && remoteHint)
}

private fun shouldUseDualBackendRace(
    container: String,
    videoCodec: String,
    audioCodec: String,
    infoLine: String,
): Boolean {
    val normalizedContainer = container.trim().lowercase(Locale.US)
    val normalizedVideoCodec = videoCodec.trim().lowercase(Locale.US)
    val normalizedAudioCodec = audioCodec.trim().lowercase(Locale.US)
    val normalizedInfoLine = infoLine.trim().lowercase(Locale.US)

    val commonContainer = normalizedContainer in setOf(
        "m4v",
        "mkv",
        "mov",
        "mp4",
        "webm",
    )
    val modernVideoRisk = normalizedVideoCodec in setOf(
        "av1",
        "h265",
        "hevc",
        "vp9",
    )
    val audioRisk = normalizedAudioCodec in setOf(
        "amr_nb",
        "amr_wb",
        "cook",
        "mp2",
        "pcm_alaw",
        "pcm_mulaw",
        "wmav1",
        "wmav2",
    )
    val networkHint = normalizedInfoLine.contains("strm") ||
        normalizedInfoLine.contains("网络直链") ||
        normalizedInfoLine.contains("direct")

    return modernVideoRisk || audioRisk || (commonContainer && networkHint)
}

private fun shouldPreferMpvBackend(
    videoRange: String,
    extendedVideoType: String,
    bitDepth: Int?,
    title: String,
): Boolean {
    val normalizedVideoRange = videoRange.trim().lowercase(Locale.US)
    val normalizedExtendedVideoType = extendedVideoType.trim().lowercase(Locale.US)
    val normalizedTitle = title.trim().lowercase(Locale.US)
    return normalizedVideoRange.contains("hdr") ||
        normalizedExtendedVideoType.isNotBlank() ||
        normalizedTitle.contains("dolby vision") ||
        normalizedTitle.contains("dv ") ||
        normalizedTitle.contains("dovi") ||
        normalizedTitle.contains("hdr10") ||
        normalizedTitle.contains("hdr 10") ||
        normalizedTitle.contains("hlg") ||
        (bitDepth ?: 8) > 8
}

private fun buildMpvHwdecOption(
    hdrRisk: Boolean,
): String {
    return if (hdrRisk) {
        // HDR / Dolby Vision content is safer through MPV's copy-back path so the GPU
        // pipeline can tone-map it instead of relying on direct surface output.
        "mediacodec-copy,mediacodec"
    } else {
        "mediacodec,mediacodec-copy"
    }
}

private fun shouldPreferSystemFastStartBackend(
    container: String,
    videoCodec: String,
    audioCodec: String,
    videoRange: String,
    extendedVideoType: String,
    bitDepth: Int?,
    isRemote: Boolean,
    infoLine: String,
): Boolean {
    val normalizedContainer = container.trim().lowercase(Locale.US)
    val normalizedVideoCodec = videoCodec.trim().lowercase(Locale.US)
    val normalizedAudioCodec = audioCodec.trim().lowercase(Locale.US)
    val normalizedVideoRange = videoRange.trim().lowercase(Locale.US)
    val normalizedExtendedVideoType = extendedVideoType.trim().lowercase(Locale.US)
    val normalizedInfoLine = infoLine.trim().lowercase(Locale.US)

    val commonContainer = normalizedContainer in setOf(
        "m4v",
        "mkv",
        "mov",
        "mp4",
    )
    val safeVideoCodec = normalizedVideoCodec in setOf(
        "avc",
        "h264",
        "mpeg4",
    )
    val unsupportedAudioRisk = normalizedAudioCodec in setOf(
        "amr_nb",
        "amr_wb",
        "cook",
        "mp2",
        "pcm_alaw",
        "pcm_mulaw",
        "wmav1",
        "wmav2",
    )
    val hdrRisk = normalizedVideoRange.contains("hdr") ||
        normalizedExtendedVideoType.isNotBlank() ||
        (bitDepth ?: 8) > 8
    val remoteHint = isRemote ||
        normalizedInfoLine.contains("strm") ||
        normalizedInfoLine.contains("网络直链") ||
        normalizedInfoLine.contains("direct")

    return commonContainer &&
        safeVideoCodec &&
        !unsupportedAudioRisk &&
        !hdrRisk &&
        !remoteHint
}

private fun buildAutomaticBackendOrder(
    runtimeMode: String,
    legacyRisk: Boolean,
    hdrRisk: Boolean,
    container: String,
    videoCodec: String,
    audioCodec: String,
    isRemote: Boolean,
    infoLine: String,
): List<PlayerBackendKind> {
    if (legacyRisk) {
        return listOf(
            PlayerBackendKind.Vlc,
            PlayerBackendKind.Mpv,
            PlayerBackendKind.Exo,
        )
    }
    if (hdrRisk) {
        return listOf(
            PlayerBackendKind.Vlc,
            PlayerBackendKind.Mpv,
            PlayerBackendKind.Exo,
        )
    }
    val normalizedInfoLine = infoLine.trim().lowercase(Locale.US)
    val remoteManagedLink = isRemote ||
        normalizedInfoLine.contains("strm") ||
        normalizedInfoLine.contains("网络直链") ||
        normalizedInfoLine.contains("direct")
    if (remoteManagedLink) {
        return listOf(
            PlayerBackendKind.Exo,
            PlayerBackendKind.Vlc,
            PlayerBackendKind.Mpv,
        )
    }
    if (
        shouldPreferSystemFastStartBackend(
            container = container,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            videoRange = "",
            extendedVideoType = "",
            bitDepth = 8,
            isRemote = isRemote,
            infoLine = infoLine,
        )
    ) {
        return when (runtimeMode) {
            PLAYER_MODE_COMPATIBILITY -> listOf(
                PlayerBackendKind.Mpv,
                PlayerBackendKind.Exo,
                PlayerBackendKind.Vlc,
            )

            PLAYER_MODE_SYSTEM -> listOf(
                PlayerBackendKind.Exo,
                PlayerBackendKind.Mpv,
                PlayerBackendKind.Vlc,
            )

            else -> listOf(
                PlayerBackendKind.Exo,
                PlayerBackendKind.Mpv,
                PlayerBackendKind.Vlc,
            )
        }
    }
    return when (runtimeMode) {
        PLAYER_MODE_SYSTEM -> listOf(
            PlayerBackendKind.Mpv,
            PlayerBackendKind.Exo,
            PlayerBackendKind.Vlc,
        )

        PLAYER_MODE_COMPATIBILITY -> listOf(
            PlayerBackendKind.Mpv,
            PlayerBackendKind.Vlc,
            PlayerBackendKind.Exo,
        )

        else -> listOf(
            PlayerBackendKind.Mpv,
            PlayerBackendKind.Exo,
            PlayerBackendKind.Vlc,
        )
    }
}

private fun List<PlayerBackendKind>.filterAvailableBackends(
    mpvAvailable: Boolean,
): List<PlayerBackendKind> {
    val filtered = filterNot { backend ->
        backend == PlayerBackendKind.Mpv && !mpvAvailable
    }
    return if (filtered.isNotEmpty()) {
        filtered
    } else {
        listOf(PlayerBackendKind.Vlc, PlayerBackendKind.Exo)
    }
}

private fun companionArmDelayMs(modeLabel: String): Long = when (modeLabel) {
    PLAYER_MODE_SYSTEM -> DualBackendExoCompanionDelayFastMs
    PLAYER_MODE_COMPATIBILITY -> DualBackendExoCompanionDelayCompatibilityMs
    else -> DualBackendExoCompanionDelayBalancedMs
}

private fun buildBackendMemoryKey(
    mediaSourceId: String,
    streamOptionId: String,
    container: String,
    videoCodec: String,
    audioCodec: String,
): String {
    return listOf(
        mediaSourceId.trim(),
        streamOptionId.trim(),
        container.trim().lowercase(Locale.US),
        videoCodec.trim().lowercase(Locale.US),
        audioCodec.trim().lowercase(Locale.US),
    ).joinToString(separator = "|")
}

private fun runtimeModeDescription(label: String): String = when (label) {
    PLAYER_MODE_SYSTEM -> "优先追求起播速度，常见直解片源会先走 Exo，起不来再自动切到兼容内核。"
    PLAYER_MODE_COMPATIBILITY -> "优先走兼容内核，适合 STRM、302、HDR / DV 和更挑片源的封装。"
    else -> "系统会根据片源自动选最合适的内核，并在起播异常时无感切换兜底。"
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
        backendKind = PlayerBackendKind.Mpv,
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
        backendKind = PlayerBackendKind.Exo,
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
        backendKind = PlayerBackendKind.Mpv,
        label = PLAYER_MODE_STANDARD,
        decoderFallback = true,
        minBufferMs = 10_000,
        maxBufferMs = 42_000,
        bufferForPlaybackMs = 1_100,
        bufferForPlaybackAfterRebufferMs = 2_000,
        seekBackMs = 5_000L,
        seekForwardMs = 10_000L,
    )
}

private fun EmbySubtitleTrack.toPlayerSubtitleTrack(): PlayerSubtitleTrack {
    return PlayerSubtitleTrack(
        key = "emby:${if (isExternal) "ext" else "int"}:$index",
        serverIndex = index,
        label = label,
        language = language,
        url = url,
        mimeType = mimeType,
        isDefault = isDefault,
        isExternal = isExternal,
    )
}

private fun EmbyAudioTrack.toPlayerAudioTrack(): PlayerAudioTrack {
    return PlayerAudioTrack(
        key = "emby:$index",
        serverIndex = index,
        label = label,
        language = language,
        codec = codec,
        channels = channels,
        isDefault = isDefault,
    )
}

private fun MpvRuntimeSubtitleTrack.toPlayerSubtitleTrack(): PlayerSubtitleTrack {
    return PlayerSubtitleTrack(
        key = "mpv:$id",
        serverIndex = null,
        label = label,
        language = language,
        url = null,
        mimeType = null,
        isDefault = false,
        isExternal = false,
        mpvTrackId = id,
    )
}

private fun MpvRuntimeAudioTrack.toPlayerAudioTrack(): PlayerAudioTrack {
    return PlayerAudioTrack(
        key = "mpv:$id",
        serverIndex = null,
        label = label,
        language = language,
        codec = null,
        channels = null,
        isDefault = false,
        mpvTrackId = id,
    )
}

private fun VlcRuntimeSubtitleTrack.toPlayerSubtitleTrack(): PlayerSubtitleTrack {
    return PlayerSubtitleTrack(
        key = "vlc:$id",
        serverIndex = null,
        label = label,
        language = language,
        url = null,
        mimeType = null,
        isDefault = false,
        isExternal = false,
        mpvTrackId = null,
        vlcTrackId = id,
    )
}

private fun VlcRuntimeAudioTrack.toPlayerAudioTrack(): PlayerAudioTrack {
    return PlayerAudioTrack(
        key = "vlc:$id",
        serverIndex = null,
        label = label,
        language = language,
        codec = null,
        channels = null,
        isDefault = false,
        mpvTrackId = null,
        vlcTrackId = id,
    )
}

private fun ExoRuntimeSubtitleTrack.toPlayerSubtitleTrack(): PlayerSubtitleTrack {
    return PlayerSubtitleTrack(
        key = "exo:$groupIndex:$trackIndex",
        serverIndex = null,
        label = label,
        language = language,
        url = null,
        mimeType = null,
        isDefault = isDefault,
        isExternal = false,
        exoGroupIndex = groupIndex,
        exoTrackIndex = trackIndex,
    )
}

private fun ExoRuntimeAudioTrack.toPlayerAudioTrack(): PlayerAudioTrack {
    return PlayerAudioTrack(
        key = "exo:$groupIndex:$trackIndex",
        serverIndex = null,
        label = label,
        language = language,
        codec = null,
        channels = null,
        isDefault = isDefault,
        exoGroupIndex = groupIndex,
        exoTrackIndex = trackIndex,
    )
}

private fun mergeSubtitleTracks(
    sourceTracks: List<PlayerSubtitleTrack>,
    runtimeTracks: List<PlayerSubtitleTrack>,
): List<PlayerSubtitleTrack> {
    if (runtimeTracks.isEmpty()) return sourceTracks

    val matchedRuntimeKeys = mutableSetOf<String>()
    val mergedTracks = sourceTracks.map { sourceTrack ->
        if (sourceTrack.isExternal) {
            return@map sourceTrack
        }
        val runtimeTrack = runtimeTracks.firstOrNull { candidate ->
            candidate.key !in matchedRuntimeKeys && sourceTrack.canMergeWith(candidate)
        }
        if (runtimeTrack == null) {
            sourceTrack
        } else {
            matchedRuntimeKeys += runtimeTrack.key
            sourceTrack.copy(
                label = sourceTrack.label.takeIf { it.isNotBlank() } ?: runtimeTrack.label,
                language = sourceTrack.language ?: runtimeTrack.language,
                isDefault = sourceTrack.isDefault || runtimeTrack.isDefault,
                mpvTrackId = runtimeTrack.mpvTrackId,
                vlcTrackId = runtimeTrack.vlcTrackId,
                exoGroupIndex = runtimeTrack.exoGroupIndex,
                exoTrackIndex = runtimeTrack.exoTrackIndex,
            )
        }
    }.toMutableList()

    runtimeTracks
        .filterNot { it.key in matchedRuntimeKeys }
        .forEach { mergedTracks += it }

    return mergedTracks.distinctBy { it.key }
}

private fun mergeAudioTracks(
    sourceTracks: List<PlayerAudioTrack>,
    runtimeTracks: List<PlayerAudioTrack>,
): List<PlayerAudioTrack> {
    if (runtimeTracks.isEmpty()) return sourceTracks

    val matchedRuntimeKeys = mutableSetOf<String>()
    val mergedTracks = sourceTracks.map { sourceTrack ->
        val runtimeTrack = runtimeTracks.firstOrNull { candidate ->
            candidate.key !in matchedRuntimeKeys && sourceTrack.canMergeWith(candidate)
        }
        if (runtimeTrack == null) {
            sourceTrack
        } else {
            matchedRuntimeKeys += runtimeTrack.key
            sourceTrack.copy(
                label = sourceTrack.label.takeIf { it.isNotBlank() } ?: runtimeTrack.label,
                language = sourceTrack.language ?: runtimeTrack.language,
                isDefault = sourceTrack.isDefault || runtimeTrack.isDefault,
                mpvTrackId = runtimeTrack.mpvTrackId,
                vlcTrackId = runtimeTrack.vlcTrackId,
                exoGroupIndex = runtimeTrack.exoGroupIndex,
                exoTrackIndex = runtimeTrack.exoTrackIndex,
            )
        }
    }.toMutableList()

    runtimeTracks
        .filterNot { it.key in matchedRuntimeKeys }
        .forEach { mergedTracks += it }

    return mergedTracks.distinctBy { it.key }
}

private fun PlayerSubtitleTrack.canMergeWith(runtimeTrack: PlayerSubtitleTrack): Boolean {
    if (isExternal || runtimeTrack.isExternal) return false

    val sourceLabel = normalizeSubtitleMatchText(label)
    val runtimeLabel = normalizeSubtitleMatchText(runtimeTrack.label)
    val sourceLanguage = normalizedSubtitleLanguage(language, label)
    val runtimeLanguage = normalizedSubtitleLanguage(runtimeTrack.language, runtimeTrack.label)
    val languageMatches = sourceLanguage != null && sourceLanguage == runtimeLanguage

    return when {
        sourceLabel.isNotBlank() && runtimeLabel.isNotBlank() &&
            (sourceLabel == runtimeLabel || sourceLabel.contains(runtimeLabel) || runtimeLabel.contains(sourceLabel)) -> true
        languageMatches && (sourceLabel.isGenericSubtitleLabel() || runtimeLabel.isGenericSubtitleLabel()) -> true
        else -> false
    }
}

private fun PlayerAudioTrack.canMergeWith(runtimeTrack: PlayerAudioTrack): Boolean {
    val sourceLabel = normalizeSubtitleMatchText(label)
    val runtimeLabel = normalizeSubtitleMatchText(runtimeTrack.label)
    val sourceLanguage = normalizedSubtitleLanguage(language, label)
    val runtimeLanguage = normalizedSubtitleLanguage(runtimeTrack.language, runtimeTrack.label)
    val languageMatches = sourceLanguage != null && sourceLanguage == runtimeLanguage

    return when {
        sourceLabel.isNotBlank() && runtimeLabel.isNotBlank() &&
            (sourceLabel == runtimeLabel || sourceLabel.contains(runtimeLabel) || runtimeLabel.contains(sourceLabel)) -> true
        languageMatches && (sourceLabel.isGenericSubtitleLabel() || runtimeLabel.isGenericSubtitleLabel()) -> true
        else -> false
    }
}

private fun String.isGenericSubtitleLabel(): Boolean {
    val value = lowercase(Locale.US)
    return value.isBlank() ||
        value.startsWith("subtitle") ||
        value.startsWith("audio") ||
        value.startsWith("track") ||
        value.startsWith("spu") ||
        value.startsWith("字幕")
}

private fun normalizeSubtitleMatchText(value: String): String {
    return value
        .lowercase(Locale.US)
        .replace("subtitle", "")
        .replace("audio", "")
        .replace("track", "")
        .replace("spu", "")
        .replace("字幕", "")
        .replace(" ", "")
        .replace("-", "")
        .replace("_", "")
        .trim()
}

private fun normalizedSubtitleLanguage(
    language: String?,
    label: String,
): String? {
    val languageCode = language.orEmpty().lowercase(Locale.US)
    val displayText = label.lowercase(Locale.US)
    return when {
        languageCode == "zh" ||
            languageCode.startsWith("zh-") ||
            languageCode == "chi" ||
            languageCode == "zho" ||
            languageCode == "chs" ||
            languageCode == "cht" ||
            languageCode == "zhs" ||
            languageCode == "zht" ||
            displayText.contains("中") ||
            displayText.contains("chinese") -> "zh"
        languageCode == "en" ||
            languageCode.startsWith("en-") ||
            languageCode == "eng" ||
            displayText.contains("english") ||
            displayText.contains("英文") ||
            displayText.contains("英语") -> "en"
        languageCode == "ja" ||
            languageCode.startsWith("ja-") ||
            languageCode == "jpn" ||
            displayText.contains("japanese") ||
            displayText.contains("日文") ||
            displayText.contains("日语") -> "ja"
        languageCode == "ko" ||
            languageCode.startsWith("ko-") ||
            languageCode == "kor" ||
            displayText.contains("korean") ||
            displayText.contains("韩文") ||
            displayText.contains("韩语") -> "ko"
        else -> null
    }
}

private fun List<PlayerSubtitleTrack>.resolveAutomaticSubtitleSelection(
    embeddedLanguagePreference: String,
    externalLanguagePreference: String,
): List<PlayerSubtitleTrack> {
    if (isEmpty()) return emptyList()

    val embeddedTracks = filterNot { it.isExternal }
    val externalTracks = filter { it.isExternal }
    val preferred = when {
        embeddedTracks.isNotEmpty() -> embeddedTracks.findPreferredSubtitle(embeddedLanguagePreference)
        externalTracks.isNotEmpty() -> externalTracks.findPreferredSubtitle(externalLanguagePreference)
        else -> null
    } ?: return emptyList()

    return listOf(
        preferred.copy(isDefault = true),
    )
}

private fun List<PlayerAudioTrack>.resolveAutomaticAudioSelection(): List<PlayerAudioTrack> {
    val preferred = firstOrNull { it.isDefault } ?: firstOrNull() ?: return emptyList()
    return listOf(preferred.copy(isDefault = true))
}

private fun List<PlayerSubtitleTrack>.findPreferredSubtitle(
    languagePreference: String,
): PlayerSubtitleTrack? {
    return when (languagePreference) {
        SUBTITLE_LANGUAGE_PREFERENCE_FOLLOW_DEFAULT -> firstOrNull { it.isDefault } ?: firstOrNull()
        else -> firstOrNull { it.matchesLanguagePreference(languagePreference) }
            ?: firstOrNull { it.isDefault }
            ?: firstOrNull()
    }
}

private fun PlayerSubtitleTrack.matchesLanguagePreference(
    languagePreference: String,
): Boolean {
    val languageCode = language.orEmpty().lowercase(Locale.US)
    val displayText = label.lowercase(Locale.US)

    return when (languagePreference) {
        SUBTITLE_LANGUAGE_PREFERENCE_CHINESE -> {
            displayText.contains("中") ||
                displayText.contains("双语") ||
                displayText.contains("chinese") ||
                languageCode == "zh" ||
                languageCode.startsWith("zh-") ||
                languageCode == "chi" ||
                languageCode == "zho" ||
                languageCode == "chs" ||
                languageCode == "cht" ||
                languageCode == "zhs" ||
                languageCode == "zht"
        }

        SUBTITLE_LANGUAGE_PREFERENCE_SIMPLIFIED_CHINESE -> {
            displayText.contains("简体") ||
                displayText.contains("简中") ||
                displayText.contains("chs") ||
                languageCode == "chs" ||
                languageCode == "zhs"
        }

        SUBTITLE_LANGUAGE_PREFERENCE_TRADITIONAL_CHINESE -> {
            displayText.contains("繁体") ||
                displayText.contains("繁中") ||
                displayText.contains("cht") ||
                languageCode == "cht" ||
                languageCode == "zht"
        }

        SUBTITLE_LANGUAGE_PREFERENCE_ENGLISH -> {
            displayText.contains("english") ||
                displayText.contains("英文") ||
                displayText.contains("英语") ||
                languageCode == "en" ||
                languageCode.startsWith("en-") ||
                languageCode == "eng"
        }

        SUBTITLE_LANGUAGE_PREFERENCE_JAPANESE -> {
            displayText.contains("日文") ||
                displayText.contains("日语") ||
                displayText.contains("japanese") ||
                languageCode == "ja" ||
                languageCode.startsWith("ja-") ||
                languageCode == "jpn"
        }

        SUBTITLE_LANGUAGE_PREFERENCE_KOREAN -> {
            displayText.contains("韩文") ||
                displayText.contains("韩语") ||
                displayText.contains("korean") ||
                languageCode == "ko" ||
                languageCode.startsWith("ko-") ||
                languageCode == "kor"
        }

        else -> false
    }
}

private fun buildAudioOptions(
    sourceTracks: List<PlayerAudioTrack>,
    autoTracks: List<PlayerAudioTrack>,
    selectedAudioKey: String?,
): List<SubtitleOption> {
    val autoLabel = autoTracks.firstOrNull()?.label ?: "默认音轨"
    return buildList {
        add(
            SubtitleOption(
                title = "自动",
                subtitle = autoLabel,
                targetKey = null,
                selected = selectedAudioKey == null,
            ),
        )
        sourceTracks.forEach { track ->
            add(
                SubtitleOption(
                    title = track.label,
                    subtitle = buildString {
                        append(track.language ?: "未标记语言")
                        track.codec?.takeIf { it.isNotBlank() }?.let { codec ->
                            append(" · ")
                            append(codec.uppercase(Locale.US))
                        }
                        track.channels?.takeIf { it > 0 }?.let { channels ->
                            append(" · ")
                            append(
                                when (channels) {
                                    1 -> "1.0"
                                    2 -> "2.0"
                                    6 -> "5.1"
                                    8 -> "7.1"
                                    else -> "${channels}声道"
                                },
                            )
                        }
                    },
                    targetKey = track.key,
                    selected = selectedAudioKey == track.key,
                ),
            )
        }
    }
}

private fun formatAudioChoiceLabel(
    selectedAudioKey: String?,
    sourceTracks: List<PlayerAudioTrack>,
    autoTracks: List<PlayerAudioTrack>,
): String {
    return when (selectedAudioKey) {
        null -> autoTracks.firstOrNull()?.label ?: "自动"
        else -> sourceTracks.firstOrNull { it.key == selectedAudioKey }?.label ?: "音轨"
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
