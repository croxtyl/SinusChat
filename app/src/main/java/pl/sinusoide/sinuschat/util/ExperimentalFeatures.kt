package pl.sinusoide.sinuschat.util

import android.content.Context

object ExperimentalFeatures {
    private const val PREFS = "sinuschat_experimental"
    private const val KEY_AI = "ai_mode"
    private const val KEY_PIP = "call_pip"
    private const val KEY_BUBBLES = "chat_bubbles"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAiEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AI, false)

    fun setAiEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AI, enabled).commit()
    }

    fun isPipEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PIP, false)

    fun setPipEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PIP, enabled).apply()
    }

    fun isBubblesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BUBBLES, false)

    fun setBubblesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BUBBLES, enabled).apply()
    }

    fun isPipSupported(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun downloadedModelFiles(context: Context): List<java.io.File> =
        pl.sinusoide.sinuschat.SiniaOfflineFragment.MODELS
            .map { java.io.File(context.filesDir, it.filename) }
            .filter { it.isFile }

    fun downloadedModelsBytes(context: Context): Long =
        downloadedModelFiles(context).sumOf { it.length() }

    fun deleteDownloadedModels(context: Context): Long {
        val cutoff = System.currentTimeMillis() - 2 * 60_000L
        var freed = 0L
        for (f in downloadedModelFiles(context)) {
            if (f.lastModified() > cutoff) continue
            val len = f.length()
            if (f.delete()) freed += len
        }
        return freed
    }
}
