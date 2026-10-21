package pl.sinusoide.sinuschat.messenger

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.data.MessageEntity
import pl.sinusoide.sinuschat.ui.ThemeUi
import pl.sinusoide.sinuschat.ui.ViewerChrome
import pl.sinusoide.sinuschat.util.AppExecutor
import pl.sinusoide.sinuschat.util.MediaManager
import pl.sinusoide.sinuschat.util.VideoThumbs
import pl.sinusoide.sinuschat.util.ZoomableImageView
import java.io.File

object MediaViewers {

    class Action(val label: String, val run: (MessageEntity, Dialog) -> Unit)

    class Handle(val dialog: Dialog, private val onPause: () -> Unit) {
        fun pause() = onPause()
    }

    private class ViewerDialog(activity: Activity) : Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
        var onBack: (() -> Unit)? = null
        override fun onBackPressed() {
            val back = onBack
            if (back != null) back() else super.onBackPressed()
        }
    }

    private fun newDialog(activity: Activity): ViewerDialog {
        val dialog = ViewerDialog(activity)
        pl.sinusoide.sinuschat.util.PopupHelper.trackOverlay(dialog)
        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        dialog.window?.apply {
            setWindowAnimations(0)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        return dialog
    }

    private fun bar(activity: Activity, top: Boolean, content: LinearLayout.() -> Unit): LinearLayout {
        val dp = activity.resources.displayMetrics.density
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable(
                if (top) android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                else android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xA6000000.toInt(), 0x00000000))
            content()
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val side = (8 * dp).toInt()
            if (top) v.setPadding(bars.left + side, bars.top + (14 * dp).toInt(), bars.right + side, (36 * dp).toInt())
            else v.setPadding(bars.left + (12 * dp).toInt(), (40 * dp).toInt(), bars.right + (14 * dp).toInt(), bars.bottom + (18 * dp).toInt())
            insets
        }
        return bar
    }

    private fun button(activity: Activity, icon: Int, desc: String, onClick: (View) -> Unit) = ImageButton(activity).apply {
        val dp = activity.resources.displayMetrics.density
        setImageResource(icon); setColorFilter(Color.WHITE)
        background = android.util.TypedValue().let { tv ->
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            androidx.core.content.ContextCompat.getDrawable(activity, tv.resourceId)
        }
        val p = (11 * dp).toInt(); setPadding(p, p, p, p)
        contentDescription = desc
        setOnClickListener { onClick(it) }
        layoutParams = LinearLayout.LayoutParams((46 * dp).toInt(), (46 * dp).toInt())
    }

    private fun isVideo(m: MessageEntity) = MediaManager.isVideo(m.attachmentName ?: "")

    private fun localFile(activity: Activity, m: MessageEntity): File? =
        MediaManager.getMediaFile(activity, m.conversationId, m.id, m.attachmentName ?: "").takeIf { it.exists() && it.length() > 0 }

    fun showMedia(
        activity: Activity, items: List<MessageEntity>, start: Int,
        onSave: ((MessageEntity) -> Unit)?, onFail: (MessageEntity, File) -> Unit,
        actions: List<Action> = emptyList(), onClosed: (() -> Unit)? = null
    ): Handle? {
        val media = items.filter { !it.viewOnce && (MediaManager.isImage(it.attachmentName ?: "") || isVideo(it)) }
        if (media.isEmpty()) return null
        val first = items.getOrNull(start)?.let { s -> media.indexOfFirst { it.id == s.id } }?.takeIf { it >= 0 } ?: 0
        pl.sinusoide.sinuschat.util.VoicePlayer.pauseActive()
        val dp = activity.resources.displayMetrics.density
        val dialog = newDialog(activity)
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        val pager = androidx.viewpager2.widget.ViewPager2(activity)
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))

        var revealed = false
        var closing = false
        fun reveal() {
            if (revealed || closing) return
            revealed = true
            root.animate().alpha(1f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            pager.scaleX = 0.92f; pager.scaleY = 0.92f
            pager.animate().scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f)).start()
        }
        fun close() {
            if (closing) return
            closing = true
            root.animate().cancel(); pager.animate().cancel()
            root.animate().alpha(0f).setDuration(190)
                .setInterpolator(android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction { try { dialog.dismiss() } catch (_: Exception) {} }.start()
            pager.animate().scaleX(0.93f).scaleY(0.93f).setDuration(190)
                .setInterpolator(android.view.animation.AccelerateInterpolator(1.4f)).start()
        }
        dialog.onBack = { close() }

        val counter = TextView(activity).apply {
            setTextColor(Color.WHITE); textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val top = bar(activity, true) {
            addView(button(activity, R.drawable.ic_back, activity.getString(R.string.back)) { close() })
            addView(counter, LinearLayout.LayoutParams(0, -2, 1f))
            if (onSave != null) addView(button(activity, R.drawable.ic_download, activity.getString(R.string.save_to_device)) {
                media.getOrNull(pager.currentItem)?.let(onSave)
            })
            if (actions.isNotEmpty()) addView(button(activity, R.drawable.ic_more, activity.getString(R.string.more_options)) { anchor ->
                val m = media.getOrNull(pager.currentItem) ?: return@button
                val popup = android.widget.PopupMenu(activity, anchor)
                actions.forEachIndexed { i, a -> popup.menu.add(0, i, i, a.label) }
                popup.setOnMenuItemClickListener { mi -> actions.getOrNull(mi.itemId)?.run?.invoke(m, dialog); true }
                popup.show()
            })
        }
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        fun timeText() = TextView(activity).apply {
            textSize = 12.5f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            fontFeatureSettings = "tnum"
            text = "0:00"
        }
        val elapsed = timeText()
        val remaining = timeText()
        val wave = WaveformView(activity).apply { setColors(ThemeUi.accent(activity), 0x73FFFFFF) }
        val play = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_play_voice); setColorFilter(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x33FFFFFF)
            }
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val bottom = bar(activity, false) {
            addView(play, LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply { marginEnd = (12 * dp).toInt() })
            addView(elapsed)
            addView(wave, LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f).apply {
                marginStart = (10 * dp).toInt(); marginEnd = (10 * dp).toInt()
            })
            addView(remaining)
        }
        bottom.visibility = View.INVISIBLE
        bottom.alpha = 0f
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        var video: VideoView? = null
        var videoPage: FrameLayout? = null
        var player: android.media.MediaPlayer? = null
        var playing = false
        var finished = false
        var scrubbing = false
        var wasPlaying = false
        var resumeAfterSeek = false
        var seekingUntil = 0L
        var anchorPos = 0
        var anchorAt = 0L
        val chrome = ViewerChrome(listOf(top), 3000L) { video?.isPlaying != true }

        fun clock() = android.os.SystemClock.uptimeMillis()
        fun showTime(pos: Int, dur: Int) {
            val e = VideoThumbs.formatDuration(pos.toLong())
            if (elapsed.text.toString() != e) elapsed.text = e
            val r = "-" + VideoThumbs.formatDuration((dur - pos).coerceAtLeast(0).toLong())
            if (remaining.text.toString() != r) remaining.text = r
        }
        fun smoothPos(v: VideoView, dur: Int): Int {
            val cur = v.currentPosition.coerceIn(0, dur)
            val now = clock()
            if (!v.isPlaying || anchorAt == 0L) { anchorPos = cur; anchorAt = now; return cur }
            val est = anchorPos + (now - anchorAt).toInt()
            if (kotlin.math.abs(cur - est) > 300) { anchorPos = cur; anchorAt = now; return cur }
            return est.coerceIn(0, dur)
        }
        fun seekTo(ms: Int) {
            val v = video ?: return
            seekingUntil = clock() + 1500
            anchorAt = 0L
            val mp = player
            if (mp != null) {
                try { mp.seekTo(ms.toLong(), android.media.MediaPlayer.SEEK_CLOSEST) } catch (_: Exception) { v.seekTo(ms) }
            } else v.seekTo(ms)
        }
        fun refresh() {
            val v = video ?: return
            if (resumeAfterSeek && !scrubbing && clock() >= seekingUntil) {
                resumeAfterSeek = false
                v.start()
            }
            val dur = v.duration
            if (dur > 0 && !scrubbing && clock() >= seekingUntil) {
                val pos = if (finished) dur else smoothPos(v, dur)
                wave.progress = pos.toFloat() / dur
                showTime(pos, dur)
            }
            val now = v.isPlaying
            if (now != playing) {
                playing = now
                play.setImageResource(if (now) R.drawable.ic_pause else R.drawable.ic_play_voice)
                play.contentDescription = activity.getString(if (now) R.string.video_pause else R.string.video_play)
                if (now) { wave.resumeAnimation(); chrome.bump() } else { wave.pauseAnimation(); chrome.show() }
            }
        }
        fun pageBadge(page: FrameLayout?): ImageView? = page?.findViewWithTag("badge")
        fun detachVideo() {
            val v = video ?: return
            try { v.stopPlayback() } catch (_: Exception) {}
            (v.parent as? ViewGroup)?.removeView(v)
            pageBadge(videoPage)?.visibility = View.VISIBLE
            videoPage?.findViewWithTag<View>("poster")?.visibility = View.VISIBLE
            video = null; videoPage = null; player = null
            playing = false; finished = false; scrubbing = false; resumeAfterSeek = false
            seekingUntil = 0L; anchorAt = 0L
            wave.stopAnimation(); wave.progress = 0f
            play.setImageResource(R.drawable.ic_play_voice)
            elapsed.text = "0:00"; remaining.text = "0:00"
        }
        fun attachVideo(page: FrameLayout, m: MessageEntity, file: File) {
            detachVideo()
            val v = VideoView(activity)
            page.addView(v, 1, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            video = v
            videoPage = page
            pageBadge(page)?.visibility = View.GONE
            v.setOnPreparedListener { mp ->
                if (video !== v) return@setOnPreparedListener
                player = mp
                page.findViewWithTag<View>("poster")?.visibility = View.INVISIBLE
                mp.setOnSeekCompleteListener {
                    seekingUntil = 0L
                    anchorAt = 0L
                    if (resumeAfterSeek && !scrubbing && video === v) {
                        resumeAfterSeek = false
                        v.start()
                    }
                }
                v.start()
                refresh()
                chrome.bump()
            }
            v.setOnCompletionListener {
                if (video !== v) return@setOnCompletionListener
                finished = true
                resumeAfterSeek = false
                refresh()
                chrome.show()
            }
            v.setOnErrorListener { _, _, _ ->
                if (video === v) detachVideo()
                onFail(m, file)
                true
            }
            v.setVideoURI(Uri.fromFile(file))
        }

        fun pageFor(position: Int): FrameLayout? =
            ((pager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(position)?.itemView as? FrameLayout)

        fun startVideoAt(position: Int) {
            val m = media.getOrNull(position) ?: return
            if (!isVideo(m)) return
            val go = { f: File ->
                if (pager.currentItem == position && dialog.isShowing) {
                    val page = pageFor(position)
                    if (page != null) attachVideo(page, m, f)
                    else pager.post { pageFor(position)?.let { attachVideo(it, m, f) } }
                }
            }
            val local = localFile(activity, m)
            if (local != null) go(local)
            else AttachmentDownloader.fetch(activity, m) { f ->
                if (f != null) {
                    pager.adapter?.notifyItemChanged(position)
                    pager.post { go(f) }
                }
            }
        }

        var applied = -1
        fun applyPage(position: Int) {
            if (position == applied) return
            applied = position
            counter.text = if (media.size > 1) "${position + 1} / ${media.size}" else ""
            detachVideo()
            val m = media.getOrNull(position) ?: return
            if (isVideo(m)) {
                chrome.views = listOf(top, bottom)
                chrome.show()
                startVideoAt(position)
            } else {
                chrome.views = listOf(top)
                bottom.animate().cancel()
                bottom.alpha = 0f
                bottom.visibility = View.INVISIBLE
                if (!chrome.shown) chrome.show()
            }
        }

        val ticker = object : Runnable {
            override fun run() {
                refresh()
                if (dialog.isShowing) wave.postOnAnimation(this)
            }
        }

        play.setOnClickListener {
            val v = video
            if (v == null) startVideoAt(pager.currentItem)
            else if (v.isPlaying) v.pause()
            else { finished = false; resumeAfterSeek = false; v.start() }
            refresh()
            chrome.bump()
        }
        wave.onScrub = { active ->
            val v = video
            if (v != null) {
                if (active) {
                    scrubbing = true
                    wasPlaying = v.isPlaying || resumeAfterSeek
                    resumeAfterSeek = false
                    if (v.isPlaying) v.pause()
                    wave.pauseAnimation()
                } else if (scrubbing) {
                    scrubbing = false
                    val dur = v.duration
                    if (dur > 0) {
                        finished = false
                        resumeAfterSeek = wasPlaying && player != null
                        seekTo((wave.progress * dur).toInt())
                        if (wasPlaying && player == null) v.start()
                    }
                }
            } else if (!active) wave.progress = 0f
            chrome.bump()
        }
        wave.onSeek = { p ->
            val dur = video?.duration ?: 0
            if (dur > 0) showTime((p * dur).toInt(), dur)
            chrome.bump()
        }

        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = media.size
            override fun getItemViewType(position: Int) = if (isVideo(media[position])) 1 else 0
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                if (viewType == 0) {
                    return object : RecyclerView.ViewHolder(ZoomableImageView(activity).apply {
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        setBackgroundColor(Color.BLACK)
                        onClick = { chrome.toggle() }
                        onScaleChanged = { scale -> pager.isUserInputEnabled = scale <= 1.05f }
                    }) {}
                }
                val page = FrameLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setBackgroundColor(Color.BLACK)
                }
                page.addView(ImageView(activity).apply {
                    tag = "poster"
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, FrameLayout.LayoutParams(-1, -1))
                page.addView(View(activity).apply {
                    isClickable = true
                    setOnClickListener {
                        val pos = (page.parent as? RecyclerView)?.getChildAdapterPosition(page) ?: RecyclerView.NO_POSITION
                        if (video != null && videoPage === page) chrome.toggle()
                        else if (pos != RecyclerView.NO_POSITION) startVideoAt(pos)
                    }
                }, FrameLayout.LayoutParams(-1, -1))
                page.addView(ImageView(activity).apply {
                    tag = "badge"
                    setColorFilter(Color.WHITE)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x73000000)
                    }
                    val p = (18 * dp).toInt(); setPadding(p, p, p, p)
                }, FrameLayout.LayoutParams((68 * dp).toInt(), (68 * dp).toInt(), Gravity.CENTER))
                return object : RecyclerView.ViewHolder(page) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val m = media[position]
                if (holder.itemView is ZoomableImageView) {
                    val iv = holder.itemView as ZoomableImageView
                    Glide.with(iv).clear(iv)
                    iv.setImageDrawable(null)
                    val ext = (m.attachmentName ?: "").substringAfterLast('.', "").lowercase()
                    fun show(f: File) {
                        if (holder.bindingAdapterPosition != position) return
                        if (ext == "gif" || ext == "webp") {
                            Glide.with(iv).load(f).into(iv)
                            if (position == first) reveal()
                            return
                        }
                        AppExecutor.io {
                            val bmp = MediaManager.loadBitmapFromFile(f, 2048)
                            AppExecutor.main {
                                if (holder.bindingAdapterPosition != position) return@main
                                iv.setImageBitmap(bmp)
                                if (position == first && !revealed) reveal()
                                else {
                                    iv.alpha = 0f
                                    iv.animate().alpha(1f).setDuration(160).start()
                                }
                            }
                        }
                    }
                    val local = localFile(activity, m)
                    if (local != null) show(local) else AttachmentDownloader.fetch(activity, m) { f -> if (f != null) show(f) }
                    return
                }
                val page = holder.itemView as FrameLayout
                if (position == first) page.post { reveal() }
                if (videoPage === page && pager.currentItem != position) detachVideo()
                val poster = page.findViewWithTag<ImageView>("poster")
                val badge = page.findViewWithTag<ImageView>("badge")
                poster.visibility = View.VISIBLE
                Glide.with(poster).clear(poster)
                val thumb = VideoThumbs.bitmap(activity, m.conversationId, m.id)
                val local = localFile(activity, m)
                if (local != null) {
                    val ph = thumb?.let { android.graphics.drawable.BitmapDrawable(activity.resources, it) }
                    Glide.with(poster).asBitmap().load(local)
                        .apply(com.bumptech.glide.request.RequestOptions.frameOf(0))
                        .placeholder(ph).error(ph).into(poster)
                } else poster.setImageBitmap(thumb)
                badge.setImageResource(if (local != null) R.drawable.ic_play_voice else R.drawable.ic_download)
                badge.visibility = if (videoPage === page) View.GONE else View.VISIBLE
            }
        }

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = applyPage(position)
        })

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            wave.removeCallbacks(ticker)
            detachVideo()
            chrome.release()
            onClosed?.invoke()
        }
        pager.setCurrentItem(first, false)
        dialog.show()
        root.postDelayed({ reveal() }, 260)
        if (first == 0) pager.post { applyPage(0) }
        wave.postOnAnimation(ticker)
        return Handle(dialog) { video?.let { if (it.isPlaying) it.pause() } }
    }
}
