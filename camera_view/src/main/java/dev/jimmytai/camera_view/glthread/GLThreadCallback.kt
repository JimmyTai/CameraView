package dev.jimmytai.camera_view.glthread

import android.graphics.SurfaceTexture
import android.util.Size

interface GLThreadCallback {
    fun onCreateSurfaceTexture(surfaceTexture: SurfaceTexture)

    fun onProcessTexture(textureId: Int, cameraSize: Size, surfaceSize: Size, transformMatrix: FloatArray): Int
}
