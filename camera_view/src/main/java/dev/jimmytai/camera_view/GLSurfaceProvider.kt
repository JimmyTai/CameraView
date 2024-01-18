package dev.jimmytai.camera_view

import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import dev.jimmytai.camera_view.utils.Logger
import java.util.concurrent.Executors

typealias OnCameraResolutionSelected = (resolution: Size, rotationDegrees: Int) -> Unit

/**
 * 主要負責將GLThread生成的SurfaceTexture提供給CameraX，讓相機資料可以輸出至SurfaceTexture中
 * 後續GLThread就可以透過這個SurfaceTexture提取Texture做後續處理
 */
class GLSurfaceProvider(
    private val onCameraResolutionSelected: OnCameraResolutionSelected
) : Preview.SurfaceProvider {
    companion object {
        private val TAG: String = GLSurfaceProvider::class.java.simpleName
    }

    private var mSurfaceRequest: SurfaceRequest? = null

    private var mSurfaceTexture: SurfaceTexture? = null

    override fun onSurfaceRequested(request: SurfaceRequest) {
        mSurfaceRequest = request
        val resolution: Size = request.resolution
        request.setTransformationInfoListener(Executors.newSingleThreadExecutor()) { transformationInfo ->
            Logger.d(
                TAG,
                "resolution size: $resolution, rotation: ${transformationInfo.rotationDegrees}"
            )
            onCameraResolutionSelected.invoke(resolution, transformationInfo.rotationDegrees)
        }
        val surfaceTexture: SurfaceTexture = mSurfaceTexture ?: return
        provideSurfaceTexture(surfaceTexture, force = true)
    }

    fun provideSurfaceTexture(surfaceTexture: SurfaceTexture) {
        provideSurfaceTexture(surfaceTexture, force = false)
    }

    private fun provideSurfaceTexture(surfaceTexture: SurfaceTexture, force: Boolean) {
        val request: SurfaceRequest? = mSurfaceRequest
        if ((mSurfaceTexture != surfaceTexture || force) && request != null) {
            val resolution: Size = request.resolution
            surfaceTexture.setDefaultBufferSize(resolution.width, resolution.height)
            val surface = Surface(surfaceTexture)
            Logger.d(TAG, "SurfaceRequest provider surface")
            request.provideSurface(surface, Executors.newSingleThreadExecutor()) { result ->
                if (result.resultCode == SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY) {
                    result.surface.release()
                }
            }
        }
        mSurfaceTexture = surfaceTexture
    }

    fun release() {
        mSurfaceTexture = null
        mSurfaceRequest = null
    }
}