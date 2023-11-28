package dev.jimmytai.camera_view.utils

import android.util.Log

object Logger {
    var level: Int = Log.VERBOSE
    var custom: CustomLogger? = null

    fun v(tag: String, message: String) {
        if (level > Log.VERBOSE) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.verbose(tag, message)
        } else {
            Log.v(tag, message)
        }
    }

    fun d(tag: String, message: String) {
        if (level > Log.DEBUG) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.debug(tag, message)
        } else {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (level > Log.INFO) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.info(tag, message)
        } else {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (level > Log.WARN) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.warn(tag, message)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String) {
        if (level > Log.ERROR) return
        val custom: CustomLogger? = this.custom
        if (custom != null) {
            custom.error(tag, message)
        } else {
            Log.e(tag, message)
        }
    }
}

interface CustomLogger {
    fun verbose(tag: String, message: String)

    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warn(tag: String, message: String)

    fun error(tag: String, message: String)
}