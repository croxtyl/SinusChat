package pl.sinusoide.sinuschat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calls",
    indices = [Index("startedAtMs")]
)
data class CallEntity(
    @PrimaryKey val id: String,
    val otherUserHash: String,
    val otherUserName: String,
    val conversationId: String,
    val isVideo: Boolean,
    val direction: String,
    val durationSec: Int,
    val startedAtMs: Long
)
