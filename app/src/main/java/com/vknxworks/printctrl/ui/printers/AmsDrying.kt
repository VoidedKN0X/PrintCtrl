package com.vknxworks.printctrl.ui.printers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.AmsUnit
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.PrinterStatus
import com.vknxworks.printctrl.data.net.ApiJson
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.CtrlAlertDialog
import com.vknxworks.printctrl.ui.components.CtrlSwitch
import com.vknxworks.printctrl.ui.components.SectionLabel
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.theme.CtrlType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.math.roundToInt

/** Temperature and hours per material, for the AMS 2 Pro (n3f) and the AMS HT (n3s), as Bambuddy has them. */
@Serializable
data class DryingPreset(val n3f: Int = 45, val n3s: Int = 45, val n3fHours: Int = 12, val n3sHours: Int = 12)

/** Bambuddy's built-in presets (from Bambu Studio's filament profiles), used where its settings have none. */
private val BUILT_IN_PRESETS = linkedMapOf(
    "PLA" to DryingPreset(45, 45, 12, 12),
    "PETG" to DryingPreset(65, 65, 12, 12),
    "TPU" to DryingPreset(65, 75, 12, 18),
    "ABS" to DryingPreset(65, 80, 12, 8),
    "ASA" to DryingPreset(65, 80, 12, 8),
    "PA" to DryingPreset(65, 85, 12, 12),
    "PC" to DryingPreset(65, 80, 12, 8),
    "PVA" to DryingPreset(65, 85, 12, 18),
)

/** Nylon is spelled many ways; all of them dry as PA. */
private val MATERIAL_ALIASES = mapOf("NYLON" to "PA", "PA6" to "PA", "PA11" to "PA", "PA12" to "PA", "PAHT" to "PA", "PPA" to "PA")

/** The preset for a tray's material: composites and support materials dry as their base, anything else as PLA (the coolest). */
private fun presetKey(trayType: String?, presets: Map<String, DryingPreset>): String {
    val raw = trayType.orEmpty().trim().split(' ').first().uppercase()
    for (candidate in listOf(raw, raw.substringBefore('-'))) {
        val key = MATERIAL_ALIASES[candidate] ?: candidate
        if (presets.containsKey(key)) return key
    }
    return "PLA"
}

private fun parsePresets(json: String): Map<String, DryingPreset> {
    if (json.isBlank()) return BUILT_IN_PRESETS
    val custom = runCatching { ApiJson.decodeFromString(MapSerializer(String.serializer(), DryingPreset.serializer()), json) }.getOrNull()
    return if (custom.isNullOrEmpty()) BUILT_IN_PRESETS else LinkedHashMap(BUILT_IN_PRESETS).apply { putAll(custom) }
}

/** Why the firmware says this AMS can't dry right now, in words. */
private fun reasonText(code: Int): Int? = when (code) {
    0 -> R.string.dry_reason_busy
    1 -> R.string.dry_reason_power
    2 -> R.string.dry_reason_ams_busy
    3 -> R.string.dry_reason_outlet
    4 -> R.string.dry_reason_starting
    5 -> R.string.dry_reason_2d
    6 -> R.string.dry_reason_drying
    7 -> R.string.dry_reason_upgrading
    8 -> R.string.dry_reason_adapter
    else -> null
}

/**
 * Drying for one AMS 2 Pro or AMS HT: what it's doing, and start or stop. Shown only for units with a
 * heater ([AmsUnit.canDry]), so an AMS or AMS lite (an A1's) never gets it.
 */
@Composable
fun AmsDryingRow(
    unit: AmsUnit,
    status: PrinterStatus,
    client: BambuddyClient,
    printerId: Int,
    act: (suspend BambuddyClient.() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    var starting by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    val settings = rememberLoader("drying-presets:${client.conn.key}") { client.settings() }
    val presets = remember(settings.value?.dryingPresets) { parsePresets(settings.value?.dryingPresets.orEmpty()) }
    val printing = status.phase.isActive
    val screenOnly = status.dryingScreenOnly
    val remote = status.supportsDrying && !screenOnly
    val blockedByPrint = printing && !status.supportsDryingWhilePrinting

    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(stringResource(R.string.dry_title))
                Text(statusLine(unit), style = CtrlType.Data, color = if (unit.drying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (remote) {
                if (unit.drying) {
                    TextButton(onClick = { confirmStop = true }) { Text(stringResource(R.string.dry_stop), color = MaterialTheme.colorScheme.error) }
                } else {
                    TextButton(onClick = { starting = true }, enabled = !blockedByPrint) { Text(stringResource(R.string.dry_start)) }
                }
            }
        }
        val note = when {
            screenOnly -> stringResource(R.string.dry_screen_only)
            !status.supportsDrying -> stringResource(R.string.dry_unsupported)
            blockedByPrint && !unit.drying -> stringResource(R.string.dry_while_printing)
            else -> null
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!unit.drying) {
            unit.drySfReason.mapNotNull { reasonText(it) }.distinct().forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = Warning)
            }
        }
    }

    if (starting) {
        val loadedHere = if (unit.id >= 128) status.trayNow == unit.id else status.trayNow < 254 && status.trayNow / 4 == unit.id
        DryingDialog(unit, presets, rotateAllowed = !loadedHere, onDismiss = { starting = false }) { material, temp, hours, rotate ->
            starting = false
            act { startDrying(printerId, unit.id, temp, hours, material, rotate) }
        }
    }
    if (confirmStop) {
        ConfirmDialog(
            stringResource(R.string.dry_stop_title),
            stringResource(R.string.dry_stop_body),
            stringResource(R.string.dry_stop),
            onDismiss = { confirmStop = false },
        ) {
            confirmStop = false
            act {
                stopDrying(printerId, unit.id)
                // Only once Bambuddy took the stop: a stop that failed mustn't hide the real "finished" later.
                c.drying.stoppedByUser(printerId, unit.id)
            }
        }
    }
}

@Composable
private fun statusLine(unit: AmsUnit): String {
    val context = LocalContext.current
    if (!unit.drying) return stringResource(R.string.dry_idle)
    if (unit.dryCountdownStalled && unit.dryStatus !in 1..3) return stringResource(R.string.dry_not_started)
    val stage = when (unit.dryStatus) {
        1 -> stringResource(R.string.dry_stage_checking)
        3 -> stringResource(R.string.dry_stage_cooling)
        else -> if (unit.drySubStatus == 1) stringResource(R.string.dry_stage_heating) else stringResource(R.string.dry_stage_drying)
    }
    val parts = buildList {
        add(stage)
        unit.dryFilament?.takeIf { it.isNotBlank() }?.let { add(it) }
        unit.dryTargetTemp?.let { add(stringResource(R.string.dry_target, it)) }
        unit.temp?.let { add(stringResource(R.string.dry_now, it.roundToInt())) }
        if (unit.dryTime > 0) add(stringResource(R.string.time_left, formatMinutes(context, unit.dryTime)))
    }
    return parts.joinToString("  ·  ")
}

/** Material preset, temperature, hours and spool rotation; nothing is sent until Start. */
@Composable
private fun DryingDialog(
    unit: AmsUnit,
    presets: Map<String, DryingPreset>,
    rotateAllowed: Boolean,
    onDismiss: () -> Unit,
    onStart: (material: String, temp: Int, hours: Int, rotate: Boolean) -> Unit,
) {
    val ht = unit.moduleType == "n3s"
    val maxTemp = unit.maxDryTemp
    fun tempOf(p: DryingPreset) = (if (ht) p.n3s else p.n3f).coerceIn(45, maxTemp)
    fun hoursOf(p: DryingPreset) = (if (ht) p.n3sHours else p.n3fHours).coerceIn(1, 24)
    val first = presetKey(unit.tray.firstOrNull { !it.trayType.isNullOrBlank() }?.trayType, presets)
    var material by remember { mutableStateOf(first) }
    var temp by remember { mutableFloatStateOf(tempOf(presets[first] ?: DryingPreset()).toFloat()) }
    var hours by remember { mutableFloatStateOf(hoursOf(presets[first] ?: DryingPreset()).toFloat()) }
    var rotate by remember { mutableStateOf(false) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dry_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dry_dialog_body))
                SectionLabel(stringResource(R.string.material))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.keys.forEach { key ->
                        FilterChip(
                            selected = key == material,
                            onClick = {
                                material = key
                                presets[key]?.let { temp = tempOf(it).toFloat(); hours = hoursOf(it).toFloat() }
                            },
                            label = { Text(key) },
                        )
                    }
                }
                Row {
                    Text(stringResource(R.string.dry_temperature), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${temp.roundToInt()}°C", style = CtrlType.Data)
                }
                Slider(value = temp, onValueChange = { temp = it }, valueRange = 45f..maxTemp.toFloat(), steps = maxTemp - 45 - 1)
                Row {
                    Text(stringResource(R.string.dry_duration), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.dry_hours, hours.roundToInt()), style = CtrlType.Data)
                }
                Slider(value = hours, onValueChange = { hours = it }, valueRange = 1f..24f, steps = 22)
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dry_rotate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    CtrlSwitch(rotate && rotateAllowed, onCheckedChange = { rotate = it }, enabled = rotateAllowed)
                }
                if (!rotateAllowed) Text(stringResource(R.string.dry_rotate_locked), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.dry_max_note, maxTemp), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(material, temp.roundToInt(), hours.roundToInt(), rotate && rotateAllowed) }) {
                Text(stringResource(R.string.dry_start))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
