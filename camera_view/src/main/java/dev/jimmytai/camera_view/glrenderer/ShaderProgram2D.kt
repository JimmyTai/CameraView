package dev.jimmytai.camera_view.glrenderer

import android.opengl.GLES20
import android.opengl.Matrix
import dev.jimmytai.camera_view.gles.Drawable2d
import dev.jimmytai.camera_view.gles.GlUtil
import dev.jimmytai.camera_view.utils.Logger
import java.nio.ByteBuffer

class ShaderProgram2D : ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D) {
    companion object {
        private val TAG = ShaderProgram2D::class.java.simpleName

        const val VERTEX_SHADER =
            """
                uniform mat4 uMVPMatrix;
                attribute vec4 aPosition;
                attribute vec2 aTextureCoord;
                varying vec2 vTextureCoord;
                
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = aTextureCoord;
                }
            """

        const val FRAGMENT_SHADER_2D =
            """
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform sampler2D sTexture;
                
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """
    }

    private var maPositionLoc: Int? = null

    private var maTextureCoordLoc: Int? = null

    private var muMVPMatrixLoc: Int? = null

    private var muTextureLoc: Int? = null

    override fun getDrawable2D(): Drawable2d = Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE)

    override fun getLocations() {
        val aPositionLoc: Int =
            GLES20.glGetAttribLocation(mProgramId, "aPosition").also { maPositionLoc = it }
        GlUtil.checkLocation(aPositionLoc, "aPosition")

        val aTextureCoordLoc: Int =
            GLES20.glGetAttribLocation(mProgramId, "aTextureCoord").also { maTextureCoordLoc = it }
        GlUtil.checkLocation(aTextureCoordLoc, "aTextureCoord")

        val uMVPMatrixLoc: Int =
            GLES20.glGetUniformLocation(mProgramId, "uMVPMatrix").also { muMVPMatrixLoc = it }
        GlUtil.checkLocation(uMVPMatrixLoc, "uMVPMatrix")

        val uTextureLoc: Int =
            GLES20.glGetUniformLocation(mProgramId, "sTexture").also { muTextureLoc = it }
        GlUtil.checkLocation(uTextureLoc, "sTexture")
    }

    override fun drawFrameOnScreen(textureId: Int, width: Int, height: Int, mvpMatrix: FloatArray) {
        GlUtil.checkGlError("draw start")

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        // Select the program.
        GLES20.glUseProgram(mProgramId)
        GlUtil.checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc!!, 1, false, mvpMatrix, 0)
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

        GLES20.glViewport(0, 0, width, height)

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mDrawable2d.vertexCount)
        GlUtil.checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc!!)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc!!)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    override fun drawFrameOffScreen(
        textureId: Int,
        width: Int,
        height: Int,
        mvpMatrix: FloatArray
    ): Int {
        GlUtil.checkGlError("draw start")

        initFrameBufferIfNeed(width, height)
        GlUtil.checkGlError("initFrameBufferIfNeed")

        // Select the program.
        GLES20.glUseProgram(mProgramId)
        GlUtil.checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GlUtil.checkGlError("glBindTexture")

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers!![0])
        GlUtil.checkGlError("glBindFramebuffer")

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc!!, 1, false, mvpMatrix, 0)
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
            GLES20.GL_FLOAT, false, Drawable2d.TEXTURE_COORD_STRIDE, mDrawable2d.texCoordArrayFB
        )
        GlUtil.checkGlError("glVertexAttribPointer")

        GLES20.glViewport(0, 0, width, height)

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mDrawable2d.vertexCount)
        GlUtil.checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc!!)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc!!)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
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
     * 读取渲染结果的buffer
     * @param width 目标宽度
     * @param height 目标高度
     * @return 渲染结果的像素Buffer 格式RGBA
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
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )
        GLES20.glTexParameterf(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat()
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textures[0], 0
        )

        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Logger.e(TAG, "framebuffer not set correctly")
            return null
        }

        GLES20.glUseProgram(mProgramId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(muTextureLoc!!, 0)

        val mMVPMatrix = FloatArray(16)
        Matrix.setIdentityM(mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc!!, 1, false, mMVPMatrix, 0)

        GLES20.glEnableVertexAttribArray(maPositionLoc!!)
        GLES20.glVertexAttribPointer(
            maPositionLoc!!, Drawable2d.COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, Drawable2d.VERTEX_STRIDE, mDrawable2d.vertexArray
        )

        GLES20.glEnableVertexAttribArray(maTextureCoordLoc!!)
        GLES20.glVertexAttribPointer(
            maTextureCoordLoc!!, 2,
            GLES20.GL_FLOAT, false, Drawable2d.TEXTURE_COORD_STRIDE, mDrawable2d.texCoordArrayFB
        )

        GLES20.glViewport(0, 0, width, height)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mDrawable2d.vertexCount)
        GlUtil.checkGlError("glDrawArrays")

        GLES20.glReadPixels(
            0, 0, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, captureBuffer
        )

        GLES20.glDisableVertexAttribArray(maPositionLoc!!)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc!!)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteTextures(1, textures, 0)
        GLES20.glUseProgram(0)

        GLES20.glDeleteFramebuffers(1, frameBuffer, 0)

        return captureBuffer
    }
}