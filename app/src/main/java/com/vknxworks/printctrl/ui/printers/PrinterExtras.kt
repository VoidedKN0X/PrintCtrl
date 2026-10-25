package com.vknxworks.printctrl.ui.printers

import androidx.compose.foundation.border
import com.vknxworks.printctrl.ui.theme.CardBorder
import com.vknxworks.printctrl.ui.theme.CtrlShapes
import com.vknxworks.printctrl.ui.components.CtrlSwitch
import com.vknxworks.printctrl.ui.components.CtrlAlertDialog
import com.vknxworks.printctrl.ui.components.CtrlTextField
import com.vknxworks.printctrl.ui.components.CtrlProgressBar
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.MaintenanceItem
import com.vknxworks.printctrl.data.bambuddy.PrintObject
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------- Maintenance ----------

/** What's due on this printer, from Bambuddy's maintenance tracking, with "done" for each item. */
@Composable
fun MaintenanceGroup(api: BambuddyClient, printerId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("maintenance:${api.conn.key}:$printerId") { runCatching { api.maintenance(printerId) }.getOrNull() }
    var confirm by remember { mutableStateOf<MaintenanceItem?>(null) }
    val overview = loader.value ?: return
    val items = overview.maintenanceItems.filter { it.enabled }.sortedByDescending { it.progress }
    if (items.isEmpty()) return
    Group(stringResource(R.string.maintenance), footer = stringResource(R.string.maintenance_footer, overview.totalPrintHours.roundToInt())) {
        items.forEach { m ->
            val color = when {
                m.isDue -> MaterialTheme.colorScheme.error
                m.isWarning -> Warning
                else -> MaterialTheme.colorScheme.primary
            }
            val left = if (m.byDays) m.daysUntilDue?.let { stringResource(R.string.maintenance_days_left, it.roundToInt()) }
            else stringResource(R.string.maintenance_hours_left, m.hoursUntilDue.roundToInt())
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.maintenanceTypeName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (m.isDue) stringResource(R.string.maintenance_due) else left.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (m.isDue || m.isWarning) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { confirm = m }) { Text(stringResource(R.string.maintenance_done)) }
                }
                CtrlProgressBar(
                    progress = { m.progress.coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }
    }
    confirm?.let { m ->
        ConfirmDialog(
            stringResource(R.string.maintenance_confirm_title, m.maintenanceTypeName),
            stringResource(R.string.maintenance_confirm_body),
            stringResource(R.string.maintenance_done),
            destructive = false,
            onDismiss = { confirm = null },
        ) {
            confirm = null
            scope.launch {
                runCatching { api.performMaintenance(m.id) }
                    .onSuccess { loader.reload(silent = true) }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            }
        }
    }
}

// ---------- Smart plug ----------

/** The printer's smart plug from Bambuddy: its state and power use, and switching it (turning off asks first). */
@Composable
fun SmartPlugGroup(api: BambuddyClient, printerId: Int, printing: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plug = rememberLoader("plug:${api.conn.key}:$printerId") { runCatching { api.plugFor(printerId) }.getOrNull() }
    val p = plug.value ?: return
    var status by remember { mutableStateOf<com.vknxworks.printctrl.data.bambuddy.PlugStatus?>(null) }
    var confirmOff by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        while (true) {
            status = runCatching { api.plugStatus(p.id) }.getOrNull()
            delay(15_000)
        }
    }
    fun switch(on: Boolean) {
        busy = true
        scope.launch {
            runCatching { api.switchPlug(p.id, on) }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            delay(1_500)
            status = runCatching { api.plugStatus(p.id) }.getOrNull()
            busy = false
        }
    }
    val st = status
    Group(stringResource(R.string.smart_plug)) {
        GroupRow(
            p.name.ifBlank { stringResource(R.string.smart_plug) },
            when {
                st == null -> stringResource(R.string.checking)
                !st.reachable || st.state == null -> stringResource(R.string.plug_unreachable)
                else -> listOfNotNull(
                    stringResource(if (st.on) R.string.plug_on else R.string.plug_off),
                    st.energy?.power?.let { stringResource(R.string.plug_watts, it.roundToInt()) },
                    st.energy?.today?.let { stringResource(R.string.plug_today, "%.2f".format(it)) },
                ).joinToString("  ·  ")
            },
            if (st?.on == true) Icons.Outlined.Power else Icons.Outlined.PowerOff,
            trailing = {
                if (busy) CircularProgressIndicator(Modifier.heightIn(max = 24.dp), strokeWidth = 2.dp)
                else if (p.switchable && st?.state != null) {
                    CtrlSwitch(st.on, onCheckedChange = { on -> if (!on) confirmOff = true else switch(true) })
                }
            },
        )
    }
    if (confirmOff) {
        ConfirmDialog(
            stringResource(R.string.plug_off_title),
            stringResource(if (printing) R.string.plug_off_printing else R.string.plug_off_body),
            stringResource(R.string.plug_turn_off),
            onDismiss = { confirmOff = false },
        ) {
            confirmOff = false
            switch(false)
        }
    }
}

// ---------- Temperatures ----------

/** Sets a heater's target: nozzle up to 320 °C, bed up to 140 °C; 0 turns it off. */
@Composable
fun TargetDialog(title: String, current: Double?, max: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(current?.takeIf { it > 0 }?.roundToInt()?.toString().orEmpty()) }
    val value = text.toIntOrNull()
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(
                    text, { text = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.target)) },
                    suffix = { Text("°C") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(stringResource(R.string.target_help, max), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSet) }, enabled = value != null && value in 0..max) { Text(stringResource(R.string.set)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSet(0) }) { Text(stringResource(R.string.heater_off)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

// ---------- Motion ----------

/** Home and jog the axes, and extrude or retract, for when the printer is idle. */
@Composable
fun MotionGroup(printerId: Int, act: (suspend BambuddyClient.() -> Unit) -> Unit) {
    var step by remember { mutableStateOf(10.0) }
    var confirmHome by remember { mutableStateOf(false) }
    Group(stringResource(R.string.motion), footer = stringResource(R.string.motion_footer)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(1.0, 10.0, 50.0).forEachIndexed { i, mm ->
                    SegmentedButton(step == mm, onClick = { step = mm }, shape = SegmentedButtonDefaults.itemShape(i, 3, baseShape = CtrlShapes.Control)) { Text("${mm.roundToInt()} mm") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogXY(printerId, 0.0, step) } }) { Text("Y+") }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogXY(printerId, 0.0, -step) } }) { Text("Y−") }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogXY(printerId, -step, 0.0) } }) { Text("X−") }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogXY(printerId, step, 0.0) } }) { Text("X+") }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogBed(printerId, -step) } }) { Text(stringResource(R.string.bed_up)) }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogBed(printerId, step) } }) { Text(stringResource(R.string.bed_down)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { confirmHome = true }) { Text(stringResource(R.string.home_axes)) }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogExtruder(printerId, step.coerceAtMost(50.0)) } }) { Text(stringResource(R.string.extrude)) }
                FilledTonalButton(shape = CtrlShapes.Control, onClick = { act { jogExtruder(printerId, -step.coerceAtMost(50.0)) } }) { Text(stringResource(R.string.retract)) }
            }
        }
    }
    if (confirmHome) {
        ConfirmDialog(
            stringResource(R.string.home_title),
            stringResource(R.string.home_body),
            stringResource(R.string.home_axes),
            destructive = false,
            onDismiss = { confirmHome = false },
        ) {
            confirmHome = false
            act { homeAxes(printerId) }
        }
    }
}

// ---------- AMS slot actions ----------

/**
 * What can be done with one AMS slot: load or unload its filament, read its RFID tag again, and see or
 * change which Spoolman spool is in it.
 */
@Composable
fun SlotSheet(
    printerId: Int,
    amsId: Int,
    slot: Int,
    title: String,
    linkedSpool: String?,
    canLoad: Boolean,
    isLoaded: Boolean,
    onAssign: (() -> Unit)?,
    onDismiss: () -> Unit,
    act: (suspend BambuddyClient.() -> Unit) -> Unit,
    onTakeOut: (() -> Unit)? = null,
) {
    var confirmLoad by remember { mutableStateOf(false) }
    var confirmUnload by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = CtrlShapes.SheetTop, modifier = Modifier.border(CardBorder, CtrlShapes.SheetTop)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            linkedSpool?.let { GroupRow(stringResource(R.string.slot_spool), it, Icons.Outlined.Inventory2) }
            if (onAssign != null) {
                GroupRow(stringResource(if (linkedSpool == null) R.string.slot_assign else R.string.slot_change), icon = Icons.Outlined.Inventory2, showChevron = true, onClick = {
                    onDismiss()
                    onAssign()
                })
            }
            if (linkedSpool != null && onTakeOut != null) {
                GroupRow(stringResource(R.string.slot_take_out), stringResource(R.string.slot_take_out_sub), Icons.Outlined.Upload, onClick = {
                    onDismiss()
                    onTakeOut()
                })
            }
            if (canLoad && !isLoaded) {
                GroupRow(stringResource(R.string.slot_load), stringResource(R.string.slot_load_sub), Icons.Outlined.Download, onClick = { confirmLoad = true })
            }
            if (isLoaded) {
                GroupRow(stringResource(R.string.slot_unload), stringResource(R.string.slot_unload_sub), Icons.Outlined.Upload, onClick = { confirmUnload = true })
            }
            if (amsId < 128 && amsId != 255) {
                GroupRow(stringResource(R.string.slot_reread), stringResource(R.string.slot_reread_sub), Icons.Outlined.Nfc, onClick = {
                    onDismiss()
                    act { refreshSlot(printerId, amsId, slot) }
                })
            }
        }
    }
    if (confirmLoad) {
        ConfirmDialog(
            stringResource(R.string.slot_load_title, title),
            stringResource(R.string.slot_load_body),
            stringResource(R.string.slot_load),
            destructive = false,
            onDismiss = { confirmLoad = false },
        ) {
            confirmLoad = false
            onDismiss()
            val tray = if (amsId == 255) 254 else amsId * 4 + slot
            act { loadFilament(printerId, tray) }
        }
    }
    if (confirmUnload) {
        ConfirmDialog(
            stringResource(R.string.slot_unload_title),
            stringResource(R.string.slot_unload_body),
            stringResource(R.string.slot_unload),
            destructive = false,
            onDismiss = { confirmUnload = false },
        ) {
            confirmUnload = false
            onDismiss()
            act { unloadFilament(printerId) }
        }
    }
}

// ---------- Picking a spool for a slot ----------

/**
 * Picks a Spoolman spool for an AMS slot: spools of the slot's material come first, then the rest.
 * Archived spools are left out.
 */
@Composable
fun SpoolPickerSheet(
    spoolman: com.vknxworks.printctrl.data.spoolman.SpoolmanClient,
    slotTitle: String,
    material: String?,
    onDismiss: () -> Unit,
    onPick: (com.vknxworks.printctrl.data.spoolman.Spool) -> Unit,
) {
    val context = LocalContext.current
    val loader = rememberLoader("pick-spools:${spoolman.conn.key}") { spoolman.spools(0, 500, "filament.material:asc,filament.name:asc").first }
    var filter by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = CtrlShapes.SheetTop, modifier = Modifier.border(CardBorder, CtrlShapes.SheetTop)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.pick_spool_title, slotTitle), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            CtrlTextField(
                filter, { filter = it },
                placeholder = { Text(stringResource(R.string.spools_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            when (val st = loader.state) {
                is com.vknxworks.printctrl.ui.components.Load.Loading -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is com.vknxworks.printctrl.ui.components.Load.Err -> Text(st.error.friendlyMessage(context), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                is com.vknxworks.printctrl.ui.components.Load.Ok -> {
                    val wanted = material?.trim()?.lowercase()
                    val list = st.value
                        .filter { s -> filter.isBlank() || "${s.filament.displayName} ${s.filament.material} ${s.location}".contains(filter.trim(), ignoreCase = true) }
                        .sortedBy { s -> if (wanted != null && s.filament.material?.lowercase() == wanted) 0 else 1 }
                    if (list.isEmpty()) Text(stringResource(R.string.spools_no_match), modifier = Modifier.padding(24.dp))
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(list.size, key = { list[it].id }) { i ->
                            val spool = list[i]
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(spool) }.padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.vknxworks.printctrl.ui.spools.Swatch(spool.colors.mapNotNull { com.vknxworks.printctrl.ui.components.parseHexColor(it) })
                                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(spool.filament.displayName.ifEmpty { stringResource(R.string.spool_number, spool.id) }, style = MaterialTheme.typography.bodyLarge)
                                    com.vknxworks.printctrl.ui.components.MetaRow(
                                        listOfNotNull(
                                            spool.filament.material,
                                            spool.remainingWeight?.let { stringResource(R.string.grams_left, it.roundToInt()) },
                                            spool.location?.takeIf { it.isNotBlank() },
                                            stringResource(R.string.spool_id, spool.id),
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
}

// ---------- Skip objects ----------

/** Lists the objects on the plate; ticked ones are skipped for the rest of the print, after a confirmation. */
@Composable
fun SkipObjectsDialog(api: BambuddyClient, printerId: Int, onDismiss: () -> Unit, act: (suspend BambuddyClient.() -> Unit) -> Unit) {
    val context = LocalContext.current
    val loader = rememberLoader("objects:${api.conn.key}:$printerId") { api.printObjects(printerId) }
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    var confirm by remember { mutableStateOf(false) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skip_title)) },
        text = {
            val objects: List<PrintObject> = loader.value?.objects.orEmpty()
            when {
                loader.value == null && loader.state !is com.vknxworks.printctrl.ui.components.Load.Err -> CircularProgressIndicator()
                loader.state is com.vknxworks.printctrl.ui.components.Load.Err -> Text((loader.state as com.vknxworks.printctrl.ui.components.Load.Err).error.friendlyMessage(context))
                objects.size < 2 -> Text(stringResource(R.string.skip_none))
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    objects.forEach { o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = o.skipped || o.id in chosen,
                                enabled = !o.skipped,
                                onCheckedChange = { on -> chosen = if (on) chosen + o.id else chosen - o.id },
                            )
                            Text(
                                o.name.ifBlank { "#${o.id}" } + if (o.skipped) "  (${context.getString(R.string.skipped)})" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm = true }, enabled = chosen.isNotEmpty()) {
                Text(stringResource(R.string.skip_confirm, chosen.size), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirm) {
        ConfirmDialog(
            stringResource(R.string.skip_sure_title),
            stringResource(R.string.skip_sure_body),
            stringResource(R.string.skip_confirm, chosen.size),
            onDismiss = { confirm = false },
        ) {
            confirm = false
            val ids = chosen.toList()
            onDismiss()
            act { skipObjects(printerId, ids) }
        }
    }
}

/** Obico's verdict on the running print, when Bambuddy's AI failure detection watches this printer. */
@Composable
fun AiDetectionRow(api: BambuddyClient, printerId: Int) {
    var verdict by remember { mutableStateOf<com.vknxworks.printctrl.data.bambuddy.ObicoPrinter?>(null) }
    LaunchedEffect(printerId, api) {
        while (true) {
            verdict = runCatching { api.obico().takeIf { it.enabled }?.perPrinter?.get(printerId.toString()) }.getOrNull()
            delay(30_000)
        }
    }
    val v = verdict ?: return
    val (label, color) = when (v.`class`) {
        "safe" -> R.string.ai_safe to MaterialTheme.colorScheme.tertiary
        "warning" -> R.string.ai_warning to Warning
        "failure" -> R.string.ai_failure to MaterialTheme.colorScheme.error
        else -> R.string.ai_unknown to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Group {
        GroupRow(stringResource(R.string.ai_title), stringResource(label), Icons.Outlined.Build, iconTint = color)
    }
}
