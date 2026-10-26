package com.vuegram.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun newerVersions() {
        assertTrue(Updates.isNewer("0.2.0", "0.1.0"))
        assertTrue(Updates.isNewer("0.1.1", "0.1.0"))
        assertTrue(Updates.isNewer("1.0.0", "0.9.9"))
        assertTrue(Updates.isNewer("0.10.0", "0.9.0"))   // numbers, not text
    }

    @Test
    fun sameOrOlderVersions() {
        assertFalse(Updates.isNewer("0.1.0", "0.1.0"))
        assertFalse(Updates.isNewer("0.0.9", "0.1.0"))
        assertFalse(Updates.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun malformedVersionsAreNeverOffered() {
        // versionCode is built from exactly three parts below 100, so nothing else can be installed as an update.
        assertFalse(Updates.isNewer("0.1.0.1", "0.1.0"))
        assertFalse(Updates.isNewer("1.0", "0.9.9"))
        assertFalse(Updates.isNewer("2.0.0-beta", "0.1.0"))
        assertFalse(Updates.isNewer("100.0.0", "0.1.0"))
        assertFalse(Updates.isNewer("", "0.1.0"))
    }

    private val release = listOf(
        "vuegram.apk", "vuegram.apk.sha256",
        "vuegram-armeabi-v7a.apk", "vuegram-armeabi-v7a.apk.sha256",
        "vuegram-arm64-v8a.apk", "vuegram-arm64-v8a.apk.sha256"
    )

    @Test
    fun apkForTheTvsProcessor() {
        assertEquals("vuegram-arm64-v8a.apk", Updates.chooseApk(release, listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        // A 64-bit chip running 32-bit Android (most TVs) lists only 32-bit ABIs.
        assertEquals("vuegram-armeabi-v7a.apk", Updates.chooseApk(release, listOf("armeabi-v7a", "armeabi")))
        // Something else (an x86 TV box): the universal APK.
        assertEquals("vuegram.apk", Updates.chooseApk(release, listOf("x86_64", "x86")))
    }

    @Test
    fun anApkWithoutItsChecksumIsNeverChosen() {
        assertEquals("vuegram.apk", Updates.chooseApk(listOf("vuegram-arm64-v8a.apk", "vuegram.apk", "vuegram.apk.sha256"), listOf("arm64-v8a")))
        assertNull(Updates.chooseApk(listOf("vuegram.apk"), listOf("arm64-v8a")))
    }
}
