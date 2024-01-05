package dev.jimmytai.camera_view.glrenderer

import android.opengl.GLES11Ext
import android.opengl.GLES20
import dev.jimmytai.camera_view.gles.Drawable2d
import dev.jimmytai.camera_view.gles.GlUtil
import java.nio.ByteBuffer

class ShaderProgramOES : ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT) {
    companion object {
        // Simple vertex shader, used for all programs.
        private const val VERTEX_SHADER =
            """ 
                uniform mat4 uMVPMatrix;
                uniform mat4 uUVMatrix;
                attribute vec4 aPosition;
                attribute vec2 aTextureCoord;
                varying vec2 vTextureCoord;
                
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vec4 TexCoord = uUVMatrix * vec4(aTextureCoord, 0.0, 1.0);
                    vTextureCoord = TexCoord.xy;
                }
            """

        // Simple fragment shader for use with external 2D textures (e.g. what we get from SurfaceTexture).
        private const val FRAGMENT_SHADER_EXT =
            """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """
    }

    private var muMVPMatrixLoc: Int? = null

    private var muUVMatrixLoc: Int? = null

    private var maPositionLoc: Int? = null

    private var maTextureCoordLoc: Int? = null

    private val identityMat: FloatArray = floatArrayOf(
        1.0f, 0.0f,
        0.0f, 0.0f,
        0.0f, 1.0f,
        0.0f, 0.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 0.0f,
        0.0f, 1.0f
    )

    override fun getDrawable2D(): Drawable2d = Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE)

    override fun getLocations() {
        val aPositionLoc =
            GLES20.glGetAttribLocation(mProgramId, "aPosition").also { maPositionLoc = it }
        GlUtil.checkLocation(aPositionLoc, "aPosition")

        val aTextureCoordLoc =
            GLES20.glGetAttribLocation(mProgramId, "aTextureCoord").also { maTextureCoordLoc = it }
        GlUtil.checkLocation(aTextureCoordLoc, "aTextureCoord")

        val uMVPMatrixLoc =
            GLES20.glGetUniformLocation(mProgramId, "uMVPMatrix").also { muMVPMatrixLoc = it }
        GlUtil.checkLocation(uMVPMatrixLoc, "uMVPMatrix")

        val uUVMatrixLoc =
            GLES20.glGetUniformLocation(mProgramId, "uUVMatrix").also { muUVMatrixLoc = it }
        GlUtil.checkLocation(uUVMatrixLoc, "uUVMatrix")
    }

    override fun drawFrameOnScreen(textureId: Int, width: Int, height: Int, mvpMatrix: FloatArray) {
        GlUtil.checkGlError("draw start")

        // Select the program.
        GLES20.glUseProgram(mProgramId)
        GlUtil.checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc!!, 1, false, mvpMatrix, 0)
        GlUtil.checkGlError("glUniformMatrix4fv")

        GLES20.glUniformMatrix4fv(muUVMatrixLoc!!, 1, false, identityMat, 0)
        GlUtil.checkGlError("glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc!!)
        GlUtil.checkGlError("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(
            maPositionLoc!!, Drawable2d.COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, Drawable2d.VERTEX_STRIDE, mDrawable2d.vertexArray
        )
        GlUtil.checkGlError("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc!!)
        GlUtil.checkGlError("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(
            maTextureCoordLoc!!, 2,
            GLES20.GL_FLOAT, false, Drawable2d.TEXTURE_COORD_STRIDE, mDrawable2d.texCoordArray
        )
        GlUtil.checkGlError("glVertexAttribPointer")

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mDrawable2d.vertexCount)
        GlUtil.checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc!!)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc!!)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
    }

    override fun drawFrameOffScreen(
        textureId: Int,
        width: Int,
        height: Int,
        mvpMatrix: FloatArray
    ): Int = drawFrameOffscreen(textureId, width, height, mvpMatrix, identityMat)

    fun drawFrameOffscreen(
        textureId: Int,
        width: Int,
        height: Int,
        mvpMatrix: FloatArray,
        uvMatrix: FloatArray
    ): Int {
        if (mvpMatrix.contentEquals(identityMat) && uvMatrix.contentEquals(identityMat)) {
            // invalid uv input
            return 0
        }
        GlUtil.checkGlError("draw start")
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        initFrameBufferIfNeed(width, height)
        GlUtil.checkGlError("initFrameBufferIfNeed")

        // Select the program.
        GLES20.glUseProgram(mProgramId)
        GlUtil.checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GlUtil.checkGlError("glBindTexture")

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![0])
        GlUtil.checkGlError("glBindFramebuffer")

        // Copy the model / view / projection matrix over.
        mvpMatrix[5] *= -1.0f
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc!!, 1, false, mvpMatrix, 0)
        GlUtil.checkGlError("glUniformMatrix4fv")

        GLES20.glUniformMatrix4fv(muUVMatrixLoc!!, 1, false, uvMatrix, 0)
        GlUtil.checkGlError("glUniformMatrix4fv")

        GLES20.glViewport(0, 0, width, height)

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc!!)
        GlUtil.checkGlError("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(
            maPositionLoc!!, Drawable2d.COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, Drawable2d.VERTEX_STRIDE, mDrawable2d.vertexArray
        )
        GlUtil.checkGlError("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc!!)
        GlUtil.checkGlError("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(
            maTextureCoordLoc!!,
            2,
            GLES20.GL_FLOAT,
            false,
            Drawable2d.TEXTURE_COORD_STRIDE,
            mDrawable2d.texCoordArrayFB
        )
        GlUtil.checkGlError("glVertexAttribPointer")

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mDrawable2d.vertexCount)
        GlUtil.checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc!!)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc!!)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glUseProgram(0)

        return mFrameBufferTextures!![0]
    }

    override fun drawFrameOffScreenForCompare(
        textureId: Int,
        srcTextureId: Int,
        progress: Float,
        width: Int,
        height: Int,
        mvpMatrix: FloatArray
    ): Int = 0

    private var mWidth: Int = 0

    private var mHeight: Int = 0

    private var mCaptureBuffer: ByteBuffer? = null

    /**
     * {zh}
     * 读取渲染结果的buffer
     * @param width 目标宽度
     * @param height 目标高度
     * @return 渲染结果的像素Buffer 格式RGBA
     * <br>
     * {en}
     * Read the buffer
     * @param width target width
     * @param height target height
     * @return pixel Buffer  format of the rendered result RGBA
     */
    override fun readBuffer(textureId: Int, width: Int, height: Int): ByteBuffer? {
        if (textureId == GlUtil.NO_TEXTURE) return null
        if (width * height == 0) return null

        val captureBuffer: ByteBuffer =
            if (mCaptureBuffer == null || mWidth * mHeight != width * height) {
                mWidth = width
                mHeight = height
                ByteBuffer.allocateDirect(width * height * 4)
            } else {
                mCaptureBuffer!!
            }.also { mCaptureBuffer = it }
        captureBuffer.position(0)

        val frameBuffer = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBuffer, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureId,
            0
        )
        GLES20.glReadPixels(
            0, 0, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, captureBuffer
        )

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, frameBuffer, 0)

        return captureBuffer
    }
}