package com.practicepartner.music

import org.json.JSONObject

internal fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

internal fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)

internal fun JSONObject.optLongOrNull(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)
