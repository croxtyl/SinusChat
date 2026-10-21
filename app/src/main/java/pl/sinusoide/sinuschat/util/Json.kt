package pl.sinusoide.sinuschat.util

import org.json.JSONObject

fun JSONObject.optStringOrNull(name: String): String? {
    if (isNull(name)) return null
    val v = optString(name)
    return if (v.isEmpty() || v == "null") null else v
}
