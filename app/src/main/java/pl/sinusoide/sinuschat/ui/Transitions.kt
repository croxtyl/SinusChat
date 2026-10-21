package pl.sinusoide.sinuschat.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build

fun Activity.startWithFade(intent: Intent, enter: Int = android.R.anim.fade_in, exit: Int = android.R.anim.fade_out) {
    startActivity(intent, ActivityOptions.makeCustomAnimation(this, enter, exit).toBundle())
}

fun Activity.finishWithFade() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    } else {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
