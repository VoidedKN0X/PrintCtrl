package com.vknxworks.printctrl.data.bambuddy

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vknxworks.printctrl.data.net.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.vknxworks.printctrl.data.net.UserMessageException
import com.vknxworks.printctrl.R

/** Bambuddy sent a text part instead of a picture, e.g. "Camera connection failed". */
class CameraMessage(message: String) : IOException(message)

/** What a printer's live camera shows right now. */
class MjpegState {
    var frame by mutableStateOf<ImageBitmap?>(null)
        internal set

    /** False while (re)connecting: [frame] is then the last picture seen, not a live one. */
    var live by mutableStateOf(false)
        internal set

    /** Why there's no live picture, when something went wrong. */
    var error by mutableStateOf<Throwable?>(null)
        internal set

    /** The last picture as Bambuddy sent it, for saving to the gallery. */
    @Volatile
    var lastJpeg: ByteArray? = null
        internal set
}

/**
 * One camera stream per printer, shared by every screen that shows it, so going from the printer page
 * to full screen (or scrolling the camera out of view and back) never reconnects. A stream keeps running
 * for [LINGER_MS] after the last screen lets go of it, so coming back is instant; Bambuddy's own camera
 * connection to the printer (the slow part to start) stays open meanwhile. The last picture is kept, so
 * even a stream that did stop shows something straight away while it reconnects.
 *
 * The stream always comes from Bambuddy, never from the printer itself: the printer allows only one
 * camera connection, and Bambuddy shares its single connection with every viewer.
 */
class CameraStreams(
    private val scope: CoroutineScope,
    private val bambuddy: StateFlow<BambuddyClient?>,
    private val fps: kotlinx.coroutines.flow.Flow<Int>,
) {
    private class Entry(val state: MjpegState) {
        var users = 0
        var job: Job? = null
        var stopper: Job? = null
    }

    private val entries = HashMap<Int, Entry>()

    /** The state for [printerId] without starting anything; for showing the last picture. */
    @Synchronized
    fun state(printerId: Int): MjpegState = entries.getOrPut(printerId) { Entry(MjpegState()) }.state

    /** Starts (or keeps) the stream for [printerId]. Pair every call with [release]. */
    @Synchronized
    fun acquire(printerId: Int): MjpegState {
        val e = entries.getOrPut(printerId) { Entry(MjpegState()) }
        e.users++
        e.stopper?.cancel()
        e.stopper = null
        if (e.job?.isActive != true) e.job = scope.launch { run(printerId, e.state) }
        return e.state
    }

    @Synchronized
    fun release(printerId: Int) {
        val e = entries[printerId] ?: return
        e.users = (e.users - 1).coerceAtLeast(0)
        if (e.users > 0) return
        e.stopper?.cancel()
        e.stopper = scope.launch {
            delay(LINGER_MS)
            synchronized(this@CameraStreams) {
                if (e.users == 0) {
                    e.job?.cancel()
                    e.job = null
                    e.state.live = false
                }
            }
        }
    }

    /** Starts fetching a camera token early, so opening the camera skips that round trip. */
    fun warmUp() {
        scope.launch { runCatching { bambuddy.value?.cameraToken() } }
    }

    private suspend fun run(printerId: Int, state: MjpegState) {
        combine(bambuddy, fps.distinctUntilChanged()) { api, rate -> api to rate }.collectLatest { (api, rate) ->
            if (api == null) return@collectLatest
            var newToken = false
            var failures = 0
            while (true) {
                try {
                    stream(api, printerId, rate, newToken, state)
                    newToken = false
                    failures = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // An expired or refused token: get a new one on the next try.
                    newToken = e is ApiException && (e.code == 401 || e.code == 403)
                    state.error = e
                    failures++
                }
                state.live = false
                // Retry quickly the first time (a dropped connection), then back off a little.
                delay(if (failures <= 1) 500L else 3_000L)
            }
        }
    }

    private suspend fun stream(api: BambuddyClient, printerId: Int, fps: Int, newToken: Boolean, state: MjpegState) = withContext(Dispatchers.IO) {
        val token = api.cameraToken(forceNew = newToken)
        val http = api.conn.client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
        val call = http.newCall(Request.Builder().url(api.cameraStreamUrl(printerId, token, fps)).build())
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                if (!r.isSuccessful) throw ApiException(r.code, null)
                val body = r.body ?: throw EOFException()
                val boundary = body.contentType()?.parameter("boundary")?.trim('"') ?: "frame"
                val marker = "--$boundary"
                val src = body.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    var line = src.readUtf8Line() ?: throw EOFException()
                    while (!line.startsWith(marker)) line = src.readUtf8Line() ?: throw EOFException()
                    var length = -1L
                    var type = ""
                    while (true) {
                        val h = src.readUtf8Line() ?: throw EOFException()
                        if (h.isBlank()) break
                        val colon = h.indexOf(':')
                        if (colon < 0) continue
                        val value = h.substring(colon + 1).trim()
                        when (h.substring(0, colon).trim().lowercase()) {
                            "content-length" -> length = value.toLongOrNull() ?: -1L
                            "content-type" -> type = value.lowercase()
                        }
                    }
                    // A frame is a JPEG of a few hundred KB; anything far bigger is a broken or hostile stream,
                    // not something to load into memory.
                    if (length > MAX_FRAME_BYTES) throw UserMessageException(R.string.err_camera_frame_too_large)
                    val bytes = if (length >= 0) {
                        src.readByteArray(length)
                    } else {
                        val end = src.indexOf("\r\n$marker".encodeUtf8())
                        if (end < 0) throw EOFException()
                        if (end > MAX_FRAME_BYTES) throw UserMessageException(R.string.err_camera_frame_too_large)
                        src.readByteArray(end)
                    }
                    if (type.startsWith("text")) throw CameraMessage(String(bytes).trim().removePrefix("Error:").trim())
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    state.lastJpeg = bytes
                    state.frame = bitmap.asImageBitmap()
                    state.live = true
                    state.error = null
                }
            }
        } finally {
            handle.dispose()
        }
    }

    private companion object {
        const val LINGER_MS = 30_000L
        const val MAX_FRAME_BYTES = 16L * 1024 * 1024
    }
}
