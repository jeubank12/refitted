package com.litus_animae.refitted.util

import android.content.Context

interface ParameterizedResource {
    fun getStringValue(context: Context): String
    fun getParameters(): Array<Any>
}