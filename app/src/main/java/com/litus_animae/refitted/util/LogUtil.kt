package com.litus_animae.refitted.util

interface LogUtil {
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String)
    fun e(tag: String, msg: String, ex: Throwable)
}