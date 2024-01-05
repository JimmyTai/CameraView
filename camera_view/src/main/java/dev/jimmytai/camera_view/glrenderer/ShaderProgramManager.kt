package dev.jimmytai.camera_view.glrenderer

import dev.jimmytai.camera_view.constant.TextureFormat

class ShaderProgramManager {
    private var mShaderProgram2D: ShaderProgram2D? = null

    private var mShaderProgramOES: ShaderProgramOES? = null

    fun getProgram(srcTextureFormat: TextureFormat): ShaderProgram =
        when (srcTextureFormat) {
            TextureFormat.TextureOES -> {
                mShaderProgramOES ?: ShaderProgramOES().also { mShaderProgramOES = it }
            }

            TextureFormat.Texture2D -> {
                mShaderProgram2D ?: ShaderProgram2D().also { mShaderProgram2D = it }
            }
        }

    fun release() {
        mShaderProgramOES?.release()
        mShaderProgramOES = null

        mShaderProgram2D?.release()
        mShaderProgram2D = null
    }
}