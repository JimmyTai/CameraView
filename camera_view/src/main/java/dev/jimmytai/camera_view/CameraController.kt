package dev.jimmytai.camera_view

import android.content.Context
import android.util.Size
import android.view.SurfaceView
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import dev.jimmytai.camera_view.utils.Logger

class CameraController(context: Context, cameraSize: Size) {
    companion object {
        private val TAG = CameraController::class.java.simpleName
    }

    private var mCameraSource: CameraSource = CameraSource(context, cameraSize)
    private var mSurfaceProvider: GLSurfaceProvider? = null

    private var onCameraPreviewAction: OnCameraPreviewAction? = null

    internal fun attachFromView(
        surfaceView: SurfaceView,
        onCameraPreviewAction: OnCameraPreviewAction
    ) {
        Logger.d(TAG, "attachFromView")
        this.onCameraPreviewAction = onCameraPreviewAction
        this.mSurfaceProvider = GLSurfaceProvider(surfaceView)
    }

    internal fun onViewResumed() {
        handleCameraResume()
    }

    fun startPreview() {
        val surfaceProvider: GLSurfaceProvider? = mSurfaceProvider
        if (surfaceProvider == null) {
            Logger.e(TAG, "Please attach controller to view first.")
            return
        }
        mCameraSource.startPreview(surfaceProvider)
    }

    fun stopPreview() {
        mCameraSource.stopPreview()
    }

    fun release() {
        mCameraSource.release()
        mSurfaceProvider?.release()
        mSurfaceProvider = null
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

    internal interface OnCameraPreviewAction
}