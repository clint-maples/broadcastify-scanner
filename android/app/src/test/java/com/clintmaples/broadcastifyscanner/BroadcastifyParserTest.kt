package com.clintmaples.broadcastifyscanner

import com.clintmaples.broadcastifyscanner.data.BroadcastifyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BroadcastifyParserTest {
    @Test
    fun parsePopout_extractsEscapedHlsUrlAndName() {
        val html = """
            <title>East Placer — Broadcastify</title>
            <script>
            ListenPlayer.init('listenPlayerMount', {
                feedId: 14826,
                hlsUrl: "https:\/\/hls-o2.broadcastify.com\/t\/v1.PAYLOAD.SIG\/feed\/14826\/playlist.m3u8",
                feedName: "East Placer and Nevada Counties CAL FIRE NEU - Kings Beach Area"
            });
            </script>
        """.trimIndent()

        val meta = BroadcastifyParser.parsePopout(html, "14826")
        assertEquals("14826", meta.feedId)
        assertEquals(
            "East Placer and Nevada Counties CAL FIRE NEU - Kings Beach Area",
            meta.name,
        )
        assertEquals(
            "https://hls-o2.broadcastify.com/t/v1.PAYLOAD.SIG/feed/14826/playlist.m3u8",
            meta.hlsUrl,
        )
        assertTrue(BroadcastifyParser.hasHardcodedLook(meta.hlsUrl))
    }

    @Test
    fun unescapeJsonish_handlesSlashes() {
        assertEquals(
            "https://example.com/a",
            BroadcastifyParser.unescapeJsonish("https:\\/\\/example.com\\/a"),
        )
    }

    @Test
    fun tokenTimeClaim_readsExp() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1789589000}""".toByteArray())
        val url = "https://hls-o2.broadcastify.com/t/eyJhbGciOiJub25lIn0.$payload.sig/feed/1/playlist.m3u8"
        val claim = BroadcastifyParser.tokenTimeClaimEpochSec(url)
        assertNotNull(claim)
        assertEquals("exp", claim!!.first)
        assertEquals(1789589000L, claim.second)
    }

    @Test
    fun tokenTimeClaim_readsBroadcastifyIssuedAt() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"c":"web","u":0,"f":14826,"t":1789588171}""".toByteArray())
        val url = "https://hls-o2.broadcastify.com/t/v1.$payload.sig/feed/14826/playlist.m3u8"
        val claim = BroadcastifyParser.tokenTimeClaimEpochSec(url)
        assertNotNull(claim)
        assertEquals("t", claim!!.first)
        assertEquals(1789588171L, claim.second)
    }

    @Test(expected = IllegalStateException::class)
    fun parsePopout_missingHlsUrl_throws() {
        BroadcastifyParser.parsePopout("<html><title>Nope</title></html>", "14826")
    }
}
