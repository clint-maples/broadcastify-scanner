package com.clintmaples.broadcastifyscanner.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Fail-closed host allowlist for popout scrapes and HLS playback.
 * HTTPS only, apex or subdomain of broadcastify.com, port 443, no userinfo.
 * Redirect targets are re-checked before OkHttp follows them.
 */
object BroadcastifyAllowlist {
    private const val APEX = "broadcastify.com"

    fun isAllowedHttpsHost(host: String): Boolean {
        val normalized = host.lowercase().trim().trimEnd('.')
        if (normalized.isEmpty() || normalized.contains(' ')) return false
        return normalized == APEX || normalized.endsWith(".$APEX")
    }

    fun isAllowedUrl(url: HttpUrl): Boolean {
        if (url.scheme != "https") return false
        if (url.port != 443) return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        return isAllowedHttpsHost(url.host)
    }

    fun isAllowedUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return isAllowedUrl(parsed)
    }

    fun requireAllowedHlsUrl(url: String): String {
        val parsed = url.toHttpUrlOrNull()
            ?: throw IllegalStateException("Rejected invalid hlsUrl")
        if (!isAllowedUrl(parsed)) {
            throw IllegalStateException("Rejected non-allowlisted hlsUrl")
        }
        return parsed.toString()
    }

    fun requireAllowedRedirect(from: HttpUrl, location: String): HttpUrl {
        val next = from.resolve(location)
            ?: throw IOException("Rejected invalid redirect Location")
        if (!isAllowedUrl(next)) {
            throw IOException("Rejected off-origin redirect")
        }
        return next
    }

    object RedirectGuard : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!isAllowedUrl(request.url)) {
                throw IOException("Rejected non-allowlisted request host")
            }
            val response = chain.proceed(request)
            if (response.isRedirect) {
                val location = response.header("Location")
                if (location.isNullOrBlank()) {
                    response.close()
                    throw IOException("Redirect missing Location")
                }
                try {
                    requireAllowedRedirect(request.url, location)
                } catch (e: IOException) {
                    response.close()
                    throw e
                }
            }
            return response
        }
    }
}
