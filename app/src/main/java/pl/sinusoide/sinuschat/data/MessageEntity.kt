package pl.sinusoide.sinuschat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("status"),
        Index("expiresAtMs")
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderHash: String,
    val text: String = "",
    val timestampMs: Long,
    val status: String = STATUS_PENDING,
    val type: String = TYPE_TEXT,
    val fileId: String? = null,
    val fileKey: String? = null,
    val attachmentName: String? = null,
    val pinned: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val replyToId: String? = null,
    val replyToText: String? = null,
    val expiresAtMs: Long? = null,
    val edited: Boolean = false,
    val viewOnce: Boolean = false,
    val viewed: Boolean = false,
    val isSystemMessage: Boolean = false,
    val albumId: String? = null,
    val trOrig: String? = null,
    val trLocal: String? = null,
    val trFrom: String? = null,
    val trTo: String? = null
) {
    companion object {
        const val STATUS_PENDING   = "pending"
        const val STATUS_SENT      = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ      = "read"
        const val STATUS_FAILED    = "failed"
        const val STATUS_UPLOADING = "uploading"
        const val STATUS_UNDELIVERED = "undelivered"
        const val TYPE_EXPIRED_NOTICE = "sys_message_expired"

        const val TYPE_TEXT       = "text"
        const val TYPE_ATTACHMENT = "attachment"
        const val TYPE_VOICE      = "voice"
    }
}
