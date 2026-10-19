package com.vknxworks.printctrl.ui.queue

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.vknxworks.printctrl.data.bambuddy.UploadResult
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.SendRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import kotlin.math.roundToInt

/** Bambuddy's file library: pick a file to print, or upload one from the phone. */
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val api = client
        if (uri != null && api != null) {
            uploading = true
            uploadError = null
            scope.launch {
                runCatching { uploadFromPhone(context, api, uri) }
                    .onSuccess { nav.navigate(SendRoute(it.id)) }
                    .onFailure { uploadError = it.friendlyMessage(context) }
                uploading = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.library)) {
                IconButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = client != null && !uploading) {
                    Icon(Icons.Outlined.Upload, stringResource(R.string.upload))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (uploading) {
                Row(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.uploading))
                }
            }
            uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Gutter)) }
            val api = client
            if (api == null) LoadingView() else LibraryList(api) { nav.navigate(SendRoute(it)) }
        }
    }
}

@Composable
private fun LibraryList(api: BambuddyClient, onOpen: (Int) -> Unit) {
    val context = LocalContext.current
    val loader = rememberLoader("library:${api.conn.key}") { api.libraryFiles().sortedByDescending { it.createdAt } }
    var filter by rememberSaveable { mutableStateOf("") }
    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> {
            if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.FolderOpen, stringResource(R.string.library_empty), stringResource(R.string.library_empty_body))
                return
            }
            val files = s.value.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) || it.filename.contains(filter.trim(), ignoreCase = true) }
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    filter, { filter = it },
                    placeholder = { Text(stringResource(R.string.library_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(files, key = { it.id }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(horizontal = Gutter, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Thumb(api.libraryThumbnailUrl(f), Modifier.size(56.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                MetaRow(
                                    listOfNotNull(
                                        f.fileType.uppercase(),
                                        f.printTimeSeconds?.let { formatMinutes(context, (it / 60.0).roundToInt()) },
                                        f.filamentUsedGrams?.let { stringResource(R.string.grams_value, it.roundToInt()) },
                                        if (!f.printable && f.sliceable) stringResource(R.string.needs_slicing) else null,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The file's name as the app that holds it reports it, without any folder part. */
private fun displayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()?.take(200)?.takeIf { it.isNotEmpty() }

/**
 * Streams a picked or shared file from the phone, so even a large one never has to fit in memory.
 * Opened again if OkHttp has to send the body a second time.
 */
private class UriBody(private val context: android.content.Context, private val uri: Uri, private val size: Long) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength(): Long = size
    override fun writeTo(sink: BufferedSink) {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Couldn't read the file")
        input.source().use { sink.writeAll(it) }
    }
}

/** Reads a file the user picked or shared and uploads it to the library. */
suspend fun uploadFromPhone(context: android.content.Context, api: BambuddyClient, uri: Uri): UploadResult {
    val (name, size) = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "upload.3mf"
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()?.takeIf { it > 0 } ?: -1L
        name to size
    }
    return api.upload(name, UriBody(context.applicationContext, uri, size))
}

/**
 * A file shared from another app (or opened with PrintCtrl): asks first, then uploads it and hands
 * back its library id (null when cancelled or failed). Nothing is sent to Bambuddy before the user agrees.
 */
@Composable
fun SharedUploadDialog(uri: Uri, onDone: (Int?) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var confirmed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val name = remember(uri) { displayName(context, uri) }
    androidx.compose.runtime.LaunchedEffect(uri, client, confirmed) {
        if (!confirmed) return@LaunchedEffect
        val api = client ?: return@LaunchedEffect
        runCatching { uploadFromPhone(context, api, uri) }
            .onSuccess { onDone(it.id) }
            .onFailure { error = it.friendlyMessage(context) }
    }
    if (!confirmed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text(stringResource(R.string.share_confirm_title)) },
            text = { Text(stringResource(R.string.share_confirm_body, name ?: stringResource(R.string.share_unknown_file))) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmed = true }) { Text(stringResource(R.string.upload)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name ?: stringResource(R.string.share_unknown_file))
                if (error == null) CircularProgressIndicator(Modifier.size(24.dp)) else Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(null) }) { Text(stringResource(if (error == null) R.string.cancel else R.string.close)) } },
    )
}
