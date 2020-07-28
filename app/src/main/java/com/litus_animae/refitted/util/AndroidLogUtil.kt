package com.litus_animae.refitted.util

import android.util.Log

object AndroidLogUtil : LogUtil{
    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    override fun e(tag: String, msg: String, ex: Throwable) {
        Log.e(tag, msg, ex)
    }
}