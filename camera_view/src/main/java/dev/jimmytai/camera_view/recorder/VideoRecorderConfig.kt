package dev.jimmytai.camera_view.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import androidx.annotation.IntDef

class VideoRecorderConfig(
    val outputFormat: Int,
    val videoEncoder: Int,
    val videoFrameRate: Int,
    val videoEncodingBitrate: Int,
    val audioEncoder: Int,
    val audioEncodingBitrate: Int,
) {
    @MustBeDocumented
    @IntDef(
        MediaRecorder.OutputFormat.DEFAULT,
        MediaRecorder.OutputFormat.THREE_GPP,
        MediaRecorder.OutputFormat.MPEG_4
    )
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class OutputFormat

    @MustBeDocumented
    @IntDef(
        MediaRecorder.VideoEncoder.DEFAULT,
        MediaRecorder.VideoEncoder.H264,
        MediaRecorder.VideoEncoder.VP8
    )
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class VideoEncoder

    @MustBeDocumented
    @IntDef(
        MediaRecorder.AudioEncoder.DEFAULT,
        MediaRecorder.AudioEncoder.AAC,
        MediaRecorder.AudioEncoder.HE_AAC,
        MediaRecorder.AudioEncoder.AAC_ELD
    )
    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    annotation class AudioEncoder

    class Builder {
        private var mOutputFormat: Int? = null

        private var mVideoEncoder: Int? = null

        private var mVideoFrameRate: Int? = null

        private var mVideoEncodingBitrate: Int? = null

        private var mAudioEncoder: Int? = null

        private var mAudioEncodingBitrate: Int? = null

        fun setOutputFormat(@OutputFormat format: Int): Builder {
            mOutputFormat = format
            return this
        }

        fun setVideoEncoder(@VideoEncoder encoder: Int): Builder {
            mVideoEncoder = encoder
            return this
        }

        fun setVideoFrameRate(frameRate: Int): Builder {
            mVideoFrameRate = frameRate
            return this
        }

        fun setVideoEncodingBitrate(bitrate: Int): Builder {
            mVideoEncodingBitrate = bitrate
            return this
        }

        fun setAudioEncoder(@AudioEncoder encoder: Int): Builder {
            mAudioEncoder = encoder
            return this
        }

        fun setAudioEncodingBitrate(bitrate: Int): Builder {
            mAudioEncodingBitrate = bitrate
            return this
        }

        fun build(): VideoRecorderConfig = VideoRecorderConfig(
            outputFormat = mOutputFormat ?: MediaRecorder.OutputFormat.MPEG_4,
            videoEncoder = mVideoEncoder ?: MediaRecorder.VideoEncoder.H264,
            videoFrameRate = mVideoFrameRate ?: 30,
            videoEncodingBitrate = mVideoEncodingBitrate ?: 8_000_000,
            audioEncoder = mAudioEncoder ?: MediaRecorder.AudioEncoder.AAC,
            audioEncodingBitrate = mAudioEncodingBitrate ?: 128_000
        )
    }
}

internal fun VideoRecorderConfig.createRecorder(
    context: Context,
    filePath: String,
    outputSize: Size,
): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }.apply {
        /*
         * [Important] - Don't adjust the sequence of these functions.
         *
         * It will report IllegalStateException when the order is incorrect.
         */
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setAudioSource(MediaRecorder.AudioSource.MIC)

        setOutputFormat(outputFormat)
        setVideoSize(outputSize.width, outputSize.height)

        setVideoFrameRate(videoFrameRate)
        setVideoEncodingBitRate(videoEncodingBitrate)
        setVideoEncoder(videoEncoder)

        setAudioEncodingBitRate(audioEncodingBitrate)
        setAudioEncoder(audioEncoder)

        setOutputFile(filePath)
    }
}