package dev.jimmytai.camera_view

import android.content.Context
import android.util.Range
import android.util.Size
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.common.util.concurrent.ListenableFuture
import dev.jimmytai.camera_view.utils.Logger

class CameraSource(
    private val context: Context,
    private val cameraSize: Size
) {
    companion object {
        private val TAG: String = CameraSource::class.java.simpleName
    }

    private val cameraLifecycleOwner: CameraLifecycleOwner = CameraLifecycleOwner()

    private var mCameraProvider: ProcessCameraProvider? = null
    private var mPreview: Preview? = null

    init {
        cameraLifecycleOwner.doOnCreate()
    }

    val cameraWidth: Int
        get() = cameraSize.width

    val cameraHeight: Int
        get() = cameraSize.height

    fun startPreview(surfaceProvider: GLSurfaceProvider) {
        cameraLifecycleOwner.doOnStart()
        cameraLifecycleOwner.doOnResume()
        initCameraWhenCreated(surfaceProvider)
    }

    fun stopPreview() {
        cameraLifecycleOwner.doOnPause()
        cameraLifecycleOwner.doOnStop()
    }

    fun release() {
        mCameraProvider?.unbindAll()
        mPreview?.setSurfaceProvider(null)
        cameraLifecycleOwner.doOnDestroy()
    }

    private fun initCameraWhenCreated(surfaceProvider: GLSurfaceProvider) {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context.applicationContext)
        cameraProviderFuture.addListener(
            {
                try {
                    mCameraProvider = cameraProviderFuture.get()
                } catch (e: Exception) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                val cameraSize: Size = this.cameraSize
                val builder: Preview.Builder = Preview.Builder()

                val resolutionSelectorBuilder: ResolutionSelector.Builder =
                    ResolutionSelector.Builder()
                        .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                resolutionSelectorBuilder
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            cameraSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                        )
                    )
                    .build()

                val preview: Preview =
                    builder.setResolutionSelector(resolutionSelectorBuilder.build())
                        .setTargetFrameRate(Range(15, 30)).build()
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
            cameraProvider.bindToLifecycle(cameraLifecycleOwner, cameraSelector, mPreview)
        } else {
            // TODO: report error to developer
        }
    }

    interface OnCamera
}