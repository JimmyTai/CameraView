package dev.jimmytai.camera_view

import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import dev.jimmytai.camera_view.glthread.GLThreadCallback
import dev.jimmytai.camera_view.glthread.SurfaceViewGLThread
import dev.jimmytai.camera_view.utils.Logger
import java.util.concurrent.Executors

class GLSurfaceProvider(surfaceView: SurfaceView) : Preview.SurfaceProvider, GLThreadCallback {
    companion object {
        private val TAG: String = GLSurfaceProvider::class.java.simpleName
    }

    private val glThread: SurfaceViewGLThread = SurfaceViewGLThread("GL_THREAD", surfaceView, this)
    private var mSurfaceTexture: SurfaceTexture? = null

    init {
        glThread.start()
    }

    override fun onCreateSurfaceTexture(surfaceTexture: SurfaceTexture) {
        Logger.d(TAG, "onCreateSurfaceTexture")
        mSurfaceTexture = surfaceTexture
        setOnFrameAvailableListener()
    }

    override fun onGLContextCreated() {
    }

    override fun onCustomProcessTexture(
        textureId: Int,
        textureWidth: Int,
        textureHeight: Int
    ): Int {
        return textureId
    }

    override fun onGLContextDestroy() {
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        val resolution: Size = request.resolution
        request.setTransformationInfoListener(Executors.newSingleThreadExecutor()) { transformationInfo ->
            Logger.d(TAG, "resolution size: $resolution, rotation: ${transformationInfo.rotationDegrees}")
            glThread.onCameraSizeChange(
                resolution.width,
                resolution.height,
                transformationInfo.rotationDegrees
            )
        }

        val surfaceTexture: SurfaceTexture = mSurfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(resolution.width, resolution.height)
        val surface = Surface(surfaceTexture)
        request.provideSurface(surface, Executors.newSingleThreadExecutor()) { result ->
            if (result.resultCode == SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY) {
                result.surface.release()
            }
        }
    }

    private fun setOnFrameAvailableListener() {
        mSurfaceTexture!!.setOnFrameAvailableListener {
            glThread.process()
        }
    }

    fun release() {
        glThread.release()
    }
}