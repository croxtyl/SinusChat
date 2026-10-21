package pl.sinusoide.sinuschat.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

object AppExecutor {
    private val pool = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun io(block: () -> Unit) {
        pool.execute {
            try {
                block()
            } catch (e: Throwable) {
                if (e.message == "DATABASE_LOCKED") {
                    Log.w("AppExecutor", "Zadanie zignorowane: Baza danych jest zablokowana.")
                } else {
                    Log.e("AppExecutor", "Background task failed", e)
                }
            }
        }
    }

    fun main(block: () -> Unit) {
        mainHandler.post(block)
    }
}
