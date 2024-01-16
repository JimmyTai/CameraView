package dev.jimmytai.camera_view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.camera.core.CameraInfo
import dev.jimmytai.camera_view.utils.Logger

class CameraView(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
    FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    companion object {
        private val TAG: String = CameraView::class.java.simpleName
    }

    private val surfaceView: SurfaceView

    private var mCameraController: CameraController? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attrs,
        defStyleAttr,
        0
    )

    init {
        setBackgroundColor(Color.BLACK)
        surfaceView = SurfaceView(context)
        removeAllViews()
        addView(surfaceView)
    }

    fun setController(cameraController: CameraController) {
        mCameraController = cameraController
        cameraController.attachFromView(surfaceView)
    }

    private var skipWindowFocusChange = true

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (skipWindowFocusChange) {
            skipWindowFocusChange = false
            return
        }
        Logger.d(TAG, "onWindowFocusChanged -> hasWindowFocus: $hasWindowFocus")
        if (hasWindowFocus) {
            mCameraController?.onViewResumed()
        }
    }
}