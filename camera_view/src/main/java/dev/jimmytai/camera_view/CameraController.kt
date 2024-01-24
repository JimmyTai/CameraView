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

    /**
     * 相機主要操作
     */
    private var mCameraSource: CameraSource = CameraSource(context, cameraSize)

    /**
     * 渲染執行緒，負責將資料繪至目標的Surface上
     */
    private var mGlThread: SurfaceViewGLThread? = null

    /**
     * CameraX的API，主要負責連接Camera與SurfaceTexture，讓相機畫面綁定texture
     */
    private var mSurfaceProvider: GLSurfaceProvider =
        GLSurfaceProvider { resolution, rotationDegrees ->
            mCameraResolution = resolution
            mGlThread?.updateCameraConfigs(resolution, rotationDegrees)
        }

    /**
     * 相機所使用的像素
     */
    private var mCameraResolution: Size? = null

    /**
     * 相機資料綁定的SurfaceTexture
     */
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

    /**
     * Open camera and start producing data on screen.
     */
    fun startPreview() {
        mCameraSource.startPreview(mSurfaceProvider)
    }

    /**
     * Close camera and stop producing data on screen
     */
    fun stopPreview() {
        mCameraSource.stopPreview()
    }

    private var mRecorder: MediaRecorder? = null
    private var mSurface: Surface? = null

    /**
     * Start record video with configurations
     * @param filePath      Where to store the video file.
     * @param outputSize    Width/Height of the output video.
     * @param config        Video/Audio configuration for output file.
     */
    fun startRecord(filePath: String, outputSize: Size?, config: VideoRecorderConfig) {
        val glThread: SurfaceViewGLThread? = mGlThread
        if (glThread == null) {
            Logger.e(TAG, "GLThread is not running, please attach controller to CameraView first")
            return
        }

        // 如果未指定outputSize，則使用當前螢幕長寬為output file長寬尺寸
        val finalOutputSize: Size = outputSize ?: glThread.surfaceViewSize
        val recorder =
            config.createRecorder(context, filePath, finalOutputSize).also {
                mRecorder = it
            }
        try {
            recorder.prepare()

            // 獲取MediaRecorder的Surface，Surface為資料的接口
            val surface: Surface = recorder.surface.also { mSurface = it }

            // 將Surface新增進GLThread中，GLThread會將畫面多畫進這個Surface
            val outputSurfaceOption = OutputSurfaceOption(outputSize = finalOutputSize)
            glThread.addOutputSurface(surface, outputSurfaceOption)

            // 開始錄影
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
            // 停止錄影
            recorder.stop()

            // 將MediaRecorder的Surface從GLThread中移除
            val surface: Surface? = mSurface
            if (surface != null) {
                glThread.removeOutputSurface(surface)
            }

            // 釋放MediaRecorder相關資源
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

    /**
     * 檢查相機是否需要被重新開啟
     */
    private fun handleCameraResume() {
        // 可以透過cameraInfo判斷目前是不是在Preview中
        val cameraInfo: CameraInfo = mCameraSource.cameraInfo ?: return
        val cameraState: CameraState? = cameraInfo.cameraState.value
        when (cameraState?.type) {
            // 如果相機不是開啟的狀態，重新打開相機
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