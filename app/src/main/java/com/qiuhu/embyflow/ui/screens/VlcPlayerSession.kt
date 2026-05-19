package com.qiuhu.embyflow.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.util.Locale
import kotlin.math.max
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

internal data class VlcRuntimeSubtitleTrack(
    val id: Int,
    val label: String,
    val language: String?,
)

internal data class VlcRuntimeAudioTrack(
    val id: Int,
    val label: String,
    val language: String?,
)

internal data class VlcExternalSubtitleTrack(
    val label: String,
    val url: String,
    val isDefault: Boolean,
)

internal class VlcPlayerSession(
    context: Context,
    private val streamUrl: String,
    subtitleTracks: List<VlcExternalSubtitleTrack>,
    private val onSubtitleTracksChanged: ((List<VlcRuntimeSubtitleTrack>, Int?) -> Unit)? = null,
    private val onAudioTracksChanged: ((List<VlcRuntimeAudioTrack>, Int?) -> Unit)? = null,
    requestHeaders: Map<String, String>,
    private val forceSoftwareDecode: Boolean,
    private val startPositionMs: Long,
    private val playWhenReady: Boolean,
    initialVolume: Int,
    initialPlaybackSpeed: Float,
    private val seekBackMs: Long,
    private val seekForwardMs: Long,
    private val onFirstFrameRendered: (() -> Unit)? = null,
) {
    companion object {
        private const val Tag = "AurePVlcPlayer"
        private const val BufferingProgressGraceMs = 1_200L
        private const val SubtitleSwitchGraceMs = 2_500L
        private const val SubtitleRecoveryFastDelayMs = 220L
        private const val SubtitleRecoverySlowDelayMs = 1_100L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(appContext, buildLibVlcOptions(requestHeaders, forceSoftwareDecode))
    private val mediaPlayer = MediaPlayer(libVlc)
    private var media: Media? = null
    private var initialRuntimeStateApplied = false
    private var initialSeekApplied = startPositionMs <= 0L
    private var pendingErrorMessage: String? = null
    private var desiredSubtitleTrackId: Int? = null
    private var subtitlesDisabled: Boolean = false
    private var desiredAudioTrackId: Int? = null
    private var lastSubtitleTracksSignature: String = ""
    private var lastAudioTracksSignature: String = ""

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
    private var lastStatsBytesRead: Long = 0L
    private var lastStatsSampleTimeMs: Long = 0L
    private var lastNonZeroStatsSampleTimeMs: Long = 0L
    private var smoothedBitrateBitsPerSecond: Long = 0L
    private var lastProgressPositionMs: Long = startPositionMs.coerceAtLeast(0L)
    private var lastProgressRealtimeMs: Long = 0L
    private var lastSubtitleSelectionRealtimeMs: Long = 0L
    private var subtitleRecoveryGeneration: Int = 0
    private var lastTrackRefreshRealtimeMs: Long = 0L
    private var lastBufferingLogBucket: Int = -1
    private var lastLoggedVoutCount: Int = -1

    val view: View = VLCVideoLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.BLACK)
    }

    init {
        mediaPlayer.attachViews(view as VLCVideoLayout, null, true, false)
        mediaPlayer.setEventListener { event ->
            handleEvent(event)
        }
        prepareMedia(
            subtitleTracks = subtitleTracks,
            requestHeaders = requestHeaders,
        )
    }

    fun syncState() {
        currentPositionMs = mediaPlayer.time.coerceAtLeast(0L)
        durationMs = mediaPlayer.length.coerceAtLeast(0L)
        bufferedPositionMs = if (durationMs > 0L && !isBuffering) {
            durationMs
        } else {
            currentPositionMs
        }
        updateBitrateEstimate()
    }

    fun consumePendingError(): String? = pendingErrorMessage.also {
        pendingErrorMessage = null
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer.time = positionMs.coerceAtLeast(0L)
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
        mediaPlayer.play()
        isPlaying = true
        isEnded = false
    }

    fun pause() {
        playWhenReadyRequested = false
        mediaPlayer.pause()
        isPlaying = false
    }

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = value
        runCatching {
            mediaPlayer.rate = value
        }.onFailure { error ->
            Log.w(Tag, "failed to set playback speed=$value url=$streamUrl", error)
        }
    }

    fun setVolume(value: Int) {
        playbackVolume = value.coerceIn(0, 100)
        runCatching {
            mediaPlayer.volume = playbackVolume
        }.onFailure { error ->
            Log.w(Tag, "failed to set volume=$playbackVolume url=$streamUrl", error)
        }
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        mediaPlayer.setEventListener(null)
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { media?.release() }
        runCatching { libVlc.release() }
    }

    private fun prepareMedia(
        subtitleTracks: List<VlcExternalSubtitleTrack>,
        requestHeaders: Map<String, String>,
    ) {
        setVolume(playbackVolume)
        val preparedMedia = Media(libVlc, Uri.parse(streamUrl)).apply {
            setHWDecoderEnabled(!forceSoftwareDecode, !forceSoftwareDecode)
            addOption(":input-fast-seek")
            addOption(":http-reconnect=true")
            addOption(":network-caching=1500")
            addOption(":file-caching=1500")
            addOption(":live-caching=1500")
            applyRequestHeaders(requestHeaders)
        }
        Log.i(
            Tag,
            "prepare url=$streamUrl forceSoftwareDecode=$forceSoftwareDecode subtitles=${subtitleTracks.size}",
        )
        media = preparedMedia
        mediaPlayer.media = preparedMedia
        subtitleTracks.forEach { track ->
            val subtitleUri = runCatching { Uri.parse(track.url) }.getOrNull() ?: return@forEach
            runCatching {
                mediaPlayer.addSlave(
                    IMedia.Slave.Type.Subtitle,
                    subtitleUri,
                    track.isDefault,
                )
            }.onFailure { error ->
                Log.w(Tag, "failed to attach subtitle=${track.label} url=${track.url}", error)
            }
        }
        resolvedPlaybackUrl = streamUrl
        mediaPlayer.play()
        applyInitialPlaybackState(applySeek = false)
        scanRuntimeSubtitleTracks()
        scanRuntimeAudioTracks()
        syncState()
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

    private fun applyInitialPlaybackState(
        applySeek: Boolean,
    ) {
        if (!initialRuntimeStateApplied) {
            initialRuntimeStateApplied = true
            setPlaybackSpeed(playbackSpeed)
            if (!playWhenReady) {
                playWhenReadyRequested = false
                runCatching {
                    mediaPlayer.pause()
                }
                isPlaying = false
            }
        }
        if (applySeek && !initialSeekApplied && startPositionMs > 0L) {
            runCatching {
                mediaPlayer.time = startPositionMs
            }
            currentPositionMs = startPositionMs.coerceAtLeast(0L)
            lastProgressPositionMs = max(lastProgressPositionMs, currentPositionMs)
            lastProgressRealtimeMs = SystemClock.elapsedRealtime()
            initialSeekApplied = true
        }
    }

    private fun handleEvent(event: MediaPlayer.Event) {
        maybeLogEvent(event)
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                isBuffering = true
                isEnded = false
            }

            MediaPlayer.Event.Buffering -> {
                val nowMs = SystemClock.elapsedRealtime()
                val positionMs = mediaPlayer.time.coerceAtLeast(0L)
                isBuffering = shouldTreatBufferingAsActive(
                    bufferingPercent = event.getBuffering(),
                    positionMs = positionMs,
                    nowMs = nowMs,
                )
                isEnded = false
            }

            MediaPlayer.Event.Playing -> {
                applyInitialPlaybackState(applySeek = true)
                notifyFirstFrameRendered()
                isPlaying = true
                isBuffering = false
                isEnded = false
                markPlaybackProgress(force = true)
                refreshRuntimeTracks(force = true)
            }

            MediaPlayer.Event.TimeChanged,
            MediaPlayer.Event.PositionChanged,
            MediaPlayer.Event.Vout,
            -> {
                markPlaybackProgress(force = false)
                if (mediaPlayer.isPlaying || mediaPlayer.time > 0L) {
                    notifyFirstFrameRendered()
                    isBuffering = false
                }
            }

            MediaPlayer.Event.Paused -> {
                isPlaying = false
                isBuffering = false
            }

            MediaPlayer.Event.Stopped -> {
                isPlaying = false
                isBuffering = false
            }

            MediaPlayer.Event.EndReached -> {
                isPlaying = false
                isBuffering = false
                isEnded = true
            }

            MediaPlayer.Event.EncounteredError -> {
                isPlaying = false
                isBuffering = false
                pendingErrorMessage = "VLC 播放失败"
                Log.e(Tag, "vlc playback error url=$streamUrl")
            }
        }
        if (
            event.type != MediaPlayer.Event.TimeChanged &&
            event.type != MediaPlayer.Event.PositionChanged &&
            event.type != MediaPlayer.Event.Vout
        ) {
            syncState()
        }
    }

    private fun maybeLogEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                Log.i(Tag, "event opening time=${mediaPlayer.time}")
            }

            MediaPlayer.Event.Playing -> {
                Log.i(Tag, "event playing time=${mediaPlayer.time}")
            }

            MediaPlayer.Event.Buffering -> {
                val buffering = runCatching { event.getBuffering() }.getOrNull() ?: return
                val bucket = (buffering / 10f).toInt()
                if (bucket != lastBufferingLogBucket) {
                    lastBufferingLogBucket = bucket
                    Log.i(Tag, "event buffering percent=$buffering time=${mediaPlayer.time}")
                }
            }

            MediaPlayer.Event.Vout -> {
                val voutCount = runCatching { event.getVoutCount() }.getOrNull() ?: return
                if (voutCount != lastLoggedVoutCount) {
                    lastLoggedVoutCount = voutCount
                    Log.i(Tag, "event vout count=$voutCount time=${mediaPlayer.time}")
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                Log.e(Tag, "event error time=${mediaPlayer.time}")
            }
        }
    }

    private fun scanRuntimeSubtitleTracks() {
        val trackDescriptions = runCatching { mediaPlayer.getSpuTracks() }.getOrNull().orEmpty()
        val runtimeTracks = trackDescriptions
            .asSequence()
            .filter { it.id >= 0 }
            .map {
                VlcRuntimeSubtitleTrack(
                    id = it.id,
                    label = it.name.takeIf { name -> name.isNotBlank() } ?: "字幕 ${it.id}",
                    language = guessTrackLanguage(it.name),
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toList()
        val selectedTrackId = runCatching { mediaPlayer.getSpuTrack() }
            .getOrNull()
            ?.takeIf { it >= 0 }
        val signature = buildString {
            runtimeTracks.forEach { track ->
                append(track.id)
                append(':')
                append(track.label)
                append(':')
                append(track.language.orEmpty())
                append('|')
            }
            append("selected=")
            append(selectedTrackId ?: -1)
        }
        if (signature == lastSubtitleTracksSignature) {
            return
        }
        lastSubtitleTracksSignature = signature
        onSubtitleTracksChanged?.invoke(runtimeTracks, selectedTrackId)
        applyDesiredSubtitleSelection()
    }

    private fun scanRuntimeAudioTracks() {
        val trackDescriptions = runCatching { mediaPlayer.getAudioTracks() }.getOrNull().orEmpty()
        val runtimeTracks = trackDescriptions
            .asSequence()
            .filter { it.id >= 0 }
            .map {
                VlcRuntimeAudioTrack(
                    id = it.id,
                    label = it.name.takeIf { name -> name.isNotBlank() } ?: "音轨 ${it.id}",
                    language = guessTrackLanguage(it.name),
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toList()
        val selectedTrackId = runCatching { mediaPlayer.getAudioTrack() }
            .getOrNull()
            ?.takeIf { it >= 0 }
        val signature = buildString {
            runtimeTracks.forEach { track ->
                append(track.id)
                append(':')
                append(track.label)
                append(':')
                append(track.language.orEmpty())
                append('|')
            }
            append("selected=")
            append(selectedTrackId ?: -1)
        }
        if (signature == lastAudioTracksSignature) {
            return
        }
        lastAudioTracksSignature = signature
        onAudioTracksChanged?.invoke(runtimeTracks, selectedTrackId)
        applyDesiredAudioSelection()
    }

    private fun refreshRuntimeTracks(force: Boolean = false) {
        val nowMs = SystemClock.elapsedRealtime()
        if (!force && nowMs - lastTrackRefreshRealtimeMs < 750L) {
            return
        }
        lastTrackRefreshRealtimeMs = nowMs
        scanRuntimeSubtitleTracks()
        scanRuntimeAudioTracks()
    }

    private fun applyDesiredSubtitleSelection() {
        if (subtitlesDisabled) {
            val currentTrackId = runCatching { mediaPlayer.getSpuTrack() }.getOrNull()
            if (currentTrackId == -1) {
                return
            }
            val shouldResume = playWhenReadyRequested || mediaPlayer.isPlaying
            val resumePositionMs = mediaPlayer.time.coerceAtLeast(0L)
            lastSubtitleSelectionRealtimeMs = SystemClock.elapsedRealtime()
            Log.i(Tag, "subtitle switch disable current=$currentTrackId url=$streamUrl")
            runCatching {
                mediaPlayer.setSpuTrack(-1)
                resumePlaybackAfterSubtitleSwitch(
                    shouldResume = shouldResume,
                    resumePositionMs = resumePositionMs,
                )
                refreshRuntimeTracks(force = true)
            }
            return
        }
        val trackId = desiredSubtitleTrackId ?: return
        val currentTrackId = runCatching { mediaPlayer.getSpuTrack() }.getOrNull()
        if (currentTrackId == trackId) {
            return
        }
        val shouldResume = playWhenReadyRequested || mediaPlayer.isPlaying
        val resumePositionMs = mediaPlayer.time.coerceAtLeast(0L)
        lastSubtitleSelectionRealtimeMs = SystemClock.elapsedRealtime()
        Log.i(Tag, "subtitle switch select=$trackId current=$currentTrackId url=$streamUrl")
        runCatching {
            mediaPlayer.setSpuTrack(trackId)
            resumePlaybackAfterSubtitleSwitch(
                shouldResume = shouldResume,
                resumePositionMs = resumePositionMs,
            )
            refreshRuntimeTracks(force = true)
        }
    }

    private fun applyDesiredAudioSelection() {
        val trackId = desiredAudioTrackId ?: return
        val currentTrackId = runCatching { mediaPlayer.getAudioTrack() }.getOrNull()
        if (currentTrackId == trackId) {
            return
        }
        runCatching {
            mediaPlayer.setAudioTrack(trackId)
            refreshRuntimeTracks(force = true)
        }
    }

    private fun markPlaybackProgress(force: Boolean = false) {
        val nowMs = SystemClock.elapsedRealtime()
        val positionMs = mediaPlayer.time.coerceAtLeast(0L)
        val positionAdvanced = positionMs > lastProgressPositionMs
        if (force || positionAdvanced || lastProgressRealtimeMs == 0L) {
            lastProgressPositionMs = max(lastProgressPositionMs, positionMs)
            lastProgressRealtimeMs = nowMs
        }
    }

    private fun shouldTreatBufferingAsActive(
        bufferingPercent: Float,
        positionMs: Long,
        nowMs: Long,
    ): Boolean {
        if (bufferingPercent >= 99.5f) {
            return false
        }
        val positionAdvanced = positionMs > lastProgressPositionMs
        if (positionAdvanced) {
            lastProgressPositionMs = positionMs
            lastProgressRealtimeMs = nowMs
            return false
        }
        if (!hasRenderedFirstFrame) {
            return true
        }
        val lastProgressAgeMs = if (lastProgressRealtimeMs > 0L) {
            nowMs - lastProgressRealtimeMs
        } else {
            Long.MAX_VALUE
        }
        val subtitleSwitchAgeMs = if (lastSubtitleSelectionRealtimeMs > 0L) {
            nowMs - lastSubtitleSelectionRealtimeMs
        } else {
            Long.MAX_VALUE
        }
        val recentlyProgressed = lastProgressAgeMs <= BufferingProgressGraceMs
        val withinSubtitleSwitchGrace = subtitleSwitchAgeMs <= SubtitleSwitchGraceMs
        return !(recentlyProgressed || (withinSubtitleSwitchGrace && positionMs > 0L))
    }

    private fun resumePlaybackAfterSubtitleSwitch(
        shouldResume: Boolean,
        resumePositionMs: Long,
    ) {
        subtitleRecoveryGeneration += 1
        val generation = subtitleRecoveryGeneration
        if (shouldResume) {
            playWhenReadyRequested = true
            runCatching { mediaPlayer.play() }
        }
        if (resumePositionMs > 0L) {
            runCatching { mediaPlayer.time = resumePositionMs }
        }
        if (mediaPlayer.isPlaying || hasRenderedFirstFrame) {
            isPlaying = shouldResume
            isBuffering = false
            markPlaybackProgress(force = true)
        }
        scheduleSubtitleRecovery(
            generation = generation,
            delayMs = SubtitleRecoveryFastDelayMs,
            shouldResume = shouldResume,
            resumePositionMs = resumePositionMs,
        )
        scheduleSubtitleRecovery(
            generation = generation,
            delayMs = SubtitleRecoverySlowDelayMs,
            shouldResume = shouldResume,
            resumePositionMs = resumePositionMs,
        )
    }

    private fun scheduleSubtitleRecovery(
        generation: Int,
        delayMs: Long,
        shouldResume: Boolean,
        resumePositionMs: Long,
    ) {
        mainHandler.postDelayed(
            {
                if (generation != subtitleRecoveryGeneration) {
                    return@postDelayed
                }
                val nowPositionMs = mediaPlayer.time.coerceAtLeast(0L)
                val stalled = nowPositionMs <= resumePositionMs && !mediaPlayer.isPlaying
                if (!stalled && nowPositionMs > 0L) {
                    return@postDelayed
                }
                Log.w(
                    Tag,
                    "subtitle recovery retry delayMs=$delayMs stalled=$stalled playWhenReady=$playWhenReadyRequested position=$nowPositionMs target=$resumePositionMs url=$streamUrl",
                )
                if (shouldResume) {
                    playWhenReadyRequested = true
                    runCatching { mediaPlayer.play() }
                }
                if (resumePositionMs > 0L) {
                    runCatching {
                        mediaPlayer.time = (resumePositionMs - 150L).coerceAtLeast(0L)
                    }
                }
                isPlaying = shouldResume
                isBuffering = true
            },
            delayMs,
        )
    }

    private fun guessTrackLanguage(label: String?): String? {
        val value = label.orEmpty().lowercase(Locale.US)
        return when {
            value.contains("chi") || value.contains("chinese") || value.contains("中文") -> "zh"
            value.contains("eng") || value.contains("english") || value.contains("英文") -> "en"
            value.contains("jpn") || value.contains("japanese") || value.contains("日文") -> "ja"
            value.contains("kor") || value.contains("korean") || value.contains("韩文") -> "ko"
            else -> null
        }
    }

    private fun notifyFirstFrameRendered() {
        if (!hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            onFirstFrameRendered?.invoke()
        }
    }

    private fun updateBitrateEstimate() {
        val stats = media?.stats
        if (stats == null) {
            bitrateEstimateBitsPerSecond = 0L
            return
        }
        val nowMs = SystemClock.elapsedRealtime()
        val totalBytesRead = max(stats.readBytes, stats.demuxReadBytes).toLong().coerceAtLeast(0L)
        if (lastStatsSampleTimeMs == 0L) {
            lastStatsBytesRead = totalBytesRead
            lastStatsSampleTimeMs = nowMs
            lastNonZeroStatsSampleTimeMs = nowMs
            bitrateEstimateBitsPerSecond = 0L
            return
        }
        val bytesDelta = when {
            totalBytesRead >= lastStatsBytesRead -> totalBytesRead - lastStatsBytesRead
            else -> totalBytesRead
        }
        val timeDeltaMs = (nowMs - lastStatsSampleTimeMs).coerceAtLeast(1L)
        if (bytesDelta > 0L) {
            val instantBitrate = bytesDelta * 8_000L / timeDeltaMs
            smoothedBitrateBitsPerSecond = when {
                smoothedBitrateBitsPerSecond <= 0L -> instantBitrate
                else -> ((smoothedBitrateBitsPerSecond * 0.58) + (instantBitrate * 0.42)).toLong()
            }
            lastNonZeroStatsSampleTimeMs = nowMs
        } else if (nowMs - lastNonZeroStatsSampleTimeMs > 1_500L) {
            smoothedBitrateBitsPerSecond = 0L
        }
        lastStatsBytesRead = totalBytesRead
        lastStatsSampleTimeMs = nowMs
        bitrateEstimateBitsPerSecond = smoothedBitrateBitsPerSecond.coerceAtLeast(0L)
    }

    private fun Media.applyRequestHeaders(requestHeaders: Map<String, String>) {
        requestHeaders.forEach { (name, value) ->
            val headerName = name.trim()
            val headerValue = value.trim()
            if (headerName.isBlank() || headerValue.isBlank()) return@forEach
            when (headerName.lowercase(Locale.US)) {
                "user-agent" -> addOption(":http-user-agent=$headerValue")
                "referer", "referrer", "origin" -> addOption(":http-referrer=$headerValue")
                "cookie" -> addOption(":http-cookie=$headerValue")
                else -> addOption(":http-header=$headerName=$headerValue")
            }
        }
    }

    private fun buildLibVlcOptions(
        requestHeaders: Map<String, String>,
        forceSoftwareDecode: Boolean,
    ): ArrayList<String> {
        val options = arrayListOf(
            "--file-caching=1500",
            "--network-caching=1500",
            "--live-caching=1500",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--http-reconnect",
        )
        options += if (forceSoftwareDecode) "--avcodec-hw=none" else "--avcodec-hw=any"
        requestHeaders.forEach { (name, value) ->
            val headerValue = value.trim()
            if (headerValue.isBlank()) return@forEach
            when (name.trim().lowercase(Locale.US)) {
                "user-agent" -> options += "--http-user-agent=$headerValue"
                "referer", "referrer", "origin" -> options += "--http-referrer=$headerValue"
            }
        }
        return options
    }
}
