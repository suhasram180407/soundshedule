package com.soundschedule.utils

import android.util.Log

object AppLog {
    var isTestEnvironment: Boolean = false

    fun d(tag: String, msg: String) {
        if (isTestEnvironment) {
            println("DEBUG: [$tag] $msg")
            return
        }
        try {
            Log.d(tag, msg)
        } catch (_: RuntimeException) {
            println("DEBUG: [$tag] $msg")
        }
    }

    fun i(tag: String, msg: String) {
        if (isTestEnvironment) {
            println("INFO: [$tag] $msg")
            return
        }
        try {
            Log.i(tag, msg)
        } catch (_: RuntimeException) {
            println("INFO: [$tag] $msg")
        }
    }

    fun w(tag: String, msg: String) {
        if (isTestEnvironment) {
            println("WARN: [$tag] $msg")
            return
        }
        try {
            Log.w(tag, msg)
        } catch (_: RuntimeException) {
            println("WARN: [$tag] $msg")
        }
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        if (isTestEnvironment) {
            println("ERROR: [$tag] $msg ${throwable?.message ?: ""}")
            return
        }
        try {
            Log.e(tag, msg, throwable)
        } catch (_: RuntimeException) {
            println("ERROR: [$tag] $msg ${throwable?.message ?: ""}")
        }
    }
}
