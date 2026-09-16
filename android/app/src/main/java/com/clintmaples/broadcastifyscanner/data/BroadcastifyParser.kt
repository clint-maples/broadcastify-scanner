package com.clintmaples.broadcastifyscanner.data

import java.util.Base64

/**
 * Parses Broadcastify popout HTML the same way as desktop `server.py`.
 * HLS URLs look like:
 * `https://hls-o2.broadcastify.com/t/v1.<payload>.<sig>/feed/<id>/playlist.m3u8`
 * The path token is JWT-shaped but uses `v1` instead of a JWT header; claims
 * use `t` (issued-at) rather than standard `exp`. Never persist these URLs.
 */
object BroadcastifyParser {
    private val HLS_RE = Regex("""hlsUrl:\s*"((?:\\.|[^"\\])*)"""")
    private val NAME_RE = Regex("""feedName:\s*"((?:\\.|[^"\\])*)"""")
    private val TITLE_RE = Regex("""<title>([^—<]+)""")
    private val JWT_LIKE = Regex("""(?:eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)|(?:v1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)""")

    fun unescapeJsonish(s: String): String {
        return s.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    fun parsePopout(html: String, feedId: String): FeedMeta {
        val id = feedId.filter { it.isDigit() }
        require(id.isNotEmpty()) { "Invalid feedId" }

        val hlsMatch = HLS_RE.find(html)
            ?: throw IllegalStateException(
                "Could not find hlsUrl in popout page (feed may be offline or page changed)",
            )
        val rawHlsUrl = unescapeJsonish(hlsMatch.groupValues[1])
        if (rawHlsUrl.isBlank()) {
            throw IllegalStateException("Empty hlsUrl in popout page")
        }
        val hlsUrl = BroadcastifyAllowlist.requireAllowedHlsUrl(rawHlsUrl)

        val name = NAME_RE.find(html)?.let { unescapeJsonish(it.groupValues[1]) }
            ?: TITLE_RE.find(html)?.groupValues?.get(1)?.trim()
            ?: DefaultFeeds.ALL.firstOrNull { it.feedId == id }?.name
            ?: "Feed $id"

        return FeedMeta(feedId = id, name = name, hlsUrl = hlsUrl)
    }

    fun extractToken(hlsUrl: String): String? {
        return JWT_LIKE.find(hlsUrl)?.value
    }

    /**
     * Seconds since epoch from a JWT `exp` claim, or Broadcastify `t` claim.
     * `t` on current popout tokens is issued-at, not expiry — callers must
     * not treat a present `t` as "token already expired".
     */
    fun tokenTimeClaimEpochSec(hlsUrl: String): Pair<String, Long>? {
        val token = extractToken(hlsUrl) ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        val payloadB64 = parts[1]
        val padded = payloadB64 + "=".repeat((4 - payloadB64.length % 4) % 4)
        val payload = try {
            String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        Regex(""""exp"\s*:\s*(\d+)""").find(payload)?.groupValues?.get(1)?.toLongOrNull()
            ?.let { return "exp" to it }
        Regex(""""t"\s*:\s*(\d+)""").find(payload)?.groupValues?.get(1)?.toLongOrNull()
            ?.let { return "t" to it }
        return null
    }

    fun hasHardcodedLook(hlsUrl: String): Boolean {
        return hlsUrl.contains("/t/") || extractToken(hlsUrl) != null
    }
}
