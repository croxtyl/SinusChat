package pl.sinusoide.sinuschat.setup

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import pl.sinusoide.sinuschat.R

class WelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_setup_welcome, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            super.onViewCreated(view, savedInstanceState)

            val logo = view.findViewById<View>(R.id.welcome_logo)
            val title = view.findViewById<TextView>(R.id.welcome_title)
            val subtitle = view.findViewById<TextView>(R.id.welcome_subtitle)
            val btnStart = view.findViewById<Button>(R.id.btn_start)

            listOf(logo, title, subtitle, btnStart).forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                v.alpha = 0f
                v.translationY = 40f
                v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setStartDelay(200L + i * 150L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }

            btnStart?.setOnClickListener {
                (activity as? SetupActivity)?.navigateTo(EmailInputFragment())
            }
        } catch (e: Throwable) {
            Log.e("WelcomeFragment", "Crash in WelcomeFragment", e)
        }
    }
}
