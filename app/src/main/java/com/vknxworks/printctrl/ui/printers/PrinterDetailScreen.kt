package com.vknxworks.printctrl.ui.printers

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.bambuddy.PrinterStatus
import com.vknxworks.printctrl.ui.components.ChoiceSheet
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.RoundAction
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.formatTemp
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.speedLabel
import com.vknxworks.printctrl.ui.nav.CameraRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Everything about one printer: camera, job, controls, errors, temperatures and the AMS. */
@Composable
fun PrinterDetailScreen(printerId: Int, confirmStop: Boolean = false) {
    val context = LocalContext.current
    val c = context.container
    val live by remember(printerId) { c.printers.printer(printerId) }.collectAsStateWithLifecycle(null)
    val state by c.printers.state.collectAsStateWithLifecycle()
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            c.printers.refresh()
            delay(900)
            refreshing = false
        }
    }
    val p = live

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(p?.name ?: stringResource(R.string.printer)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                p == null && !state.loaded -> LoadingView()
                p == null -> MessageView(Icons.Outlined.Print, stringResource(R.string.printer_gone), stringResource(R.string.printer_gone_body))
                else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true }, modifier = Modifier.fillMaxSize()) {
                    PrinterDetail(p, client, confirmStop)
                }
            }
        }
    }
}

@Composable
private fun PrinterDetail(p: LivePrinter, client: BambuddyClient?, confirmStop: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val s = p.status
    val phase = s?.phase ?: PrintPhase.UNKNOWN
    val spoolman by c.spoolman.collectAsStateWithLifecycle()
    var setTarget by remember { mutableStateOf<String?>(null) }
    var slotSheet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var skipping by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Which Spoolman spool is in each slot, with names when Spoolman is set up in the app too.
    val links = rememberLoader("links:${client?.conn?.key}:${spoolman?.conn?.key}:${p.id}") {
        val api = client ?: return@rememberLoader emptyMap<Pair<Int, Int>, Pair<Int, String>>()
        val assignments = runCatching { api.slotAssignments(p.id) }.getOrDefault(emptyList())
        assignments.associate { a ->
            val name = spoolman?.let { sm -> runCatching { sm.spool(a.spoolmanSpoolId).filament.displayName }.getOrNull() }
            (a.amsId to a.trayId) to (a.spoolmanSpoolId to (name?.takeIf { it.isNotBlank() } ?: "#${a.spoolmanSpoolId}"))
        }
    }

    /** Runs a printer command; a refused one says why (usually a missing permission on the API key). */
    fun command(kind: String?, block: suspend BambuddyClient.() -> Unit) {
        val api = client ?: return
        scope.launch {
            runCatching { api.block() }
                .onSuccess {
                    // Usage stats (only if opted in): which control, never which printer or print.
                    if (kind != null) context.container.telemetry.printControl(kind)
                    context.container.printers.refresh()
                }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    fun act(block: suspend BambuddyClient.() -> Unit) = command(null, block)

    /** Pause, resume and stop: counted in the usage stats by kind (never which printer or print). */
    fun control(kind: String, block: suspend BambuddyClient.() -> Unit) = command(kind, block)

    // The camera stream belongs to the page, not to its row in the list, so it keeps running while
    // the camera is scrolled out of view.
    val mjpeg = if (s?.connected == true) rememberMjpeg(p.id) else null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        // Camera, while the printer is online. Tap to open it full screen.
        if (mjpeg != null) {
            item(key = "camera") {
                Box(
                    Modifier
                        .padding(horizontal = Gutter, vertical = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { nav.navigate(CameraRoute(p.id)) },
                ) {
                    CameraImage(mjpeg, Modifier.fillMaxSize(), rotation = p.printer.cameraRotation)
                    FilledTonalIconButton(
                        onClick = { nav.navigate(CameraRoute(p.id)) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = OnCamera),
                    ) { Icon(Icons.Filled.Fullscreen, stringResource(R.string.camera_fullscreen)) }
                }
            }
        }

        item(key = "job") { JobSection(p, client, phase) }

        if (s != null && s.connected) {
            item(key = "controls") { Controls(s, phase, confirmStop, act = ::act, control = ::control, onSkip = { skipping = true }) }
            if (phase == PrintPhase.PRINTING || phase == PrintPhase.PAUSED) {
                client?.let { api -> item(key = "ai") { AiDetectionRow(api, p.id) } }
            }
        }

        val hms = s?.hmsErrors.orEmpty()
        if (hms.isNotEmpty()) {
            item(key = "hms") {
                Group(stringResource(R.string.hms_title)) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hms.forEach { HmsCard(it) }
                    }
                    TextButton(onClick = { act { clearHms(p.id) } }, modifier = Modifier.padding(start = 6.dp)) {
                        Text(stringResource(R.string.hms_clear))
                    }
                }
            }
        }

        if (s != null && s.connected) {
            item(key = "temps") {
                Group(stringResource(R.string.temperatures)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TempGraph(
                            stringResource(R.string.nozzle), s.nozzle, s.nozzleTarget, p.temps,
                            value = { it.nozzle }, targetOf = { it.nozzleTarget },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { setTarget = "nozzle" },
                        )
                        TempGraph(
                            stringResource(R.string.bed), s.bed, s.bedTarget, p.temps,
                            value = { it.bed }, targetOf = { it.bedTarget },
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { setTarget = "bed" },
                        )
                        Text(stringResource(R.string.temps_tap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.chamber?.let {
                            MetaRow(listOf(stringResource(R.string.chamber_value, formatTemp(it))))
                        }
                    }
                }
            }

            val units = s.ams.orEmpty()
            val external = s.vtTray.firstOrNull { !it.trayType.isNullOrBlank() }
            if (units.isNotEmpty() || external != null) {
                item(key = "ams") {
                    Group(stringResource(R.string.filament)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            units.forEach { unit ->
                                val title = if (unit.moduleType == "n3s" || unit.isAmsHt) stringResource(R.string.ams_ht_title, unit.id + 1)
                                else if (units.size == 1) stringResource(R.string.ams_title_single)
                                else stringResource(R.string.ams_title, unit.id + 1)
                                AmsUnitView(
                                    unit, s.trayNow, title,
                                    linked = { ams, slot -> links.value?.get(ams to slot)?.second },
                                    onSlot = { slot -> slotSheet = unit.id to slot },
                                )
                            }
                            if (external != null) {
                                Row {
                                    TrayTile(
                                        external, stringResource(R.string.external_spool), loaded = s.trayNow == 254,
                                        modifier = Modifier.width(80.dp).clip(MaterialTheme.shapes.medium).clickable { slotSheet = 255 to 0 },
                                        linkedSpool = links.value?.get(255 to 0)?.second,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val fans = listOfNotNull(
                s.coolingFanSpeed?.let { Triple("part", R.string.fan_part, it) },
                s.bigFan1Speed?.let { Triple("aux", R.string.fan_aux, it) },
                s.bigFan2Speed?.let { Triple("chamber", R.string.fan_chamber, it) },
            )
            if (fans.isNotEmpty()) {
                item(key = "fans") {
                    Group(stringResource(R.string.fans), footer = stringResource(R.string.fans_footer)) {
                        fans.forEach { (fan, label, speed) ->
                            FanRow(stringResource(label), speed) { v -> act { setFan(p.id, fan, v) } }
                        }
                    }
                }
            }

            if (!phase.isActive) item(key = "motion") { MotionGroup(p.id, ::act) }
            client?.let { api ->
                item(key = "plug") { SmartPlugGroup(api, p.id, printing = phase.isActive) }
                item(key = "maintenance") { MaintenanceGroup(api, p.id) }
            }

            item(key = "files") {
                Group {
                    GroupRow(
                        stringResource(R.string.sd_files),
                        stringResource(R.string.sd_files_sub),
                        Icons.Outlined.SdStorage,
                        showChevron = true,
                        onClick = { nav.navigate(com.vknxworks.printctrl.ui.nav.PrinterFilesRoute(p.id)) },
                    )
                }
            }

            item(key = "info") {
                Group(stringResource(R.string.printer_info)) {
                    p.printer.model?.let { GroupRow(stringResource(R.string.model), it) }
                    s.firmwareVersion?.let { GroupRow(stringResource(R.string.firmware), it) }
                    s.wifiSignal?.let { GroupRow(stringResource(R.string.wifi), stringResource(R.string.wifi_value, it)) }
                }
            }
        }
    }

    if (s != null) {
        when (setTarget) {
            "nozzle" -> TargetDialog(stringResource(R.string.nozzle_target), s.nozzleTarget, 320, onDismiss = { setTarget = null }) { v ->
                setTarget = null
                act { setNozzleTemp(p.id, v) }
            }
            "bed" -> TargetDialog(stringResource(R.string.bed_target), s.bedTarget, 140, onDismiss = { setTarget = null }) { v ->
                setTarget = null
                act { setBedTemp(p.id, v) }
            }
        }
        slotSheet?.let { (ams, slot) ->
            val tray = if (ams == 255) s.vtTray.firstOrNull() else s.ams.orEmpty().firstOrNull { it.id == ams }?.tray?.firstOrNull { it.id == slot }
            val global = if (ams == 255) 254 else ams * 4 + slot
            val title = if (ams == 255) stringResource(R.string.external_spool) else stringResource(R.string.ams_slot, ('A' + (ams % 26)).toString(), slot + 1)
            val link = links.value?.get(ams to slot)
            SlotSheet(
                printerId = p.id,
                amsId = ams,
                slot = slot,
                title = title,
                linkedSpool = link?.second,
                canLoad = !tray?.trayType.isNullOrBlank() && !phase.isActive,
                isLoaded = s.trayNow == global && !phase.isActive,
                onAssign = if (spoolman != null) { { pickFor = ams to slot } } else null,
                onDismiss = { slotSheet = null },
                act = ::act,
                onTakeOut = if (link == null) null else fun() {
                    val spoolId = link.first
                    scope.launch {
                        runCatching { client?.unassignSlot(spoolId) }
                            .onSuccess {
                                c.telemetry.spoolAction("unassign_slot")
                                links.reload(silent = true)
                            }
                            .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                    }
                },
            )
        }
        val sm = spoolman
        pickFor?.let { (ams, slot) ->
            if (sm != null) {
                val tray = if (ams == 255) s.vtTray.firstOrNull() else s.ams.orEmpty().firstOrNull { it.id == ams }?.tray?.firstOrNull { it.id == slot }
                val title = if (ams == 255) stringResource(R.string.external_spool) else stringResource(R.string.ams_slot, ('A' + (ams % 26)).toString(), slot + 1)
                SpoolPickerSheet(sm, title, tray?.trayType, onDismiss = { pickFor = null }) { spool ->
                    pickFor = null
                    scope.launch {
                        runCatching { client?.assignSlot(spool.id, p.id, ams, slot) }
                            .onSuccess {
                                c.telemetry.spoolAction("assign_slot")
                                Toast.makeText(context, R.string.assign_done, Toast.LENGTH_SHORT).show()
                                links.reload(silent = true)
                                c.printers.refresh()
                            }
                            .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }
    if (skipping && client != null) {
        SkipObjectsDialog(client, p.id, onDismiss = { skipping = false }, act = ::act)
    }
}

@Composable
private fun JobSection(p: LivePrinter, client: BambuddyClient?, phase: PrintPhase) {
    val context = LocalContext.current
    val s = p.status
    Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhaseChip(phase)
            s?.stgCurName?.takeIf { it.isNotBlank() && phase.isActive }?.let {
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val job = s?.jobName
        if (s != null && job != null && phase != PrintPhase.IDLE && phase != PrintPhase.OFFLINE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(client, p, Modifier.size(88.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(job, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val progress = ((s.progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
            }
            val facts = jobFacts(context, p).toMutableList()
            val started = p.startedAtMs
            if (started != null && phase.isActive) {
                val minutes = ((System.currentTimeMillis() - started) / 60_000L).toInt()
                if (minutes in 0..(7 * 24 * 60)) facts += stringResource(R.string.elapsed_value, formatMinutes(context, minutes))
            }
            MetaRow(facts)
        } else if (phase == PrintPhase.IDLE) {
            Text(stringResource(R.string.printer_idle_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (phase == PrintPhase.OFFLINE) {
            Text(stringResource(R.string.printer_offline_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pause, resume, stop, speed, light and the plate-cleared button, as far as this printer supports them. */
@Composable
private fun Controls(
    s: PrinterStatus,
    phase: PrintPhase,
    askStop: Boolean,
    act: (suspend BambuddyClient.() -> Unit) -> Unit,
    control: (String, suspend BambuddyClient.() -> Unit) -> Unit,
    onSkip: () -> Unit,
) {
    var confirmStop by remember { mutableStateOf(askStop && (phase.isActive)) }
    var pickSpeed by remember { mutableStateOf(false) }
    val id = s.id
    val printing = phase == PrintPhase.PRINTING || phase == PrintPhase.PREPARING
    val paused = phase == PrintPhase.PAUSED
    Column(Modifier.padding(horizontal = Gutter - 4.dp, vertical = 4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (paused) {
                RoundAction(Icons.Filled.PlayArrow, stringResource(R.string.resume), onClick = { control("resume") { resume(id) } }, active = true)
            } else {
                RoundAction(Icons.Filled.Pause, stringResource(R.string.pause), onClick = { control("pause") { pause(id) } }, enabled = printing)
            }
            RoundAction(Icons.Filled.Stop, stringResource(R.string.stop), onClick = { confirmStop = true }, enabled = printing || paused, danger = true)
            RoundAction(Icons.Filled.Speed, stringResource(speedLabel(s.speedLevel)), onClick = { pickSpeed = true }, enabled = printing || paused)
            RoundAction(
                if (s.chamberLight) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                stringResource(if (s.chamberLight) R.string.light_on else R.string.light_off),
                onClick = { act { setLight(id, !s.chamberLight) } },
                active = s.chamberLight,
            )
            if (phase == PrintPhase.PRINTING || phase == PrintPhase.PAUSED) {
                RoundAction(Icons.Outlined.ContentCut, stringResource(R.string.skip_objects), onClick = onSkip)
            }
            if (s.awaitingPlateClear) {
                RoundAction(Icons.Outlined.CleaningServices, stringResource(R.string.plate_cleared), onClick = { act { clearPlate(id) } }, active = true)
            }
        }
        if (s.awaitingPlateClear) {
            Text(
                stringResource(R.string.plate_waiting_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }

    if (confirmStop) {
        ConfirmDialog(
            stringResource(R.string.stop_title),
            stringResource(R.string.stop_body, s.jobName ?: stringResource(R.string.this_print)),
            stringResource(R.string.stop),
            onDismiss = { confirmStop = false },
        ) {
            confirmStop = false
            control("stop") { stop(id) }
        }
    }
    if (pickSpeed) {
        ChoiceSheet(
            stringResource(R.string.speed_title),
            (1..4).map { it to stringResource(speedLabel(it)) },
            s.speedLevel,
            onDismiss = { pickSpeed = false },
        ) { level ->
            pickSpeed = false
            act { setSpeed(id, level) }
        }
    }
}

@Composable
private fun FanRow(label: String, speed: Int, onSet: (Int) -> Unit) {
    // Follows the printer's value until the user drags; the new value is sent when they let go.
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableFloatStateOf(speed.toFloat()) }
    val value = if (dragging) dragged else speed.toFloat()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.percent, value.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = { dragging = true; dragged = it },
            onValueChangeFinished = {
                dragging = false
                onSet(dragged.roundToInt())
            },
            valueRange = 0f..100f,
        )
    }
}
