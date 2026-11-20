package com.vknxworks.printctrl.data.bambuddy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
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
        val frames = CameraFrames()
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
        e.frames.watching(true)
        e.stopper?.cancel()
        e.stopper = null
        if (e.job?.isActive != true) e.job = scope.launch { run(printerId, e.state, e.frames) }
        return e.state
    }

    @Synchronized
    fun release(printerId: Int) {
        val e = entries[printerId] ?: return
        e.users = (e.users - 1).coerceAtLeast(0)
        if (e.users > 0) return
        e.frames.watching(false)
        e.stopper?.cancel()
        e.stopper = scope.launch {
            delay(LINGER_MS)
            synchronized(this@CameraStreams) {
                if (e.users == 0) {
                    e.job?.cancel()
                    e.job = null
                    e.state.live = false
                    // The spare pictures aren't needed while nothing streams; the last one shown stays.
                    e.frames.clear()
                }
            }
        }
    }

    /** Starts fetching a camera token early, so opening the camera skips that round trip. */
    fun warmUp() {
        scope.launch { runCatching { bambuddy.value?.cameraToken() } }
    }

    private suspend fun run(printerId: Int, state: MjpegState, frames: CameraFrames) {
        combine(bambuddy, fps.distinctUntilChanged()) { api, rate -> api to rate }.collectLatest { (api, rate) ->
            if (api == null) return@collectLatest
            var newToken = false
            var failures = 0
            while (true) {
                try {
                    stream(api, printerId, rate, newToken, state, frames)
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

    private suspend fun stream(api: BambuddyClient, printerId: Int, fps: Int, newToken: Boolean, state: MjpegState, frames: CameraFrames) = withContext(Dispatchers.IO) {
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
                        // No length given: the frame runs up to the next boundary. Look for it in what has
                        // arrived so far and read on a little at a time, giving up once more than the largest
                        // allowed frame is waiting, so a stream that never sends the boundary can't fill memory.
                        val needle = "\r\n$marker".encodeUtf8()
                        var from = 0L
                        var end = src.buffer.indexOf(needle, from)
                        while (end < 0) {
                            val have = src.buffer.size
                            if (have > MAX_FRAME_BYTES + needle.size) throw UserMessageException(R.string.err_camera_frame_too_large)
                            // The boundary may have arrived half: look again from just before the end.
                            from = (have - needle.size + 1).coerceAtLeast(0L)
                            if (!src.request(have + 1)) throw EOFException()
                            end = src.buffer.indexOf(needle, from)
                        }
                        if (end > MAX_FRAME_BYTES) throw UserMessageException(R.string.err_camera_frame_too_large)
                        src.readByteArray(end)
                    }
                    if (type.startsWith("text")) throw CameraMessage(String(bytes).trim().removePrefix("Error:").trim())
                    // Decoded into one of a few bitmaps used in turn (see CameraFrames); a frame that can't be
                    // decoded is skipped and the last picture stays.
                    val shown = frames.show(bytes) { bitmap ->
                        state.lastJpeg = bytes
                        state.frame = bitmap.asImageBitmap()
                    }
                    if (!shown) continue
                    state.live = true
                    state.error = null
                }
            }
        } finally {
            handle.dispose()
        }
    }

    /**
     * Decodes one printer's camera frames into three bitmaps used in turn instead of a new full-size bitmap for
     * every frame (up to 10 a second).
     *
     * The picture is shared: every screen showing this camera draws [MjpegState.frame] as it is at that moment,
     * and none of them keeps an older one. So a bitmap may be written again once the screens have moved on from
     * it, and that is waited for, not assumed: after a frame is shown, three screen refreshes (frames of the
     * main thread) are counted before every frame before it counts as no longer drawn ([FrameRotation.settle]).
     * By then the screens have drawn the newer frame at least once and the drawing that used the older one is
     * finished. That only counts while a screen was showing the camera the whole time ([watching]): a screen
     * that is in the background doesn't redraw, so it could come back still holding an older picture.
     * If a bitmap isn't known to be free when its turn comes (frames arriving in a burst, nobody watching,
     * a busy main thread), the frame gets a new bitmap, exactly as before. So does a frame of another
     * size or anything else Android can't decode into an old bitmap.
     */
    private class CameraFrames {
        private val rotation = FrameRotation<Bitmap>(BUFFERS)
        private val main = Handler(Looper.getMainLooper())

        /** A visible screen is showing this camera. */
        @Volatile private var watched = false

        /** Changes whenever [watched] does, so a wait that started before a change doesn't count. */
        @Volatile private var watchTurn = 0

        /** Called by [acquire] and [release] (one at a time): whether any visible screen shows this camera. */
        fun watching(on: Boolean) {
            if (watched != on) {
                watched = on
                watchTurn++
            }
        }

        /**
         * Decodes [jpeg] and hands the bitmap to [publish]. False when it isn't a picture Android can decode.
         * One frame at a time, also when a stream that was just replaced is still decoding its last frame.
         */
        @Synchronized
        fun show(jpeg: ByteArray, publish: (Bitmap) -> Unit): Boolean {
            val bitmap = rotation.reusable()?.let { decodeInto(jpeg, it) }
                ?: decodeInto(jpeg, null)
                ?: BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                ?: return false
            publish(bitmap)
            // Only a bitmap that can be written again is kept for reuse.
            val number = rotation.shown(bitmap.takeIf { it.isMutable })
            if (watched) {
                val turn = watchTurn
                main.post {
                    afterScreenFrames(SETTLE_FRAMES) { if (watched && watchTurn == turn) rotation.settle(number) }
                }
            }
            return true
        }

        @Synchronized
        fun clear() = rotation.clear()

        /** Runs [done] at the start of the [count]th screen refresh from now. Main thread only. */
        private fun afterScreenFrames(count: Int, done: () -> Unit) {
            if (count <= 0) {
                done()
                return
            }
            Choreographer.getInstance().postFrameCallback { afterScreenFrames(count - 1, done) }
        }

        private fun decodeInto(jpeg: ByteArray, reuse: Bitmap?): Bitmap? = runCatching {
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inSampleSize = 1
                inBitmap = reuse
            }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
        }.getOrNull()

        private companion object {
            const val BUFFERS = 3
            const val SETTLE_FRAMES = 3
        }
    }

    private companion object {
        const val LINGER_MS = 30_000L
        const val MAX_FRAME_BYTES = 16L * 1024 * 1024
    }
}
