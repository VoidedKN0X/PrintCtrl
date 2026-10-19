package com.vknxworks.printctrl.ui.printers

import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.PrinterFile
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.data.net.await
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Human-readable size: 1.4 MB. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return "%.1f %s".format(v, units[i])
}

/**
 * The printer's SD card, through Bambuddy (which reads it over the printer's FTP): browse folders,
 * save a file to the phone's Downloads, and delete files. Needs "Control Printer" on the API key.
 */
@Composable
fun PrinterFilesScreen(printerId: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val live by remember(printerId) { c.printers.printer(printerId) }.collectAsStateWithLifecycle(null)
    var path by rememberSaveable { mutableStateOf("/") }
    BackHandler(enabled = path != "/") { path = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.sd_title, live?.name.orEmpty()).trim()) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val api = client
            if (api == null) LoadingView() else FileList(api, printerId, path, onOpen = { path = it })
        }
    }
}

@Composable
private fun FileList(api: BambuddyClient, printerId: Int, path: String, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("sd:${api.conn.key}:$printerId:$path") {
        api.printerFiles(printerId, path).files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    val storage = rememberLoader("sd-storage:${api.conn.key}:$printerId") { runCatching { api.printerStorage(printerId) }.getOrNull() }
    var deleting by remember { mutableStateOf<PrinterFile?>(null) }
    var downloading by remember { mutableStateOf<String?>(null) }

    fun download(f: PrinterFile) {
        downloading = f.path
        scope.launch {
            val ok = runCatching { saveToDownloads(context, api, printerId, f) }
            downloading = null
            Toast.makeText(
                context,
                ok.fold({ context.getString(R.string.sd_saved, f.name) }, { it.friendlyMessage(context) }),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        storage.value?.let { st ->
            val used = st.usedBytes
            val free = st.freeBytes
            if (used != null && free != null && used + free > 0) {
                Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    Text(stringResource(R.string.sd_storage, formatBytes(used), formatBytes(used + free)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { (used.toFloat() / (used + free)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {})
                }
            }
        }
        if (path != "/") {
            Text(path, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp))
        }
        when (val s = loader.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> {
                val e = s.error
                val msg = if (e is ApiException && e.code == 403) context.getString(R.string.sd_needs_control) else e.friendlyMessage(context)
                ErrorView(msg) { loader.reload() }
            }
            is Load.Ok -> PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                if (s.value.isEmpty()) {
                    MessageView(Icons.Outlined.FolderOff, stringResource(R.string.sd_empty), stringResource(R.string.sd_empty_body))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(s.value, key = { it.path }) { f ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { if (f.isDirectory) onOpen(f.path) else download(f) }
                                    .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    when {
                                        f.isDirectory -> Icons.Outlined.Folder
                                        f.name.endsWith(".3mf", true) -> Icons.Outlined.ViewInAr
                                        f.name.endsWith(".mp4", true) || f.name.endsWith(".avi", true) -> Icons.Outlined.Movie
                                        else -> Icons.Outlined.Description
                                    },
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (!f.isDirectory) MetaRow(listOfNotNull(formatBytes(f.size), f.mtime?.take(16)?.replace('T', ' ')))
                                }
                                if (!f.isDirectory) {
                                    if (downloading == f.path) {
                                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { download(f) }) { Icon(Icons.Outlined.Download, stringResource(R.string.sd_download)) }
                                    }
                                    IconButton(onClick = { deleting = f }) { Icon(Icons.Outlined.Delete, stringResource(R.string.sd_delete), tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { f ->
        ConfirmDialog(
            stringResource(R.string.sd_delete_title),
            stringResource(R.string.sd_delete_body, f.name),
            stringResource(R.string.sd_delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            scope.launch {
                runCatching { api.deletePrinterFile(printerId, f.path) }
                    .onSuccess { loader.reload(silent = true); storage.reload(silent = true) }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            }
        }
    }
}

/**
 * A file name from the printer, made safe to save: no folders, control characters or leading dots, and
 * not too long. Android checks this too; this keeps it from depending on that.
 */
private fun safeFileName(name: String): String {
    val clean = name.substringAfterLast('/').substringAfterLast('\\')
        .filter { it >= ' ' && it != '\u007F' && it !in "<>:\"|?*" }
        .trim().trimStart('.').take(120)
    return clean.ifBlank { "printer-file" }
}

/** Streams a printer file through Bambuddy into Download/PrintCtrl. Large files can take a while. */
private suspend fun saveToDownloads(context: android.content.Context, api: BambuddyClient, printerId: Int, f: PrinterFile) = withContext(Dispatchers.IO) {
    val http = api.conn.client.newBuilder().readTimeout(10, TimeUnit.MINUTES).callTimeout(35, TimeUnit.MINUTES).build()
    http.newCall(Request.Builder().url(api.printerFileUrl(printerId, f.path)).build()).await().use { r ->
        if (!r.isSuccessful) throw ApiException(r.code, null)
        val body = r.body ?: throw IOException("Empty answer")
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeFileName(f.name))
            put(MediaStore.Downloads.MIME_TYPE, body.contentType()?.toString()?.substringBefore(';') ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/PrintCtrl")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Couldn't create the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) } ?: throw IOException("Couldn't write the file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
