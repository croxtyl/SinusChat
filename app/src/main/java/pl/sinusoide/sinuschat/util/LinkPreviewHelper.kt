package pl.sinusoide.sinuschat.util

import android.content.Context
import android.util.Log
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object LinkPreviewHelper {

    private const val MAX_HTML = 256 * 1024
    private const val MAX_IMAGE = 1_500 * 1024
    private const val MAX_ICON = 64 * 1024

    private val cache = object : android.util.LruCache<String, PreviewData>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: PreviewData) =
            1024 + (value.image?.size ?: 0) + (value.icon?.size ?: 0)
    }
    private val pending = HashMap<String, MutableList<(PreviewData?) -> Unit>>()
    private const val NONE_PREFS = "link_preview_state"
    private const val NONE_KEY = "none"
    private const val NONE_MAX = 500
    private var none: LinkedHashSet<String>? = null

    private class NoPreview : Exception()

    private fun key(url: String) = pl.sinusoide.sinuschat.crypto.E2EManager.sha256(url).take(24)

    private fun noneSet(ctx: Context): LinkedHashSet<String> = synchronized(this) {
        none ?: LinkedHashSet(ctx.applicationContext.getSharedPreferences(NONE_PREFS, Context.MODE_PRIVATE)
            .getString(NONE_KEY, "").orEmpty().split(',').filter { it.isNotEmpty() }).also { none = it }
    }

    fun hasNoPreview(ctx: Context, url: String): Boolean = synchronized(this) { key(url) in noneSet(ctx) }

    private const val STORED_KEY = "stored"
    private const val STORED_MAX = 400
    private var stored: LinkedHashSet<String>? = null

    private fun storedSet(ctx: Context): LinkedHashSet<String> = synchronized(this) {
        stored ?: LinkedHashSet(ctx.applicationContext.getSharedPreferences(NONE_PREFS, Context.MODE_PRIVATE)
            .getString(STORED_KEY, "").orEmpty().split(',').filter { it.isNotEmpty() }).also { stored = it }
    }

    fun isStored(ctx: Context, url: String): Boolean = synchronized(this) { key(url) in storedSet(ctx) }

    private fun setStored(ctx: Context, url: String, on: Boolean) = synchronized(this) {
        val set = storedSet(ctx)
        val k = key(url)
        set.remove(k)
        if (on) set.add(k)
        while (set.size > STORED_MAX) set.remove(set.first())
        ctx.applicationContext.getSharedPreferences(NONE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(STORED_KEY, set.joinToString(",")).apply()
    }

    private fun dao(ctx: Context) = try {
        pl.sinusoide.sinuschat.data.SinusChatDatabase.getInstance(ctx).linkPreviewDao()
    } catch (_: Exception) { null }

    private fun fromDisk(ctx: Context, url: String): PreviewData? {
        if (!isStored(ctx, url)) return null
        val e = try { dao(ctx)?.get(key(url)) } catch (_: Exception) { null }
        if (e == null || e.url != url) return null
        return PreviewData(url, e.title, e.description, e.image, e.icon)
    }

    private fun toDisk(ctx: Context, data: PreviewData) {
        try {
            val d = dao(ctx) ?: return
            d.put(pl.sinusoide.sinuschat.data.LinkPreviewEntity(key(data.url), data.url, data.title, data.description,
                shrink(data.image), data.icon?.takeIf { it.size <= MAX_ICON }, System.currentTimeMillis()))
            d.trim(STORED_MAX)
            setStored(ctx, data.url, true)
        } catch (e: Exception) {
            Log.w("LinkPreview", "Zapis podglądu: ${e.javaClass.simpleName}")
        }
    }

    private fun shrink(bytes: ByteArray?): ByteArray? {
        bytes ?: return null
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return bytes.takeIf { it.size <= 300 * 1024 }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val out = java.io.ByteArrayOutputStream()
            if (bmp.hasAlpha()) bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            else bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
            bmp.recycle()
            out.toByteArray().takeIf { it.size < bytes.size } ?: bytes.takeIf { it.size <= MAX_IMAGE }
        } catch (_: Throwable) { null }
    }

    private fun markNoPreview(ctx: Context, url: String) = synchronized(this) {
        val set = noneSet(ctx)
        set.remove(key(url))
        set.add(key(url))
        while (set.size > NONE_MAX) set.remove(set.first())
        ctx.applicationContext.getSharedPreferences(NONE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(NONE_KEY, set.joinToString(",")).apply()
    }
    private val worker = java.util.concurrent.Executors.newFixedThreadPool(2)

    private val allowed = mutableSetOf<String>()

    class PreviewData(
        val url: String,
        val title: String?,
        val description: String?,
        val image: ByteArray?,
        val icon: ByteArray?
    )

    fun findUrl(text: String): String? {
        val pattern = Pattern.compile(
            "((https?|ftp|file)://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            var url = matcher.group()
            if (url.startsWith("www.", true)) url = "https://$url"
            return url
        }

        val simplePattern = Pattern.compile(
            "([a-z0-9]+(-[a-z0-9]+)*\\.)+[a-z]{2,}(/[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])?",
            Pattern.CASE_INSENSITIVE
        )
        val simpleMatcher = simplePattern.matcher(text)
        if (simpleMatcher.find()) {
            val url = simpleMatcher.group()
            return "https://$url"
        }

        return null
    }

    fun isPreviewable(url: String): Boolean {
        val uri = try { android.net.Uri.parse(url) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".internal")) return false
        if (Regex("^(10|127)\\.").containsMatchIn(host)) return false
        if (Regex("^192\\.168\\.").containsMatchIn(host)) return false
        if (Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)) return false
        if (Regex("^169\\.254\\.").containsMatchIn(host)) return false
        if (host.startsWith("[") || host.contains(":")) return false
        return true
    }

    private const val TRUSTED_HOST = "sinusoide.pl"

    fun isTrusted(url: String): Boolean {
        val uri = try { java.net.URI(url) } catch (_: Exception) { return false }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.rawUserInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return host == TRUSTED_HOST || host.endsWith(".$TRUSTED_HOST")
    }

    fun hostOf(url: String): String = try { android.net.Uri.parse(url).host ?: url } catch (_: Exception) { url }

    fun getCached(url: String): PreviewData? = cache.get(url)

    @Suppress("UNUSED_PARAMETER")
    fun canLoadWithoutAsking(ctx: Context, url: String): Boolean =
        isTrusted(url) || synchronized(allowed) { url in allowed } || isStored(ctx, url)

    fun askAndLoad(activity: android.app.Activity, url: String, onStart: () -> Unit = {}, onLoaded: (PreviewData?) -> Unit) {
        SafeEngine.guard(activity, SafeEngine.Risk.LINK_PREVIEW, hostOf(url)) {
            synchronized(allowed) { allowed.add(url) }
            onStart()
            fetchPreview(activity, url, onLoaded)
        }
    }

    fun fetchPreview(ctx: Context, url: String, onResult: (PreviewData?) -> Unit) {
        cache.get(url)?.let { d -> AppExecutor.main { onResult(d) }; return }
        if (!isPreviewable(url)) { AppExecutor.main { onResult(null) }; return }
        synchronized(pending) {
            pending[url]?.let { it.add(onResult); return }
            pending[url] = mutableListOf(onResult)
        }
        val app = ctx.applicationContext
        worker.execute {
            val saved = fromDisk(app, url)
            val consent = isTrusted(url) || synchronized(allowed) { url in allowed }
            val data = saved ?: if (!consent) {
                if (isStored(app, url)) setStored(app, url, false)
                null
            } else try { load(app, url) } catch (_: NoPreview) {
                markNoPreview(app, url)
                null
            } catch (e: Throwable) {
                Log.w("LinkPreview", "Podgląd nieudany: ${e.javaClass.simpleName}")
                null
            }
            if (data != null) cache.put(url, data)
            if (data != null && saved == null) toDisk(app, data)
            val waiting = synchronized(pending) { pending.remove(url) }.orEmpty()
            AppExecutor.main { waiting.forEach { it(data) } }
        }
    }

    private fun load(ctx: Context, url: String): PreviewData? {
        val tor = PrivacySettings.isTorEnabled(ctx)
        val deadline = android.os.SystemClock.elapsedRealtime() + if (tor) 40_000L else 15_000L
        fun left() = deadline - android.os.SystemClock.elapsedRealtime()
        val client = pl.sinusoide.sinuschat.network.ApiClient.httpClient(ctx).newBuilder()
            .connectTimeout(if (tor) 30 else 6, TimeUnit.SECONDS)
            .readTimeout(if (tor) 30 else 6, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val strict = isTrusted(url) && synchronized(allowed) { url !in allowed }
        fun get(start: String, max: Int, page: Boolean = false): ByteArray? {
            var u = start
            repeat(5) {
                if (!isPreviewable(u) || (strict && !isTrusted(u))) throw NoPreview()
                val remaining = left()
                if (remaining < 500) return null
                val req = Request.Builder().url(u)
                    .header("User-Agent", "Mozilla/5.0 (Android) SinusChat-LinkPreview")
                    .build()
                val resp = client.newBuilder().callTimeout(remaining, TimeUnit.MILLISECONDS).build()
                    .newCall(req).execute()
                if (resp.isRedirect) {
                    val next = resp.header("Location")?.let { absolute(u, it) }
                    resp.close()
                    u = next ?: throw NoPreview()
                    return@repeat
                }
                return resp.use { r ->
                    if (r.code >= 500 || r.code == 429) return null
                    if (page) {
                        val type = r.header("Content-Type").orEmpty().lowercase()
                        if (!r.isSuccessful || (type.isNotEmpty() && "html" !in type)) throw NoPreview()
                    }
                    read(r, max, truncate = page)
                }
            }
            throw NoPreview()
        }

        val html = get(url, MAX_HTML, page = true)?.toString(Charsets.UTF_8) ?: return null
        val title = extractMeta(html, "og:title") ?: extractTag(html, "title")
        val description = extractMeta(html, "og:description") ?: extractMeta(html, "description")
        if (title == null && description == null && extractMeta(html, "og:image") == null) throw NoPreview()
        val imageUrl = extractMeta(html, "og:image")?.let { absolute(url, it) }
        val image = imageUrl?.let { runCatching { get(it, MAX_IMAGE) }.getOrNull() }
        val host = hostOf(url)
        val icon = runCatching { get("https://$host/favicon.ico", MAX_ICON) }.getOrNull()
        return PreviewData(url, title?.take(100), description?.take(200), image, icon)
    }

    private fun read(resp: okhttp3.Response, max: Int, truncate: Boolean = false): ByteArray? {
        if (!resp.isSuccessful) return null
        val body = resp.body ?: return null
        if (!truncate && body.contentLength() > max) return null
        return body.byteStream().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) {
                    if (!truncate) return null
                    out.write(buf, 0, n - (total - max))
                    break
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    private fun absolute(base: String, link: String): String? = try {
        java.net.URL(java.net.URL(base), link).toString()
    } catch (_: Exception) { null }

    private fun extractMeta(html: String, property: String): String? {
        val p = Pattern.compile("<meta[^>]+(?:property|name)=[\"']$property[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(html)
        if (m.find()) return m.group(1)?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }

        val p2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']$property[\"']", Pattern.CASE_INSENSITIVE)
        val m2 = p2.matcher(html)
        if (m2.find()) return m2.group(1)?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }

        return null
    }

    private fun extractTag(html: String, tag: String): String? {
        val p = Pattern.compile("<$tag[^>]*>([^<]+)</$tag>", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(html)
        if (m.find()) return m.group(1)?.trim()?.let { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
        return null
    }
}
