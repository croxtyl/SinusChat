package pl.sinusoide.sinuschat.util

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import okhttp3.Call
import okhttp3.Request
import okhttp3.ResponseBody
import pl.sinusoide.sinuschat.network.ApiClient
import java.io.IOException
import java.io.InputStream

@Suppress("DEPRECATION")
class TorGlideModule : com.bumptech.glide.module.GlideModule {

    override fun applyOptions(context: Context, builder: GlideBuilder) {}

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(GlideUrl::class.java, InputStream::class.java, Factory(context.applicationContext))
    }

    private class Factory(private val ctx: Context) : ModelLoaderFactory<GlideUrl, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> = Loader(ctx)
        override fun teardown() {}
    }

    private class Loader(private val ctx: Context) : ModelLoader<GlideUrl, InputStream> {
        override fun buildLoadData(model: GlideUrl, width: Int, height: Int, options: Options) =
            ModelLoader.LoadData(model, Fetcher(ctx, model))
        override fun handles(model: GlideUrl) = true
    }

    private class Fetcher(private val ctx: Context, private val url: GlideUrl) : DataFetcher<InputStream> {
        @Volatile private var call: Call? = null
        private var body: ResponseBody? = null

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            val req = Request.Builder().url(url.toStringUrl())
                .apply { url.headers.forEach { (k, v) -> header(k, v) } }
                .build()
            val c = ApiClient.httpClient(ctx).newCall(req)
            call = c
            try {
                val resp = c.execute()
                val b = resp.body
                if (!resp.isSuccessful || b == null) {
                    resp.close()
                    callback.onLoadFailed(IOException("HTTP ${resp.code}"))
                    return
                }
                body = b
                callback.onDataReady(b.byteStream())
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() { try { body?.close() } catch (_: Exception) {} }
        override fun cancel() { call?.cancel() }
        override fun getDataClass(): Class<InputStream> = InputStream::class.java
        override fun getDataSource(): DataSource = DataSource.REMOTE
    }
}
