package pl.sinusoide.sinuschat.call

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TextureVideoRenderer(context: Context, name: String) :
    TextureView(context), VideoSink, TextureView.SurfaceTextureListener {

    private val eglRenderer = EglRenderer(name)
    private var initialized = false
    private var released = false
    private var fit = false
    @Volatile private var frameW = 0
    @Volatile private var frameH = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun init(sharedContext: EglBase.Context) {
        if (initialized || released) return
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        initialized = true
        if (isAvailable) surfaceTexture?.let { attachSurface(it) }
    }

    fun setMirror(mirror: Boolean) {
        if (initialized) eglRenderer.setMirror(mirror)
    }

    fun setFit(value: Boolean) {
        if (fit == value) return
        fit = value
        applyScale()
    }

    fun clearImage() {
        if (initialized) eglRenderer.clearImage(0f, 0f, 0f, 0f)
    }

    fun release() {
        if (released) return
        released = true
        if (initialized) eglRenderer.release()
    }

    override fun onFrame(frame: VideoFrame) {
        if (!initialized || released) return
        val w = frame.rotatedWidth
        val h = frame.rotatedHeight
        if (w != frameW || h != frameH) {
            frameW = w
            frameH = h
            if (fit) post { applyScale() }
        }
        eglRenderer.onFrame(frame)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScale()
    }

    private fun applyScale() {
        if (!initialized || released || width <= 0 || height <= 0) return
        if (!fit || frameW <= 0 || frameH <= 0) {
            eglRenderer.setLayoutAspectRatio(width.toFloat() / height)
            setTransform(null)
            return
        }
        eglRenderer.setLayoutAspectRatio(0f)
        val viewAspect = width.toFloat() / height
        val frameAspect = frameW.toFloat() / frameH
        val m = android.graphics.Matrix()
        if (frameAspect > viewAspect) m.setScale(1f, viewAspect / frameAspect, width / 2f, height / 2f)
        else m.setScale(frameAspect / viewAspect, 1f, width / 2f, height / 2f)
        setTransform(m)
    }

    private fun attachSurface(st: SurfaceTexture) {
        eglRenderer.createEglSurface(st)
        applyScale()
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        if (initialized && !released) attachSurface(st)
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
        applyScale()
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        if (initialized && !released) {
            val latch = CountDownLatch(1)
            eglRenderer.releaseEglSurface { latch.countDown() }
            try { latch.await(1, TimeUnit.SECONDS) } catch (_: InterruptedException) {}
        }
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}
