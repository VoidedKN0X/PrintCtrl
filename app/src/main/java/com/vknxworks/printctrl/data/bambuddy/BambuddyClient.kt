package com.vknxworks.printctrl.data.bambuddy

import com.vknxworks.printctrl.data.net.ApiJson
import com.vknxworks.printctrl.data.net.NotThisServerException
import com.vknxworks.printctrl.data.net.below
import com.vknxworks.printctrl.data.net.text
import com.vknxworks.printctrl.data.servers.ServerConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Bambuddy's REST API (everything under /api/v1). The API key goes in the X-API-Key header, added by
 * the connection's client. Endpoints and fields were taken from Bambuddy's router source.
 */
class BambuddyClient(val conn: ServerConnection) {
    private val http = conn.client
    val base: HttpUrl = conn.baseUrl

    fun url(path: String): HttpUrl.Builder = base.below("api/v1/$path")

    private suspend fun <T> get(path: String, serializer: KSerializer<T>, query: Map<String, String> = emptyMap()): T {
        val u = url(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val body = http.text(Request.Builder().url(u).get().build())
        return parse(body, serializer)
    }

    private suspend fun post(path: String, query: Map<String, String> = emptyMap(), json: String = "{}"): String {
        val u = url(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return http.text(Request.Builder().url(u).post(json.toRequestBody(JSON)).build())
    }

    private fun <T> parse(body: String, serializer: KSerializer<T>): T = try {
        ApiJson.decodeFromString(serializer, body)
    } catch (e: Exception) {
        throw NotThisServerException(spoolman = false)
    }

    // ---- Server ----

    suspend fun version(): BambuddyVersion = get("updates/version", BambuddyVersion.serializer())

    suspend fun authStatus(): AuthStatus = get("auth/status", AuthStatus.serializer())

    // ---- Printers ----

    suspend fun printers(): List<Printer> = get("printers/", ListSerializer(Printer.serializer()))

    suspend fun status(printerId: Int): PrinterStatus = get("printers/$printerId/status", PrinterStatus.serializer())

    suspend fun archive(id: Int): ArchiveBrief = get("archives/$id", ArchiveBrief.serializer())

    /** Picture of the plate being printed. Loaded with the API key header, so no token is needed. */
    fun coverUrl(printerId: Int): String = url("printers/$printerId/cover").addQueryParameter("view", "top").build().toString()

    // ---- Controls (need "Control printer" on the API key) ----

    suspend fun pause(printerId: Int) = post("printers/$printerId/print/pause")
    suspend fun resume(printerId: Int) = post("printers/$printerId/print/resume")
    suspend fun stop(printerId: Int) = post("printers/$printerId/print/stop")

    /** 1 silent, 2 standard, 3 sport, 4 ludicrous. */
    suspend fun setSpeed(printerId: Int, level: Int) = post("printers/$printerId/print-speed", mapOf("mode" to level.toString()))

    suspend fun setLight(printerId: Int, on: Boolean) = post("printers/$printerId/chamber-light", mapOf("on" to on.toString()))

    /** [fan] is "part", "aux" or "chamber"; [percent] 0-100. */
    suspend fun setFan(printerId: Int, fan: String, percent: Int) =
        post("printers/$printerId/fan-speed", mapOf("fan" to fan, "speed" to percent.coerceIn(0, 100).toString()))

    suspend fun clearHms(printerId: Int) = post("printers/$printerId/hms/clear")

    /** Tells Bambuddy's queue the plate has been cleared, so the next queued job can start. */
    suspend fun clearPlate(printerId: Int) = post("printers/$printerId/clear-plate")

    // ---- Tokens for the camera and the live updates socket ----
    // Both endpoints only take a ?token= in the address. Tokens last 60 minutes; they're reused for 50.

    private val tokenLock = Mutex()
    private var cachedCameraToken: Pair<String, Long>? = null
    private var cachedSocketToken: Pair<String, Long>? = null

    suspend fun cameraToken(forceNew: Boolean = false): String = tokenLock.withLock {
        val cached = cachedCameraToken?.takeIf { !forceNew && it.second > System.currentTimeMillis() }?.first
        cached ?: parse(post("printers/camera/stream-token"), Token.serializer()).token.also { t ->
            cachedCameraToken = t to System.currentTimeMillis() + TOKEN_REUSE_MS
        }
    }

    suspend fun socketToken(forceNew: Boolean = false): String = tokenLock.withLock {
        val cached = cachedSocketToken?.takeIf { !forceNew && it.second > System.currentTimeMillis() }?.first
        cached ?: parse(post("auth/ws-token"), Token.serializer()).token.also { t ->
            cachedSocketToken = t to System.currentTimeMillis() + TOKEN_REUSE_MS
        }
    }

    fun cameraStreamUrl(printerId: Int, token: String, fps: Int): HttpUrl =
        url("printers/$printerId/camera/stream").addQueryParameter("fps", fps.toString()).addQueryParameter("token", token).build()

    fun cameraSnapshotUrl(printerId: Int, token: String): HttpUrl =
        url("printers/$printerId/camera/snapshot").addQueryParameter("token", token).build()

    /** OkHttp opens WebSockets from http(s) addresses, so the same client (pins, policy) applies. */
    fun socketUrl(token: String): HttpUrl = url("ws").addQueryParameter("token", token).build()

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val TOKEN_REUSE_MS = 50 * 60 * 1000L
    }
}
