package `is`.xyz.mpv

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.ContextCompat
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_INT64
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_NONE
import `is`.xyz.mpv.MPVLib.MpvFormat.MPV_FORMAT_STRING
import java.io.File
import kotlin.reflect.KProperty

class AurePMpvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val requestHeaders: Map<String, String> = emptyMap(),
    private val hwdecOption: String = DEFAULT_HWDEC_OPTION,
    private val enableHdrToneMapping: Boolean = false,
) : BaseMPVView(context, attrs) {
    override fun initOptions() {
        MPVLib.setOptionString("profile", "fast")
        // `gpu-next + mediacodec` can render black on some Android devices because
        // the ImageReader interop path times out. Stay on the more stable GPU VO.
        setVo("gpu")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = ContextCompat.getDisplayOrDefault(context)
            val refreshRate = display.mode.refreshRate
            Log.i(TAG, "display ${display.displayId} refreshRate=$refreshRate")
            MPVLib.setOptionString("display-fps-override", refreshRate.toString())
        }

        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", hwdecOption)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("input-default-bindings", "no")
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-pause", "yes")
        MPVLib.setOptionString("cache-pause-wait", "1")
        MPVLib.setOptionString("network-timeout", "30")
        MPVLib.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${64 * 1024 * 1024}")
        MPVLib.setOptionString("vd-lavc-fast", "yes")
        MPVLib.setOptionString("hr-seek", "yes")

        if (enableHdrToneMapping) {
            // HDR/Dolby Vision content is safer through MPV's GPU pipeline than
            // direct surface output on some Android devices, otherwise audio can
            // play while the video layer stays black.
            MPVLib.setOptionString("target-colorspace-hint", "yes")
            MPVLib.setOptionString("tone-mapping", "bt.2390")
            MPVLib.setOptionString("hdr-compute-peak", "yes")
        }

        ensureCaCertFile(context)?.let { certFile ->
            MPVLib.setOptionString("tls-verify", "yes")
            MPVLib.setOptionString("tls-ca-file", certFile.absolutePath)
        }

        val userAgent = requestHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.trim()
        if (!userAgent.isNullOrBlank()) {
            MPVLib.setOptionString("user-agent", userAgent)
        }

        val referrer = requestHeaders.entries.firstOrNull {
            it.key.equals("Referer", ignoreCase = true) || it.key.equals("Referrer", ignoreCase = true)
        }?.value?.trim()
        if (!referrer.isNullOrBlank()) {
            MPVLib.setOptionString("referrer", referrer)
        }

        val headerFields = requestHeaders.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) {
                    null
                } else {
                    "$normalizedKey: $normalizedValue"
                }
            }
            .joinToString(separator = ",")
        if (headerFields.isNotBlank()) {
            MPVLib.setOptionString("http-header-fields", headerFields)
        }
    }

    override fun postInitOptions() {
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        data class Property(
            val name: String,
            val format: Int = MPV_FORMAT_NONE,
        )

        listOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration/full", MPV_FORMAT_DOUBLE),
            Property("cache-speed", MPV_FORMAT_INT64),
            Property("pause", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("track-list"),
            Property("current-tracks/audio/selected"),
            Property("current-tracks/sub/selected"),
            Property("stream-open-filename", MPV_FORMAT_STRING),
            Property("path", MPV_FORMAT_STRING),
        ).forEach { property ->
            MPVLib.observeProperty(property.name, property.format)
        }
    }

    fun addObserver(observer: MPVLib.EventObserver) {
        MPVLib.addObserver(observer)
    }

    fun removeObserver(observer: MPVLib.EventObserver) {
        MPVLib.removeObserver(observer)
    }

    fun addLogObserver(observer: MPVLib.LogObserver) {
        MPVLib.addLogObserver(observer)
    }

    fun removeLogObserver(observer: MPVLib.LogObserver) {
        MPVLib.removeLogObserver(observer)
    }

    data class Track(
        val mpvId: Int,
        val name: String,
        val language: String?,
    )

    fun loadTracks(): Map<String, List<Track>> {
        val tracks = linkedMapOf(
            "audio" to mutableListOf<Track>(),
            "sub" to mutableListOf<Track>(),
        )
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        for (index in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$index/type") ?: continue
            val targetList = tracks[type] ?: continue
            val mpvId = MPVLib.getPropertyInt("track-list/$index/id") ?: continue
            if (mpvId < 0) continue
            val language = MPVLib.getPropertyString("track-list/$index/lang")
            val title = MPVLib.getPropertyString("track-list/$index/title")
            val name = buildString {
                append(title?.takeIf { it.isNotBlank() } ?: "轨道 $mpvId")
                language?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
            }
            targetList.add(
                Track(
                    mpvId = mpvId,
                    name = name,
                    language = language,
                ),
            )
        }
        return tracks
    }

    fun addExternalSubtitle(
        url: String,
        select: Boolean,
    ) {
        val mode = if (select) "select" else "auto"
        MPVLib.command(arrayOf("sub-add", url, mode))
    }

    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(value) = value?.let { MPVLib.setPropertyBoolean("pause", it) } ?: Unit

    var timePos: Double?
        get() = MPVLib.getPropertyDouble("time-pos/full") ?: MPVLib.getPropertyDouble("time-pos")
        set(value) = value?.let { MPVLib.setPropertyDouble("time-pos", it) } ?: Unit

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(value) = value?.let { MPVLib.setPropertyDouble("speed", it) } ?: Unit

    val currentPath: String?
        get() = MPVLib.getPropertyString("stream-open-filename")
            ?: MPVLib.getPropertyString("path")

    fun setAudioSessionId(id: Int) {
        MPVLib.setPropertyInt("audiotrack-session-id", id)
        MPVLib.setPropertyInt("aaudio-session-id", id)
    }

    class TrackDelegate(
        private val name: String,
    ) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val value = MPVLib.getPropertyString(name)
            return value?.toIntOrNull() ?: -1
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value < 0) {
                MPVLib.setPropertyString(name, "no")
            } else {
                MPVLib.setPropertyInt(name, value)
            }
        }
    }

    var sid: Int by TrackDelegate("sid")
    var aid: Int by TrackDelegate("aid")

    companion object {
        private const val TAG = "AurePMPV"
        private const val DEFAULT_HWDEC_OPTION = "mediacodec,mediacodec-copy"

        private fun ensureCaCertFile(context: Context): File? {
            return runCatching {
                val target = File(context.filesDir, "cacert.pem")
                if (!target.exists() || target.length() <= 0L) {
                    context.assets.open("cacert.pem").use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                target
            }.getOrNull()
        }
    }
}
