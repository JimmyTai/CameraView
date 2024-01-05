package dev.jimmytai.camera_view.glrenderer

import android.content.Context
import android.graphics.Point
import android.opengl.GLES20
import dev.jimmytai.camera_view.gles.Drawable2d
import dev.jimmytai.camera_view.gles.GlUtil
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/*
 * Porting from BytePlus and make a little improvement.
 */

abstract class ShaderProgram(private val vertexShader: String, private val fragmentShader: String) {
    companion object {
        private val TAG: String = ShaderProgram::class.java.simpleName

        const val FRAME_BUFFER_NUM: Int = 1
    }

    // Handles to the GL program and various components of it.
    protected var mProgramId: Int = GlUtil.createProgram(vertexShader, fragmentShader)

    protected val mDrawable2d: Drawable2d by lazy { getDrawable2D() }

    /*
     * Variables for off-screen FBO
     */
    protected var mFrameBuffers: IntArray? = null

    protected var mFrameBufferTextures: IntArray? = null

    protected var mFrameBufferShape: Point? = null

    constructor(
        context: Context,
        vertexShaderResId: Int,
        fragmentShaderResId: Int
    ) : this(
        context.readTextFileFromResource(vertexShaderResId),
        context.readTextFileFromResource(fragmentShaderResId)
    )

    abstract fun getDrawable2D(): Drawable2d

    /**
     * get locations of attributes and uniforms
     *
     * 設定shader的coordinates與matrix參數
     */
    abstract fun getLocations()

    abstract fun drawFrameOnScreen(textureId: Int, width: Int, height: Int, mvpMatrix: FloatArray)

    abstract fun drawFrameOffScreen(
        textureId: Int,
        width: Int,
        height: Int,
        mvpMatrix: FloatArray
    ): Int

    abstract fun drawFrameOffScreenForCompare(
        textureId: Int,
        srcTextureId: Int,
        progress: Float,
        width: Int,
        height: Int,
        mvpMatrix: FloatArray
    ): Int

    abstract fun readBuffer(textureId: Int, width: Int, height: Int): ByteBuffer?

    protected fun initFrameBufferIfNeed(width: Int, height: Int) {
        var need = false
        if (mFrameBufferShape?.x != width || mFrameBufferShape?.y != height) {
            need = true
        }
        if (mFrameBuffers == null || mFrameBufferTextures == null) {
            need = true
        }

        if (need) {
            destroyFrameBuffers()
            val frameBuffers = IntArray(FRAME_BUFFER_NUM).also { mFrameBuffers = it }
            val frameBufferTextures = IntArray(FRAME_BUFFER_NUM).also { mFrameBufferTextures = it }
            GLES20.glGenFramebuffers(FRAME_BUFFER_NUM, frameBuffers, 0)
            GLES20.glGenTextures(FRAME_BUFFER_NUM, frameBufferTextures, 0)
            for (i in 0 until FRAME_BUFFER_NUM) {
                bindFrameBuffer(
                    textureId = frameBufferTextures[i],
                    frameBuffer = frameBuffers[i],
                    width,
                    height
                )
            }
            mFrameBufferShape = Point(width, height)
        }
    }

    /**
     * {zh}
     * 纹理参数设置+buffer绑定
     * set texture params
     * and bind buffer
     * <br>
     * {en}
     * Texture parameter setting + buffer binding
     * set texture params
     * and binding buffer
     */
    private fun bindFrameBuffer(textureId: Int, frameBuffer: Int, width: Int, height: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
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

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureId, 0
        )

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun destroyFrameBuffers() {
        mFrameBufferTextures?.let {
            GLES20.glDeleteTextures(FRAME_BUFFER_NUM, it, 0)
        }
        mFrameBufferTextures = null

        mFrameBuffers?.let {
            GLES20.glDeleteFramebuffers(FRAME_BUFFER_NUM, it, 0)
        }
        mFrameBuffers = null
    }

    /**
     * Releases the program.
     * <p>
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    fun release() {
        destroyFrameBuffers()
        GLES20.glDeleteProgram(mProgramId)
        mProgramId = -1
    }
}

fun Context.readTextFileFromResource(resId: Int): String {
    val inputStream: InputStream = resources.openRawResource(resId)
    val data: ByteArray =
        try {
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes)
            inputStream.close()
            bytes
        } catch (e: IOException) {
            e.printStackTrace()
            ByteArray(0)
        }
    return String(data)
}
