package dev.jimmytai.camera_view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.SurfaceView
import android.widget.FrameLayout
import dev.jimmytai.camera_view.utils.Logger

class CameraView(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    companion object {
        private val TAG: String = CameraView::class.java.name
    }

    private val surfaceView: SurfaceView

    private var mCameraController: CameraController? = null

    private val onCameraPreviewAction: CameraController.OnCameraPreviewAction =
        object : CameraController.OnCameraPreviewAction {
            override fun onCameraSizeChanged(size: Size) {
//                post { adjustPreviewViewPosition(size) }
            }
        }

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attrs,
        defStyleAttr,
        0
    )

    init {
        setBackgroundColor(Color.RED)
        surfaceView = SurfaceView(context)
        removeAllViews()
        addView(surfaceView)
    }

    fun setController(cameraController: CameraController) {
        mCameraController = cameraController
        cameraController.attachFromView(surfaceView, onCameraPreviewAction)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun adjustPreviewViewPosition(size: Size) {
        // 這個view的寬高
        val viewWidth: Double = measuredWidth.toDouble()
        val viewHeight: Double = measuredHeight.toDouble()
        if (viewWidth == 0.0 || viewHeight == 0.0) return
        val viewAspectRatio: Double = viewWidth / viewHeight

        val cameraAspectRatio = size.width.toDouble() / size.height.toDouble()

        Logger.d(
            CameraView.TAG,
            "view: $viewWidth x $viewHeight, view ratio: $viewAspectRatio, camera ratio: $cameraAspectRatio"
        )

        // 計算camera preview的寬高
        val previewWidth: Int
        val previewHeight: Int
        if (viewAspectRatio < cameraAspectRatio) {
            val cameraViewHeight: Double = viewWidth / cameraAspectRatio
            previewWidth = viewWidth.toInt()
            previewHeight = cameraViewHeight.toInt()
        } else {
            val cameraViewWidth: Double = viewHeight * cameraAspectRatio
            previewWidth = cameraViewWidth.toInt()
            previewHeight = viewHeight.toInt()
        }
        Logger.d(
            CameraView.TAG,
            "preview: $previewWidth x $previewHeight"
        )
        val layoutParams: FrameLayout.LayoutParams =
            surfaceView.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = previewWidth
        layoutParams.height = previewHeight
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL
        surfaceView.layoutParams = layoutParams
    }
}