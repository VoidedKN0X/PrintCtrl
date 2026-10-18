package com.vknxworks.printctrl.ui.printers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.AmsTray
import com.vknxworks.printctrl.data.bambuddy.AmsUnit
import com.vknxworks.printctrl.data.bambuddy.HmsError
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.contentOn
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.phaseColor
import com.vknxworks.printctrl.ui.components.phaseLabel

/** Small coloured pill with the printer's state. */
@Composable
fun PhaseChip(phase: PrintPhase, modifier: Modifier = Modifier) {
    val color = phaseColor(phase)
    Text(
        stringResource(phaseLabel(phase)),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---------- AMS ----------

/** True when the slot holds no spool (or nothing is known about one). */
private val AmsTray.isEmpty: Boolean
    get() = exists == false || (exists == null && state == 9) || (trayType.isNullOrBlank() && exists != true)

/**
 * One AMS unit: a tile per slot with the filament colour, material and what's left. Humidity only
 * shows for units that report it (the AMS lite doesn't).
 */
@Composable
fun AmsUnitView(unit: AmsUnit, trayNow: Int, title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            unit.humidity?.let {
                Text(stringResource(R.string.ams_humidity, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val slots = if (unit.isAmsHt) 1 else 4
            for (i in 0 until slots) {
                val tray = unit.tray.firstOrNull { it.id == i } ?: AmsTray(id = i)
                val global = if (unit.isAmsHt) -1 else unit.id * 4 + i
                TrayTile(
                    tray,
                    label = stringResource(R.string.ams_slot, unit.id.amsLetter(), i + 1),
                    loaded = global == trayNow,
                    modifier = Modifier.weight(1f),
                )
            }
            // Keep an AMS-HT's single tile the same width as a regular slot.
            if (unit.isAmsHt) repeat(3) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun Int.amsLetter(): String = ('A' + (this % 26)).toString()

@Composable
fun TrayTile(tray: AmsTray, label: String, loaded: Boolean, modifier: Modifier = Modifier) {
    val color = parseHexColor(tray.trayColor)
    val empty = tray.isEmpty
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (empty || color == null) MaterialTheme.colorScheme.surfaceContainerHighest else color)
                .then(
                    if (loaded) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val text = when {
                empty -> stringResource(R.string.ams_empty)
                color == null -> "?"
                else -> tray.trayType.orEmpty()
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (empty || color == null) MaterialTheme.colorScheme.onSurfaceVariant else contentOn(color),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!empty) {
            Text(
                tray.traySubBrands?.takeIf { it.isNotBlank() } ?: tray.trayType.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The AMS only knows what's left on spools it can read (Bambu RFID).
            if (tray.remain > 0) {
                LinearProgressIndicator(
                    progress = { tray.remain / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(CircleShape),
                    drawStopIndicator = {},
                )
                Text(stringResource(R.string.percent, tray.remain), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loaded) {
            Text(stringResource(R.string.ams_in_use), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A row of colour dots for the printer card: one per AMS slot. */
@Composable
fun AmsDots(units: List<AmsUnit>, trayNow: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        units.forEach { unit ->
            val slots = if (unit.isAmsHt) 1 else 4
            for (i in 0 until slots) {
                val tray = unit.tray.firstOrNull { it.id == i } ?: AmsTray(id = i)
                val color = parseHexColor(tray.trayColor)
                val loaded = !unit.isAmsHt && unit.id * 4 + i == trayNow
                Box(
                    Modifier
                        .size(if (loaded) 18.dp else 14.dp)
                        .clip(CircleShape)
                        .background(if (tray.isEmpty || color == null) MaterialTheme.colorScheme.surfaceContainerHighest else color)
                        .border(if (loaded) 2.dp else 1.dp, if (loaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ---------- HMS ----------

/** Bambu writes HMS codes as four groups of four hex digits: HMS_0300_1A00_0002_0001. */
fun HmsError.displayCode(): String {
    val hex = fullCode.ifBlank { code }.uppercase().filter { it.isLetterOrDigit() }
    if (hex.isEmpty()) return code
    val groups = hex.chunked(4).joinToString("_")
    return if (hex.length == 16) "HMS_$groups" else groups
}

@Composable
fun HmsCard(error: HmsError, modifier: Modifier = Modifier) {
    val (color, icon, label) = when (error.severity) {
        1 -> Triple(MaterialTheme.colorScheme.error, Icons.Filled.ErrorOutline, R.string.hms_error)
        2 -> Triple(Warning, Icons.Filled.WarningAmber, R.string.hms_warning)
        else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Info, R.string.hms_notice)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.10f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = color)
                Text(
                    error.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.hms_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    error.displayCode(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Colour of a line-art icon shown on top of the camera picture. */
val OnCamera = Color.White
