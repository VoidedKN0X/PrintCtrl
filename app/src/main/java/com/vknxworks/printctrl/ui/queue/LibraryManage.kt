package com.vknxworks.printctrl.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.LibraryFolder
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.theme.Gutter

/** One menu entry: label, and what it does. */
data class MenuAction(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** A "more" button opening a small menu, for a row in the library. */
@Composable
fun MoreMenu(contentDescription: String, actions: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label, color = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        open = false
                        a.onClick()
                    },
                )
            }
        }
    }
}

/** A folder in the library list: tap to open it. */
@Composable
fun FolderRow(folder: LibraryFolder, onOpen: () -> Unit, actions: List<MenuAction>) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = Gutter, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp).padding(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            MetaRow(
                listOfNotNull(
                    pluralStringResource(R.plurals.library_folder_files, folder.fileCount, folder.fileCount),
                    folder.projectName,
                    if (folder.externalReadonly) stringResource(R.string.library_readonly) else null,
                ),
            )
        }
        if (actions.isNotEmpty()) MoreMenu(stringResource(R.string.library_more, folder.name), actions)
    }
}

/**
 * Asks for a name (a new folder, or renaming). For a file the name is pre-selected up to its
 * extension, so typing replaces the name and keeps ".3mf".
 */
@Composable
fun NameDialog(title: String, label: String, initial: String, busy: Boolean, error: String?, keepExtension: Boolean = false, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val end = if (keepExtension) initial.indexOf('.').takeIf { it > 0 } ?: initial.length else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val ok = value.text.isNotBlank() && !busy
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value, { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(value.text.trim()) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text.trim()) }, enabled = ok) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Picks where to move a file: the top of the library or any folder Bambuddy may write to. */
@Composable
fun MoveDialog(fileName: String, folders: List<Pair<LibraryFolder, Int>>, currentFolderId: Int?, busy: Boolean, onDismiss: () -> Unit, onPick: (LibraryFolder?) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.library_move_title, fileName), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "root") {
                    MoveTarget(stringResource(R.string.library_move_root), Icons.Outlined.Home, 0, enabled = !busy && currentFolderId != null) { onPick(null) }
                }
                items(folders.filter { !it.first.externalReadonly }, key = { it.first.id }) { (f, depth) ->
                    MoveTarget(f.name, Icons.Outlined.Folder, depth + 1, enabled = !busy && f.id != currentFolderId) { onPick(f) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveTarget(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, depth: Int, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
