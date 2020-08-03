package com.litus_animae.refitted.util

object TestLogUtil : LogUtil {
    override fun d(tag: String, msg: String) {
        println("DEBUG: $tag$msg")
    }

    override fun w(tag: String, msg: String) {
        println("WARN: $tag$msg")
    }

    override fun e(tag: String, msg: String) {
        println("ERROR: $tag$msg")
    }

    override fun e(tag: String, msg: String, ex: Throwable) {
        println("ERROR: $tag$msg ${ex.message}")
    }
}