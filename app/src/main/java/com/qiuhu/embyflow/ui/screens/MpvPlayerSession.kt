package com.qiuhu.embyflow.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.View
import android.view.ViewGroup
import `is`.xyz.mpv.AurePMpvView
import `is`.xyz.mpv.MPVLib
import java.io.File
import kotlin.math.roundToLong

internal data class MpvRuntimeSubtitleTrack(
    val id: Int,
    val label: String,
    val language: String?,
)

internal data class MpvRuntimeAudioTrack(
    val id: Int,
    val label: String,
    val language: String?,
)

internal data class MpvExternalSubtitleTrack(
    val label: String,
    val url: String,
    val isDefault: Boolean,
)

internal class MpvPlayerSession(
    context: Context,
    private val streamUrl: String,
    subtitleTracks: List<MpvExternalSubtitleTrack>,
    private val onSubtitleTracksChanged: ((List<MpvRuntimeSubtitleTrack>, Int?) -> Unit)? = null,
    private val onAudioTracksChanged: ((List<MpvRuntimeAudioTrack>, Int?) -> Unit)? = null,
    requestHeaders: Map<String, String>,
    hwdecOption: String,
    enableHdrToneMapping: Boolean,
    private val startPositionMs: Long,
    private val playWhenReady: Boolean,
    initialVolume: Int,
    initialPlaybackSpeed: Float,
    private val seekBackMs: Long,
    private val seekForwardMs: Long,
    private val onFirstFrameRendered: (() -> Unit)? = null,
) : MPVLib.EventObserver, MPVLib.LogObserver {
    companion object {
        private const val Tag = "AurePMpvPlayer"
    }

    private val appContext = context.applicationContext
    private val mpvView = AurePMpvView(
        context = context,
        requestHeaders = requestHeaders,
        hwdecOption = hwdecOption,
        enableHdrToneMapping = enableHdrToneMapping,
    ).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.BLACK)
    }
    private val pendingSubtitleTracks = subtitleTracks
    private var initialRuntimeStateApplied = false
    private var initialSeekApplied = startPositionMs <= 0L
    private var releaseRequested = false
    private var pendingErrorMessage: String? = null
    private var desiredSubtitleTrackId: Int? = null
    private var subtitlesDisabled = false
    private var desiredAudioTrackId: Int? = null
    private var lastSubtitleTracksSignature = ""
    private var lastAudioTracksSignature = ""
    private var lastLogErrorMessage: String? = null

    var hasRenderedFirstFrame: Boolean = false
        private set
    var isPlaying: Boolean = playWhenReady
        private set
    var isBuffering: Boolean = true
        private set
    var playWhenReadyRequested: Boolean = playWhenReady
        private set
    var isEnded: Boolean = false
        private set
    var currentPositionMs: Long = startPositionMs.coerceAtLeast(0L)
        private set
    var bufferedPositionMs: Long = 0L
        private set
    var durationMs: Long = 0L
        private set
    var bitrateEstimateBitsPerSecond: Long = 0L
        private set
    var resolvedPlaybackUrl: String = streamUrl
        private set
    var playbackSpeed: Float = initialPlaybackSpeed
        private set
    private var playbackVolume: Int = initialVolume.coerceIn(0, 100)

    val view: View = mpvView

    init {
        MPVLib.requireAvailable()
        val configDir = File(appContext.filesDir, "mpv-config").apply { mkdirs() }
        Log.i(
            Tag,
            "prepare url=$streamUrl hwdec=$hwdecOption hdrToneMap=$enableHdrToneMapping subtitles=${subtitleTracks.size}",
        )
        MPVLib.addObserver(this)
        MPVLib.addLogObserver(this)
        mpvView.initialize(
            configDir = configDir.absolutePath,
            cacheDir = appContext.cacheDir.absolutePath,
        )
        mpvView.playFile(streamUrl)
        applyInitialPlaybackState(applySeek = false)
        syncState()
    }

    fun syncState() {
        currentPositionMs = (mpvView.timePos ?: 0.0)
            .coerceAtLeast(0.0)
            .times(1000.0)
            .roundToLong()
        durationMs = (MPVLib.getPropertyDouble("duration/full") ?: 0.0)
            .coerceAtLeast(0.0)
            .times(1000.0)
            .roundToLong()
        val paused = mpvView.paused == true
        val cachePaused = MPVLib.getPropertyBoolean("paused-for-cache") == true
        bufferedPositionMs = if (durationMs > 0L && !cachePaused) {
            durationMs
        } else {
            currentPositionMs
        }
        bitrateEstimateBitsPerSecond = MPVLib.getPropertyInt("cache-speed")
            ?.toLong()
            ?.coerceAtLeast(0L)
            ?.times(8L)
            ?: 0L
        isBuffering = cachePaused || (!hasRenderedFirstFrame && playWhenReadyRequested && !isEnded)
        isPlaying = !paused && !cachePaused && playWhenReadyRequested && !isEnded
        resolvedPlaybackUrl = mpvView.currentPath?.takeIf { it.isNotBlank() } ?: streamUrl
        if (currentPositionMs > 0L && !hasRenderedFirstFrame) {
            notifyFirstFrameRendered()
        }
        updateTrackSnapshots()
    }

    fun consumePendingError(): String? = pendingErrorMessage.also {
        pendingErrorMessage = null
    }

    fun seekTo(positionMs: Long) {
        mpvView.timePos = positionMs.coerceAtLeast(0L).toDouble() / 1000.0
        syncState()
    }

    fun seekBack() {
        seekTo(currentPositionMs - seekBackMs)
    }

    fun seekForward() {
        val maxPosition = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        seekTo((currentPositionMs + seekForwardMs).coerceAtMost(maxPosition))
    }

    fun play() {
        playWhenReadyRequested = true
        mpvView.paused = false
        isPlaying = true
        isEnded = false
    }

    fun pause() {
        playWhenReadyRequested = false
        mpvView.paused = true
        isPlaying = false
    }

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = value
        mpvView.playbackSpeed = value.toDouble()
    }

    fun setVolume(value: Int) {
        playbackVolume = value.coerceIn(0, 100)
        MPVLib.setPropertyInt("volume", playbackVolume)
    }

    fun release() {
        releaseRequested = true
        MPVLib.removeObserver(this)
        MPVLib.removeLogObserver(this)
        runCatching { mpvView.destroyPlayer() }
    }

    override fun eventProperty(property: String) {
        when (property) {
            "track-list",
            "current-tracks/audio/selected",
            "current-tracks/sub/selected",
            -> updateTrackSnapshots()
            "stream-open-filename",
            "path",
            -> resolvedPlaybackUrl = mpvView.currentPath?.takeIf { it.isNotBlank() } ?: streamUrl
        }
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos",
            "duration/full",
            "cache-speed",
            -> syncState()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause",
            "paused-for-cache",
            -> syncState()
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "stream-open-filename",
            "path",
            -> resolvedPlaybackUrl = value.takeIf { it.isNotBlank() } ?: streamUrl
        }
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos",
            "duration/full",
            -> syncState()
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                applyInitialPlaybackState(applySeek = true)
                applyExternalSubtitleTracks()
                updateTrackSnapshots()
                syncState()
            }

            MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG,
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART,
            -> {
                notifyFirstFrameRendered()
                isBuffering = false
                isEnded = false
                syncState()
            }

            MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG -> {
                updateTrackSnapshots()
                syncState()
            }

            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                isPlaying = false
                isBuffering = false
                isEnded = true
                if (pendingErrorMessage == null) {
                    val terminalError = lastLogErrorMessage?.takeIf { it.isTerminalPlaybackFailure() }
                    if (terminalError != null) {
                        pendingErrorMessage = terminalError
                    } else if (!hasRenderedFirstFrame) {
                        pendingErrorMessage = lastLogErrorMessage ?: "MPV 播放失败"
                    }
                }
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (level <= MPVLib.MpvLogLevel.MPV_LOG_LEVEL_ERROR) {
            Log.e(Tag, "mpv[$prefix] $text")
            lastLogErrorMessage = text
            if (!releaseRequested && pendingErrorMessage == null && text.isTerminalPlaybackFailure()) {
                pendingErrorMessage = text
            }
        } else {
            Log.i(Tag, "mpv[$prefix] $text")
        }
    }

    private fun applyInitialPlaybackState(
        applySeek: Boolean,
    ) {
        if (!initialRuntimeStateApplied) {
            initialRuntimeStateApplied = true
            mpvView.playbackSpeed = playbackSpeed.toDouble()
            mpvView.paused = !playWhenReady
            if (playWhenReady) {
                mpvView.paused = false
            }
            setVolume(playbackVolume)
        }
        if (applySeek && !initialSeekApplied && startPositionMs > 0L) {
            mpvView.timePos = startPositionMs.toDouble() / 1000.0
            currentPositionMs = startPositionMs.coerceAtLeast(0L)
            initialSeekApplied = true
        }
    }

    private fun applyExternalSubtitleTracks() {
        pendingSubtitleTracks.forEach { track ->
            runCatching {
                mpvView.addExternalSubtitle(
                    url = track.url,
                    select = track.isDefault,
                )
            }.onFailure { error ->
                Log.w(Tag, "failed to add subtitle=${track.label} url=${track.url}", error)
            }
        }
    }

    fun updateSubtitleSelection(
        trackId: Int?,
        disabled: Boolean,
    ) {
        desiredSubtitleTrackId = trackId
        subtitlesDisabled = disabled
        applyDesiredSubtitleSelection()
    }

    fun updateAudioSelection(trackId: Int?) {
        desiredAudioTrackId = trackId
        applyDesiredAudioSelection()
    }

    private fun applyDesiredSubtitleSelection() {
        if (subtitlesDisabled || desiredSubtitleTrackId == null) {
            mpvView.sid = -1
            MPVLib.setPropertyBoolean("sub-visibility", false)
            return
        }
        MPVLib.setPropertyBoolean("sub-visibility", true)
        mpvView.sid = desiredSubtitleTrackId ?: -1
    }

    private fun applyDesiredAudioSelection() {
        val trackId = desiredAudioTrackId ?: return
        mpvView.aid = trackId
    }

    private fun notifyFirstFrameRendered() {
        if (hasRenderedFirstFrame) return
        hasRenderedFirstFrame = true
        onFirstFrameRendered?.invoke()
    }

    private fun updateTrackSnapshots() {
        val trackLists = mpvView.loadTracks()
        val subtitleTracks = trackLists["sub"].orEmpty()
        val audioTracks = trackLists["audio"].orEmpty()

        val subtitleSignature = subtitleTracks.joinToString(separator = "|") { track ->
            "${track.mpvId}:${track.name}:${track.language.orEmpty()}"
        }
        if (subtitleSignature != lastSubtitleTracksSignature) {
            lastSubtitleTracksSignature = subtitleSignature
            onSubtitleTracksChanged?.invoke(
                subtitleTracks.map { track ->
                    MpvRuntimeSubtitleTrack(
                        id = track.mpvId,
                        label = track.name,
                        language = track.language,
                    )
                },
                mpvView.sid.takeIf { it >= 0 },
            )
        }

        val audioSignature = audioTracks.joinToString(separator = "|") { track ->
            "${track.mpvId}:${track.name}:${track.language.orEmpty()}"
        }
        if (audioSignature != lastAudioTracksSignature) {
            lastAudioTracksSignature = audioSignature
            onAudioTracksChanged?.invoke(
                audioTracks.map { track ->
                    MpvRuntimeAudioTrack(
                        id = track.mpvId,
                        label = track.name,
                        language = track.language,
                    )
                },
                mpvView.aid.takeIf { it >= 0 },
            )
        }
    }

    private fun String.isTerminalPlaybackFailure(): Boolean {
        return contains("opening failed", ignoreCase = true) ||
            contains("failed to open", ignoreCase = true) ||
            contains("loading failed", ignoreCase = true) ||
            contains("internal server error", ignoreCase = true) ||
            contains("http error", ignoreCase = true) ||
            contains("server returned", ignoreCase = true) ||
            contains("cannot open", ignoreCase = true)
    }
}
