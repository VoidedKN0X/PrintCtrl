package com.vknxworks.printctrl.data.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException
import com.vknxworks.printctrl.R

/**
 * OkHttp follows redirects on its own. It drops the standard Authorization header when a redirect
 * leaves the host, but not Bambuddy's X-API-Key header, and a 307/308 redirect sends the request
 * body again. Installed as a network interceptor, this sees every hop: a hop to another server loses
 * the credential headers, and a request with a body is never passed on to another server at all.
 * Moving the same host from HTTP to HTTPS counts as the same server.
 */
object RedirectGuard {
    private val SENSITIVE_HEADERS = listOf("X-API-Key", "Authorization", "Cookie", "Proxy-Authorization")

    private fun sameServer(hop: HttpUrl, original: HttpUrl): Boolean {
        if (!hop.host.equals(original.host, ignoreCase = true)) return false
        if (hop.scheme == original.scheme) return hop.port == original.port
        return !original.isHttps && hop.isHttps
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val original = chain.call().request()
        if (sameServer(request.url, original.url)) return@Interceptor chain.proceed(request)
        if (request.body != null) {
            throw UserMessageException(R.string.err_redirect_refused, request.url.host)
        }
        // Camera and WebSocket tokens travel in the address; another server must not get them.
        val url = request.url.newBuilder().removeAllQueryParameters("token").build()
        val stripped = request.newBuilder()
            .url(url)
            .apply { SENSITIVE_HEADERS.forEach { removeHeader(it) } }
            .build()
        chain.proceed(stripped)
    }
}
