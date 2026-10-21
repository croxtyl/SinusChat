package pl.sinusoide.sinuschat

import android.graphics.Bitmap

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: Role,
    val text: String,
    val image: Bitmap? = null,
    val emotion: Emotion = Emotion.NORMAL,
    val isStreaming: Boolean = false,
    val thinking: String = "",
    val file: SiniaFile? = null
) {
    enum class Role { USER, SINIA }
    enum class Emotion { HAPPY, SAD, LOVE, NORMAL }
}
