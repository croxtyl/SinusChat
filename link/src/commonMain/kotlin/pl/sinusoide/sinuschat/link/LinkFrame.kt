package pl.sinusoide.sinuschat.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatSummary(
    val id: String,
    val title: String,
    val isGroup: Boolean = false,
    val peer: String = "",
    val color: String = "#5288C1",
    val lastText: String = "",
    val lastKind: String = ChatMessage.KIND_TEXT,
    val lastAt: Long = 0L,
    val lastFromMe: Boolean = false,
    val lastStatus: String = "",
    val unread: Int = 0,
    val muted: Boolean = false,
    val online: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: String,
    val conv: String,
    val sender: String,
    val senderName: String = "",
    val mine: Boolean = false,
    val text: String = "",
    val kind: String = KIND_TEXT,
    val fileName: String? = null,
    val at: Long,
    val status: String = "",
    val replyTo: String? = null,
    val replyText: String? = null,
    val edited: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val viewOnce: Boolean = false,
    val expiresAt: Long? = null,
    val pinned: Boolean = false
) {
    companion object {
        const val KIND_TEXT = "text"
        const val KIND_SYSTEM = "system"
        const val KIND_PHOTO = "photo"
        const val KIND_VIDEO = "video"
        const val KIND_VOICE = "voice"
        const val KIND_FILE = "file"
        const val KIND_LOCATION = "location"

        const val STATUS_PENDING = "pending"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
    }
}

@Serializable
sealed class LinkFrame {

    @Serializable
    @SerialName("hello")
    data class Hello(val name: String, val app: String, val protocol: Int = LinkProtocol.VERSION) : LinkFrame()

    @Serializable
    @SerialName("ping")
    data class Ping(val id: Long) : LinkFrame()

    @Serializable
    @SerialName("pong")
    data class Pong(val id: Long) : LinkFrame()

    @Serializable
    @SerialName("bye")
    data class Bye(val reason: String) : LinkFrame()

    @Serializable
    @SerialName("chats_get")
    data class ChatsRequest(val refresh: Boolean = false) : LinkFrame()

    @Serializable
    @SerialName("chat_open")
    data class OpenChat(val conv: String, val limit: Int = 80) : LinkFrame()

    @Serializable
    @SerialName("chat_close")
    data class CloseChat(val conv: String) : LinkFrame()

    @Serializable
    @SerialName("chat_older")
    data class OlderMessages(val conv: String, val before: Long, val limit: Int = 80) : LinkFrame()

    @Serializable
    @SerialName("send_text")
    data class SendText(val id: String, val conv: String, val text: String, val replyTo: String? = null) : LinkFrame()

    @Serializable
    @SerialName("mark_read")
    data class MarkRead(val conv: String) : LinkFrame()

    @Serializable
    @SerialName("typing_out")
    data class TypingOut(val conv: String, val typing: Boolean) : LinkFrame()

    @Serializable
    @SerialName("chats")
    data class Chats(val items: List<ChatSummary>, val hidePreviews: Boolean = false) : LinkFrame()

    @Serializable
    @SerialName("messages")
    data class Messages(
        val conv: String,
        val items: List<ChatMessage>,
        val mode: String = MODE_LATEST,
        val more: Boolean = false
    ) : LinkFrame()

    @Serializable
    @SerialName("send_result")
    data class SendResult(val id: String, val ok: Boolean, val error: String? = null) : LinkFrame()

    @Serializable
    @SerialName("phone_state")
    data class PhoneState(val locked: Boolean) : LinkFrame()

    @Serializable
    @SerialName("incoming")
    data class Incoming(
        val conv: String,
        val id: String,
        val title: String,
        val sender: String,
        val text: String,
        val kind: String = ChatMessage.KIND_TEXT,
        val at: Long,
        val preview: String = PREVIEW_FULL
    ) : LinkFrame()

    @Serializable
    @SerialName("typing_in")
    data class TypingIn(val conv: String, val name: String, val typing: Boolean) : LinkFrame()

    companion object {
        const val BYE_UNPAIRED = "unpaired"
        const val BYE_CLOSING = "closing"

        const val MODE_LATEST = "latest"
        const val MODE_OLDER = "older"

        const val PREVIEW_FULL = "full"
        const val PREVIEW_NAME = "name"
        const val PREVIEW_HIDDEN = "hidden"

        const val ERROR_BLOCKED = "blocked"
        const val ERROR_DELETED = "deleted"
        const val ERROR_NO_KEY = "no_key"
        const val ERROR_READ_ONLY = "read_only"
        const val ERROR_LOCKED = "locked"
        const val ERROR_FAILED = "failed"

        val json = Json {
            classDiscriminator = "t"
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun encode(frame: LinkFrame): String = json.encodeToString(serializer(), frame)

        fun decode(text: String): LinkFrame? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            null
        }
    }
}
