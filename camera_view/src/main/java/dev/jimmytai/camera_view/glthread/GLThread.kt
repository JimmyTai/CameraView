package dev.jimmytai.camera_view.glthread

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Process
import android.util.Size
import dev.jimmytai.camera_view.constant.CropScaleType
import dev.jimmytai.camera_view.constant.TextureFormat
import dev.jimmytai.camera_view.gles.EglCore
import dev.jimmytai.camera_view.gles.GlUtil
import dev.jimmytai.camera_view.gles.WindowSurface
import dev.jimmytai.camera_view.glrenderer.GLRenderer
import dev.jimmytai.camera_view.utils.Logger

abstract class GLThread(
    name: String,
    priority: Int = Process.THREAD_PRIORITY_DEFAULT,
    private val callback: GLThreadCallback? = null
) :
    HandlerThread(name, priority) {
    companion object {
        val TAG: String = GLThread::class.java.simpleName

        // 初始化OpenGL環境
        const val INIT: Int = 0

        // 處理Texture
        const val PROCESS: Int = 1

        // 銷毀OpenGL環境
        const val RELEASE: Int = 2

        const val UPDATE_CAMERA_CONFIGS = 3

        const val UPDATE_SURFACE_CONFIGS = 4

        const val UPDATE_TEXTURE_PROCESS_CONFIGS: Int = 5
    }

    private object CameraConfigs {
        const val SIZE = "size"
        const val ROTATION_DEGREES = "rotation-degrees"
    }

    private object SurfaceConfigs {
        const val SIZE = "size"
    }

    private object TextureProcessConfigs {
        const val PRE_PROCESS_ENABLE = "pre-process-enable"
        const val RENDER_ON_SCREEN_ENABLE = "render-on-screen-enable"
    }

    private var mHandler: Handler? = null

    private var mEglCore: EglCore? = null
    private var mWindowSurface: WindowSurface? = null

    private var mGLRenderer: GLRenderer = GLRenderer()

    private var mCameraOesTextureId: Int = -1
    private var mSurfaceTexture: SurfaceTexture? = null

    private var mCameraSize: Size = Size(-1, -1)
    private var mCameraRotationDegrees: Int = 0

    private var mSurfaceViewSize: Size = Size(-1, -1)

    private var mTransformMatrix: FloatArray = FloatArray(16)

    /**
     * 是否要進行Texture的預處理
     */
    private var mTexturePreProcessEnable: Boolean = true

    /**
     *
     */
    private var mTextureRenderOnScreenEnable: Boolean = true

    /**
     * This function will be triggered after OpenGL engine initialized.
     */
    abstract fun createWindowSurface(eglCore: EglCore): WindowSurface

    abstract fun releaseInputData()

    fun initGL() {
        mHandler?.sendEmptyMessage(INIT)
    }

    fun pause() {
        mHandler?.removeMessages(PROCESS)
    }

    fun process() {
        // 因有時處理texture時間可能大於下一個frame available的時間，確保不會造成back pressure
        mHandler?.removeMessages(PROCESS)
        mHandler?.sendEmptyMessage(PROCESS)
    }

    fun release() {
        Logger.d(TAG, "release GLThread")
        mHandler?.removeMessages(PROCESS)
        mHandler?.sendEmptyMessage(RELEASE)
    }

    fun updateCameraConfigs(size: Size, rotationDegrees: Int) {
        Logger.d(
            TAG,
            "updateCameraConfigs -> width: ${size.width}, height: ${size.height}, rotation: $rotationDegrees"
        )
        val message = Message().apply {
            what = UPDATE_CAMERA_CONFIGS
        }
        val bundle = Bundle().apply {
            putSize(
                CameraConfigs.SIZE, if (rotationDegrees % 180 == 90) {
                    Size(size.height, size.width)
                } else {
                    Size(size.width, size.height)
                }
            )
            putInt(CameraConfigs.ROTATION_DEGREES, rotationDegrees)
        }
        message.data = bundle
        mHandler?.sendMessage(message)
    }

    fun updateSurfaceConfigs(size: Size) {
        val message = Message().apply {
            what = UPDATE_SURFACE_CONFIGS
        }
        val bundle = Bundle().apply {
            putSize(SurfaceConfigs.SIZE, size)
        }
        message.data = bundle
        mHandler?.sendMessage(message)
    }

    fun updateTextureProcessConfigs(preProcessEnable: Boolean, renderOnScreenEnable: Boolean) {
        val message = Message().apply {
            what = UPDATE_TEXTURE_PROCESS_CONFIGS
        }
        val bundle = Bundle().apply {
            putBoolean(TextureProcessConfigs.PRE_PROCESS_ENABLE, preProcessEnable)
            putBoolean(TextureProcessConfigs.RENDER_ON_SCREEN_ENABLE, renderOnScreenEnable)
        }
        message.data = bundle
        mHandler?.sendMessage(message)
    }


    override fun start() {
        super.start()
        mHandler = Handler(looper) { msg ->
            when (msg.what) {
                INIT -> {
                    onInitGL()
                    true
                }

                PROCESS -> {
                    onProcess()
                    true
                }

                RELEASE -> {
                    onRelease()
                    true
                }

                UPDATE_CAMERA_CONFIGS -> {
                    val size: Size? = msg.data.getSize(CameraConfigs.SIZE)
                    if (size != null) {
                        onUpdateCameraConfigs(
                            size,
                            msg.data.getInt(CameraConfigs.ROTATION_DEGREES)
                        )
                    }
                    true
                }

                UPDATE_SURFACE_CONFIGS -> {
                    val size: Size? = msg.data.getSize(SurfaceConfigs.SIZE)
                    if (size != null) {
                        onUpdateSurfaceConfigs(size)
                    }
                    true
                }

                UPDATE_TEXTURE_PROCESS_CONFIGS -> {
                    onUpdateTextureProcessConfigs(
                        msg.data.getBoolean(TextureProcessConfigs.PRE_PROCESS_ENABLE),
                        msg.data.getBoolean(TextureProcessConfigs.RENDER_ON_SCREEN_ENABLE)
                    )
                    true
                }

                else -> false
            }
        }
    }

    private fun onInitGL() {
        Logger.d(TAG, "onInitGL")
        var eglCore: EglCore? = mEglCore
        if (eglCore == null) {
            eglCore = EglCore(null, 0)
            mEglCore = eglCore
        }

        mWindowSurface?.release()
        mWindowSurface = null
        mWindowSurface = createWindowSurface(eglCore)

        if (mSurfaceTexture == null) {
            GlUtil.releaseTextureId(mCameraOesTextureId)
            mCameraOesTextureId = GlUtil.createExternalOESTextureId()

            val surfaceTexture = SurfaceTexture(mCameraOesTextureId)
            mSurfaceTexture = surfaceTexture
        }
        callback?.onCreateSurfaceTexture(mSurfaceTexture!!)
    }

    private fun onUpdateCameraConfigs(size: Size, rotationDegrees: Int) {
        mCameraSize = size
        mCameraRotationDegrees = rotationDegrees
    }

    private fun onUpdateSurfaceConfigs(size: Size) {
        mSurfaceViewSize = size
    }

    private fun onUpdateTextureProcessConfigs(
        preProcessEnable: Boolean,
        renderOnScreenEnable: Boolean
    ) {
        mTexturePreProcessEnable = preProcessEnable
        mTextureRenderOnScreenEnable = renderOnScreenEnable
    }

    private var taskId = 0

    private fun onProcess() {
        try {
            val windowSurface: WindowSurface = mWindowSurface ?: return
            val surfaceTexture: SurfaceTexture = mSurfaceTexture ?: return

            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(mTransformMatrix)

            var preProcessTextureId: Int = mCameraOesTextureId
            if (mTexturePreProcessEnable) {
                // 清空缓冲区颜色
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                val transition: GLRenderer.Transition =
                    GLRenderer.Transition().rotate(mCameraRotationDegrees.toFloat())
                        .flip(x = false, y = true)
                preProcessTextureId = mGLRenderer.transferTextureToTexture(
                    mCameraOesTextureId,
                    TextureFormat.TextureOES,
                    TextureFormat.Texture2D,
                    mCameraSize.width,
                    mCameraSize.height,
                    transition
                )
            }

            val processedTextureId: Int = callback?.onProcessTexture(
                preProcessTextureId,
                mCameraSize,
                mSurfaceViewSize,
                mTransformMatrix
            ) ?: preProcessTextureId

            if (mTextureRenderOnScreenEnable) {
                if (!GLES20.glIsTexture(processedTextureId)) {
                    Logger.e(TAG, "output texture not a valid texture")
                    return
                }

                val onScreenTransition: GLRenderer.Transition =
                    GLRenderer.Transition()
                        .crop(
                            CropScaleType.CENTER_CROP,
                            0,
                            mCameraSize.width,
                            mCameraSize.height,
                            mSurfaceViewSize.width,
                            mSurfaceViewSize.height
                        )
                mGLRenderer.transferTextureToScreen(
                    processedTextureId,
                    TextureFormat.Texture2D,
                    mSurfaceViewSize.width,
                    mSurfaceViewSize.height,
                    onScreenTransition.matrix
                )
            }

            windowSurface.swapBuffers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onRelease() {
        Logger.d(TAG, "onRelease: ${Thread.currentThread().name}")
        releaseInputData()

        mGLRenderer.release()

        mWindowSurface?.release()
        mWindowSurface = null

        mSurfaceTexture?.release()
        mSurfaceTexture = null

        GlUtil.releaseTextureId(mCameraOesTextureId)

        mEglCore?.release()
        mEglCore = null

        mHandler?.looper?.quit()
        mHandler = null
    }
}