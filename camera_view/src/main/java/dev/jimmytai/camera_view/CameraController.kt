package dev.jimmytai.camera_view

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.util.Size
import android.view.Surface
import android.view.SurfaceView
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import dev.jimmytai.camera_view.glthread.GLThreadCallback
import dev.jimmytai.camera_view.glthread.SurfaceViewGLThread
import dev.jimmytai.camera_view.interfaces.CameraTextureProcessor
import dev.jimmytai.camera_view.model.OutputSurfaceOption
import dev.jimmytai.camera_view.recorder.VideoRecorderConfig
import dev.jimmytai.camera_view.recorder.createRecorder
import dev.jimmytai.camera_view.utils.Logger

class CameraController(
    private val context: Context,
    cameraSize: Size,
    private val cameraTextureProcessor: CameraTextureProcessor
) : GLThreadCallback {
    companion object {
        private val TAG = CameraController::class.java.simpleName
    }

    private var mCameraSource: CameraSource = CameraSource(context, cameraSize)

    private var mGlThread: SurfaceViewGLThread? = null

    private var mSurfaceProvider: GLSurfaceProvider =
        GLSurfaceProvider { resolution, rotationDegrees ->
            mCameraResolution = resolution
            mGlThread?.updateCameraConfigs(resolution, rotationDegrees)
        }

    private var mCameraResolution: Size? = null

    private var mSurfaceTexture: SurfaceTexture? = null

    internal fun attachFromView(
        surfaceView: SurfaceView,
    ) {
        Logger.d(TAG, "attachFromView")
        val glThread =
            SurfaceViewGLThread(
                "GL_THREAD",
                surfaceView,
                this,
                cameraTextureProcessor
            ).also { this.mGlThread = it }
        glThread.start()
    }

    internal fun onViewResumed() {
        handleCameraResume()
    }

    fun startPreview() {
        mCameraSource.startPreview(mSurfaceProvider)
    }

    fun stopPreview() {
        mCameraSource.stopPreview()
    }

    private var mRecorder: MediaRecorder? = null
    private var mSurface: Surface? = null

    fun startRecord(filePath: String, outputSize: Size?, config: VideoRecorderConfig) {
        val glThread: SurfaceViewGLThread? = mGlThread
        if (glThread == null) {
            Logger.e(TAG, "GLThread is not running, please attach controller to CameraView first")
            return
        }

        val finalOutputSize: Size = outputSize ?: glThread.surfaceViewSize
        val recorder =
            config.createRecorder(context, filePath, finalOutputSize).also {
                mRecorder = it
            }
        try {
            recorder.prepare()
            val surface: Surface = recorder.surface.also { mSurface = it }
            val outputSurfaceOption = OutputSurfaceOption(outputSize = finalOutputSize)
            glThread.addOutputSurface(surface, outputSurfaceOption)
            recorder.start()
        } catch (e: Exception) {
            // TODO: report error to developer
            e.printStackTrace()
        }
    }

    fun stopRecord() {
        val glThread: SurfaceViewGLThread = mGlThread ?: return
        val recorder: MediaRecorder = mRecorder ?: return
        try {
            recorder.stop()
            val surface: Surface? = mSurface
            if (surface != null) {
                glThread.removeOutputSurface(surface)
            }
            recorder.release()
        } catch (e: Exception) {
            e.printStackTrace()
            // TODO: report error to developer
        }
    }

    fun release() {
        mSurfaceTexture = null

        mCameraSource.release()

        mSurfaceProvider.release()

        mGlThread?.release()
        mGlThread = null

        mRecorder?.release()
        mRecorder = null
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

    private fun setOnFrameAvailableListener(surfaceTexture: SurfaceTexture) {
        surfaceTexture.setOnFrameAvailableListener {
            mGlThread?.process()
        }
    }
}