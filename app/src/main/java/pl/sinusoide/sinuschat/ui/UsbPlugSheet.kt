package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import pl.sinusoide.sinuschat.R
import pl.sinusoide.sinuschat.util.PopupHelper

object UsbPlugSheet {

    private const val CHECK_MS = 1500L

    fun show(activity: Activity, desc: String, isReady: () -> Boolean, onReady: () -> Unit) {
        val ctx: Context = activity
        val dp = ctx.resources.displayMetrics.density
        val sheet = BottomSheetDialog(ctx, R.style.Theme_SinusChat_BottomSheet)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
        }
        root.addView(View(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(ThemeUi.handle(ctx)) }
        }, LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply { bottomMargin = (14 * dp).toInt() })
        root.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.setup_usb_title)
            textSize = 19f; setTextColor(ThemeUi.textPrimary(ctx)); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(ctx).apply {
            text = desc
            textSize = 13.5f; setTextColor(ThemeUi.textSecondary(ctx)); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(0, (6 * dp).toInt(), 0, (4 * dp).toInt())
        })
        val plug = UsbPlugView(ctx).apply {
            setColors(ThemeUi.textPrimary(ctx), ThemeUi.accent(ctx))
        }
        root.addView(plug, LinearLayout.LayoutParams(-1, (210 * dp).toInt()))

        val handler = Handler(Looper.getMainLooper())
        var found = false
        val onFound = {
            if (!found) {
                found = true
                plug.setConnected(true)
                if (!activity.isFinishing) PopupHelper.showSuccess(activity, ctx.getString(R.string.setup_usb_found))
                handler.postDelayed({
                    sheet.dismiss()
                    if (!activity.isFinishing) onReady()
                }, CHECK_MS)
            }
        }
        val watcher = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!found && isReady()) onFound()
            }
        }
        val poll = object : Runnable {
            override fun run() {
                if (!found && isReady()) onFound()
                if (!found) handler.postDelayed(this, 1000)
            }
        }

        ContextCompat.registerReceiver(ctx, watcher,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED), ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.postDelayed(poll, 450)
        sheet.setOnDismissListener {
            found = true
            handler.removeCallbacksAndMessages(null)
            try { ctx.unregisterReceiver(watcher) } catch (_: Exception) {}
        }
        sheet.setContentView(root)
        FlowGlass.show(sheet)
    }

    fun backupFolderUri(ctx: Context): android.net.Uri? = try {
        val sm = ctx.getSystemService(android.os.storage.StorageManager::class.java)
        val uuid = sm?.storageVolumes
            ?.firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }?.uuid
        uuid?.let {
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "$it:${pl.sinusoide.sinuschat.util.BackupRunner.BACKUP_DIR}")
        }
    } catch (_: Throwable) {
        null
    }

    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    fun usbRootUri(ctx: Context): android.net.Uri? = try {
        val sm = ctx.getSystemService(android.os.storage.StorageManager::class.java)
        val uuid = sm?.storageVolumes
            ?.firstOrNull { !it.isPrimary && it.isRemovable && it.state == Environment.MEDIA_MOUNTED }?.uuid
        uuid?.let { android.provider.DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, "$it:") }
    } catch (_: Throwable) {
        null
    }

    fun phoneDocumentsUri(): android.net.Uri =
        android.provider.DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, "primary:Documents")

    fun usbAttached(ctx: Context): Boolean = try {
        val usb = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
        usb?.deviceList?.isNotEmpty() == true ||
            ctx.getExternalFilesDirs(null).filterNotNull().drop(1).any {
                Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
            }
    } catch (_: Throwable) {
        false
    }
}
