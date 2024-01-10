package dev.jimmytai.camera_view.glthread

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
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
    }

    private var mHandler: Handler? = null

    private var mEglCore: EglCore? = null
    private var mWindowSurface: WindowSurface? = null

    private var mGLRenderer: GLRenderer = GLRenderer()

    private var mCameraOesTextureId: Int = -1
    private var mSurfaceTexture: SurfaceTexture? = null

    private var mCameraWidth: Int = -1
    private var mCameraHeight: Int = -1
    private var mCameraRotationDegrees: Int = 0

    protected var mSurfaceViewWidth: Int = -1
    protected var mSurfaceViewHeight: Int = -1

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

    fun onCameraSizeChange(width: Int, height: Int, rotationDegrees: Int) {
        Logger.d(
            TAG,
            "onCameraSizeChange -> width: $width, height: $height, rotation: $rotationDegrees"
        )
        if (rotationDegrees % 180 == 90) {
            mCameraWidth = height
            mCameraHeight = width
        } else {
            mCameraWidth = width
            mCameraHeight = height
        }
        mCameraRotationDegrees = rotationDegrees
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

        callback?.onGLContextCreated()
    }

    private fun onProcess() {
        val windowSurface: WindowSurface = mWindowSurface ?: return
        val surfaceTexture: SurfaceTexture = mSurfaceTexture ?: return

        surfaceTexture.updateTexImage()

        // 清空缓冲区颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val transition: GLRenderer.Transition =
            GLRenderer.Transition().rotate(mCameraRotationDegrees.toFloat())
                .flip(x = false, y = true)
        val textureId: Int = mGLRenderer.transferTextureToTexture(
            mCameraOesTextureId,
            TextureFormat.TextureOES,
            TextureFormat.Texture2D,
            mCameraWidth,
            mCameraHeight,
            transition
        )

        val processedTextureId: Int =
            callback?.onCustomProcessTexture(textureId, mCameraWidth, mCameraHeight) ?: textureId

        if (!GLES20.glIsTexture(processedTextureId)) {
            Logger.e(TAG, "output texture not a valid texture")
            return
        }

        val onScreenTransition: GLRenderer.Transition =
            GLRenderer.Transition()
                .crop(
                    CropScaleType.CENTER_CROP, 0,
                    mCameraWidth, mCameraHeight, mSurfaceViewWidth, mSurfaceViewHeight
                )
        mGLRenderer.transferTextureToScreen(
            processedTextureId,
            TextureFormat.Texture2D,
            mSurfaceViewWidth,
            mSurfaceViewHeight,
            onScreenTransition.matrix
        )

        windowSurface.swapBuffers()
    }

    private fun onRelease() {
        Logger.d(TAG, "onRelease: ${Thread.currentThread().name}")
        callback?.onGLContextDestroy()
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