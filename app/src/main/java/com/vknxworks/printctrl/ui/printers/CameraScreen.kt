package com.vknxworks.printctrl.ui.printers

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Full-screen camera: pinch to zoom, drag to look around, double-tap to reset, save a picture to the gallery. */
@Composable
fun CameraScreen(printerId: Int) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val live by remember(printerId) { c.printers.printer(printerId) }.collectAsStateWithLifecycle(null)
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val mjpeg = rememberMjpeg(printerId)

    if (settings.keepScreenOnCamera) {
        DisposableEffect(Unit) {
            val window = context.findActivity()?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraImage(
            mjpeg,
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    })
                }
                .transformable(transform),
            rotation = live?.printer?.cameraRotation ?: 0,
            imageModifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .systemBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = OnCamera) }
            Text(
                live?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = OnCamera,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val jpeg = mjpeg.lastJpeg
                if (jpeg == null) {
                    Toast.makeText(context, R.string.snapshot_none, Toast.LENGTH_SHORT).show()
                    return@IconButton
                }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val name = "PrintCtrl-${(live?.name ?: "printer").replace(Regex("[^A-Za-z0-9_-]"), "_")}-$stamp.jpg"
                scope.launch {
                    val ok = saveToGallery(context, jpeg, name)
                    Toast.makeText(context, if (ok) R.string.snapshot_saved else R.string.snapshot_failed, Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.snapshot), tint = OnCamera) }
        }
        if (scale > 1f) {
            Text(
                "${"%.1f".format(scale)}×",
                color = OnCamera,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
