package pl.sinusoide.sinuschat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.sinusoide.sinuschat.R

class FileIconsTest {

    @Test
    fun iconsByFileName() {
        assertEquals(R.drawable.ic_html, FileIcons.forName("index.html"))
        assertEquals(R.drawable.ic_html, FileIcons.forName("strona.HTM"))
        assertEquals(R.drawable.ic_js, FileIcons.forName("app.js"))
        assertEquals(R.drawable.ic_js, FileIcons.forName("view.jsx"))
        assertEquals(R.drawable.ic_css, FileIcons.forName("style.css"))
        assertEquals(R.drawable.ic_css, FileIcons.forName("theme.scss"))
        assertEquals(R.drawable.ic_php, FileIcons.forName("config.php"))
        assertEquals(R.drawable.ic_json, FileIcons.forName("package.json"))
        assertEquals(R.drawable.ic_sql, FileIcons.forName("dump.sql"))
        assertEquals(R.drawable.ic_xml, FileIcons.forName("AndroidManifest.xml"))
        assertEquals(R.drawable.ic_terminal, FileIcons.forName("deploy.sh"))
        assertEquals(R.drawable.ic_terminal, FileIcons.forName("run.bat"))
        assertEquals(R.drawable.ic_terminal, FileIcons.forName("setup.ps1"))
        assertEquals(R.drawable.ic_code, FileIcons.forName("Main.kt"))
        assertEquals(R.drawable.ic_code, FileIcons.forName("server.ts"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("paczka.zip"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("backup.RAR"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("archiwum.7z"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("kopia.tar.gz"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("logi.tgz"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("dane.tar"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("obraz.tar.xz"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("plik.bz2"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("paczka.zst"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("instalator.cab"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("duze.zipx"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("film.7z.001"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("film.z01"))
        assertEquals(R.drawable.ic_zip, FileIcons.forName("film.r00"))
        assertEquals(R.drawable.ic_document, FileIcons.forName("raport.001"))
        assertEquals(R.drawable.ic_apk, FileIcons.forName("SinusChat-0.9.5.apk"))
        assertEquals(R.drawable.ic_apk, FileIcons.forName("gra.XAPK"))
        assertEquals(R.drawable.ic_apk, FileIcons.forName("app.apks"))
        assertEquals(R.drawable.ic_apk, FileIcons.forName("app.apkm"))
        assertEquals(R.drawable.ic_document, FileIcons.forName("notatki.txt"))
        assertEquals(R.drawable.ic_document, FileIcons.forName("umowa.pdf"))
        assertEquals(R.drawable.ic_document, FileIcons.forName("README"))
    }

    @Test
    fun recognisesAdbCommands() {
        assertTrue(FileIcons.isAdb("adb devices\nadb shell pm list packages -3\nadb reboot"))
        assertTrue(FileIcons.isAdb("@echo off\nadb.exe install app.apk\npause"))
        assertTrue(FileIcons.isAdb("#!/bin/bash\n# flash\nfastboot flash boot boot.img\nfastboot reboot"))
        assertTrue(FileIcons.isAdb("$ adb logcat -c\n$ adb logcat"))
        assertFalse(FileIcons.isAdb("sudo apt update\nsudo apt upgrade -y\nadb devices\nsudo reboot"))
        assertFalse(FileIcons.isAdb("ls -la\ncd /var/www"))
        assertFalse(FileIcons.isAdb(""))
    }

    @Test
    fun snippetIcons() {
        val adb = CodeSnippets.segments("adb devices\nadb shell pm list packages")!!.single { it.code }
        assertEquals(R.drawable.ic_adb, FileIcons.forSnippet(adb))
        val shell = CodeSnippets.segments("sudo apt update\nsudo systemctl restart nginx")!!.single { it.code }
        assertEquals(R.drawable.ic_terminal, FileIcons.forSnippet(shell))
        val html = CodeSnippets.segments("```html\n<p>Hej</p>\n```")!!.single { it.code }
        assertEquals(R.drawable.ic_html, FileIcons.forSnippet(html))
        val kotlin = CodeSnippets.segments("```kotlin\nval x = 1\n```")!!.single { it.code }
        assertEquals(R.drawable.ic_code, FileIcons.forSnippet(kotlin))
    }
}
