package pl.sinusoide.sinuschat

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import pl.sinusoide.sinuschat.util.AccountWiper
import pl.sinusoide.sinuschat.util.ThemeManager
import androidx.activity.enableEdgeToEdge

class SessionKickedActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(pl.sinusoide.sinuschat.util.LocaleManager.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_kicked)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener((findViewById<android.view.ViewGroup>(android.R.id.content)).getChildAt(0)) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<pl.sinusoide.sinuschat.ui.SessionMoveView>(R.id.iv_session_anim)
            ?.setColors(android.graphics.Color.WHITE, pl.sinusoide.sinuschat.ui.ThemeUi.accent(this))

        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { wipeAndExit() }
            })

        findViewById<Button>(R.id.btn_understood).setOnClickListener { wipeAndExit() }
    }

    private fun wipeAndExit() {
        AccountWiper.wipeAndRestart(applicationContext)
        finishAffinity()
    }
}
