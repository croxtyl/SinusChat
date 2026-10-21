package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AppExitsTest {

    private class Pb {
        val out = ByteArrayOutputStream()
        fun varint(v: Long) {
            var x = v
            while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
            out.write(x.toInt())
        }
        fun num(field: Int, v: Long) = apply { varint((field shl 3).toLong()); varint(v) }
        fun bytes(field: Int, b: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(b.size.toLong()); out.write(b) }
        fun str(field: Int, s: String) = bytes(field, s.toByteArray())
        fun msg(field: Int, m: Pb) = bytes(field, m.out.toByteArray())
        fun fixed64(field: Int) = apply { varint(((field shl 3) or 1).toLong()); repeat(8) { out.write(0) } }
    }

    private fun frame(relPc: Long, file: String, fn: String = "") = Pb().num(1, relPc).num(2, relPc + 0x7000000000L)
        .num(3, 0x7fff0000L).str(4, fn).num(5, 24).str(6, file).str(8, "0123456789abcdef0123")

    private fun thread(id: Long, name: String, vararg frames: Pb) = Pb().num(1, id).str(2, name)
        .msg(3, Pb().str(1, "x0").num(2, 42)).apply { frames.forEach { msg(4, it) } }

    @Test
    fun tombstoneShowsCrashingThreadOnly() {
        val t = Pb()
            .num(1, 3).str(2, "google/fingerprint").num(5, 1234).num(6, 1300).num(7, 10123)
            .msg(10, Pb().num(1, 11).str(2, "SIGSEGV").num(3, 1).str(4, "SEGV_MAPERR").num(8, 1).num(9, 0x10))
            .str(14, "")
            .msg(15, Pb().str(1, "null pointer dereference"))
            .msg(16, Pb().num(1, 1234).msg(2, thread(1234, "main", frame(0x10, "/system/lib64/libc.so"))))
            .msg(16, Pb().num(1, 1300).msg(2, thread(1300, "Thread-7",
                frame(0x1a2b3c, "/data/app/~~x==/pl.sinusoide.sinuschat-y==/base.apk!libsignal_jni.so"),
                frame(0x44, "/data/app/~~x==/pl.sinusoide.sinuschat-y==/lib/arm64/libsignal_jni.so", "Java_org_signal_Native_x"))))
            .msg(17, Pb().num(1, 0x1000).str(7, "/system/lib64/libc.so"))
            .msg(18, Pb().str(1, "main").msg(2, Pb().str(6, "secret log line")))
            .fixed64(30)
        val s = AppExits.Tombstone.summary(t.out.toByteArray(), 16)!!
        assertTrue(s, s.contains("SIGSEGV SEGV_MAPERR · null pointer dereference"))
        assertTrue(s, s.contains("thread \"Thread-7\""))
        assertTrue(s, s.contains("#00 libsignal_jni.so +0x1a2b3c [0123456789ab]"))
        assertTrue(s, s.contains("#01 libsignal_jni.so +0x44 (Java_org_signal_Native_x+24)"))
        assertTrue(s, !s.contains("libc.so") && !s.contains("secret"))
    }

    @Test
    fun tombstoneLimitsFramesAndShowsAbort() {
        val frames = Array(30) { frame(it.toLong(), "/x/liblitertlm_jni.so") }
        val t = Pb().num(6, 5).msg(10, Pb().str(2, "SIGABRT")).str(14, "Out of memory\nmore")
            .msg(16, Pb().num(1, 5).msg(2, thread(5, "llm", *frames)))
        val s = AppExits.Tombstone.summary(t.out.toByteArray(), 16)!!
        assertTrue(s, s.contains("abort: Out of memory"))
        assertTrue(s, !s.contains("more"))
        assertEquals(16, s.lines().count { it.trim().startsWith("#") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun tombstoneRejectsGarbage() {
        AppExits.Tombstone.summary(byteArrayOf(0x0a, 0x7f, 0x01), 16)
    }

    @Test
    fun emptyTombstoneGivesNothing() {
        assertNull(AppExits.Tombstone.summary(ByteArray(0), 16))
    }

    @Test
    fun anrKeepsMainThreadFrames() {
        val trace = """
            ----- pid 1234 at 2026-10-03 21:00:00 -----
            Cmd line: pl.sinusoide.sinuschat

            "Signal Catcher" daemon prio=10 tid=4 Runnable
              at dalvik.system.Other.run(Other.java:1)

            "main" prio=5 tid=1 Blocked
              | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72 self=0xb4
              | sysTid=1234 nice=-10 cgrp=top-app
              at pl.sinusoide.sinuschat.messenger.ChatActivity.onPause(ChatActivity.kt:812)
              - waiting to lock <0x0abc> (a java.lang.Object) held by thread 12
              at android.app.Activity.performPause(Activity.java:8000)

            "Thread-12" prio=5 tid=12 Native
              at pl.sinusoide.sinuschat.Other.x(Other.kt:1)
        """.trimIndent()
        val s = AppExits.anrMainThread(trace)!!
        assertEquals(
            listOf(
                "  \"main\" prio=5 tid=1 Blocked",
                "  messenger.ChatActivity.onPause(ChatActivity.kt:812)",
                "  - waiting to lock <0x0abc> (a java.lang.Object) held by thread 12",
                "  android.app.Activity.performPause(Activity.java:8000)"
            ), s.lines())
    }
}
