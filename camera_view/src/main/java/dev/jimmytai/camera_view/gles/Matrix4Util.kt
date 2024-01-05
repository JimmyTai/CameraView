package dev.jimmytai.camera_view.gles

import android.opengl.Matrix
import android.widget.ImageView

object Matrix4Util {
    fun crop(
        matrix: FloatArray?,
        type: ImageView.ScaleType,
        imgWidth: Int,
        imgHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (imgHeight > 0 && imgWidth > 0 && viewWidth > 0 && viewHeight > 0) {
            val projection = FloatArray(16)
            val camera = FloatArray(16)
            if (type == ImageView.ScaleType.FIT_XY) {
                Matrix.orthoM(projection, 0, -1f, 1f, -1f, 1f, 1f, 3f)
                Matrix.setLookAtM(camera, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
                Matrix.multiplyMM(matrix, 0, projection, 0, camera, 0)
            }
            val sWhView = viewWidth.toFloat() / viewHeight
            val sWhImg = imgWidth.toFloat() / imgHeight
            if (sWhImg > sWhView) {
                when (type) {
                    ImageView.ScaleType.CENTER_CROP -> {
                        Matrix.orthoM(
                            projection,
                            0,
                            -sWhView / sWhImg,
                            sWhView / sWhImg,
                            -1f,
                            1f,
                            1f,
                            3f
                        )
                        Matrix.scaleM(projection, 0, GlUtil.X_SCALE, GlUtil.Y_SCALE, 1f)
                    }

                    ImageView.ScaleType.CENTER_INSIDE -> Matrix.orthoM(
                        projection,
                        0,
                        -1f,
                        1f,
                        -sWhImg / sWhView,
                        sWhImg / sWhView,
                        1f,
                        3f
                    )

                    ImageView.ScaleType.FIT_START -> Matrix.orthoM(
                        projection,
                        0,
                        -1f,
                        1f,
                        1 - 2 * sWhImg / sWhView,
                        1f,
                        1f,
                        3f
                    )

                    ImageView.ScaleType.FIT_END -> Matrix.orthoM(
                        projection,
                        0,
                        -1f,
                        1f,
                        -1f,
                        2 * sWhImg / sWhView - 1,
                        1f,
                        3f
                    )

                    else -> {}
                }
            } else {
                when (type) {
                    ImageView.ScaleType.CENTER_CROP -> {
                        Matrix.orthoM(
                            projection,
                            0,
                            -1f,
                            1f,
                            -sWhImg / sWhView,
                            sWhImg / sWhView,
                            1f,
                            3f
                        )
                        Matrix.scaleM(projection, 0, GlUtil.X_SCALE, GlUtil.Y_SCALE, 1f)
                    }

                    ImageView.ScaleType.CENTER_INSIDE -> Matrix.orthoM(
                        projection,
                        0,
                        -sWhView / sWhImg,
                        sWhView / sWhImg,
                        -1f,
                        1f,
                        1f,
                        3f
                    )

                    ImageView.ScaleType.FIT_START -> Matrix.orthoM(
                        projection,
                        0,
                        -1f,
                        2 * sWhView / sWhImg - 1,
                        -1f,
                        1f,
                        1f,
                        3f
                    )

                    ImageView.ScaleType.FIT_END -> Matrix.orthoM(
                        projection,
                        0,
                        1 - 2 * sWhView / sWhImg,
                        1f,
                        -1f,
                        1f,
                        1f,
                        3f
                    )

                    else -> {}
                }
            }
            Matrix.setLookAtM(camera, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
            Matrix.multiplyMM(matrix, 0, projection, 0, camera, 0)
        }
    }

    fun rotate(matrix: FloatArray?, angle: Float): FloatArray? {
        Matrix.rotateM(matrix, 0, angle, 0f, 0f, 1f)
        return matrix
    }

    fun flip(matrix: FloatArray?, x: Boolean, y: Boolean): FloatArray? {
        if (x || y) {
            Matrix.scaleM(matrix, 0, (if (x) -1 else 1).toFloat(), (if (y) -1 else 1).toFloat(), 1f)
        }
        return matrix
    }
}