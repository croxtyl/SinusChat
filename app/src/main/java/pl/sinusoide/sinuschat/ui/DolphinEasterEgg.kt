package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import pl.sinusoide.sinuschat.R

object DolphinEasterEgg {

    fun show(activity: Activity, versionName: String) {
        val dp = activity.resources.displayMetrics.density
        val dialog = android.app.Dialog(activity, android.R.style.Theme_NoTitleBar)
        
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.enableEdgeToEdge(window)
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        val bgColor = pl.sinusoide.sinuschat.ui.ThemeUi.backgroundSolid(activity)
        val textColor = pl.sinusoide.sinuschat.ui.ThemeUi.textPrimary(activity)
        
        val root = FrameLayout(activity).apply {
            setBackgroundColor(bgColor)
        }

        val physicsContainer = FrameLayout(activity)
        root.addView(physicsContainer, FrameLayout.LayoutParams(-1, -1))

        var currentNavBarHeight = 0
        var currentStatusBarHeight = 0

        val glassNav = View(activity).apply {
            val glassColor = if (bgColor == android.graphics.Color.BLACK) "#33FFFFFF" else "#40808080"
            setBackgroundColor(android.graphics.Color.parseColor(glassColor))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.MIRROR))
            }
        }
        val navParams = FrameLayout.LayoutParams(-1, 0, android.view.Gravity.BOTTOM)
        root.addView(glassNav, navParams)

        val glassStatus = View(activity).apply {
            val glassColor = if (bgColor == android.graphics.Color.BLACK) "#33FFFFFF" else "#40808080"
            setBackgroundColor(android.graphics.Color.parseColor(glassColor))
        }
        val statusParams = FrameLayout.LayoutParams(-1, 0, android.view.Gravity.TOP)
        root.addView(glassStatus, statusParams)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            currentNavBarHeight = bars.bottom
            currentStatusBarHeight = bars.top
            navParams.height = bars.bottom
            glassNav.layoutParams = navParams
            statusParams.height = bars.top
            glassStatus.layoutParams = statusParams
            insets
        }

        val tvVersion = TextView(activity).apply {
            text = activity.getString(R.string.version_dolphin_format, versionName)
            textSize = 18f
            setTextColor(textColor)
            alpha = 0.3f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        val versionParams = FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM)
        root.addView(tvVersion, versionParams)

        val mainDolphin = ImageView(activity).apply {
            setImageResource(R.drawable.ic_dolphin)
            val dp = activity.resources.displayMetrics.density
            layoutParams = FrameLayout.LayoutParams((220 * dp).toInt(), (220 * dp).toInt(), android.view.Gravity.CENTER)
            setColorFilter(android.graphics.Color.parseColor("#007AFF"))
            isClickable = true
            isFocusable = true
        }
        root.addView(mainDolphin)

        var tiltGravityX = 0f
        var tiltGravityY = 1800f

        class PhysicsDolphin(val view: ImageView) {
            var x = 0f
            var y = 0f
            var vx = 0f
            var vy = 0f
            var isDragging = false
            var lastTouchX = 0f
            var lastTouchY = 0f

            fun update(dt: Float, width: Int, height: Int) {
                if (isDragging) return

                vx += tiltGravityX * dt
                vy += tiltGravityY * dt

                x += vx * dt
                y += vy * dt

                val size = view.width.toFloat()
                if (x < 0) { x = 0f; vx = -vx * 0.5f }
                if (x > width - size) { x = width - size; vx = -vx * 0.5f }
                
                if (y < currentStatusBarHeight) {
                    y = currentStatusBarHeight.toFloat()
                    vy = -vy * 0.5f
                }
                if (y > height - currentNavBarHeight - size) {
                    y = height - currentNavBarHeight - size
                    vy = -vy * 0.4f
                    vx *= 0.95f 
                }

                view.translationX = x
                view.translationY = y
                view.rotation = (vx * 0.04f).coerceIn(-60f, 60f)
            }
        }

        val dolphins = mutableListOf<PhysicsDolphin>()
        val random = java.util.Random()

        fun spawnDolphins(count: Int) {
            val dp = activity.resources.displayMetrics.density
            val colors = listOf("#FF3B30", "#FF9500", "#FFCC00", "#4CD964", "#5AC8FA", "#007AFF", "#5856D6", "#FF2D55", "#00E5FF", "#FF00FF")
            
            for (i in 0 until count) {
                val size = (50 + random.nextInt(40)) * dp
                val iv = ImageView(activity).apply {
                    setImageResource(R.drawable.ic_dolphin)
                    layoutParams = FrameLayout.LayoutParams(size.toInt(), size.toInt())
                    setColorFilter(android.graphics.Color.parseColor(colors[random.nextInt(colors.size)]))
                }
                physicsContainer.addView(iv)
                
                val pd = PhysicsDolphin(iv)
                val loc = IntArray(2)
                mainDolphin.getLocationInWindow(loc)
                pd.x = loc[0].toFloat() + mainDolphin.width / 2 - size / 2
                pd.y = loc[1].toFloat() + mainDolphin.height / 2 - size / 2
                
                val angle = random.nextFloat() * Math.PI * 2
                val speed = 800f + random.nextFloat() * 1200f
                pd.vx = (Math.cos(angle) * speed).toFloat()
                pd.vy = (Math.sin(angle) * speed).toFloat()
                
                iv.setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            pd.isDragging = true
                            pd.lastTouchX = event.rawX
                            pd.lastTouchY = event.rawY
                            pd.vx = 0f; pd.vy = 0f
                            v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).start()
                            v.performClick()
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - pd.lastTouchX
                            val dy = event.rawY - pd.lastTouchY
                            pd.x += dx
                            pd.y += dy
                            v.translationX = pd.x
                            v.translationY = pd.y
                            pd.vx = dx / 0.016f 
                            pd.vy = dy / 0.016f
                            pd.lastTouchX = event.rawX
                            pd.lastTouchY = event.rawY
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            pd.isDragging = false
                            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        }
                    }
                    true
                }
                dolphins.add(pd)
            }
        }

        mainDolphin.setOnClickListener {
            spawnDolphins(13)
            
            mainDolphin.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                .withEndAction { mainDolphin.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }
                .start()
        }

        val sensorManager = activity.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val sensorListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                tiltGravityX = -event.values[0] * 380f
                tiltGravityY = event.values[1] * 380f
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        }

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val physicsRunnable = object : Runnable {
            var lastTime = System.nanoTime()
            override fun run() {
                val now = System.nanoTime()
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                
                val w = root.width
                val h = root.height
                if (w > 0 && h > 0) {
                    dolphins.forEach { it.update(dt.coerceAtMost(0.033f), w, h) }
                }
                handler.postDelayed(this, 16)
            }
        }
        handler.post(physicsRunnable)

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            handler.removeCallbacks(physicsRunnable)
            sensorManager?.unregisterListener(sensorListener)
        }
        
        mainDolphin.scaleX = 0f; mainDolphin.scaleY = 0f; mainDolphin.alpha = 0f
        mainDolphin.animate().scaleX(1f).scaleY(1f).alpha(1f).rotationBy(360f).setDuration(1000).start()
        
        dialog.show()
    }
}
