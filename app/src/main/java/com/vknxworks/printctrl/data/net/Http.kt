package com.vknxworks.printctrl.data.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bambuddy and Spoolman both change often, so every model is parsed leniently: unknown fields are
 * ignored, a null or wrong-typed value falls back to the field's default, and fields are snake_case
 * on the wire.
 */
val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/** A request the server answered with an error status. [detail] is the server's own message, if any. */
class ApiException(val code: Int, val detail: String?) : IOException(detail ?: "HTTP $code")

/** The server answered, but not with what a Bambuddy ([spoolman] false) or Spoolman server would send. */
class NotThisServerException(val spoolman: Boolean) :
    IOException(if (spoolman) "Not a Spoolman server" else "Not a Bambuddy server")

/** Runs the call on OkHttp's thread pool and cancels it when the coroutine is cancelled. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCompleted) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

/** Sends [request] and returns the body as text, or throws [ApiException] for an error status. */
suspend fun OkHttpClient.text(request: Request): String {
    newCall(request).await().use { r ->
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw ApiException(r.code, errorDetail(body))
        return body
    }
}

/** Bambuddy answers errors with {"detail": ...}, Spoolman with {"message": ...}. */
private fun errorDetail(body: String): String? {
    return runCatching {
        val obj = ApiJson.parseToJsonElement(body) as? JsonObject ?: return null
        listOf("detail", "message").firstNotNullOfOrNull { key -> (obj[key] as? JsonPrimitive)?.contentOrNull }
    }.getOrNull()?.take(300)
}

/**
 * [base] plus a path below it, keeping any path prefix the server lives under. A trailing slash is
 * kept: Bambuddy registers list routes as e.g. "/printers/", and its web-app catch-all answers the
 * address without the slash with 404 instead of redirecting.
 */
fun HttpUrl.below(path: String): HttpUrl.Builder = newBuilder().apply {
    path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
    if (path.endsWith("/")) addPathSegment("")
}

/** "host:port" for pinning and for matching requests to a server. */
fun HttpUrl.origin(): String = "${host.lowercase()}:$port"
