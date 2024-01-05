package dev.jimmytai.camera_view.glthread

import android.graphics.SurfaceTexture

interface GLThreadCallback {
    fun onCreateSurfaceTexture(surfaceTexture: SurfaceTexture)

    fun onGLContextCreated()

    fun onCustomProcessTexture(textureId: Int, textureWidth: Int, textureHeight: Int): Int

    fun onGLContextDestroy()
}