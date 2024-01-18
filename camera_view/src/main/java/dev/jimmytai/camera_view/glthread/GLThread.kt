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

        // 更新相機相關的配置
        const val UPDATE_CAMERA_CONFIGS: Int = 3

        // 更新Surface相關的配置
        const val UPDATE_SURFACE_CONFIGS: Int = 4

        // 新增一個繪製窗口
        const val ADD_OUTPUT_SURFACE: Int = 5

        // 移除一個繪製窗口
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

    /**
     * 負責在此執行序中獲取任務的Handler
     */
    private var mHandler: Handler? = null

    /**
     * EGL的實例
     */
    private var mEglCore: EglCore? = null

    /**
     * EGL的繪製窗口，透過Surface建立並綁定
     *
     * SwapBuffer時會將圖像資料輸出至Surface，達到繪製的目的
     */
    private var mWindowSurface: WindowSurface? = null

    /**
     * OpenGL中與Texture相關的操作
     */
    private var mGLRenderer: GLRenderer = GLRenderer()

    /**
     * 相機資料輸出的Texture
     */
    private var mCameraOesTextureId: Int = -1

    /**
     * 提供給相機的SurfaceTexture，綁定[mCameraOesTextureId]
     */
    private var mSurfaceTexture: SurfaceTexture? = null

    /**
     * 相機尺寸，也代表原始Texture尺寸
     */
    private var mCameraSize: Size = Size(-1, -1)

    /**
     * 相機旋轉角度
     */
    private var mCameraRotationDegrees: Int = 0

    /**
     * SurfaceView的尺寸，也代表最終繪製至螢幕的輸出尺寸
     */
    private var mSurfaceViewSize: Size = Size(-1, -1)

    /**
     * SurfaceView中的 transform matrix
     */
    private var mTransformMatrix: FloatArray = FloatArray(16)

    /**
     * 儲存額外的輸出窗口，Unique ID為Surface的hash code
     */
    private var mOutputWindowSurfaces: MutableMap<Int, OutputWindowSurface> = mutableMapOf()

    /**
     * 目前GLThread渲染使用的相機尺寸
     */
    val cameraSize: Size
        get() = mCameraSize

    /**
     * 目前GLThread渲染至螢幕的尺寸
     */
    val surfaceViewSize: Size
        get() = mSurfaceViewSize

    /**
     * This function will be triggered after OpenGL engine initialized.
     */
    abstract fun createWindowSurface(eglCore: EglCore): WindowSurface

    abstract fun releaseInputData()

    /**
     * 通知GLThread初始化OpenGL與EGL的事件
     */
    fun initGL() {
        mHandler?.sendEmptyMessage(INIT)
    }

    /**
     * 移除當前隊列的GLThread渲染事件
     */
    fun pause() {
        mHandler?.removeMessages(PROCESS)
    }

    /**
     * 通知GLThread渲染的事件
     */
    fun process() {
        // 因有時處理texture時間可能大於下一個frame available的時間，確保不會造成back pressure
        mHandler?.removeMessages(PROCESS)
        mHandler?.sendEmptyMessage(PROCESS)
    }

    /**
     * 通知GLThread釋放資源的事件
     */
    fun release() {
        Logger.d(TAG, "release GLThread")
        mHandler?.removeMessages(UPDATE_CAMERA_CONFIGS)
        mHandler?.removeMessages(UPDATE_SURFACE_CONFIGS)
        mHandler?.removeMessages(ADD_OUTPUT_SURFACE)
        mHandler?.removeMessages(PROCESS)
        mHandler?.sendEmptyMessage(RELEASE)
    }

    /**
     * 通知GLThread更新相機配置的事件
     */
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

    /**
     * 通知GLThread更新Surface配置的事件
     */
    fun updateSurfaceConfigs(size: Size) {
        val message = Message().apply {
            what = UPDATE_SURFACE_CONFIGS
            data = Bundle().apply {
                putSize(SurfaceConfigs.SIZE, size)
            }
        }
        mHandler?.sendMessage(message)
    }

    /**
     * 通知GLThread新增一個輸出窗口(Surface)的事件
     */
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

    /**
     * 通知GLThread移除一個輸出窗口(Surface)的事件
     */
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

    /**
     * 初始化OpenGL與EGL，並創建一個SurfaceView準備提供給相機
     */
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

        // 如果沒有SurfaceTexture，創建一個
        if (mSurfaceTexture == null) {
            GlUtil.releaseTextureId(mCameraOesTextureId)
            mCameraOesTextureId = GlUtil.createExternalOESTextureId()

            val surfaceTexture = SurfaceTexture(mCameraOesTextureId)
            mSurfaceTexture = surfaceTexture
        }
        // 通知外部SurfaceView已建立，可以綁定至相機
        callback.onCreateSurfaceTexture(mSurfaceTexture!!)
    }

    /**
     * 更新相機相關配置
     */
    private fun onUpdateCameraConfigs(size: Size, rotationDegrees: Int) {
        mCameraSize = size
        mCameraRotationDegrees = rotationDegrees
    }

    /**
     * 更新Surface相關配置
     */
    private fun onUpdateSurfaceConfigs(size: Size) {
        mSurfaceViewSize = size
    }

    /**
     * 新增一個輸出窗口(Surface)，並建立一個EGL繪製窗口(WindowSurface)
     */
    private fun onAddOutputSurface(surface: Surface, option: OutputSurfaceOption?) {
        Logger.d(TAG, "onAddOutputSurface -> surface: ${surface.hashCode()}")
        val windowSurface = WindowSurface(mEglCore, surface, false)
        mOutputWindowSurfaces[surface.hashCode()] = OutputWindowSurface(windowSurface, option)
    }

    /**
     * 移除一個輸出窗口，並釋放EGL繪製窗口
     */
    private fun onRemoveOutputSurface(surfaceHashCode: Int) {
        Logger.d(TAG, "onRemoveOutputSurface -> surface: $surfaceHashCode")
        mOutputWindowSurfaces[surfaceHashCode]?.windowSurface?.release()
        mOutputWindowSurfaces.remove(surfaceHashCode)
    }

    /**
     * 渲染流程
     */
    private fun onProcess() {
        try {
            val windowSurface: WindowSurface = mWindowSurface ?: return
            val surfaceTexture: SurfaceTexture = mSurfaceTexture ?: return

            // 從SurfaceTexture中更新當前Texture回來
            surfaceTexture.updateTexImage()
            // 獲取SurfaceTexture目前的transform matrix
            surfaceTexture.getTransformMatrix(mTransformMatrix)

            // 預渲染流程 -
            //      return null 代表外部不處理，使用GLRenderer做旋轉與將畫面處理為鏡射畫面
            //                  並將OES Texture轉為2D Texture
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

            // 提供給外部的渲染步驟
            //      return null 代表外部不處理，直接使用原本的texture id
            val processedTextureId: Int = cameraTextureProcessor.onProcessTexture(
                textureId = preProcessTextureId,
                cameraSize = mCameraSize,
                textureSize = mSurfaceViewSize,
                transformMatrix = mTransformMatrix
            ) ?: preProcessTextureId

            // 將螢幕的繪製窗口與額外的繪製窗口 整合至同一個List，提供後續繪製至窗口
            val outputs: List<OutputWindowSurface> = mutableListOf<OutputWindowSurface>().apply {
                this.addAll(mOutputWindowSurfaces.values)
                this.add(DisplayWindowSurface(windowSurface))
            }

            // 儲存當前螢幕繪製窗口的資訊
            saveRenderState()

            for (output in outputs) {

                if (output is DisplayWindowSurface) {
                    // 回復當前螢幕繪製窗口的資訊
                    restoreRenderState()
                } else {
                    // 切換至額外的EGL繪製窗口
                    output.windowSurface.makeCurrent()
                }

                // 渲染至窗口的處理
                //      return false 代表外部不處理，使用預設的繪製操作
                //             true  代表外部已處理，不需額外操作
                val handled: Boolean = cameraTextureProcessor.onRenderTexture(
                    textureId = processedTextureId,
                    cameraSize = mCameraSize,
                    textureSize = mSurfaceViewSize,
                    surfaceSize = output.option?.outputSize ?: mSurfaceViewSize,
                    transformMatrix = mTransformMatrix,
                    isDisplayWindow = output is DisplayWindowSurface
                )

                if (!handled) {
                    // 預設的繪製上屏操作
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
                            )
                    mGLRenderer.transferTextureToScreen(
                        textureId = processedTextureId,
                        srcTextureFormat = TextureFormat.Texture2D,
                        surfaceSize = output.option?.outputSize ?: mSurfaceViewSize,
                        mvpMatrix = onScreenTransition.matrix
                    )
                }

                // 將Texture資料輸出至EGL繪製窗口
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

    // 釋放GLThread使用的資源
    private fun onRelease() {
        Logger.d(TAG, "onRelease: ${Thread.currentThread().name}")
        releaseInputData()

        // 釋放額外的EGL繪製窗口資源
        for (outputWindowSurface in mOutputWindowSurfaces.values) {
            outputWindowSurface.windowSurface.release()
        }
        mOutputWindowSurfaces.clear()

        // 釋放Texture操作的資源
        mGLRenderer.release()

        // 釋放螢幕繪製窗口的資源
        mWindowSurface?.release()
        mWindowSurface = null

        // 釋放SurfaceTexture的資源
        mSurfaceTexture?.release()
        mSurfaceTexture = null

        // 釋放綁定在SurfaceTexture中的Texture
        GlUtil.releaseTextureId(mCameraOesTextureId)

        // 釋放EGL的資源
        mEglCore?.release()
        mEglCore = null

        // 關閉GLThread的Handler，GLThread將停止
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