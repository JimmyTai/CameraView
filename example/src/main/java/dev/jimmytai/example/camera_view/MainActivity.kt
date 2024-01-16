package dev.jimmytai.example.camera_view

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.jimmytai.camera_view.CameraController
import dev.jimmytai.camera_view.CameraView
import dev.jimmytai.camera_view.interfaces.CameraTextureProcessor
import dev.jimmytai.example.camera_view.ui.theme.CameraViewTheme
import org.wysaid.nativePort.CGEFrameRenderer

class MainActivity : ComponentActivity(), CameraTextureProcessor {

    private enum class BeautyEffectEngine {
        NONE, DEFAULT
    }

    private val mCameraView: CameraView by lazy { CameraView(this) }

    private val mCameraController = CameraController(this, Size(1080, 720))

    private val mCameraPermissionRequest: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                mCameraController.startPreview()
            } else {
                Toast.makeText(this, "Please grant camera permission", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    private var mBeautyEffectEngineAttempt: BeautyEffectEngine? = null
    private var mBeautyEffectEngine: BeautyEffectEngine = BeautyEffectEngine.DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CameraViewTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeautyEffectCamera(mCameraView, mCameraController) {
                        when (mBeautyEffectEngine) {
                            BeautyEffectEngine.NONE -> {
                                mBeautyEffectEngineAttempt = BeautyEffectEngine.DEFAULT
                                mCameraController.setCameraTextureProcessor(
                                    this,
                                    preProcessEnable = false,
                                    renderOnScreenEnable = false
                                )
                            }

                            BeautyEffectEngine.DEFAULT -> {
                                mBeautyEffectEngineAttempt = BeautyEffectEngine.NONE
                                mCameraController.setCameraTextureProcessor(this)
                            }
                        }
                    }
                }
            }
        }

        mCameraView.setController(mCameraController)
        when (mBeautyEffectEngine) {
            BeautyEffectEngine.NONE -> {
                mCameraController.setCameraTextureProcessor(this)
            }

            BeautyEffectEngine.DEFAULT -> {
                mCameraController.setCameraTextureProcessor(
                    this,
                    preProcessEnable = false,
                    renderOnScreenEnable = false
                )
            }
        }
        startCameraPreview(mCameraController)
    }

    private fun startCameraPreview(cameraController: CameraController) {
        if (ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraController.startPreview()
        } else {
            mCameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mCameraController.let {
            it.stopPreview()
            it.release()
        }
    }

    private var mCameraSize: Size? = null
    private var mSurfaceSize: Size? = null
    private var mFrameRenderer: CGEFrameRenderer? = null

    override fun onProcessTexture(
        textureId: Int,
        cameraSize: Size,
        surfaceSize: Size,
        transformMatrix: FloatArray
    ): Int {
        if (mBeautyEffectEngine == BeautyEffectEngine.DEFAULT) {
            if (mFrameRenderer == null || mCameraSize != cameraSize || mSurfaceSize != surfaceSize) {
                mFrameRenderer?.release()
                mFrameRenderer = CGEFrameRenderer().also {
                    val filterConfig =
                        FilterConfig.values()[FilterConfig.FILTER_NONE.ordinal].configString(
                            true
                        )
                    it.init(
                        cameraSize.width,
                        cameraSize.height,
                        surfaceSize.width,
                        surfaceSize.height
                    )
                    it.setFilterWidthConfig(filterConfig)
                    // 水平翻轉
                    // it.setSrcFlipScale(1.0f, 1.0f)
                    // it.setRenderFlipScale(-1.0f, 1.0f)

                    GLES20.glDisable(GLES20.GL_DEPTH_TEST)
                    GLES20.glDisable(GLES20.GL_STENCIL_TEST)
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                }
            }
            val frameRenderer: CGEFrameRenderer? = mFrameRenderer
            if (frameRenderer != null) {
                frameRenderer.update(textureId, transformMatrix)
                frameRenderer.runProc()

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                frameRenderer.render(0, 0, surfaceSize.width, surfaceSize.height)
            }
        }
        val beautyEffectEngineAttempt: BeautyEffectEngine? = mBeautyEffectEngineAttempt
        if (beautyEffectEngineAttempt != null) {
            val frameRenderer: CGEFrameRenderer? = mFrameRenderer
            if (frameRenderer != null) {
//                    frameRenderer.update(0, transformMatrix)
                frameRenderer.release()
                mFrameRenderer = null
            }
            mBeautyEffectEngineAttempt = null
            mBeautyEffectEngine = beautyEffectEngineAttempt
        }
        return textureId
    }
}

typealias OnBeautyEffectChange = () -> Unit

@Composable
fun BeautyEffectCamera(
    cameraView: CameraView,
    cameraController: CameraController,
    onBeautyEffectChange: OnBeautyEffectChange,
) {
    val cameraState: MutableState<Boolean> = remember { mutableStateOf(true) }

    Box {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                cameraView
            })
        Button(
            onClick = {
                if (cameraState.value) {
                    cameraController.stopPreview()
                } else {
                    cameraController.startPreview()
                }
                cameraState.value = !cameraState.value
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 64.dp, end = 16.dp)
        ) {
            Text(text = "Start/Stop")
        }
        Button(
            onClick = {
                onBeautyEffectChange.invoke()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            Text(text = "Beauty Effect")
        }
    }

}

@Preview(showBackground = true)
@Composable
fun BeautyEffectCameraPreview() {
    val cameraView = CameraView(LocalContext.current)
    val cameraController = CameraController(LocalContext.current, Size(1080, 720))

    CameraViewTheme {
        BeautyEffectCamera(cameraView, cameraController) {}
    }
}

enum class FilterConfig {
    FILTER_NONE,
    FILTER_A,
    FILTER_B,
    FILTER_C,
    FILTER_D,
    FILTER_E,
    FILTER_F,
    FILTER_G,
    FILTER_H,
    FILTER_I,
    FILTER_J;

    fun configString(beautySkin: Boolean) = if (beautySkin) {
        "$beautyFilter ${advanceFilter[ordinal]}"
    } else {
        advanceFilter[ordinal]
    }

    private val advanceFilter = arrayOf(
        "",
        /*光圈*/
        "@vigblend mix 10 10 30 255 91 0 1.0 0.5 0.5 3 @curve R(0, 31)(35, 75)(81, 139)(109, 174)(148, 207)(255, 255)G(0, 24)(59, 88)(105, 146)(130, 171)(145, 187)(180, 214)(255, 255)B(0, 96)(63, 130)(103, 157)(169, 194)(255, 255)",
        /*黃銅*/
        "@curve R(0, 0)(120, 96)(165, 255)G(90, 0)(131, 145)(172, 255)B(77, 0)(165, 167)(255, 255)", //172
        /*黑白*/
        "@colormul mat 0.34 0.48 0.22 0.34 0.48 0.22 0.34 0.48 0.22 @curve R(0, 0)(9, 10)(47, 38)(87, 69)(114, 92)(134, 116)(175, 167)(218, 218)(255, 255)G(40, 0)(45, 14)(58, 34)(74, 55)(125, 118)(192, 205)(255, 255)B(0, 0)(15, 16)(37, 31)(71, 55)(108, 88)(159, 151)(204, 201)(255, 255)", //120
        /*紅*/
        "@adjust hsv 0.3 -0.5 -0.3 0 0.35 -0.2 @curve R(0, 0)(111, 163)(255, 255)G(0, 0)(72, 56)(155, 190)(255, 255)B(0, 0)(103, 70)(212, 244)(255, 255)", //24
        /*寫實??*/
        "@curve R(0, 0)(69, 63)(105, 138)(151, 222)(255, 255)G(0, 0)(67, 51)(135, 191)(255, 255)B(0, 0)(86, 76)(150, 212)(255, 255)", //6
        "@curve R(0, 0)(149, 159)(255, 255)RGB(0, 0)(160, 95)(255, 255)",
        "@adjust brightness 0.3",
        "@adjust exposure 0.7",
        "@curve R(15, 0)(92, 133)(255, 234)G(0, 20)(105, 128)(255, 255)B(0, 0)(120, 132)(255, 214)",
        "@adjust hsl 0.02 -0.31 -0.17 @curve R(0, 28)(23, 45)(117, 148)(135, 162)G(0, 8)(131, 152)(255, 255)B(0, 17)(58, 80)(132, 131)(127, 131)(255, 225)"
    )
    private val beautyFilter = "#unpack @beautify face 1.0"
}