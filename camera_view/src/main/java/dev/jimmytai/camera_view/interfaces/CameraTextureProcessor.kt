package dev.jimmytai.camera_view.interfaces

import android.util.Size

interface CameraTextureProcessor {
    fun onPreProcessTexture(
        textureId: Int,
        cameraSize: Size,
        textureSize: Size,
        transformMatrix: FloatArray
    ): Int?

    fun onProcessTexture(
        textureId: Int,
        cameraSize: Size,
        textureSize: Size,
        transformMatrix: FloatArray
    ): Int?

    fun onRenderTexture(
        textureId: Int,
        cameraSize: Size,
        textureSize: Size,
        surfaceSize: Size,
        transformMatrix: FloatArray,
        isDisplayWindow: Boolean
    ): Boolean

    fun onProcessEnd(
        cameraSize: Size,
        surfaceSize: Size,
    )
}