package dev.jimmytai.camera_view.model

import android.os.Parcelable
import android.util.Size
import kotlinx.parcelize.Parcelize

@Parcelize
data class OutputSurfaceOption(val outputSize: Size): Parcelable