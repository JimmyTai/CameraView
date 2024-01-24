package dev.jimmytai.camera_view.glrenderer

import android.graphics.Bitmap
import android.graphics.Point
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Size
import dev.jimmytai.camera_view.constant.CropScaleType
import dev.jimmytai.camera_view.constant.PixelFormat
import dev.jimmytai.camera_view.constant.TextureFormat
import dev.jimmytai.camera_view.gles.GlUtil
import dev.jimmytai.camera_view.gles.Matrix4Util
import dev.jimmytai.camera_view.utils.Logger
import java.nio.ByteBuffer

class GLRenderer {
    companion object {
        private val TAG: String = GLRenderer::class.java.simpleName

        private const val FRAME_BUFFER_NUM = 1
    }

    protected var mFrameBuffers: IntArray? = null

    protected var mFrameBufferTextures: IntArray? = null

    protected var mFrameBufferShape: Point? = null

    protected var mTextureIds: IntArray? = null

    private var mProgramManager: ShaderProgramManager? = null

    private val programManager: ShaderProgramManager
        get() = mProgramManager ?: ShaderProgramManager().also { mProgramManager = it }

    /**
     * 默认的离屏渲染绑定的纹理
     * @return 纹理id
     */
    val outputTexture: Int
        get() {
            val frameBufferTextures: IntArray = mFrameBufferTextures ?: return GlUtil.NO_TEXTURE
            return frameBufferTextures[0]
        }

    /**
     * 准备帧缓冲区纹理对象
     *
     * @param width  纹理宽度
     * @param height 纹理高度
     * @return 纹理ID
     */
    fun prepareTexture(size: Size): Int {
        initFrameBufferIfNeed(size)
        return mFrameBufferTextures!![0]
    }

    /**
     * 初始化帧缓冲区
     *
     * @param width  缓冲的纹理宽度
     * @param height 缓冲的纹理高度
     */
    private fun initFrameBufferIfNeed(size: Size) {
        var need = false
        if (mFrameBufferShape?.x != size.width || mFrameBufferShape?.y != size.height) {
            need = true
        }
        if (mFrameBuffers == null || mFrameBufferTextures == null) {
            need = true
        }

        if (need) {
            destroyFrameBuffers()
            val frameBuffers = IntArray(ShaderProgram.FRAME_BUFFER_NUM).also { mFrameBuffers = it }
            val frameBufferTextures =
                IntArray(ShaderProgram.FRAME_BUFFER_NUM).also { mFrameBufferTextures = it }
            GLES20.glGenFramebuffers(ShaderProgram.FRAME_BUFFER_NUM, frameBuffers, 0)
            GLES20.glGenTextures(ShaderProgram.FRAME_BUFFER_NUM, frameBufferTextures, 0)
            for (i in 0 until ShaderProgram.FRAME_BUFFER_NUM) {
                bindFrameBuffer(
                    textureId = frameBufferTextures[i],
                    frameBuffer = frameBuffers[i],
                    size
                )
            }
            mFrameBufferShape = Point(size.width, size.height)
        }
    }

    /**
     * 纹理参数设置+buffer绑定
     * set texture params
     * and bind buffer
     */
    private fun bindFrameBuffer(textureId: Int, frameBuffer: Int, size: Size) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size.width, size.height, 0,
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

    /**
     * 销毁帧缓冲区对象
     */
    private fun destroyFrameBuffers() {
        mFrameBufferTextures?.let {
            GLES20.glDeleteTextures(ShaderProgram.FRAME_BUFFER_NUM, it, 0)
        }
        mFrameBufferTextures = null

        mFrameBuffers?.let {
            GLES20.glDeleteFramebuffers(ShaderProgram.FRAME_BUFFER_NUM, it, 0)
        }
        mFrameBuffers = null
    }

    /**
     * 释放资源，包括帧缓冲区及Program对象
     */
    fun release() {
        destroyFrameBuffers()

        mProgramManager?.release()
        mProgramManager = null

        mTextureIds?.let {
            GLES20.glDeleteTextures(1, it, 0)
        }
        mTextureIds = null
    }

    /**
     * @param inputTexture        输入纹理
     * @param inputTextureFormat  输入纹理格式，2D/OES
     * @param outputTextureFormat 输出纹理格式，2D/OES
     * @param width               输入纹理的宽
     * @param height              输入纹理的高
     * @param transition          纹理变换方式
     * @return 输出纹理
     * @brief 纹理转纹理
     */
    fun transferTextureToTexture(
        inputTextureId: Int, inputTextureFormat: TextureFormat, outputTextureFormat: TextureFormat,
        size: Size, transition: Transition
    ): Int {
        if (outputTextureFormat != TextureFormat.Texture2D) {
            Logger.e(
                TAG,
                "The inputTexture is not supported,please use Texture2D as output texture format"
            )
            return GlUtil.NO_TEXTURE
        }

        val targetRotated: Boolean = (transition.angle % 180 == 90)
        return programManager.getProgram(inputTextureFormat).drawFrameOffScreen(
            inputTextureId,
            if (targetRotated) size.height else size.width,
            if (targetRotated) size.width else size.height,
            transition.matrix
        )
    }

    fun transferTextureToTexture(
        inputTextureId: Int,
        inputTextureFormat: TextureFormat,
        outputTextureFormat: TextureFormat,
        size: Size,
        transition: Transition,
        mUVMatrix: FloatArray
    ): Int {
        if (outputTextureFormat != TextureFormat.Texture2D) {
            Logger.e(
                TAG,
                "The inputTexture is not supported,please use Texture2D as output texture format"
            )
            return GlUtil.NO_TEXTURE
        }

        val targetRotated: Boolean = (transition.angle % 180 == 90)
        if (inputTextureFormat == TextureFormat.TextureOES) {
            val oesProgram: ShaderProgramOES =
                programManager.getProgram(inputTextureFormat) as ShaderProgramOES
            // 传入uv matrix，该矩阵从视频流中获取，仅在oes转2d纹理时会使用
            return oesProgram.drawFrameOffscreen(
                inputTextureId,
                if (targetRotated) size.height else size.width,
                if (targetRotated) size.width else size.height,
                transition.matrix,
                mUVMatrix
            )
        }
        return programManager.getProgram(inputTextureFormat).drawFrameOffScreen(
            inputTextureId,
            if (targetRotated) size.height else size.width,
            if (targetRotated) size.width else size.height,
            transition.matrix
        )
    }

    /**
     * @param textureId       纹理
     * @param inputTextureFormat 纹理格式，2D/OES
     * @param outputPixelFormat  输出 buffer 格式
     * @param width         宽
     * @param height        高
     * @return 输出 buffer
     * @brief 纹理转 buffer
     */
    fun transferTextureToBuffer(
        textureId: Int,
        inputTextureFormat: TextureFormat,
        outputPixelFormat: PixelFormat,
        size: Size,
        ratio: Float
    ): ByteBuffer? {
        if (outputPixelFormat != PixelFormat.RGBA8888) {
            Logger.e(
                TAG,
                "The outputFormat is not supported,please use RGBA8888 as output texture format"
            )
            return null
        }
        return programManager.getProgram(inputTextureFormat)
            .readBuffer(textureId, (size.width * ratio).toInt(), (size.height * ratio).toInt())
    }

    fun transferTextureToBitmap(
        textureId: Int,
        inputTextureFormat: TextureFormat,
        size: Size
    ): Bitmap? {
        val buffer: ByteBuffer = transferTextureToBuffer(
            textureId,
            inputTextureFormat,
            PixelFormat.RGBA8888,
            size,
            1.0f
        ) ?: return null
        return transferBufferToBitmap(buffer, PixelFormat.RGBA8888, size)
    }

    /**
     * @param buffer 输入 buffer
     * @param pixelFormat 输入 buffer 格式
     * @param width  宽
     * @param height 高
     * @return 输出 bitmap
     * @brief buffer 转 bitmap
     */
    fun transferBufferToBitmap(
        buffer: ByteBuffer,
        pixelFormat: PixelFormat,
        size: Size,
    ): Bitmap? {
        if (pixelFormat != PixelFormat.RGBA8888) {
            Logger.e(
                TAG,
                "transferBufferToBitmap: the inputFormat is not supported,please use RGBA8888"
            )
            return null
        }

        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)

        buffer.position(0)
        bitmap.copyPixelsFromBuffer(buffer)
        buffer.position(0)

        return bitmap
    }

    /**
     * @param buffer                输入 buffer
     * @param inputPixelFormat      buffer 格式
     * @param outputTextureFormat   输出纹理格式
     * @param width                 宽
     * @param height                高
     * @param consistent            传入的buffer是否在一张纹理上更新
     * @return 输出纹理
     * @brief buffer 转纹理
     */
    fun transferBufferToTexture(
        buffer: ByteBuffer,
        inputPixelFormat: PixelFormat,
        outputTextureFormat: TextureFormat,
        size: Size,
        consistent: Boolean
    ): Int {
        if (inputPixelFormat != PixelFormat.RGBA8888) {
            Logger.e(TAG, "InputFormat support RGBA8888 only")
            return GlUtil.NO_TEXTURE
        }

        if (outputTextureFormat != TextureFormat.Texture2D) {
            Logger.e(TAG, "outputFormat support Texture2D only")
            return GlUtil.NO_TEXTURE
        }

        val textureId: Int?
        if (!consistent) {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            textureId = textureIds[0]
            GlUtil.checkGlError("glGenTextures")
        } else {
            val textureIds = mTextureIds ?: IntArray(1).also {
                mTextureIds = it
                GLES20.glGenTextures(1, it, 0)
                GlUtil.checkGlError("glGenTextures")
            }
            textureId = textureIds[0]
        }
        create2DTexture(buffer, size, GLES20.GL_RGBA, textureId)
        return textureId
    }

    private fun create2DTexture(
        data: ByteBuffer,
        size: Size,
        @Suppress("SameParameterValue") format: Int,
        textureHandle: Int
    ) {
        // Bind the texture handle to the 2D texture target.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        // Configure min/mag filtering, i.e. what scaling method do we use if what we're rendering
        // is smaller or larger than the source image.
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GlUtil.checkGlError("loadImageTexture")

        // Load the data from the buffer into the texture handle.
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, format,
            size.width, size.height, 0, format, GLES20.GL_UNSIGNED_BYTE, data
        )
        GlUtil.checkGlError("loadImageTexture")
    }

    fun transferTextureToScreen(
        textureId: Int,
        srcTextureFormat: TextureFormat,
        surfaceSize: Size,
        mvpMatrix: FloatArray
    ) {
        programManager.getProgram(srcTextureFormat)
            .drawFrameOnScreen(textureId, surfaceSize.width, surfaceSize.height, mvpMatrix)
    }

    class Transition {
        private var mMVPMatrix: FloatArray = FloatArray(16)

        private var mAngle: Int = 0

        val angle: Int
            get() = mAngle % 360

        val matrix: FloatArray
            get() = mMVPMatrix

        init {
            Matrix.setIdentityM(mMVPMatrix, 0)
        }

        /**
         * @brief 镜像
         */
        fun flip(x: Boolean = false, y: Boolean = false): Transition {
            Matrix4Util.flip(mMVPMatrix, x, y)
            return this
        }

        /**
         * @param angle 旋转角度，仅支持 0/90/180/270
         * @brief 旋转
         */
        fun rotate(angle: Float): Transition {
            mAngle += angle.toInt()
            Matrix4Util.rotate(mMVPMatrix, angle)
            return this
        }

        fun crop(
            scaleType: CropScaleType,
            rotation: Int,
            textureSize: Size,
            surfaceSize: Size
        ): Transition {
            if (rotation % 180 == 90) {
                Matrix4Util.crop(
                    mMVPMatrix, scaleType,
                    textureSize.height, textureSize.width, surfaceSize.width, surfaceSize.height
                )
            } else {
                Matrix4Util.crop(
                    mMVPMatrix, scaleType,
                    textureSize.width, textureSize.height, surfaceSize.width, surfaceSize.height
                )
            }
            return this
        }

        /**
         * @return 逆向后的 transition
         * @brief 逆向生成新的 transition
         * @details 变换操作有顺序之分，本方法可以将一系列操作逆序，
         * 如将先镜像再旋转，逆序为先旋转再镜像
         */
        fun reverse(): Transition {
            val invertedMatrix = FloatArray(16)
            if (Matrix.invertM(invertedMatrix, 0, mMVPMatrix, 0)) {
                mMVPMatrix = invertedMatrix
            }
            return this
        }

        override fun toString(): String {
            val sb = StringBuilder()
            for (value in mMVPMatrix) {
                sb.append(value).append("  ")
            }
            return sb.toString()
        }
    }
}