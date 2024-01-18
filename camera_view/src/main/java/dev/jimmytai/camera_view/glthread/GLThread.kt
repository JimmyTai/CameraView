package dev.jimmytai.camera_view.glthread

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Process
import android.util.Size
import android.view.Surface
import dev.jimmytai.camera_view.constant.CropScaleType
import dev.jimmytai.camera_view.constant.TextureFormat
import dev.jimmytai.camera_view.gles.EglCore
import dev.jimmytai.camera_view.gles.GlUtil
import dev.jimmytai.camera_view.gles.WindowSurface
import dev.jimmytai.camera_view.glrenderer.GLRenderer
import dev.jimmytai.camera_view.interfaces.CameraTextureProcessor
import dev.jimmytai.camera_view.model.OutputSurfaceOption
import dev.jimmytai.camera_view.utils.Logger

abstract class GLThread(
    name: String,
    priority: Int = Process.THREAD_PRIORITY_DEFAULT,
    private val callback: GLThreadCallback,
    private val cameraTextureProcessor: CameraTextureProcessor
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

        const val UPDATE_CAMERA_CONFIGS: Int = 3

        const val UPDATE_SURFACE_CONFIGS: Int = 4

        const val ADD_OUTPUT_SURFACE: Int = 5

        const val REMOVE_OUTPUT_SURFACE: Int = 6
    }

    private object CameraConfigs {
        const val SIZE = "size"
        const val ROTATION_DEGREES = "rotation-degrees"
    }

    private object SurfaceConfigs {
        const val SIZE = "size"
    }

    private object OutputSurfaceConfigs {
        const val SURFACE = "surface"
        const val OPTION = "option"
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

    private var mOutputWindowSurfaces: MutableMap<Int, OutputWindowSurface> = mutableMapOf()

    val cameraSize: Size
        get() = mCameraSize

    val surfaceViewSize: Size
        get() = mSurfaceViewSize

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
        mHandler?.removeMessages(UPDATE_CAMERA_CONFIGS)
        mHandler?.removeMessages(UPDATE_SURFACE_CONFIGS)
        mHandler?.removeMessages(ADD_OUTPUT_SURFACE)
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
            data = Bundle().apply {
                putSize(
                    CameraConfigs.SIZE, if (rotationDegrees % 180 == 90) {
                        Size(size.height, size.width)
                    } else {
                        Size(size.width, size.height)
                    }
                )
                putInt(CameraConfigs.ROTATION_DEGREES, rotationDegrees)
            }
        }
        mHandler?.sendMessage(message)
    }

    fun updateSurfaceConfigs(size: Size) {
        val message = Message().apply {
            what = UPDATE_SURFACE_CONFIGS
            data = Bundle().apply {
                putSize(SurfaceConfigs.SIZE, size)
            }
        }
        mHandler?.sendMessage(message)
    }

    fun addOutputSurface(surface: Surface, surfaceOption: OutputSurfaceOption? = null) {
        val message = Message().apply {
            what = ADD_OUTPUT_SURFACE
            data = Bundle().apply {
                putParcelable(OutputSurfaceConfigs.SURFACE, surface)
                putParcelable(OutputSurfaceConfigs.OPTION, surfaceOption)
            }
        }
        mHandler?.sendMessage(message)
    }

    fun removeOutputSurface(surface: Surface) {
        val message = Message().apply {
            what = REMOVE_OUTPUT_SURFACE
            data = Bundle().apply {
                putInt(OutputSurfaceConfigs.SURFACE, surface.hashCode())
            }
        }
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

                ADD_OUTPUT_SURFACE -> {
                    val surface: Surface?
                    val option: OutputSurfaceOption?
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        surface = msg.data.getParcelable(
                            OutputSurfaceConfigs.SURFACE,
                            Surface::class.java
                        )
                        option = msg.data.getParcelable(
                            OutputSurfaceConfigs.OPTION,
                            OutputSurfaceOption::class.java
                        )
                    } else {
                        surface = msg.data.getParcelable(OutputSurfaceConfigs.SURFACE) as Surface?
                        option = msg.data.getParcelable(OutputSurfaceConfigs.OPTION)
                    }

                    if (surface != null) {
                        onAddOutputSurface(surface, option)
                    }
                    true
                }

                REMOVE_OUTPUT_SURFACE -> {
                    val surfaceHashCode: Int = msg.data.getInt(OutputSurfaceConfigs.SURFACE)
                    onRemoveOutputSurface(surfaceHashCode)
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
        callback.onCreateSurfaceTexture(mSurfaceTexture!!)
    }

    private fun onUpdateCameraConfigs(size: Size, rotationDegrees: Int) {
        mCameraSize = size
        mCameraRotationDegrees = rotationDegrees
    }

    private fun onUpdateSurfaceConfigs(size: Size) {
        mSurfaceViewSize = size
    }

    private fun onAddOutputSurface(surface: Surface, option: OutputSurfaceOption?) {
        Logger.d(TAG, "onAddOutputSurface -> surface: ${surface.hashCode()}")
        val windowSurface = WindowSurface(mEglCore, surface, false)
        mOutputWindowSurfaces[surface.hashCode()] = OutputWindowSurface(windowSurface, option)
    }

    private fun onRemoveOutputSurface(surfaceHashCode: Int) {
        Logger.d(TAG, "onRemoveOutputSurface -> surface: $surfaceHashCode")
        mOutputWindowSurfaces[surfaceHashCode]?.windowSurface?.release()
        mOutputWindowSurfaces.remove(surfaceHashCode)
    }

    private fun onProcess() {
        try {
            val windowSurface: WindowSurface = mWindowSurface ?: return
            val surfaceTexture: SurfaceTexture = mSurfaceTexture ?: return

            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(mTransformMatrix)

            val preProcessTextureId: Int = cameraTextureProcessor.onPreProcessTexture(
                textureId = mCameraOesTextureId,
                cameraSize = mCameraSize,
                textureSize = mSurfaceViewSize,
                transformMatrix = mTransformMatrix
            ).let {
                if (it != null) {
                    return@let it
                }

                // 清空缓冲区颜色
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                val transition: GLRenderer.Transition =
                    GLRenderer.Transition().rotate(mCameraRotationDegrees.toFloat())
                        .flip(x = false, y = true)
                return@let mGLRenderer.transferTextureToTexture(
                    inputTextureId = mCameraOesTextureId,
                    inputTextureFormat = TextureFormat.TextureOES,
                    outputTextureFormat = TextureFormat.Texture2D,
                    size = mCameraSize,
                    transition = transition
                )
            }

            val processedTextureId: Int = cameraTextureProcessor.onProcessTexture(
                textureId = preProcessTextureId,
                cameraSize = mCameraSize,
                textureSize = mSurfaceViewSize,
                transformMatrix = mTransformMatrix
            ) ?: preProcessTextureId

            val outputs: List<OutputWindowSurface> = mutableListOf<OutputWindowSurface>().apply {
                this.addAll(mOutputWindowSurfaces.values)
                this.add(DisplayWindowSurface(windowSurface))
            }

            saveRenderState()

            for (output in outputs) {

                if (output is DisplayWindowSurface) {
                    restoreRenderState()
                } else {
                    output.windowSurface.makeCurrent()
                }

                val handled: Boolean = cameraTextureProcessor.onRenderTexture(
                    textureId = processedTextureId,
                    cameraSize = mCameraSize,
                    textureSize = mSurfaceViewSize,
                    surfaceSize = output.option?.outputSize ?: mSurfaceViewSize,
                    transformMatrix = mTransformMatrix,
                    isDisplayWindow = output is DisplayWindowSurface
                )

                if (!handled) {
                    if (!GLES20.glIsTexture(processedTextureId)) {
                        Logger.e(TAG, "output texture not a valid texture")
                        return
                    }

                    val onScreenTransition: GLRenderer.Transition =
                        GLRenderer.Transition()
                            .crop(
                                scaleType = CropScaleType.CENTER_CROP,
                                rotation = 0,
                                textureSize = mCameraSize,
                                surfaceSize = output.option?.outputSize ?: mSurfaceViewSize,
//                                surfaceSize = Size(720, 1280)
                            )
                    mGLRenderer.transferTextureToScreen(
                        textureId = processedTextureId,
                        srcTextureFormat = TextureFormat.Texture2D,
                        surfaceSize = output.option?.outputSize ?: mSurfaceViewSize,
//                        surfaceSize = Size(720, 1280),
                        mvpMatrix = onScreenTransition.matrix
                    )
                }

                output.windowSurface.swapBuffers()
            }

            cameraTextureProcessor.onProcessEnd(
                cameraSize = mCameraSize,
                surfaceSize = mSurfaceViewSize
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var mSavedEglDisplay: EGLDisplay? = null
    private var mSavedEglDrawSurface: EGLSurface? = null
    private var mSavedEglReadSurface: EGLSurface? = null
    private var mSavedEglContext: EGLContext? = null

    private fun saveRenderState() {
        mSavedEglDisplay = EGL14.eglGetCurrentDisplay()
        mSavedEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        mSavedEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        mSavedEglContext = EGL14.eglGetCurrentContext()
    }

    private fun restoreRenderState() {
        val eglDisplay: EGLDisplay = mSavedEglDisplay ?: return
        val eglDrawSurface: EGLSurface = mSavedEglDrawSurface ?: return
        val eglReadSurface: EGLSurface = mSavedEglReadSurface ?: return
        val eglContext: EGLContext = mSavedEglContext ?: return
        if (!EGL14.eglMakeCurrent(eglDisplay, eglDrawSurface, eglReadSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun onRelease() {
        Logger.d(TAG, "onRelease: ${Thread.currentThread().name}")
        releaseInputData()

        for (outputWindowSurface in mOutputWindowSurfaces.values) {
            outputWindowSurface.windowSurface.release()
        }
        mOutputWindowSurfaces.clear()

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

private open class OutputWindowSurface(
    val windowSurface: WindowSurface,
    val option: OutputSurfaceOption? = null
)

private class DisplayWindowSurface(
    windowSurface: WindowSurface,
    option: OutputSurfaceOption? = null
) :
    OutputWindowSurface(windowSurface, option)