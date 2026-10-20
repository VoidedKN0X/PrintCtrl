package com.vknxworks.printctrl.ui.spools

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import zxingcpp.BarcodeReader

/**
 * Camera preview that looks for QR codes with zxing-cpp (open source, decoded on the phone).
 * [onCode] is called once per distinct code.
 */
@Composable
fun QrScanner(modifier: Modifier = Modifier, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onCode)
    val previewView = androidx.compose.runtime.remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val reader = BarcodeReader(BarcodeReader.Options(formats = setOf(BarcodeReader.Format.QR_CODE), tryHarder = true))
        var last: String? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                val text = decode(reader, image)
                image.close()
                if (text != null && text != last) {
                    last = text
                    ContextCompat.getMainExecutor(context).execute { callback.value(text) }
                }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun decode(reader: BarcodeReader, image: ImageProxy): String? = try {
    reader.read(image).firstNotNullOfOrNull { r -> r.text?.takeIf { it.isNotEmpty() } }
} catch (e: Exception) {
    null
}

/** What an NFC tag held: its UID, and any text or link stored on it (a Spoolman label link, for example). */
data class NfcRead(val uid: String, val texts: List<String>)

/**
 * Reads NFC tags while this is on screen. Bambu's own RFID tags are encrypted, so for those only the
 * UID can be read, which is still enough to look a spool up by tag.
 */
@Composable
fun NfcReader(enabled: Boolean = true, onTag: (NfcRead) -> Unit) {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onTag)
    DisposableEffect(enabled) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (!enabled || activity == null || adapter == null || !adapter.isEnabled) return@DisposableEffect onDispose { }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(activity, { tag: Tag ->
            val read = NfcRead(tag.id.joinToString("") { "%02X".format(it) }, ndefTexts(tag))
            ContextCompat.getMainExecutor(context).execute { callback.value(read) }
        }, flags, null)
        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }
}

/** True when the phone has NFC and it's switched on. */
fun nfcAvailable(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

private fun ndefTexts(tag: Tag): List<String> {
    val ndef = Ndef.get(tag) ?: return emptyList()
    return runCatching {
        ndef.connect()
        val msg: NdefMessage? = ndef.ndefMessage ?: ndef.cachedNdefMessage
        msg?.records.orEmpty().mapNotNull { r ->
            r.toUri()?.toString() ?: runCatching {
                // A text record: status byte (language length in the low bits), language, then the text.
                val p = r.payload
                val langLen = p[0].toInt() and 0x3F
                String(p, 1 + langLen, p.size - 1 - langLen, Charsets.UTF_8)
            }.getOrNull()
        }
    }.getOrDefault(emptyList()).also { runCatching { ndef.close() } }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
