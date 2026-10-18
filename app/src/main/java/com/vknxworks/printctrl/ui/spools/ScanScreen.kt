package com.vknxworks.printctrl.ui.spools

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.spoolman.SpoolCode
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ScanRoute
import com.vknxworks.printctrl.ui.nav.SpoolRoute
import kotlinx.coroutines.launch

/**
 * Point the camera at a Spoolman label, or hold a tagged spool to the phone. Opens that spool. Tags are
 * looked up in Spoolman first, then in Bambuddy's inventory (which knows Bambu's own RFID tags).
 */
@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var opened by remember { mutableStateOf(false) }

    /** Replaces the scanner with the spool, so Back returns to where scanning started. */
    fun open(id: Int) {
        if (opened) return
        opened = true
        nav.nav.navigate(SpoolRoute(id)) { popUpTo<ScanRoute> { inclusive = true } }
    }

    fun handleText(text: String): Boolean = when (val code = SpoolCode.parse(text)) {
        is SpoolCode.SpoolId -> {
            open(code.id)
            true
        }
        is SpoolCode.FilamentId -> {
            message = context.getString(R.string.scan_filament_label, code.id)
            true
        }
        null -> false
    }

    fun handleTag(read: NfcRead) {
        if (read.texts.any { handleText(it) }) return
        busy = true
        message = null
        scope.launch {
            try {
                val spoolman = c.spoolman.value
                val viaSpoolman = spoolman?.let { runCatching { it.spoolsByTag(read.uid) }.getOrNull() }?.firstOrNull()
                if (viaSpoolman != null) {
                    open(viaSpoolman.id)
                    return@launch
                }
                val viaBambuddy = c.bambuddy.value?.let { api -> runCatching { api.spoolmanIdForTag(read.uid) }.getOrNull() }
                if (viaBambuddy != null) {
                    open(viaBambuddy)
                    return@launch
                }
                message = context.getString(R.string.scan_tag_unknown, read.uid)
            } catch (e: Exception) {
                message = e.friendlyMessage(context)
            } finally {
                busy = false
            }
        }
    }

    NfcReader { handleTag(it) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            QrScanner(Modifier.fillMaxSize()) { text ->
                if (!handleText(text)) message = context.getString(R.string.scan_not_spoolman)
            }
            // Aiming square.
            Box(
                Modifier.align(Alignment.Center).size(240.dp).border(3.dp, Color.White.copy(alpha = 0.85f), MaterialTheme.shapes.large),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.scan_camera_needed), color = Color.White, textAlign = TextAlign.Center)
                FilledTonalButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
            }
        }
        Row(Modifier.fillMaxWidth().systemBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
            Text(stringResource(R.string.scan_label), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.large).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            Text(
                message ?: stringResource(if (nfcAvailable(context)) R.string.scan_hint_nfc else R.string.scan_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
