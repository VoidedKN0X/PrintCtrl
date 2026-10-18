package com.vknxworks.printctrl.ui.printers

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Bambuddy sent a text part instead of a picture, e.g. "Camera connection failed". */
private class CameraMessage(message: String) : IOException(message)

/** What the live camera shows right now. */
class MjpegState {
    var frame by mutableStateOf<ImageBitmap?>(null)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    /** The last picture as Bambuddy sent it, for saving to the gallery. */
    @Volatile
    var lastJpeg: ByteArray? = null
        internal set
}

/**
 * Plays Bambuddy's camera stream for one printer while the screen is visible. The stream comes from
 * Bambuddy, never from the printer itself: the printer allows only one camera connection, and
 * Bambuddy shares its single connection with every viewer.
 */
@Composable
fun rememberMjpeg(printerId: Int, fps: Int? = null): MjpegState {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val rate = fps ?: settings.cameraFps
    val state = remember(printerId) { MjpegState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(printerId, client, rate) {
        val api = client ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var newToken = false
            while (isActive) {
                try {
                    stream(api, printerId, rate, newToken, state)
                    newToken = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // An expired or refused token: get a new one on the next try.
                    newToken = e is ApiException && (e.code == 401 || e.code == 403)
                    state.error = e.friendlyMessage(context)
                }
                delay(3_000)
            }
        }
    }
    return state
}

private suspend fun stream(api: BambuddyClient, printerId: Int, fps: Int, newToken: Boolean, state: MjpegState) = withContext(Dispatchers.IO) {
    val token = api.cameraToken(forceNew = newToken)
    val http = api.conn.client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
    val call = http.newCall(Request.Builder().url(api.cameraStreamUrl(printerId, token, fps)).build())
    val job = currentCoroutineContext().job
    val handle = job.invokeOnCompletion { call.cancel() }
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
                val bytes = if (length >= 0) {
                    src.readByteArray(length)
                } else {
                    val end = src.indexOf("\r\n$marker".encodeUtf8())
                    if (end < 0) throw EOFException()
                    src.readByteArray(end)
                }
                if (type.startsWith("text")) throw CameraMessage(String(bytes).trim().removePrefix("Error:").trim())
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                state.lastJpeg = bytes
                state.frame = bitmap.asImageBitmap()
                state.error = null
            }
        }
    } finally {
        handle.dispose()
    }
}

/** The camera picture, or what's happening instead (connecting, an error). */
@Composable
fun CameraImage(
    state: MjpegState,
    modifier: Modifier = Modifier,
    rotation: Int = 0,
    contentScale: ContentScale = ContentScale.Fit,
    imageModifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val frame = state.frame
        if (frame != null) {
            Image(
                frame,
                contentDescription = stringResource(R.string.camera),
                contentScale = contentScale,
                modifier = imageModifier.fillMaxSize().graphicsLayer { rotationZ = rotation.toFloat() },
            )
        }
        val error = state.error
        when {
            error != null && frame == null -> Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.VideocamOff, null, tint = Color.White.copy(alpha = 0.7f))
                Text(error, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            frame == null -> CircularProgressIndicator(Modifier.size(28.dp), color = Color.White.copy(alpha = 0.8f), strokeWidth = 2.dp)
            error != null -> Text(
                stringResource(R.string.camera_reconnecting),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Saves a camera picture to Pictures/PrintCtrl. Needs no permission on Android 10 and later. */
suspend fun saveToGallery(context: Context, jpeg: ByteArray, name: String): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrintCtrl")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
    try {
        resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: throw IOException("No output stream")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
