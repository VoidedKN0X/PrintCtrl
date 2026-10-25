package com.vknxworks.printctrl.ui.printers

import com.vknxworks.printctrl.ui.components.swatchOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.AmsTray
import com.vknxworks.printctrl.data.bambuddy.AmsUnit
import com.vknxworks.printctrl.data.bambuddy.HmsError
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.ui.components.Warning
import androidx.compose.foundation.BorderStroke
import com.vknxworks.printctrl.ui.components.CtrlProgressBar
import com.vknxworks.printctrl.ui.components.SectionLabel
import com.vknxworks.printctrl.ui.components.StatusTag
import com.vknxworks.printctrl.ui.theme.CtrlColors
import com.vknxworks.printctrl.ui.theme.CtrlType
import com.vknxworks.printctrl.ui.components.contentOn
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.phaseColor
import com.vknxworks.printctrl.ui.components.phaseLabel

/** The printer's state in mono capitals with a square dot. */
@Composable
fun PhaseChip(phase: PrintPhase, modifier: Modifier = Modifier) {
    StatusTag(stringResource(phaseLabel(phase)), phaseColor(phase), modifier)
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
fun AmsUnitView(
    unit: AmsUnit,
    trayNow: Int,
    title: String,
    modifier: Modifier = Modifier,
    linked: (amsId: Int, slot: Int) -> String? = { _, _ -> null },
    onSlot: ((slot: Int) -> Unit)? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title, Modifier.weight(1f))
            unit.humidity?.let {
                Text(stringResource(R.string.ams_humidity, it), style = CtrlType.Data, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .then(if (onSlot != null) Modifier.clickable { onSlot(i) } else Modifier),
                    linkedSpool = linked(unit.id, i),
                )
            }
            // Keep an AMS-HT's single tile the same width as a regular slot.
            if (unit.isAmsHt) repeat(3) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun Int.amsLetter(): String = ('A' + (this % 26)).toString()

@Composable
fun TrayTile(tray: AmsTray, label: String, loaded: Boolean, modifier: Modifier = Modifier, linkedSpool: String? = null) {
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
                    else Modifier.border(1.dp, swatchOutline(if (empty) null else color), RoundedCornerShape(14.dp)),
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
        Text(label, style = CtrlType.Data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!empty) {
            Text(
                tray.traySubBrands?.takeIf { it.isNotBlank() } ?: tray.trayType.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The AMS only knows what's left on spools it can read (Bambu RFID).
            if (tray.remain > 0) {
                CtrlProgressBar(
                    progress = { tray.remain / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    height = 4.dp,
                )
                Text(stringResource(R.string.percent, tray.remain), style = CtrlType.Data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (loaded) {
            Text(stringResource(R.string.ams_in_use).uppercase(), style = CtrlType.Status, color = MaterialTheme.colorScheme.primary)
        }
        linkedSpool?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** A row of filament swatches for the printer card: one per AMS slot, in the real filament colours. */
@Composable
fun AmsDots(units: List<AmsUnit>, trayNow: Int, modifier: Modifier = Modifier, loadedColor: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        units.forEach { unit ->
            val slots = if (unit.isAmsHt) 1 else 4
            for (i in 0 until slots) {
                val tray = unit.tray.firstOrNull { it.id == i } ?: AmsTray(id = i)
                val color = parseHexColor(tray.trayColor)
                val loaded = !unit.isAmsHt && unit.id * 4 + i == trayNow
                val shape = RoundedCornerShape(if (loaded) 6.dp else 5.dp)
                Box(
                    Modifier
                        .size(if (loaded) 20.dp else 16.dp)
                        .clip(shape)
                        .background(if (tray.isEmpty || color == null) CtrlColors.Track else color)
                        .border(if (loaded) 2.dp else 1.dp, if (loaded) loadedColor else swatchOutline(if (tray.isEmpty) null else color), shape),
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
        1 -> Triple(MaterialTheme.colorScheme.error, Icons.Outlined.ErrorOutline, R.string.hms_error)
        2 -> Triple(Warning, Icons.Outlined.WarningAmber, R.string.hms_warning)
        else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Info, R.string.hms_notice)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(label).uppercase(), style = CtrlType.LabelStrong, color = color)
                Text(
                    error.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.hms_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    error.displayCode(),
                    style = CtrlType.Data,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Colour of a line-art icon shown on top of the camera picture. */
val OnCamera = Color.White
