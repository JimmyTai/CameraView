package dev.jimmytai.camera_view

import android.content.Context
import android.util.Range
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.common.util.concurrent.ListenableFuture
import dev.jimmytai.camera_view.utils.Logger

class CameraSource(
    private val context: Context,
    private val preferredCameraSize: Size
) {
    companion object {
        private val TAG: String = CameraSource::class.java.simpleName
    }

    private val cameraLifecycleOwner: CameraLifecycleOwner = CameraLifecycleOwner()

    private var mCameraProvider: ProcessCameraProvider? = null
    private var mPreview: Preview? = null
    private var mCamera: Camera? = null

    val cameraInfo: CameraInfo?
        get() = mCamera?.cameraInfo

    fun startPreview(surfaceProvider: GLSurfaceProvider) {
        Logger.d(TAG, "start preview")
        stopPreview()

        cameraLifecycleOwner.doOnCreate()
        cameraLifecycleOwner.doOnStart()
        cameraLifecycleOwner.doOnResume()

        initCameraWhenCreated(surfaceProvider)
    }

    fun stopPreview() {
        Logger.d(TAG, "stop preview")
        cameraLifecycleOwner.doOnPause()
        cameraLifecycleOwner.doOnStop()
        cameraLifecycleOwner.doOnDestroy()

        mPreview?.setSurfaceProvider(null)
        mPreview = null

        mCamera?.cameraInfo?.cameraState?.removeObservers(cameraLifecycleOwner)
        mCamera = null

        mCameraProvider?.unbindAll()
        mCameraProvider = null
    }

    fun release() {
        stopPreview()
    }

    private fun initCameraWhenCreated(surfaceProvider: GLSurfaceProvider) {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context.applicationContext)
        cameraProviderFuture.addListener(
            {
                try {
                    mCameraProvider = cameraProviderFuture.get()
                } catch (e: Exception) {
                    // TODO: report error to developer
                    e.printStackTrace()
                }

                val preferredCameraSize: Size = this.preferredCameraSize
                val builder: Preview.Builder = Preview.Builder()

                val resolutionSelectorBuilder: ResolutionSelector.Builder =
                    ResolutionSelector.Builder()
                        .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                resolutionSelectorBuilder
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            preferredCameraSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                        )
                    ).setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()

                val preview: Preview =
                    builder
                        .setResolutionSelector(resolutionSelectorBuilder.build())
                        .setTargetFrameRate(Range(15, 30))
                        .build()
                preview.setSurfaceProvider(surfaceProvider)
                mPreview = preview

                setupCamera()
            },
            ContextCompat.getMainExecutor(context.applicationContext)
        )
    }

    private fun setupCamera() {
        val cameraProvider: ProcessCameraProvider = mCameraProvider ?: return
        cameraProvider.unbindAll()

        val cameraSelector: CameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        val hasCamera: Boolean = try {
            cameraProvider.hasCamera(cameraSelector)
        } catch (e: CameraInfoUnavailableException) {
            Logger.e(TAG, "Cannot find camera", e)
            false
        }

        if (hasCamera) {
            if (cameraLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
            val camera: Camera =
                cameraProvider.bindToLifecycle(
                    cameraLifecycleOwner,
                    cameraSelector,
                    mPreview
                )
                    .also { mCamera = it }
            camera.cameraInfo.cameraState.observe(cameraLifecycleOwner) { state ->
                Logger.d(TAG, "camera state: $state")
            }
        } else {
            // TODO: report error to developer
        }
    }
}