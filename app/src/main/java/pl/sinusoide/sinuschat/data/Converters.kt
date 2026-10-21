package pl.sinusoide.sinuschat.data

import androidx.room.TypeConverter
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromReactionsMap(value: Map<String, String>): String {
        if (value.isEmpty()) return ""
        val obj = JSONObject()
        for ((k, v) in value) obj.put(k, v)
        return obj.toString()
    }

    @TypeConverter
    fun toReactionsMap(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(value)
            val map = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
