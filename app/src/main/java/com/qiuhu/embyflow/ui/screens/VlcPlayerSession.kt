package com.qiuhu.embyflow.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.qiuhu.embyflow.data.emby.EmbySubtitleTrack
import java.util.Locale
import kotlin.math.roundToLong
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

internal class VlcPlayerSession(
    context: Context,
    private val streamUrl: String,
    subtitleTracks: List<EmbySubtitleTrack>,
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
    }

    private val appContext = context.applicationContext
    private val libVlc = LibVLC(appContext, buildLibVlcOptions(requestHeaders, forceSoftwareDecode))
    private val mediaPlayer = MediaPlayer(libVlc)
    private var media: Media? = null
    private var initialStateApplied = false
    private var pendingErrorMessage: String? = null

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
        bitrateEstimateBitsPerSecond = media
            ?.stats
            ?.inputBitrate
            ?.times(1_000f)
            ?.roundToLong()
            ?.coerceAtLeast(0L)
            ?: 0L
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
        mediaPlayer.setEventListener(null)
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { media?.release() }
        runCatching { libVlc.release() }
    }

    private fun prepareMedia(
        subtitleTracks: List<EmbySubtitleTrack>,
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
        applyInitialPlaybackState()
        syncState()
    }

    private fun applyInitialPlaybackState() {
        if (initialStateApplied) return
        initialStateApplied = true
        if (startPositionMs > 0L) {
            runCatching {
                mediaPlayer.time = startPositionMs
            }
        }
        setPlaybackSpeed(playbackSpeed)
        if (!playWhenReady) {
            playWhenReadyRequested = false
            runCatching {
                mediaPlayer.pause()
            }
            isPlaying = false
        }
    }

    private fun handleEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening,
            MediaPlayer.Event.Buffering,
            MediaPlayer.Event.Playing,
            MediaPlayer.Event.Vout,
            MediaPlayer.Event.EncounteredError,
            -> Log.i(
                Tag,
                "event type=${event.type} buffering=${runCatching { event.getBuffering() }.getOrNull()} vout=${runCatching { event.getVoutCount() }.getOrNull()} time=${mediaPlayer.time}",
            )
        }
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                isBuffering = true
                isEnded = false
            }

            MediaPlayer.Event.Buffering -> {
                isBuffering = event.getBuffering() < 99.5f
                isEnded = false
            }

            MediaPlayer.Event.Playing -> {
                applyInitialPlaybackState()
                notifyFirstFrameRendered()
                isPlaying = true
                isBuffering = false
                isEnded = false
            }

            MediaPlayer.Event.TimeChanged,
            MediaPlayer.Event.PositionChanged,
            MediaPlayer.Event.Vout,
            -> {
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
        syncState()
    }

    private fun notifyFirstFrameRendered() {
        if (!hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            onFirstFrameRendered?.invoke()
        }
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
