package pl.sinusoide.sinuschat.util

import java.util.Date

data class Timestamp(val seconds: Long, val nanoseconds: Int) {
    fun toDate(): Date = Date(seconds * 1000L + nanoseconds / 1_000_000L)

    companion object {
        fun now(): Timestamp {
            val ms = System.currentTimeMillis()
            return Timestamp(ms / 1000L, ((ms % 1000L) * 1_000_000L).toInt())
        }
    }
}
