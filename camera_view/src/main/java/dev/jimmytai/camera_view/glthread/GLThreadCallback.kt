package dev.jimmytai.camera_view.glthread

import android.graphics.SurfaceTexture
import android.util.Size

interface GLThreadCallback {
    fun onCreateSurfaceTexture(surfaceTexture: SurfaceTexture)
}
