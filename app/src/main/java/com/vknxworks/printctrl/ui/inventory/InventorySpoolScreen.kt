package com.vknxworks.printctrl.ui.inventory

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.AmsTray
import com.vknxworks.printctrl.data.bambuddy.BambuddyInventory
import com.vknxworks.printctrl.data.bambuddy.InventoryAssignment
import com.vknxworks.printctrl.data.bambuddy.InventorySpool
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.bambuddy.InventoryChanges
import com.vknxworks.printctrl.data.bambuddy.inventory
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.RoundAction
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.printers.TrayTile
import com.vknxworks.printctrl.ui.spools.BigSwatch
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * One spool from Bambuddy's inventory: what's left, which slot it's in, and the deliberate changes
 * PrintCtrl can make. Bambuddy counts what prints use; nothing here deducts filament by itself.
 */
@Composable
fun InventorySpoolScreen(id: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val api = client
    val loader = rememberLoader("inv-spool:$id:${api?.conn?.key}") {
        val inv = (api ?: error("Bambuddy isn't connected")).inventory(fresh = true)
        Triple(inv, inv.spool(id), runCatching { inv.slotOf(id) }.getOrNull())
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(loader.value?.second?.let { it.displayName.ifEmpty { stringResource(R.string.spool_number, it.id) } } ?: stringResource(R.string.spool)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = loader.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.error.inventoryMessage(context, write = false)) { loader.reload() }
                is Load.Ok -> {
                    val (inv, spool, slot) = s.value
                    InventorySpoolDetail(inv, spool, slot, onChanged = { loader.reload(silent = true) })
                }
            }
        }
    }
}

@Composable
private fun InventorySpoolDetail(inv: BambuddyInventory, spool: InventorySpool, slot: InventoryAssignment?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var weigh by remember { mutableStateOf(false) }
    var setLeft by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var assign by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmTakeOut by remember { mutableStateOf(false) }

    fun run(after: () -> Unit = onChanged, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess {
                    InventoryChanges.changed()
                    after()
                }
                .onFailure { Toast.makeText(context, it.inventoryMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    // Weighing while the spool's printer prints would count that print twice: Bambuddy adds the whole
    // print's usage when it finishes, on top of a weight that already includes part of it.
    val printers by c.printers.state.collectAsStateWithLifecycle()
    val slotPhase = slot?.let { a -> printers.printers.firstOrNull { it.id == a.printerId }?.status?.phase }
    val printing = slotPhase?.isActive == true
    val phaseUnknown = slot != null && (slotPhase == null || slotPhase == PrintPhase.OFFLINE || slotPhase == PrintPhase.UNKNOWN)
    var blocked by remember { mutableStateOf(false) }

    val slotLabel = slot?.let { a -> if (a.amsId == 255 || a.amsId == 254) stringResource(R.string.external_spool) else com.vknxworks.printctrl.ui.components.amsSlotName(a.amsId, a.trayId) }
    val printerName = slot?.let { a -> a.printerName ?: c.printers.state.value.printers.firstOrNull { it.id == a.printerId }?.name }.orEmpty()

    Column(Modifier.readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(horizontal = Gutter, vertical = 12.dp)) {
            BigSwatch(spool.colors.mapNotNull { parseHexColor(it) })
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val left = spool.remaining
                Text(
                    stringResource(R.string.grams_left, left.roundToInt()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (left < settings.lowStockGrams) Warning else MaterialTheme.colorScheme.onSurface,
                )
                spool.fractionLeft?.let { fr -> LinearProgressIndicator(progress = { fr }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {}) }
                MetaRow(
                    listOf(
                        stringResource(R.string.spool_of_grams, spool.labelWeight),
                        stringResource(R.string.grams_used, spool.weightUsed.roundToInt()),
                    ),
                )
                if (spool.archived) Text(stringResource(R.string.spool_archived), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (slotLabel != null) Text(stringResource(R.string.inv_in_slot, slotLabel, printerName), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        FlowRow(Modifier.padding(horizontal = Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundAction(Icons.Outlined.Scale, stringResource(R.string.spool_weigh), onClick = { if (printing) blocked = true else weigh = true })
            RoundAction(Icons.Outlined.Tune, stringResource(R.string.spool_adjust), onClick = { if (printing) blocked = true else setLeft = true })
            RoundAction(Icons.Outlined.Edit, stringResource(R.string.edit), onClick = { edit = true })
            if (!spool.archived) {
                if (slot == null) RoundAction(Icons.Outlined.TableRows, stringResource(R.string.spool_assign), onClick = { assign = true })
                else RoundAction(Icons.Outlined.TableRows, stringResource(R.string.inv_take_out), onClick = { confirmTakeOut = true })
            }
            RoundAction(
                if (spool.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                stringResource(if (spool.archived) R.string.spool_unarchive else R.string.spool_archive),
                onClick = { confirmArchive = true },
            )
            RoundAction(Icons.Outlined.Delete, stringResource(R.string.inv_delete), onClick = { confirmDelete = true })
        }
        if (spool.weightLocked) {
            Text(
                stringResource(R.string.inv_weight_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )
        }

        Group(stringResource(R.string.filament)) {
            spool.brand?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_brand), it) }
            GroupRow(stringResource(R.string.material), spool.material.ifBlank { "–" })
            spool.subtype?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_subtype), it) }
            spool.colorName?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_color_name), it) }
            if (spool.nozzleTempMin != null || spool.nozzleTempMax != null) {
                GroupRow(stringResource(R.string.spool_temps), listOfNotNull(spool.nozzleTempMin, spool.nozzleTempMax).distinct().joinToString("–") { "$it°" })
            }
            spool.slicerFilamentName?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_slicer_preset), it) }
        }
        Group(stringResource(R.string.inv_details)) {
            GroupRow(stringResource(R.string.spool_id_label), stringResource(R.string.spool_id, spool.id))
            GroupRow(stringResource(R.string.location), spool.storageLocation?.takeIf { it.isNotBlank() } ?: stringResource(R.string.none))
            GroupRow(stringResource(R.string.empty_spool_weight), stringResource(R.string.grams_value, spool.coreWeight))
            spool.costPerKg?.let { GroupRow(stringResource(R.string.inv_cost_per_kg), "%.2f".format(it)) }
            spool.category?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_category), it) }
            spool.lastWeighedAt?.let { day(it) }?.let { GroupRow(stringResource(R.string.inv_last_weighed), it) }
            (spool.tagUid ?: spool.trayUuid)?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_tag), it) }
            spool.note?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.inv_note), it) }
        }
    }

    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            title = { Text(stringResource(R.string.inv_weigh_printing_title)) },
            text = { Text(stringResource(R.string.inv_weigh_printing_body, printerName)) },
            confirmButton = { TextButton(onClick = { blocked = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (weigh) InventoryWeighDialog(spool, inv, phaseUnknown, onDismiss = { weigh = false }) { gross ->
        weigh = false
        run { inv.weighIn(spool, gross) }
    }
    if (setLeft) SetLeftDialog(spool, viaSpoolman = inv.viaSpoolman, phaseUnknown = phaseUnknown, onDismiss = { setLeft = false }) { grams ->
        setLeft = false
        run { inv.setRemaining(spool, grams) }
    }
    if (edit) EditInventorySpoolDialog(spool, viaSpoolman = inv.viaSpoolman, onDismiss = { edit = false }) { body ->
        edit = false
        run { inv.update(spool.id, body) }
    }
    if (assign) InventoryAssignSheet(onDismiss = { assign = false }) { printerId, amsId, trayId ->
        assign = false
        run(after = {
            Toast.makeText(context, R.string.assign_done, Toast.LENGTH_SHORT).show()
            onChanged()
            c.printers.refresh()
        }) { inv.assign(spool.id, printerId, amsId, trayId) }
    }
    if (confirmTakeOut && slot != null) {
        ConfirmDialog(
            stringResource(R.string.inv_take_out_title, slotLabel.orEmpty()),
            stringResource(R.string.inv_take_out_body),
            stringResource(R.string.assign_remove),
            destructive = false,
            onDismiss = { confirmTakeOut = false },
        ) {
            confirmTakeOut = false
            run { inv.unassign(spool.id, slot.printerId, slot.amsId, slot.trayId) }
        }
    }
    if (confirmArchive) {
        ConfirmDialog(
            stringResource(if (spool.archived) R.string.spool_unarchive_title else R.string.spool_archive_title),
            stringResource(if (spool.archived) R.string.spool_unarchive_body else R.string.spool_archive_body),
            stringResource(if (spool.archived) R.string.spool_unarchive else R.string.spool_archive),
            destructive = false,
            onDismiss = { confirmArchive = false },
        ) {
            confirmArchive = false
            run { if (spool.archived) inv.restore(spool.id) else inv.archive(spool.id) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.inv_delete_title),
            stringResource(R.string.inv_delete_body),
            stringResource(R.string.inv_delete),
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            run(after = { nav.back() }) { inv.delete(spool.id) }
        }
    }
}

/** Weigh-in: spool and filament together on a scale. Shows the result before anything is sent. */
@Composable
private fun InventoryWeighDialog(spool: InventorySpool, inv: BambuddyInventory, phaseUnknown: Boolean, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val gross = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weigh_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.weigh_body))
                OutlinedTextField(
                    text, { text = it },
                    label = { Text(stringResource(R.string.weigh_field)) },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(stringResource(R.string.weigh_tare, spool.coreWeight), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (gross != null) {
                    Text(stringResource(R.string.weigh_result, inv.leftAfterWeighing(spool, gross).roundToInt()), style = MaterialTheme.typography.titleSmall)
                }
                if (phaseUnknown) Text(stringResource(R.string.inv_weigh_unknown_printer), style = MaterialTheme.typography.bodySmall, color = Warning)
            }
        },
        confirmButton = { TextButton(onClick = { gross?.let(onConfirm) }, enabled = gross != null && gross > 0) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A correction: set what's left directly (filament used outside Bambuddy, a wrong starting amount). */
@Composable
private fun SetLeftDialog(spool: InventorySpool, viaSpoolman: Boolean, phaseUnknown: Boolean, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(spool.remaining.roundToInt().toString()) }
    val grams = text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it <= spool.labelWeight }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inv_set_left_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.inv_set_left_body))
                OutlinedTextField(
                    text, { text = it },
                    label = { Text(stringResource(R.string.inv_set_left_field)) },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                // Only Bambuddy's own inventory locks a typed-in weight against AMS updates; Spoolman has no such lock.
                if (!viaSpoolman) Text(stringResource(R.string.inv_weigh_locks), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (phaseUnknown) Text(stringResource(R.string.inv_weigh_unknown_printer), style = MaterialTheme.typography.bodySmall, color = Warning)
            }
        },
        confirmButton = {
            TextButton(onClick = { grams?.let(onConfirm) }, enabled = grams != null) {
                Text(stringResource(R.string.inv_set_left_confirm, (grams ?: 0.0).roundToInt()))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EditInventorySpoolDialog(spool: InventorySpool, viaSpoolman: Boolean, onDismiss: () -> Unit, onConfirm: (kotlinx.serialization.json.JsonObject) -> Unit) {
    var brand by remember { mutableStateOf(spool.brand.orEmpty()) }
    var material by remember { mutableStateOf(spool.material) }
    var subtype by remember { mutableStateOf(spool.subtype.orEmpty()) }
    var colorName by remember { mutableStateOf(spool.colorName.orEmpty()) }
    var location by remember { mutableStateOf(spool.storageLocation.orEmpty()) }
    var cost by remember { mutableStateOf(spool.costPerKg?.let { "%.2f".format(it) }.orEmpty()) }
    var note by remember { mutableStateOf(spool.note.orEmpty()) }
    val costValue = cost.replace(',', '.').toDoubleOrNull()
    val ok = material.isNotBlank() && (cost.isBlank() || (costValue != null && costValue >= 0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.inv_brand)) }, singleLine = true)
                OutlinedTextField(material, { material = it }, label = { Text(stringResource(R.string.material)) }, singleLine = true, isError = material.isBlank())
                OutlinedTextField(subtype, { subtype = it }, label = { Text(stringResource(R.string.inv_subtype)) }, singleLine = true)
                OutlinedTextField(colorName, { colorName = it }, label = { Text(stringResource(R.string.inv_color_name)) }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.location)) }, singleLine = true)
                OutlinedTextField(
                    cost, { cost = it },
                    label = { Text(stringResource(R.string.inv_cost_per_kg)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.inv_note)) })
                if (viaSpoolman && spool.costPerKg != null) {
                    Text(stringResource(R.string.inv_cost_keep_spoolman), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onConfirm(
                    buildJsonObject {
                        // Bambuddy's own inventory clears a field with null. Through Spoolman, null means "keep",
                        // so an empty text clears it there instead; a price can't be removed that way.
                        fun text(key: String, value: String) {
                            when {
                                value.isNotBlank() -> put(key, value.trim())
                                viaSpoolman -> put(key, "")
                                else -> put(key, JsonNull)
                            }
                        }
                        put("material", material.trim().take(50))
                        text("brand", brand)
                        text("subtype", subtype)
                        text("color_name", colorName)
                        text("storage_location", location.trim().take(255))
                        if (costValue != null) put("cost_per_kg", costValue) else if (!viaSpoolman) put("cost_per_kg", JsonNull)
                        text("note", note.trim().take(1000))
                    },
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class InvSlot(val printerId: Int, val printerName: String, val amsId: Int, val trayId: Int, val label: String, val tray: AmsTray)

/** Pick an AMS slot (or the external holder) on any printer, then confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryAssignSheet(onDismiss: () -> Unit, onPick: (printerId: Int, amsId: Int, trayId: Int) -> Unit) {
    val context = LocalContext.current
    val state by context.container.printers.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<InvSlot?>(null) }
    val slots = state.printers.flatMap { p ->
        val s = p.status ?: return@flatMap emptyList()
        s.ams.orEmpty().flatMap { unit ->
            unit.tray.filter { !unit.isAmsHt || it.id == 0 }.map { t ->
                InvSlot(p.id, p.name, unit.id, t.id, com.vknxworks.printctrl.ui.components.amsSlotName(unit.id, t.id), t)
            }
        } + listOfNotNull(s.vtTray.firstOrNull()?.let { InvSlot(p.id, p.name, 255, 0, context.getString(R.string.external_spool), it) })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.assign_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(
                stringResource(R.string.assign_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (slots.isEmpty()) Box(Modifier.height(160.dp)) { LoadingView() }
            slots.groupBy { it.printerId }.forEach { (_, list) ->
                Group(list.first().printerName) {
                    list.chunked(5).forEach { row ->
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { slot ->
                                TrayTile(slot.tray, slot.label, loaded = false, modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { confirm = slot })
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
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
            onPick(slot.printerId, slot.amsId, slot.trayId)
        }
    }
}

private fun day(iso: String): String? = com.vknxworks.printctrl.data.bambuddy.PrintersRepository.parseInstant(iso)?.let {
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(it))
}
