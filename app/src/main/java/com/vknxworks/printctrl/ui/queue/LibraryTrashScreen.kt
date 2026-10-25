package com.vknxworks.printctrl.ui.queue

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.TrashedFile
import com.vknxworks.printctrl.data.bambuddy.deleteFromTrash
import com.vknxworks.printctrl.data.bambuddy.emptyTrash
import com.vknxworks.printctrl.data.bambuddy.libraryTrash
import com.vknxworks.printctrl.data.bambuddy.restoreFromTrash
import com.vknxworks.printctrl.ui.archive.actionMessage
import com.vknxworks.printctrl.ui.archive.formatDay
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.printers.formatBytes
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.launch
import com.vknxworks.printctrl.data.net.UserMessageException

/**
 * Files deleted from the library wait in Bambuddy's trash until it empties itself. Restore one, delete
 * one for good, or empty the whole trash. Needs "Manage Library" on the API key, even just to look.
 */
@Composable
fun LibraryTrashScreen() {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val api = client
    val loader = rememberLoader("library-trash:${api?.conn?.key}") { (api ?: throw UserMessageException(R.string.err_bambuddy_not_connected)).libraryTrash() }
    var confirmEmpty by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Bambuddy's own count: the list itself stops at 500.
    val count = loader.value?.let { maxOf(it.total, it.items.size) } ?: 0

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.library_trash)) {
                if (count > 0) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.trash_empty_action), color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val s = loader.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.error.actionMessage(context, R.string.perm_manage_library)) { loader.reload() }
                is Load.Ok -> if (api != null) {
                    PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                        if (s.value.items.isEmpty()) {
                            MessageView(Icons.Outlined.DeleteOutline, stringResource(R.string.trash_empty), stringResource(R.string.trash_empty_body))
                        } else {
                            TrashList(api, s.value.items, s.value.retentionDays) { loader.reload(silent = true) }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty && api != null) {
        ConfirmDialog(
            stringResource(R.string.trash_empty_title),
            pluralStringResource(R.plurals.trash_empty_confirm, count, count),
            stringResource(R.string.trash_empty_action),
            onDismiss = { confirmEmpty = false },
        ) {
            confirmEmpty = false
            scope.launch {
                runCatching { api.emptyTrash() }
                    .onSuccess { loader.reload(silent = true) }
                    .onFailure { if (it !is kotlin.coroutines.cancellation.CancellationException) Toast.makeText(context, it.actionMessage(context, R.string.perm_manage_library), Toast.LENGTH_LONG).show() }
            }
        }
    }
}

@Composable
private fun TrashList(api: BambuddyClient, items: List<TrashedFile>, retentionDays: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var forever by remember { mutableStateOf<TrashedFile?>(null) }
    var working by remember { mutableStateOf<Int?>(null) }

    fun change(f: TrashedFile, done: String?, block: suspend () -> Unit) {
        working = f.id
        scope.launch {
            runCatching { block() }
                .onSuccess {
                    done?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    onChanged()
                }
                .onFailure { if (it !is kotlin.coroutines.cancellation.CancellationException) Toast.makeText(context, it.actionMessage(context, R.string.perm_manage_library), Toast.LENGTH_LONG).show() }
            working = null
        }
    }

    LazyColumn(Modifier.readableWidth().fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (retentionDays > 0) {
            item(key = "info") {
                Text(
                    stringResource(R.string.trash_info, retentionDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
        }
        items(items, key = { it.id }) { f ->
            Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MetaRow(
                        listOfNotNull(
                            formatBytes(f.fileSize),
                            f.folderName?.let { stringResource(R.string.trash_from_folder, it) },
                            f.autoPurgeAt?.let { formatDay(it) }?.let { stringResource(R.string.trash_deletes_on, it) },
                        ),
                    )
                }
                IconButton(onClick = { change(f, context.getString(R.string.trash_restored, f.filename)) { api.restoreFromTrash(f.id) } }, enabled = working == null) {
                    Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.trash_restore), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { forever = f }, enabled = working == null) {
                    Icon(Icons.Outlined.DeleteForever, stringResource(R.string.trash_delete_forever), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    forever?.let { f ->
        ConfirmDialog(
            stringResource(R.string.trash_delete_title, f.filename),
            stringResource(R.string.trash_delete_body),
            stringResource(R.string.trash_delete_forever),
            onDismiss = { forever = null },
        ) {
            forever = null
            change(f, null) { api.deleteFromTrash(f.id) }
        }
    }
}
