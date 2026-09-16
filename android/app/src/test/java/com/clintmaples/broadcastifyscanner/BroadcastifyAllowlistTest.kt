package com.clintmaples.broadcastifyscanner

import com.clintmaples.broadcastifyscanner.data.BroadcastifyAllowlist
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BroadcastifyAllowlistTest {
    @Test
    fun acceptsBroadcastifyHttpsHosts() {
        assertTrue(BroadcastifyAllowlist.isAllowedUrl("https://www.broadcastify.com/listen/feed/popout.php?feedId=1"))
        assertTrue(BroadcastifyAllowlist.isAllowedUrl("https://broadcastify.com/"))
        assertTrue(BroadcastifyAllowlist.isAllowedUrl("https://hls-o2.broadcastify.com/t/v1.PAYLOAD.SIG/feed/14826/playlist.m3u8"))
        assertTrue(BroadcastifyAllowlist.isAllowedUrl("https://HLS-O2.BROADCASTIFY.COM/playlist.m3u8"))
        assertTrue(BroadcastifyAllowlist.isAllowedUrl("https://cdn.foo.broadcastify.com/seg.ts"))
        assertTrue(BroadcastifyAllowlist.isAllowedUrl("https://www.broadcastify.com:443/"))
    }

    @Test
    fun rejectsLookalikesAndUnsafeUrls() {
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("http://www.broadcastify.com/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://evil.example/playlist.m3u8"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://broadcastify.com.evil.com/t/x"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://evilbroadcastify.com/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://notbroadcastify.com/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://127.0.0.1/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://8.8.8.8/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://user:pass@www.broadcastify.com/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://www.broadcastify.com:8443/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("ftp://www.broadcastify.com/"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("javascript:alert(1)"))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl(""))
        assertFalse(BroadcastifyAllowlist.isAllowedUrl("https://evil.com/?next=https://www.broadcastify.com/"))
    }

    @Test
    fun requireAllowedHlsUrl_normalizesAllowlistedUrl() {
        val url = "https://hls-o2.broadcastify.com/t/v1.PAYLOAD.SIG/feed/14826/playlist.m3u8"
        assertEquals(url, BroadcastifyAllowlist.requireAllowedHlsUrl(url))
    }

    @Test(expected = IllegalStateException::class)
    fun requireAllowedHlsUrl_rejectsOffOrigin() {
        BroadcastifyAllowlist.requireAllowedHlsUrl("https://evil.example/playlist.m3u8")
    }

    @Test
    fun requireAllowedRedirect_sameOriginHttps() {
        val from = "https://www.broadcastify.com/listen/feed/popout.php?feedId=1".toHttpUrl()
        val next = BroadcastifyAllowlist.requireAllowedRedirect(
            from,
            "https://www.broadcastify.com/listen/feed/popout.php?feedId=1&rewritten=1",
        )
        assertEquals("www.broadcastify.com", next.host)
        assertTrue(next.queryParameterNames.contains("rewritten"))
    }

    @Test
    fun requireAllowedRedirect_relativeLocationStaysOnHost() {
        val from = "https://www.broadcastify.com/listen/feed/popout.php?feedId=1".toHttpUrl()
        val next = BroadcastifyAllowlist.requireAllowedRedirect(from, "/listen/feed/popout.php?feedId=2")
        assertEquals("https://www.broadcastify.com/listen/feed/popout.php?feedId=2", next.toString())
    }

    @Test(expected = IOException::class)
    fun requireAllowedRedirect_rejectsOffOrigin() {
        val from = "https://www.broadcastify.com/listen/feed/popout.php?feedId=1".toHttpUrl()
        BroadcastifyAllowlist.requireAllowedRedirect(from, "https://evil.example/steal")
    }

    @Test(expected = IOException::class)
    fun requireAllowedRedirect_rejectsHttpDowngrade() {
        val from = "https://www.broadcastify.com/listen/feed/popout.php?feedId=1".toHttpUrl()
        BroadcastifyAllowlist.requireAllowedRedirect(from, "http://www.broadcastify.com/listen/feed/popout.php")
    }
}
