package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

abstract class BaseMPVView(
    context: Context,
    attrs: AttributeSet?,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    fun initialize(
        configDir: String,
        cacheDir: String,
    ) {
        MPVLib.requireAvailable()
        MPVLib.create(context)
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        arrayOf("gpu-shader-cache-dir", "icc-cache-dir").forEach { option ->
            MPVLib.setOptionString(option, cacheDir)
        }
        initOptions()
        MPVLib.init()
        postInitOptions()
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")
        holder.addCallback(this)
        observeProperties()
    }

    fun destroyPlayer() {
        holder.removeCallback(this)
        MPVLib.destroy()
    }

    protected abstract fun initOptions()
    protected abstract fun postInitOptions()
    protected abstract fun observeProperties()

    private var filePath: String? = null
    private var voInUse: String = "gpu-next"

    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    fun setVo(vo: String) {
        voInUse = vo
        MPVLib.setOptionString("vo", vo)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "attach mpv surface")
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        val pendingPath = filePath
        if (pendingPath != null) {
            MPVLib.command(arrayOf("loadfile", pendingPath))
            filePath = null
        } else {
            MPVLib.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "detach mpv surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
    }

    private companion object {
        const val TAG = "AurePMPV"
    }
}
