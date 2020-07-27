package com.litus_animae.refitted.util

import android.content.Context

class EmptyStringResource : ParameterizedResource {
    override fun getStringValue(context: Context): String {
        return ""
    }
}