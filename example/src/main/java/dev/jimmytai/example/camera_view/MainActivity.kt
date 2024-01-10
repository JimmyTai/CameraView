package dev.jimmytai.example.camera_view

import android.Manifest
import android.content.pm.PackageManager
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
import dev.jimmytai.example.camera_view.ui.theme.CameraViewTheme

class MainActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CameraViewTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeautyEffectCamera(mCameraView, mCameraController, ::startCameraPreview)
                }
            }
        }

        mCameraView.setController(mCameraController)
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
}

@Composable
fun BeautyEffectCamera(
    cameraView: CameraView,
    cameraController: CameraController,
    startCameraPreview: (CameraController) -> Unit
) {
    val cameraVisibility: MutableState<Boolean> = remember { mutableStateOf(true) }

    Box {
        if (cameraVisibility.value) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    cameraView
                })
        }
        Button(
            onClick = {
                if (cameraVisibility.value) {
                    cameraController.stopPreview()
                } else {
                    cameraController.startPreview()
                }
                cameraVisibility.value = !cameraVisibility.value
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            Text(text = "Change")
        }
    }

}

@Preview(showBackground = true)
@Composable
fun BeautyEffectCameraPreview() {
    val cameraView = CameraView(LocalContext.current)
    val cameraController = CameraController(LocalContext.current, Size(1080, 720))

    CameraViewTheme {
        BeautyEffectCamera(cameraView, cameraController) {

        }
    }
}