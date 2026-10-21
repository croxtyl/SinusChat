package pl.sinusoide.sinuschat.data

import android.content.Context
import pl.sinusoide.sinuschat.util.AppExecutor
import java.util.UUID

object CallLogRepository {

    const val INCOMING = "incoming"
    const val MISSED = "missed"
    const val DECLINED = "declined"
    const val OUTGOING = "outgoing"

    fun log(context: Context, otherHash: String, otherName: String, conversationId: String,
            isVideo: Boolean, direction: String, durationSec: Int, startedAtMs: Long) {
        val entry = CallEntity(
            id = UUID.randomUUID().toString(),
            otherUserHash = otherHash,
            otherUserName = otherName,
            conversationId = conversationId,
            isVideo = isVideo,
            direction = direction,
            durationSec = durationSec,
            startedAtMs = startedAtMs
        )
        AppExecutor.io {
            try {
                SinusChatDatabase.getInstance(context).callDao().insert(entry)
            } catch (_: Exception) {}
        }
    }
}
