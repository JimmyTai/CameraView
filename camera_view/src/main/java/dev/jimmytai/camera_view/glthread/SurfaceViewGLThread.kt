package dev.jimmytai.camera_view.glthread

import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jimmytai.camera_view.gles.EglCore
import dev.jimmytai.camera_view.gles.WindowSurface
import dev.jimmytai.camera_view.utils.Logger

class SurfaceViewGLThread(name: String, private val surfaceView: SurfaceView, callback: GLThreadCallback) :
    GLThread(name, callback = callback), SurfaceHolder.Callback {

    private var mSurface: Surface? = null

    init {
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceCreated")
        mSurface = holder.surface
        initGL()
    }

    /*
     * It will be triggered after `surfaceCreated`
     */
    override fun createWindowSurface(eglCore: EglCore): WindowSurface {
        Logger.d(TAG, "createWindowSurface")
        val windowSurface = WindowSurface(eglCore, mSurface, false)
        windowSurface.makeCurrent()
        return windowSurface
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Logger.d(TAG, "surfaceChanged -> format: $format, width: $width, height: $height")
        updateSurfaceConfigs(Size(width, height))
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG, "surfaceDestroyed")
        pause()
    }

    override fun releaseInputData() {
        Logger.d(TAG, "releaseInputData")
        mSurface?.release()
        surfaceView.holder.removeCallback(this)
    }
}