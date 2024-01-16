package dev.jimmytai.camera_view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.SurfaceView
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import dev.jimmytai.camera_view.glthread.GLThreadCallback
import dev.jimmytai.camera_view.glthread.SurfaceViewGLThread
import dev.jimmytai.camera_view.interfaces.CameraTextureProcessor
import dev.jimmytai.camera_view.utils.Logger

class CameraController(context: Context, cameraSize: Size) : GLThreadCallback {
    companion object {
        private val TAG = CameraController::class.java.simpleName
    }

    private var mCameraSource: CameraSource = CameraSource(context, cameraSize)

    private var mGlThread: SurfaceViewGLThread? = null

    private var mSurfaceProvider: GLSurfaceProvider =
        GLSurfaceProvider { resolution, rotationDegrees ->
            mGlThread?.updateCameraConfigs(resolution, rotationDegrees)
        }

    private var mSurfaceTexture: SurfaceTexture? = null

    private var mCameraTextureProcessor: CameraTextureProcessor? = null

    internal fun attachFromView(
        surfaceView: SurfaceView,
    ) {
        Logger.d(TAG, "attachFromView")
        val glThread =
            SurfaceViewGLThread("GL_THREAD", surfaceView, this).also { this.mGlThread = it }
        glThread.start()
    }

    internal fun onViewResumed() {
        handleCameraResume()
    }

    fun setCameraTextureProcessor(
        processor: CameraTextureProcessor?,
        preProcessEnable: Boolean = true,
        renderOnScreenEnable: Boolean = true,
    ) {
        val glThread: SurfaceViewGLThread? = mGlThread
        if (glThread == null) {
            Logger.d(TAG, "please attach controller to CameraView first")
            return
        }
        mCameraTextureProcessor = if (processor == null) {
            glThread.updateTextureProcessConfigs(
                preProcessEnable = true,
                renderOnScreenEnable = true
            )
            null
        } else {
            glThread.updateTextureProcessConfigs(preProcessEnable, renderOnScreenEnable)
            processor
        }
    }

    fun startPreview() {
        mCameraSource.startPreview(mSurfaceProvider)
    }

    fun stopPreview() {
        mCameraSource.stopPreview()
    }

    fun release() {
        mCameraTextureProcessor = null

        mSurfaceTexture = null

        mCameraSource.release()

        mSurfaceProvider.release()

        mGlThread?.release()
        mGlThread = null
    }

    private fun handleCameraResume() {
        val cameraInfo: CameraInfo = mCameraSource.cameraInfo ?: return
        val cameraState: CameraState? = cameraInfo.cameraState.value
        when (cameraState?.type) {
            null, CameraState.Type.PENDING_OPEN, CameraState.Type.CLOSING, CameraState.Type.CLOSED -> {
                Logger.d(TAG, "handleCameraResume -> resume camera due to reason \"$cameraState\"")
                startPreview()
            }

            else -> {
                Logger.d(TAG, "handleCameraResume -> no need to re-open camera")
            }
        }
    }

    override fun onCreateSurfaceTexture(surfaceTexture: SurfaceTexture) {
        Logger.d(TAG, "onCreateSurfaceTexture")
        mSurfaceTexture = surfaceTexture
        mSurfaceProvider.provideSurfaceTexture(surfaceTexture)
        setOnFrameAvailableListener(surfaceTexture)
    }

    override fun onProcessTexture(
        textureId: Int,
        cameraSize: Size,
        surfaceSize: Size,
        transformMatrix: FloatArray
    ): Int = mCameraTextureProcessor?.onProcessTexture(
        textureId,
        cameraSize,
        surfaceSize,
        transformMatrix
    ) ?: textureId

    private fun setOnFrameAvailableListener(surfaceTexture: SurfaceTexture) {
        surfaceTexture.setOnFrameAvailableListener {
            mGlThread?.process()
        }
    }
}