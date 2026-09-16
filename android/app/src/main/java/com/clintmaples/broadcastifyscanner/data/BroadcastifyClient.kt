package com.clintmaples.broadcastifyscanner.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object BroadcastifyHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val REFERER = "https://www.broadcastify.com/"
    const val ORIGIN = "https://www.broadcastify.com"
}

class BroadcastifyClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    fun fetchFeedMeta(feedId: String): FeedMeta {
        val id = feedId.filter { it.isDigit() }
        require(id.isNotEmpty()) { "Invalid feedId" }

        val url = "https://www.broadcastify.com/listen/feed/popout.php?feedId=$id"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BroadcastifyHttp.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Broadcastify returned ${response.code}")
            }
            val html = response.body?.string().orEmpty()
            return BroadcastifyParser.parsePopout(html, id)
        }
    }
}
