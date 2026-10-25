package com.vknxworks.printctrl.ui.archive

import com.vknxworks.printctrl.ui.components.CtrlAlertDialog
import com.vknxworks.printctrl.ui.components.CtrlTextField
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.Archive
import com.vknxworks.printctrl.data.bambuddy.ArchiveDeleteImpact
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.archiveTags
import com.vknxworks.printctrl.data.bambuddy.parseTags
import com.vknxworks.printctrl.data.bambuddy.updateArchive
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.components.friendlyMessage
import kotlinx.coroutines.launch

/**
 * The message for a failed change: a refused API key (403) names the permission to turn on in Bambuddy,
 * anything else gets the usual explanation.
 */
fun Throwable.actionMessage(context: Context, permission: Int): String =
    if (this is ApiException && code == 403) context.getString(R.string.needs_permission, context.getString(permission))
    else friendlyMessage(context)

/** Edit a print's notes and tags. Tags used on other prints are offered to pick from. */
@Composable
fun EditArchiveDialog(api: BambuddyClient, archive: Archive, onDismiss: () -> Unit, onSaved: (Archive) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf(archive.notes.orEmpty()) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(parseTags(archive.tags)) } }
    var newTag by remember { mutableStateOf("") }
    var known by remember { mutableStateOf<List<String>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { known = runCatching { api.archiveTags().map { it.name } }.getOrDefault(emptyList()) }

    fun addTag() {
        // Bambuddy keeps tags in one comma-separated string, so a comma can't be part of a tag.
        newTag.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
            if (tags.none { it.equals(t, ignoreCase = true) }) tags.add(t)
        }
        newTag = ""
    }

    CtrlAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.archive_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CtrlTextField(
                    notes, { notes = it },
                    label = { Text(stringResource(R.string.archive_notes_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                )
                Text(stringResource(R.string.archive_tags), style = MaterialTheme.typography.labelLarge)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.toList().forEach { t ->
                            InputChip(
                                selected = false,
                                onClick = { tags.remove(t) },
                                label = { Text(t) },
                                trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.archive_tag_remove, t), Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CtrlTextField(
                        newTag, { newTag = it },
                        label = { Text(stringResource(R.string.archive_tag_add)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTag() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { addTag() }, enabled = newTag.isNotBlank()) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.archive_tag_add))
                    }
                }
                val suggestions = known.filter { k -> tags.none { it.equals(k, ignoreCase = true) } }.take(12)
                if (suggestions.isNotEmpty()) {
                    Text(stringResource(R.string.archive_tags_suggested), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s -> AssistChip(onClick = { tags.add(s) }, label = { Text(s) }) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (newTag.isNotBlank()) addTag()
                    saving = true
                    error = null
                    scope.launch {
                        runCatching { api.updateArchive(archive.id, notes, tags.toList()) }
                            .onSuccess { onSaved(it) }
                            .onFailure { error = it.actionMessage(context, R.string.perm_manage_archives) }
                        saving = false
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Asks before deleting a print, saying what goes with it. A print that's printing from the queue right
 * now can't be deleted (Bambuddy refuses), so the dialog says so instead of offering the button.
 */
@Composable
fun DeleteArchiveDialog(name: String, impact: ArchiveDeleteImpact, busy: Boolean, onDismiss: () -> Unit, onDelete: (purgeStats: Boolean) -> Unit) {
    var purge by remember { mutableStateOf(false) }
    val blocked = impact.currentlyPrinting > 0
    CtrlAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.archive_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (blocked) {
                    Text(stringResource(R.string.archive_delete_printing), color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.archive_delete_body, name))
                    if (impact.relatedQueueItems > 0) {
                        Text(pluralStringResource(R.plurals.archive_delete_queue, impact.relatedQueueItems, impact.relatedQueueItems))
                    }
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { purge = !purge }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(purge, { purge = it }, enabled = !busy)
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(stringResource(R.string.archive_delete_stats), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.archive_delete_stats_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!blocked) {
                TextButton(onClick = { onDelete(purge) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.archive_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(if (blocked) R.string.close else R.string.cancel)) } },
    )
}
