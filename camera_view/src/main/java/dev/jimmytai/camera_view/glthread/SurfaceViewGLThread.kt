package dev.jimmytai.camera_view.glthread

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jimmytai.camera_view.gles.EglCore
import dev.jimmytai.camera_view.gles.WindowSurface

class SurfaceViewGLThread(name: String, surfaceView: SurfaceView, callback: GLThreadCallback) :
    GLThread(name, callback = callback), SurfaceHolder.Callback {

    private var mSurface: Surface? = null

    init {
        surfaceView.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mSurface = holder.surface
        initGL()
    }

    /*
     * It will be triggered after `surfaceCreated`
     */
    override fun createWindowSurface(eglCore: EglCore): WindowSurface {
        val windowSurface = WindowSurface(eglCore, mSurface, false)
        windowSurface.makeCurrent()
        return windowSurface
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    override fun releaseInputData() {
        mSurface?.release()
    }
}