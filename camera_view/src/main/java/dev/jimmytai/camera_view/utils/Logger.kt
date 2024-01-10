package dev.jimmytai.camera_view.utils

import android.util.Log
import androidx.annotation.IntDef

object Logger {

    @Level
    private var level: Int = Log.VERBOSE
    private var custom: CustomLogger? = null

    @JvmStatic
    fun setCustomLogger(logger: CustomLogger?) {
        custom = logger
    }

    @JvmStatic
    fun setLogLevel(@Level level: Int) {
        this.level = level
    }

    @JvmStatic
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (level > Log.VERBOSE) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.verbose(tag, message, throwable)
        } else {
            Log.v(tag, message, throwable)
        }
    }

    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (level > Log.DEBUG) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.debug(tag, message, throwable)
        } else {
            Log.d(tag, message, throwable)
        }
    }

    @JvmStatic
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (level > Log.INFO) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.info(tag, message, throwable)
        } else {
            Log.i(tag, message, throwable)
        }
    }

    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (level > Log.WARN) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.warn(tag, message, throwable)
        } else {
            Log.w(tag, message, throwable)
        }
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (level > Log.ERROR) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.error(tag, message, throwable)
        } else {
            Log.e(tag, message, throwable)
        }
    }

    @MustBeDocumented
    @IntDef(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)
    @Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Level
}

interface CustomLogger {
    fun verbose(tag: String, message: String, throwable: Throwable?)

    fun debug(tag: String, message: String, throwable: Throwable?)

    fun info(tag: String, message: String, throwable: Throwable?)

    fun warn(tag: String, message: String, throwable: Throwable?)

    fun error(tag: String, message: String, throwable: Throwable?)
}