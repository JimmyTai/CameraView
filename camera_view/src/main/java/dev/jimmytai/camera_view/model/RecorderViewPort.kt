package dev.jimmytai.camera_view.model

import android.graphics.Point
import android.util.Log
import android.util.Size

data class RecorderViewPort(val point: Point, val size: Size) {
    companion object {
        fun create(size: Size, targetSize: Size, fit: Boolean): RecorderViewPort {
            val ratio: Double =
                if (size.width < size.height)
                    size.width / size.height.toDouble()
                else
                    size.height / size.width.toDouble()
            val targetRatio: Double =
                if (targetSize.width < targetSize.height)
                    targetSize.width / targetSize.height.toDouble()
                else
                    targetSize.height / targetSize.width.toDouble()
            val scale: Double = ratio / targetRatio

            val viewPortSize: Size = if (fit) {
                if (scale > 1.0) {
                    Size((targetSize.height * ratio).toInt(), targetSize.height)
                } else {
                    Size(targetSize.width, (targetSize.width / ratio).toInt())
                }
            } else {
                if (scale > 1.0) {
                    Size(targetSize.width, (targetSize.width / ratio).toInt())
                } else {
                    Size((targetSize.height * ratio).toInt(), targetSize.height)
                }
            }

            return RecorderViewPort(
                Point(
                    ((targetSize.width - viewPortSize.width) / 2.0).toInt(),
                    ((targetSize.height - viewPortSize.height) / 2.0).toInt()
                ),
                viewPortSize
            )
        }
    }
}