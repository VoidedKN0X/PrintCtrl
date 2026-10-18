package com.vknxworks.printctrl.data.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Plain HTTP is allowed by the platform (Bambuddy and Spoolman often live on a home network), so the
 * app enforces it instead: an unencrypted request only goes out to a server address the user chose
 * (an http:// address they typed, or confirmed after HTTPS failed). Everything else must be HTTPS.
 * Installed as a network interceptor, so every redirect hop is checked: an HTTP hop is allowed only
 * when the call itself was started for an allowed HTTP address (so an HTTPS request can never be
 * redirected down to HTTP).
 */
object CleartextPolicy {
    private val allowed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun origin(url: HttpUrl) = "${url.host.lowercase()}:${url.port}"

    /** Allows plain HTTP to the server at [baseUrl]. No-op for https addresses. */
    fun allowServer(baseUrl: String) {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        if (!url.isHttps) allowed += origin(url)
    }

    fun isAllowed(url: HttpUrl): Boolean = url.isHttps || origin(url) in allowed

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps) {
            val original = chain.call().request().url
            if (original.isHttps || !isAllowed(original)) {
                throw IOException("Blocked an unencrypted (HTTP) connection to ${url.host}")
            }
        }
        chain.proceed(chain.request())
    }
}
