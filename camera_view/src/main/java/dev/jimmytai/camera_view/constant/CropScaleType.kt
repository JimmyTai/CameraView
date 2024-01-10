package dev.jimmytai.camera_view.constant

/*
 * value是對應ImageView.ScaleType的值
 */
enum class CropScaleType(value: Int) {
    FIT_XY(1),
    FIT_START(2),
    FIT_END(4),
    CENTER_CROP(6),
    CENTER_INSIDE(7)
}