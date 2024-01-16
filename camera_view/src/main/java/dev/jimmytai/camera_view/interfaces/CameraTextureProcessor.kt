package dev.jimmytai.camera_view.interfaces

import android.util.Size

interface CameraTextureProcessor {
    fun onProcessTexture(
        textureId: Int,
        cameraSize: Size,
        surfaceSize: Size,
        transformMatrix: FloatArray
    ): Int
}