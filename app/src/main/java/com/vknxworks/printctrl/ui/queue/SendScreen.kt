package com.vknxworks.printctrl.ui.queue

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.AmsTray
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.FilamentRequirement
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import com.vknxworks.printctrl.data.bambuddy.SlicerPreset
import com.vknxworks.printctrl.ui.components.ChoiceSheet
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupChoice
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.GroupSwitch
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.PillButton
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.contentOn
import com.vknxworks.printctrl.ui.components.formatClock
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.QueueRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** One AMS slot (or the external spool) as a choice for a filament in the file. */
private data class TrayChoice(val globalId: Int, val label: String, val tray: AmsTray)

private fun trays(p: LivePrinter?): List<TrayChoice> {
    val s = p?.status ?: return emptyList()
    val out = mutableListOf<TrayChoice>()
    s.ams.orEmpty().forEach { unit ->
        val letter = ('A' + (unit.id % 26))
        unit.tray.forEach { t ->
            if (!t.trayType.isNullOrBlank()) {
                val id = if (unit.isAmsHt || unit.id >= 128) unit.id else unit.id * 4 + t.id
                out += TrayChoice(id, if (unit.isAmsHt) "HT${unit.id - 127}" else "$letter${t.id + 1}", t)
            }
        }
    }
    s.vtTray.firstOrNull { !it.trayType.isNullOrBlank() }?.let { out += TrayChoice(254, "Ext", it) }
    return out
}

/** How close two colours are (0 = same). */
private fun distance(a: Color?, b: Color?): Float {
    if (a == null || b == null) return 3f
    return abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
}

/** Picks a slot for each filament the file needs: same material first, then the closest colour. */
private fun autoMap(reqs: List<FilamentRequirement>, choices: List<TrayChoice>): Map<Int, Int> =
    reqs.filter { it.usedInPlate }.associate { r ->
        val sameType = choices.filter { it.tray.trayType.equals(r.type, ignoreCase = true) }
        val pool = sameType.ifEmpty { choices }
        val best = pool.minByOrNull { distance(parseHexColor(it.tray.trayColor), parseHexColor(r.color)) }
        r.slotId to (best?.globalId ?: -1)
    }

/**
 * Sends a library file, or an earlier print from the archive ([archive]), to a printer through
 * Bambuddy's queue: pick the plate, the printer and which AMS slot feeds each filament. A model that
 * isn't sliced yet can be sliced on the server first, when Bambuddy has its slicer set up.
 */
@Composable
fun SendScreen(id: Int, archive: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(if (archive) R.string.reprint else R.string.send_title)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val api = client
            if (api == null) LoadingView() else SendForm(api, id, archive)
        }
    }
}

@Composable
private fun SendForm(api: BambuddyClient, startId: Int, archive: Boolean) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    var fileId by remember { mutableIntStateOf(startId) }
    val info = rememberLoader("send:${api.conn.key}:$archive:$fileId") {
        if (archive) {
            val a = api.archiveDetail(fileId)
            Triple(a.name, api.archiveThumbnailUrl(a), true)
        } else {
            val f = api.libraryFiles().firstOrNull { it.id == fileId } ?: error(context.getString(R.string.err_not_found))
            Triple(f.name, api.libraryThumbnailUrl(f), f.printable || !f.sliceable)
        }
    }
    when (val s = info.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { info.reload() }
        is Load.Ok -> {
            val (name, thumb, printable) = s.value
            if (!printable) {
                SliceForm(api, fileId, name, thumb) { newId -> fileId = newId }
            } else {
                PrintForm(api, fileId, archive, name, thumb) {
                    Toast.makeText(context, R.string.send_added, Toast.LENGTH_SHORT).show()
                    nav.nav.navigate(QueueRoute) {
                        popUpTo(nav.nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintForm(api: BambuddyClient, fileId: Int, archive: Boolean, name: String, thumb: String?, onSent: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val printersState by c.printers.state.collectAsStateWithLifecycle()
    val printers = printersState.printers
    val plates = rememberLoader("plates:${api.conn.key}:$archive:$fileId") { if (archive) api.archivePlates(fileId) else api.plates(fileId) }
    var plate by remember { mutableStateOf<Int?>(null) }
    var printerId by remember { mutableStateOf<Int?>(null) }
    var mapping by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var timelapse by remember { mutableStateOf(false) }
    var manualStart by remember { mutableStateOf(false) }
    var scheduleAt by remember { mutableStateOf<Long?>(null) }
    var scheduling by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableStateOf<FilamentRequirement?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(printers.size) {
        if (printerId == null) {
            printerId = (printers.firstOrNull { p -> p.status?.let { !it.phase.isActive && it.connected } == true } ?: printers.firstOrNull())?.id
        }
    }
    val plateList = plates.value?.plates.orEmpty()
    LaunchedEffect(plateList) { if (plate == null) plate = plateList.firstOrNull()?.index }
    val printer = printers.firstOrNull { it.id == printerId }
    val choices = trays(printer)
    val reqs = rememberLoader("reqs:${api.conn.key}:$archive:$fileId:$plate") {
        if (archive) api.archiveFilamentRequirements(fileId, plate).filaments else api.filamentRequirements(fileId, plate).filaments
    }
    val needed = reqs.value.orEmpty().filter { it.usedInPlate }
    // A new printer or plate gets a fresh automatic match.
    LaunchedEffect(printerId, reqs.value, choices.size) { mapping = autoMap(reqs.value.orEmpty(), choices) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(plate?.let { if (plateList.size > 1) api.plateThumbnailUrl(fileId, it, archive) else null } ?: thumb, Modifier.size(84.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                plateList.firstOrNull { it.index == plate }?.let { p ->
                    MetaRow(
                        listOfNotNull(
                            p.printTimeSeconds?.let { formatMinutes(context, (it / 60.0).roundToInt()) },
                            p.filamentUsedGrams?.let { stringResource(R.string.grams_value, it.roundToInt()) },
                        ),
                    )
                }
            }
        }

        if (plateList.size > 1) {
            Text(stringResource(R.string.send_plate), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(plateList, key = { it.index }) { p ->
                    Column(
                        Modifier.width(96.dp).clip(MaterialTheme.shapes.medium).clickable { plate = p.index }
                            .border(if (plate == p.index) 3.dp else 1.dp, if (plate == p.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Thumb(if (p.hasThumbnail) api.plateThumbnailUrl(fileId, p.index, archive) else null, Modifier.size(80.dp))
                        Text(p.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.plate_n, p.index), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }

        Group(stringResource(R.string.send_printer)) {
            GroupChoice(
                stringResource(R.string.printer),
                printers.map { it.id to it.name },
                printerId,
            ) { printerId = it }
        }

        if (needed.isNotEmpty()) {
            Group(stringResource(R.string.send_filament), footer = stringResource(R.string.send_filament_footer)) {
                needed.forEach { r ->
                    val chosen = choices.firstOrNull { it.globalId == mapping[r.slotId] }
                    Row(
                        Modifier.fillMaxWidth().clickable { pickSlot = r }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(parseHexColor(r.color))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${r.type}  ·  ${stringResource(R.string.grams_value, r.usedGrams.roundToInt())}", style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.send_filament_slot, r.slotId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (chosen == null) {
                            Text(stringResource(R.string.send_pick_slot), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        } else {
                            SlotBadge(chosen)
                        }
                    }
                }
            }
        }

        Group(stringResource(R.string.send_options)) {
            GroupSwitch(stringResource(R.string.send_timelapse), checked = timelapse) { timelapse = it }
            GroupSwitch(stringResource(R.string.send_manual_start), stringResource(R.string.send_manual_start_sub), manualStart) { manualStart = it }
            GroupRow(
                stringResource(R.string.queue_schedule),
                scheduleAt?.let { stringResource(R.string.queue_scheduled_for, formatClock(context, it)) } ?: stringResource(R.string.queue_asap),
                Icons.Outlined.Schedule,
                onClick = { scheduling = true },
            )
        }

        val missing = needed.any { (mapping[it.slotId] ?: -1) < 0 }
        PillButton(
            stringResource(R.string.send_add),
            enabled = !busy && printerId != null && !missing,
            modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp).fillMaxWidth(),
            onClick = {
                busy = true
                scope.launch {
                    val slots = (needed.maxOfOrNull { it.slotId } ?: 0)
                    val ams = (1..slots).map { slot -> mapping[slot] ?: -1 }
                    val body = buildJsonObject {
                        put(if (archive) "archive_id" else "library_file_id", fileId)
                        put("printer_id", printerId!!)
                        plate?.let { put("plate_id", it) }
                        if (ams.isNotEmpty()) put("ams_mapping", JsonArray(ams.map { JsonPrimitive(it) }))
                        put("timelapse", timelapse)
                        put("manual_start", manualStart)
                        scheduleAt?.let { put("scheduled_time", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))) }
                    }
                    runCatching { api.addToQueue(body.toString()) }
                        .onSuccess { onSent() }
                        .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                    busy = false
                }
            },
        )
    }

    pickSlot?.let { r ->
        ModalBottomSheet(onDismissRequest = { pickSlot = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.send_slot_for, r.type), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                if (choices.isEmpty()) Text(stringResource(R.string.send_no_slots), modifier = Modifier.padding(24.dp))
                choices.forEach { ch ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            mapping = mapping + (r.slotId to ch.globalId)
                            pickSlot = null
                        }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SlotBadge(ch)
                        Spacer(Modifier.width(12.dp))
                        Text(ch.tray.traySubBrands?.takeIf { it.isNotBlank() } ?: ch.tray.trayType.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    if (scheduling) {
        ScheduleDialog(onDismiss = { scheduling = false }) {
            scheduleAt = it
            scheduling = false
        }
    }
}

@Composable
private fun Dot(color: Color?) {
    Box(Modifier.size(22.dp).clip(CircleShape).background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun SlotBadge(ch: TrayChoice) {
    val color = parseHexColor(ch.tray.trayColor)
    Row(
        Modifier.clip(CircleShape).background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${ch.label}  ${ch.tray.trayType.orEmpty()}", style = MaterialTheme.typography.labelLarge, color = color?.let { contentOn(it) } ?: MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Slicing on the server with Bambuddy's OrcaSlicer or Bambu Studio sidecar. Only offered when Bambuddy
 * has server slicing turned on; the result is a new, printable file in the library.
 */
@Composable
private fun SliceForm(api: BambuddyClient, fileId: Int, name: String, thumb: String?, onSliced: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup = rememberLoader("slice-setup:${api.conn.key}") {
        val enabled = runCatching { api.settings().useSlicerApi }.getOrDefault(false)
        enabled to (if (enabled) runCatching { api.presets() }.getOrNull() else null)
    }
    var printerPreset by remember { mutableStateOf<SlicerPreset?>(null) }
    var processPreset by remember { mutableStateOf<SlicerPreset?>(null) }
    var filamentPreset by remember { mutableStateOf<SlicerPreset?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(thumb, Modifier.size(84.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.needs_slicing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        when (val s = setup.state) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Load.Err -> Text(s.error.friendlyMessage(context), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Gutter))
            is Load.Ok -> {
                val (enabled, presets) = s.value
                if (!enabled || presets == null) {
                    Text(stringResource(R.string.slice_off), modifier = Modifier.padding(Gutter), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Group(stringResource(R.string.slice_title), footer = stringResource(R.string.slice_footer)) {
                        GroupRow(stringResource(R.string.slice_printer), printerPreset?.name ?: stringResource(R.string.choose), showChevron = true, onClick = { pick = "printer" })
                        GroupRow(stringResource(R.string.slice_process), processPreset?.name ?: stringResource(R.string.choose), showChevron = true, onClick = { pick = "process" })
                        GroupRow(stringResource(R.string.slice_filament), filamentPreset?.name ?: stringResource(R.string.choose), showChevron = true, onClick = { pick = "filament" })
                    }
                    status?.let { Text(it, modifier = Modifier.padding(horizontal = Gutter), style = MaterialTheme.typography.bodyMedium) }
                    PillButton(
                        stringResource(R.string.slice_start),
                        enabled = !busy && printerPreset != null && processPreset != null && filamentPreset != null,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp).fillMaxWidth(),
                        onClick = {
                            busy = true
                            status = context.getString(R.string.slice_running)
                            scope.launch {
                                try {
                                    val body = buildJsonObject {
                                        putJsonObject("printer_preset") { put("source", printerPreset!!.source); put("id", printerPreset!!.id) }
                                        putJsonObject("process_preset") { put("source", processPreset!!.source); put("id", processPreset!!.id) }
                                        put(
                                            "filament_presets",
                                            JsonArray(listOf(buildJsonObject { put("source", filamentPreset!!.source); put("id", filamentPreset!!.id) })),
                                        )
                                        put("export_3mf", true)
                                    }
                                    val job = api.slice(fileId, body.toString())
                                    while (true) {
                                        delay(2_000)
                                        val j = api.sliceJob(job.jobId)
                                        when (j.status) {
                                            "completed" -> {
                                                val newId = j.result?.libraryFileId ?: error(context.getString(R.string.slice_failed, ""))
                                                onSliced(newId)
                                                return@launch
                                            }
                                            "failed" -> error(context.getString(R.string.slice_failed, j.errorDetail.orEmpty()))
                                        }
                                    }
                                } catch (e: Exception) {
                                    status = e.friendlyMessage(context)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                    when (pick) {
                        "printer" -> ChoiceSheet(stringResource(R.string.slice_printer), presets.printers.map { it to it.name }, printerPreset, { pick = null }) { printerPreset = it; pick = null }
                        "process" -> ChoiceSheet(stringResource(R.string.slice_process), presets.processes.map { it to it.name }, processPreset, { pick = null }) { processPreset = it; pick = null }
                        "filament" -> ChoiceSheet(stringResource(R.string.slice_filament), presets.filaments.map { it to it.name }, filamentPreset, { pick = null }) { filamentPreset = it; pick = null }
                    }
                }
            }
        }
    }
}
