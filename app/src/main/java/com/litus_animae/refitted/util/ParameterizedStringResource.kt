package com.litus_animae.refitted.util

import android.content.Context

class ParameterizedStringResource @JvmOverloads constructor(
        private val resourceId: Int,
        private val parameters: Array<Any> = emptyArray()
) : ParameterizedResource {
    override fun getStringValue(context: Context): String {
        return context.getString(resourceId, *parameters)
    }

    override fun getParameters(): Array<Any> {
        return arrayOf(resourceId, *parameters)
    }
}