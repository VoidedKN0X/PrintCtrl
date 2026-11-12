package com.vknxworks.printctrl.data.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import com.vknxworks.printctrl.R

/**
 * Plain HTTP is allowed by the platform (Bambuddy and Spoolman often live on a home network), so the
 * app enforces it instead: an unencrypted request only goes out to a server address the user chose
 * (an http:// address they typed, or confirmed after HTTPS failed). Everything else must be HTTPS.
 * Installed as a network interceptor, so every redirect hop is checked: an HTTP hop is allowed only
 * when both the hop's own address and the address the call was started for are allowed HTTP
 * addresses (so an HTTPS request can never be redirected down to HTTP, and an allowed server can't
 * send the app on to another host in cleartext).
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
            // Both the address the call started at and this hop (a redirect may lead elsewhere) must be
            // servers the user chose for plain HTTP, and an HTTPS call never continues over HTTP.
            if (original.isHttps || !isAllowed(original) || !isAllowed(url)) {
                throw UserMessageException(R.string.err_cleartext_blocked, url.host)
            }
        }
        chain.proceed(chain.request())
    }
}
