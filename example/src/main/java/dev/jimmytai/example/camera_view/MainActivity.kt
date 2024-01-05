package dev.jimmytai.example.camera_view

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.jimmytai.camera_view.CameraController
import dev.jimmytai.camera_view.CameraView
import dev.jimmytai.example.camera_view.ui.theme.CameraViewTheme
import java.util.logging.Logger

class MainActivity : ComponentActivity() {

    private val cameraController = CameraController(this, Size(720, 1280))
    private lateinit var cameraView: CameraView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Jimmy", "set camera controller")

        cameraView = CameraView(this)
        cameraView.setController(cameraController)

        setContent {
            CameraViewTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeautyEffectCamera(cameraView)
                }
            }
        }
        if (ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraController.startPreview()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10001)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.stopPreview()
        cameraController.release()
    }
}

@Composable
fun BeautyEffectCamera(cameraView: CameraView, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
        cameraView
    })
}

@Preview(showBackground = true)
@Composable
fun BeautyEffectCameraPreview() {
    val cameraView = CameraView(LocalContext.current)
    CameraViewTheme {
        BeautyEffectCamera(cameraView)
    }
}