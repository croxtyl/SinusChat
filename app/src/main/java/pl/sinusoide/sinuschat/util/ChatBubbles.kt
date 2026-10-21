package pl.sinusoide.sinuschat.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.provider.Settings
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import pl.sinusoide.sinuschat.messenger.ChatActivity

object ChatBubbles {

    private const val ICON_PX = 216
    private const val LARGE_PX = 144
    private const val AVATAR_FRACTION = 0.68f
    private const val HIDE_DELAY_MS = 4_000L
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isActive(context: Context): Boolean = isSupported() && ExperimentalFeatures.isBubblesEnabled(context)

    fun systemAllows(context: Context): Boolean {
        if (!isSupported()) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            nm.bubblePreference != NotificationManager.BUBBLE_PREFERENCE_NONE
        } else {
            @Suppress("DEPRECATION")
            nm.areBubblesAllowed()
        }
    }

    fun openSystemSettings(activity: Activity) {
        val bubbles = Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        try {
            activity.startActivity(bubbles)
        } catch (_: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            } catch (_: Exception) {}
        }
    }

    fun clear(context: Context) {
        if (!isSupported()) return
        try {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            val ids = ShortcutManagerCompat.getShortcuts(context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED).map { it.id }
            if (ids.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        } catch (_: Exception) {}
    }

    fun forget(context: Context, conversationId: String) {
        if (!isSupported()) return
        try { ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(conversationId)) } catch (_: Exception) {}
    }

    fun person(context: Context, hash: String, name: String): Person =
        Person.Builder().setName(name).setKey(hash).setIcon(icon(context, hash, name)).setImportant(true).build()

    fun publishShortcut(context: Context, conversationId: String, hash: String, name: String, person: Person) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("conversationId", conversationId)
            putExtra("otherUserHash", hash)
            putExtra("otherUserName", name)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, conversationId)
            .setShortLabel(name)
            .setLongLived(true)
            .setPerson(person)
            .setIcon(person.icon ?: icon(context, hash, name))
            .setLocusId(LocusIdCompat(conversationId))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    fun publishGroupShortcut(context: Context, groupId: String, title: String, icon: IconCompat) {
        val intent = Intent(context, ChatActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("conversationId", groupId)
            putExtra("otherUserName", title)
            putExtra("isGroup", true)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, groupId)
            .setShortLabel(title)
            .setLongLived(true)
            .setIcon(icon)
            .setLocusId(LocusIdCompat(groupId))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    fun groupIcon(context: Context, groupId: String, title: String): IconCompat =
        IconCompat.createWithAdaptiveBitmap(padded(groupCircle(context, groupId, title, (ICON_PX * AVATAR_FRACTION).toInt())))

    fun groupLarge(context: Context, groupId: String, title: String): Bitmap = groupCircle(context, groupId, title, LARGE_PX)

    fun contactLarge(context: Context, hash: String, name: String): Bitmap = contactCircle(context, hash, name, LARGE_PX)

    private fun groupCircle(context: Context, groupId: String, title: String, size: Int): Bitmap {
        val (color, url) = pl.sinusoide.sinuschat.call.GroupCallRing.groupAvatar(context, groupId)
        return url?.let { load(context, it, size) }?.let { circle(it, size) }
            ?: AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(title).takeIf { it != "?" } ?: "G", color, size)
    }

    private fun contactCircle(context: Context, hash: String, name: String, size: Int): Bitmap =
        photo(context, hash, size)?.let { circle(it, size) }
            ?: AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(name), AvatarHelper.getContactColor(context, hash), size)

    private fun padded(avatar: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val offset = (ICON_PX - avatar.width) / 2f
        Canvas(out).drawBitmap(avatar, offset, offset, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun hideFromLauncher(context: Context, conversationId: String) {
        val app = context.applicationContext
        main.postDelayed({
            try { ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(conversationId)) } catch (_: Exception) {}
        }, HIDE_DELAY_MS)
    }

    private fun icon(context: Context, hash: String, name: String): IconCompat =
        IconCompat.createWithAdaptiveBitmap(avatarBitmap(context, hash, name))

    private fun avatarBitmap(context: Context, hash: String, name: String): Bitmap {
        val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val size = (ICON_PX * AVATAR_FRACTION).toInt()
        val avatar = photo(context, hash, size)?.let { circle(it, size) }
            ?: AvatarHelper.createLetterBitmap(AvatarHelper.initialOf(name), AvatarHelper.getContactColor(context, hash), size)
        val offset = (ICON_PX - size) / 2f
        Canvas(out).drawBitmap(avatar, offset, offset, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun photo(context: Context, hash: String, size: Int): Bitmap? {
        val source = ContactCache.getAvatarUrl(context, hash) ?: return null
        return load(context, source, size)
    }

    private fun load(context: Context, source: String, size: Int): Bitmap? {
        if (source.startsWith("/")) return decode(source, size)
        if (!source.startsWith("http") || android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return null
        return try {
            com.bumptech.glide.Glide.with(context.applicationContext).asBitmap()
                .load(source)
                .signature(com.bumptech.glide.signature.ObjectKey(source))
                .submit(size, size)
                .get(6, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) { null }
    }

    private fun circle(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = maxOf(size / src.width.toFloat(), size / src.height.toFloat())
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate((size - src.width * scale) / 2f, (size - src.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = android.graphics.BitmapShader(src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                .apply { setLocalMatrix(matrix) }
        }
        Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return out
    }

    private fun decode(path: String, size: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) { null }
}
