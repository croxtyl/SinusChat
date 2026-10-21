package pl.sinusoide.sinuschat.messenger

import android.content.Context
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.data.MessageEntity
import pl.sinusoide.sinuschat.util.MediaManager

object ReplyLabel {

    fun media(ctx: Context, item: MessageEntity): String? {
        val name = item.attachmentName
        return when {
            item.text.startsWith("geo:") || (name ?: "").startsWith("location_") -> ctx.getString(R.string.location)
            item.type == MessageEntity.TYPE_VOICE -> ctx.getString(R.string.voice_message)
            name == null -> null
            MediaManager.isImage(name) -> ctx.getString(R.string.photo)
            MediaManager.isVideo(name) -> ctx.getString(R.string.video)
            else -> name
        }
    }

    fun of(ctx: Context, item: MessageEntity): String =
        media(ctx, item)
            ?: item.text.take(100).ifEmpty { ctx.getString(R.string.reply_quote_message) }
}
