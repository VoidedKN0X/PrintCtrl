package com.vknxworks.printctrl.ui.spools

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.AmsTray
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.printers.TrayTile
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** A slot a spool can go in: [amsId] 255 is the external spool holder. */
private data class Slot(val printerId: Int, val printerName: String, val amsId: Int, val trayId: Int, val label: String, val tray: AmsTray)

/**
 * Puts a Spoolman spool in an AMS slot (or the external holder) through Bambuddy, which also tells the
 * printer what filament is in the slot. Bambuddy then tracks that spool's use while printing.
 */
@Composable
fun AssignSlotScreen(spoolId: Int) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val state by c.printers.state.collectAsStateWithLifecycle()
    val api = client
    val current = rememberLoader("assignments:${api?.conn?.key}") { api?.slotAssignments().orEmpty() }
    var confirm by remember { mutableStateOf<Slot?>(null) }
    var busy by remember { mutableStateOf(false) }

    val slots = state.printers.flatMap { p ->
        val s = p.status ?: return@flatMap emptyList()
        s.ams.orEmpty().flatMap { unit ->
            unit.tray.filter { !unit.isAmsHt || it.id == 0 }.map { t ->
                Slot(p.id, p.name, unit.id, t.id, if (unit.isAmsHt) "HT" else "${'A' + (unit.id % 26)}${t.id + 1}", t)
            }
        } + listOfNotNull(s.vtTray.firstOrNull()?.let { Slot(p.id, p.name, 255, 0, context.getString(R.string.external_spool), it) })
    }
    val mine = current.value?.firstOrNull { it.spoolmanSpoolId == spoolId }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.assign_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.assign_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )
            if (mine != null) {
                Row(Modifier.padding(horizontal = Gutter)) {
                    Text(
                        stringResource(R.string.assign_current, mine.printerName.orEmpty(), if (mine.amsId == 255) context.getString(R.string.external_spool) else com.vknxworks.printctrl.ui.components.amsSlotName(mine.amsId, mine.trayId)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                    TextButton(onClick = {
                        val a = api ?: return@TextButton
                        scope.launch {
                            runCatching { a.unassignSlot(spoolId) }
                                .onSuccess {
                                    c.telemetry.spoolAction("unassign_slot")
                                    current.reload(silent = true)
                                }
                                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                        }
                    }) { Text(stringResource(R.string.assign_remove)) }
                }
            }
            if (state.printers.isEmpty() || busy) Box(Modifier.height(200.dp)) { LoadingView() }
            slots.groupBy { it.printerId }.forEach { (_, list) ->
                Group(list.first().printerName) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.take(5).forEach { slot ->
                            TrayTile(
                                slot.tray,
                                slot.label,
                                loaded = mine?.printerId == slot.printerId && mine.amsId == slot.amsId && mine.trayId == slot.trayId,
                                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { confirm = slot },
                            )
                        }
                        repeat((5 - list.size.coerceAtMost(5)).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                    if (list.size > 5) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            list.drop(5).take(5).forEach { slot ->
                                TrayTile(slot.tray, slot.label, loaded = false, modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { confirm = slot })
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { slot ->
        ConfirmDialog(
            stringResource(R.string.assign_confirm_title, slot.label),
            stringResource(R.string.assign_confirm_body, slot.printerName),
            stringResource(R.string.assign_confirm),
            destructive = false,
            onDismiss = { confirm = null },
        ) {
            confirm = null
            val a = api ?: return@ConfirmDialog
            busy = true
            scope.launch {
                runCatching { a.assignSlot(spoolId, slot.printerId, slot.amsId, slot.trayId) }
                    .onSuccess {
                        c.telemetry.spoolAction("assign_slot")
                        Toast.makeText(context, R.string.assign_done, Toast.LENGTH_SHORT).show()
                        nav.back()
                    }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                busy = false
            }
        }
    }
}
