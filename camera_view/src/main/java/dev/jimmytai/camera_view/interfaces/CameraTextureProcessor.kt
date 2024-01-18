package dev.jimmytai.camera_view.interfaces

import android.util.Size

interface CameraTextureProcessor {
    /**
     * Texture預處理的接口
     *
     * @return null 代表外部不處理
     *         int  為外部處理後的texture ID
     */
    fun onPreProcessTexture(
        textureId: Int,
        cameraSize: Size,
        textureSize: Size,
        transformMatrix: FloatArray
    ): Int?

    /**
     * Texture處理的接口，通常給Beauty Effect SDK使用
     *
     * @return null 代表外部不處理
     *         int  為外部處理後的texture ID
     */
    fun onProcessTexture(
        textureId: Int,
        cameraSize: Size,
        textureSize: Size,
        transformMatrix: FloatArray
    ): Int?

    /**
     * Texture渲染的接口
     *
     * @return false 代表外部不處理
     *         true  代表外部已經自行處理
     */
    fun onRenderTexture(
        textureId: Int,
        cameraSize: Size,
        textureSize: Size,
        surfaceSize: Size,
        transformMatrix: FloatArray,
        isDisplayWindow: Boolean
    ): Boolean

    /**
     * 當前Texture處理的最後，可以用來釋放資源
     */
    fun onProcessEnd(
        cameraSize: Size,
        surfaceSize: Size,
    )
}