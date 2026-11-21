package com.vknxworks.printctrl.ui.queue

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.ArchiveChanges
import com.vknxworks.printctrl.data.bambuddy.PendingUpload
import com.vknxworks.printctrl.data.bambuddy.approveUpload
import com.vknxworks.printctrl.data.bambuddy.discardUpload
import com.vknxworks.printctrl.data.bambuddy.pendingUploads
import com.vknxworks.printctrl.data.net.UserMessageException
import com.vknxworks.printctrl.ui.archive.actionMessage
import com.vknxworks.printctrl.ui.archive.formatDay
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.readableWidth
import com.vknxworks.printctrl.ui.components.rememberLoader
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bambuddy's review queue: print files that arrived through its virtual printer in "review" mode and
 * wait for someone to approve them (they move into the archive) or discard them (the file is deleted).
 * One at a time, and discarding asks first.
 */
@Composable
fun ReviewScreen() {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val api = client
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<Int?>(null) }
    var discarding by remember { mutableStateOf<PendingUpload?>(null) }
    val loader = rememberLoader("review:${api?.conn?.key}") {
        (api ?: throw UserMessageException(R.string.err_bambuddy_not_connected)).pendingUploads()
    }

    fun run(upload: PendingUpload, permission: Int, done: Int, block: suspend () -> Unit) {
        if (busy != null) return
        busy = upload.id
        scope.launch {
            try {
                block()
                Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                loader.reload(silent = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toast.makeText(context, e.actionMessage(context, permission), Toast.LENGTH_LONG).show()
            } finally {
                busy = null
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(stringResource(R.string.review_title)) {
                IconButton(onClick = { loader.reload() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.review_refresh)) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = loader.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
                is Load.Ok -> if (s.value.isEmpty()) {
                    MessageView(Icons.Outlined.Inbox, stringResource(R.string.review_empty), stringResource(R.string.review_empty_body))
                } else if (api != null) {
                    Column(Modifier.readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        s.value.forEach { u ->
                            Group(footer = if (u === s.value.last()) stringResource(R.string.review_footer) else null) {
                                GroupRow(
                                    u.name,
                                    listOfNotNull(
                                        u.uploadedAt?.let { formatDay(it) },
                                        Formatter.formatShortFileSize(context, u.fileSize),
                                        u.sourceIp?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.review_from, it) },
                                        u.filename.takeIf { it != u.name },
                                    ).joinToString("  ·  "),
                                )
                                Row(Modifier.padding(horizontal = 8.dp)) {
                                    TextButton(
                                        enabled = busy == null,
                                        onClick = {
                                            run(u, R.string.perm_queue_add, R.string.review_approved) {
                                                api.approveUpload(u.id)
                                                ArchiveChanges.changed()
                                            }
                                        },
                                    ) { Text(stringResource(R.string.review_approve)) }
                                    TextButton(enabled = busy == null, onClick = { discarding = u }) {
                                        Text(stringResource(R.string.review_discard), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    discarding?.let { u ->
        ConfirmDialog(
            stringResource(R.string.review_discard_title),
            stringResource(R.string.review_discard_body, u.name),
            stringResource(R.string.review_discard),
            onDismiss = { discarding = null },
        ) {
            discarding = null
            val a = api ?: return@ConfirmDialog
            run(u, R.string.perm_queue_delete, R.string.review_discarded) { a.discardUpload(u.id) }
        }
    }
}
