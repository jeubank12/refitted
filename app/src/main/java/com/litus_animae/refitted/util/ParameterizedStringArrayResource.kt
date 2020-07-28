package com.litus_animae.refitted.util

import android.content.Context
import java.util.*

class ParameterizedStringArrayResource @JvmOverloads constructor(
        private val resourceId: Int,
        private val arrayIndex: Int,
        private val parameters: Array<Any> = emptyArray()
) : ParameterizedResource {
    override fun getStringValue(context: Context): String {
        val array = context.resources.getStringArray(resourceId)
        val parameterizedString = array.getOrElse(arrayIndex) {""}
        val locale = Locale.getDefault()
        return String.format(locale, parameterizedString, *parameters)
    }

    override fun getParameters(): Array<Any> {
        return arrayOf(resourceId, arrayIndex, *parameters)
    }
}