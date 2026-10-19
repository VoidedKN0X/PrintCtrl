package com.vknxworks.printctrl.ui.queue

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.vknxworks.printctrl.data.bambuddy.LibraryFile
import com.vknxworks.printctrl.data.bambuddy.LibraryFolder
import com.vknxworks.printctrl.data.bambuddy.UploadResult
import com.vknxworks.printctrl.data.bambuddy.createFolder
import com.vknxworks.printctrl.data.bambuddy.deleteFolder
import com.vknxworks.printctrl.data.bambuddy.flattenTree
import com.vknxworks.printctrl.data.bambuddy.libraryFolders
import com.vknxworks.printctrl.data.bambuddy.moveLibraryFile
import com.vknxworks.printctrl.data.bambuddy.renameFolder
import com.vknxworks.printctrl.data.bambuddy.renameLibraryFile
import com.vknxworks.printctrl.data.bambuddy.trashLibraryFile
import com.vknxworks.printctrl.ui.archive.actionMessage
import com.vknxworks.printctrl.ui.components.ConfirmDialog
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
import com.vknxworks.printctrl.ui.nav.LibraryTrashRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.SendRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Bambuddy's file library: browse folders, pick a file to print, upload one from the phone, and keep
 * it tidy (folders, renaming, moving, the trash). Changes need "Manage Library" on the API key.
 */
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var folderId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Bumped after every change, so the lists load again.
    var version by remember { mutableIntStateOf(0) }
    var uploading by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val api = client
        if (uri != null && api != null) {
            uploading = true
            uploadError = null
            val target = folderId
            scope.launch {
                runCatching {
                    val up = uploadFromPhone(context, api, uri)
                    // Uploads land at the top of the library; put it in the folder that's open.
                    if (target != null) runCatching { api.moveLibraryFile(up.id, target) }
                    up
                }
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
                IconButton(onClick = { dialogError = null; newFolder = true }, enabled = client != null) {
                    Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.library_new_folder))
                }
                IconButton(onClick = { nav.navigate(LibraryTrashRoute) }, enabled = client != null) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.library_trash))
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
            if (api == null) {
                LoadingView()
            } else {
                LibraryList(
                    api, version, folderId,
                    onFolder = { folderId = it },
                    onOpen = { nav.navigate(SendRoute(it)) },
                    onChanged = { version++ },
                )
            }
        }
    }

    val api = client
    if (newFolder && api != null) {
        NameDialog(
            stringResource(R.string.library_new_folder), stringResource(R.string.library_folder_name), "", busy, dialogError,
            onDismiss = { newFolder = false },
        ) { name ->
            busy = true
            scope.launch {
                runCatching { api.createFolder(name, folderId) }
                    .onSuccess { newFolder = false; version++ }
                    .onFailure { dialogError = it.actionMessage(context, R.string.perm_manage_library) }
                busy = false
            }
        }
    }
}

/** What's waiting for an answer in the library list. */
private sealed interface LibraryAsk {
    data class RenameFile(val file: LibraryFile) : LibraryAsk
    data class MoveFile(val file: LibraryFile) : LibraryAsk
    data class TrashFile(val file: LibraryFile) : LibraryAsk
    data class RenameFolder(val folder: LibraryFolder) : LibraryAsk
    data class DeleteFolder(val folder: LibraryFolder) : LibraryAsk
}

@Composable
private fun LibraryList(
    api: BambuddyClient,
    version: Int,
    folderId: Int?,
    onFolder: (Int?) -> Unit,
    onOpen: (Int) -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("library:${api.conn.key}:$version") {
        // Folders are a nicety: a server or key that can't list them still shows every file.
        val folders = runCatching { api.libraryFolders() }.getOrDefault(emptyList())
        api.libraryFiles().sortedByDescending { it.createdAt } to folders
    }
    var filter by rememberSaveable { mutableStateOf("") }
    var ask by remember { mutableStateOf<LibraryAsk?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val tree = loader.value?.second.orEmpty().flattenTree()
    val byId = tree.associate { it.first.id to it.first }
    // Back goes up one folder before it leaves the library.
    BackHandler(enabled = folderId != null) { onFolder(byId[folderId]?.parentId) }

    /** Runs a change, then reloads; a refusal names the permission it needs. */
    fun act(close: Boolean = true, done: String? = null, block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            runCatching { block() }
                .onSuccess {
                    if (close) ask = null
                    done?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    onChanged()
                }
                .onFailure {
                    val msg = it.actionMessage(context, R.string.perm_manage_library)
                    if (ask is LibraryAsk.RenameFile || ask is LibraryAsk.RenameFolder) {
                        error = msg
                    } else {
                        ask = null
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            busy = false
        }
    }

    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> {
            val (allFiles, _) = s.value
            val searching = filter.isNotBlank()
            val folders = if (searching) emptyList() else tree.map { it.first }.filter { it.parentId == folderId }
            val files = if (searching) {
                allFiles.filter { it.name.contains(filter.trim(), ignoreCase = true) || it.filename.contains(filter.trim(), ignoreCase = true) }
            } else {
                allFiles.filter { it.folderId == folderId }
            }
            if (allFiles.isEmpty() && tree.isEmpty()) {
                MessageView(Icons.Outlined.FolderOpen, stringResource(R.string.library_empty), stringResource(R.string.library_empty_body))
                return
            }
            Column(Modifier.readableWidth().fillMaxSize()) {
                OutlinedTextField(
                    filter, { filter = it },
                    placeholder = { Text(stringResource(R.string.library_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                )
                if (!searching && folderId != null) {
                    // Where you are: Library › Parts › Brackets. Each step opens that folder.
                    val path = generateSequence(byId[folderId]) { f -> f.parentId?.let { byId[it] } }.toList().reversed()
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter - 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onFolder(null) }) { Text(stringResource(R.string.library_root)) }
                        path.forEach { f ->
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onFolder(f.id) }, enabled = f.id != folderId) { Text(f.name, maxLines = 1) }
                        }
                    }
                }
                if (folders.isEmpty() && files.isEmpty() && !searching) {
                    MessageView(Icons.Outlined.FolderOpen, stringResource(R.string.library_folder_empty), stringResource(R.string.library_folder_empty_body))
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(folders, key = { "d${it.id}" }) { f ->
                        FolderRow(
                            f,
                            onOpen = { onFolder(f.id) },
                            actions = if (f.externalReadonly) emptyList() else listOf(
                                MenuAction(stringResource(R.string.library_rename)) { error = null; ask = LibraryAsk.RenameFolder(f) },
                                MenuAction(stringResource(R.string.library_delete_folder), danger = true) { ask = LibraryAsk.DeleteFolder(f) },
                            ),
                        )
                    }
                    items(files, key = { "f${it.id}" }) { f ->
                        val readonly = f.folderId?.let { byId[it]?.externalReadonly } == true
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(start = Gutter, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                                        // While searching, say which folder a file is in.
                                        if (searching) f.folderId?.let { byId[it]?.name } else null,
                                    ),
                                )
                            }
                            if (!readonly) {
                                MoreMenu(
                                    stringResource(R.string.library_more, f.name),
                                    listOf(
                                        MenuAction(stringResource(R.string.library_rename)) { error = null; ask = LibraryAsk.RenameFile(f) },
                                        MenuAction(stringResource(R.string.library_move)) { ask = LibraryAsk.MoveFile(f) },
                                        MenuAction(stringResource(R.string.library_to_trash), danger = true) { ask = LibraryAsk.TrashFile(f) },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val a = ask) {
        null -> Unit
        is LibraryAsk.RenameFile -> NameDialog(
            stringResource(R.string.library_rename), stringResource(R.string.library_file_name), a.file.filename, busy, error,
            keepExtension = true, onDismiss = { ask = null },
        ) { name -> act { api.renameLibraryFile(a.file.id, name) } }
        is LibraryAsk.RenameFolder -> NameDialog(
            stringResource(R.string.library_rename), stringResource(R.string.library_folder_name), a.folder.name, busy, error,
            onDismiss = { ask = null },
        ) { name -> act { api.renameFolder(a.folder.id, name) } }
        is LibraryAsk.MoveFile -> MoveDialog(a.file.name, tree, a.file.folderId, busy, onDismiss = { ask = null }) { target ->
            val where = target?.name ?: context.getString(R.string.library_move_root)
            act(done = context.getString(R.string.library_moved, where)) { api.moveLibraryFile(a.file.id, target?.id) }
        }
        is LibraryAsk.TrashFile -> ConfirmDialog(
            stringResource(R.string.library_to_trash),
            stringResource(R.string.library_to_trash_body, a.file.name),
            stringResource(R.string.library_to_trash),
            onDismiss = { ask = null },
        ) { act(done = context.getString(R.string.library_trashed, a.file.name)) { api.trashLibraryFile(a.file.id) } }
        is LibraryAsk.DeleteFolder -> ConfirmDialog(
            stringResource(R.string.library_delete_folder_title, a.folder.name),
            stringResource(R.string.library_delete_folder_body),
            stringResource(R.string.library_delete_folder),
            onDismiss = { ask = null },
        ) {
            // Leave the folder first if it's the one open (or one above it).
            if (folderId == a.folder.id || generateSequence(byId[folderId]) { f -> f.parentId?.let { byId[it] } }.any { it.id == a.folder.id }) {
                onFolder(a.folder.parentId)
            }
            act { api.deleteFolder(a.folder.id) }
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
