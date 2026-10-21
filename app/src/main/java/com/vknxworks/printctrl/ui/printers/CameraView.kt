package com.vknxworks.printctrl.ui.printers

import android.content.ContentValues
import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.MjpegState
import com.vknxworks.printctrl.ui.components.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.IOException
import com.vknxworks.printctrl.data.net.UserMessageException

/**
 * The live camera of one printer while this screen is visible. The stream itself is shared and kept
 * warm by [com.vknxworks.printctrl.data.bambuddy.CameraStreams], so other screens showing the same
 * camera, and coming back to it, don't reconnect.
 */
@Composable
fun rememberMjpeg(printerId: Int): MjpegState {
    val cameras = LocalContext.current.container.cameras
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(printerId) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            cameras.acquire(printerId)
            try {
                awaitCancellation()
            } finally {
                cameras.release(printerId)
            }
        }
    }
    return remember(printerId) { cameras.state(printerId) }
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
        val context = LocalContext.current
        val error = state.error?.friendlyMessage(context)
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
            error != null || !state.live -> Text(
                stringResource(if (error != null) R.string.camera_reconnecting else R.string.camera_connecting),
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
        resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: throw UserMessageException(R.string.err_write_file)
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
