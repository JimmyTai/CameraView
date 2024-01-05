package dev.jimmytai.camera_view

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.SurfaceView

class CameraController(private val context: Context, private val cameraSize: Size) {
    companion object {
        private val TAG = CameraController::class.java.name
    }

    private var mCameraSource: CameraSource? = null
    private var onCameraPreviewAction: OnCameraPreviewAction? = null
    private var mSurfaceProvider: GLSurfaceProvider? = null

    internal fun attachFromView(
        surfaceView: SurfaceView,
        onCameraPreviewAction: OnCameraPreviewAction
    ) {
        this.onCameraPreviewAction = onCameraPreviewAction
        this.mSurfaceProvider = GLSurfaceProvider(surfaceView)
        mCameraSource = CameraSource(context, cameraSize)
        onCameraPreviewAction.onCameraSizeChanged(cameraSize)
    }

    fun startPreview() {
        val surfaceProvider: GLSurfaceProvider? = mSurfaceProvider
        val cameraSource: CameraSource? = mCameraSource
        if (surfaceProvider == null || cameraSource == null) {
            Log.e(TAG, "Please attach controller to view first.")
            return
        }
        cameraSource.startPreview(surfaceProvider)
    }

    fun stopPreview() {
        val cameraSource: CameraSource? = mCameraSource
        if (cameraSource == null) {
            Log.e(TAG, "Please attach controller to view first.")
            return
        }
        cameraSource.stopPreview()
    }

    fun release() {
        mCameraSource?.release()
        mCameraSource = null
        mSurfaceProvider?.release()
        mSurfaceProvider = null
    }

    internal interface OnCameraPreviewAction {
        fun onCameraSizeChanged(size: Size)
    }
}