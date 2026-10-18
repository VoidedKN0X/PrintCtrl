package com.vknxworks.printctrl.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** "2 h 05 min", "45 min", "< 1 min". */
fun formatMinutes(context: Context, minutes: Int): String = when {
    minutes <= 0 -> context.getString(R.string.duration_under_minute)
    minutes < 60 -> context.getString(R.string.duration_minutes, minutes)
    else -> context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

/** Clock time of [epochMs] in the phone's 12/24-hour format, with the day when it isn't today. */
fun formatClock(context: Context, epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val clock = time.format(DateTimeFormatter.ofPattern(pattern))
    val today = Instant.now().atZone(zone).truncatedTo(ChronoUnit.DAYS)
    val days = ChronoUnit.DAYS.between(today.toLocalDate(), time.toLocalDate())
    return when (days) {
        0L -> clock
        1L -> context.getString(R.string.tomorrow_at, clock)
        else -> context.getString(R.string.day_at, time.format(DateTimeFormatter.ofPattern("EEE d MMM")), clock)
    }
}

/** "214°" or "214° / 220°" with the target. */
fun formatTemp(current: Double?, target: Double? = null): String {
    if (current == null) return "–"
    val c = "${current.roundToInt()}°"
    return if (target != null && target > 0) "$c / ${target.roundToInt()}°" else c
}

/** Bambu and Spoolman colours: "RRGGBB" or "RRGGBBAA", with or without '#'. */
fun parseHexColor(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    return runCatching {
        when (h.length) {
            6 -> Color(("FF$h").toLong(16))
            8 -> {
                val rgb = h.substring(0, 6)
                val alpha = h.substring(6, 8).toInt(16)
                // A fully transparent colour from the AMS means "unknown", not invisible.
                if (alpha == 0) null else Color(("FF$rgb").toLong(16))
            }
            else -> null
        }
    }.getOrNull()
}

/** Text that stays readable on top of [background]. */
fun contentOn(background: Color): Color = if (background.luminance() > 0.5f) Color(0xFF15181D) else Color.White

fun phaseLabel(phase: PrintPhase): Int = when (phase) {
    PrintPhase.OFFLINE -> R.string.phase_offline
    PrintPhase.IDLE -> R.string.phase_idle
    PrintPhase.PREPARING -> R.string.phase_preparing
    PrintPhase.PRINTING -> R.string.phase_printing
    PrintPhase.PAUSED -> R.string.phase_paused
    PrintPhase.FINISHED -> R.string.phase_finished
    PrintPhase.FAILED -> R.string.phase_failed
    PrintPhase.UNKNOWN -> R.string.phase_unknown
}

@Composable
@ReadOnlyComposable
fun phaseColor(phase: PrintPhase): Color = when (phase) {
    PrintPhase.PRINTING, PrintPhase.PREPARING -> MaterialTheme.colorScheme.primary
    PrintPhase.PAUSED -> Warning
    PrintPhase.FINISHED -> MaterialTheme.colorScheme.tertiary
    PrintPhase.FAILED -> MaterialTheme.colorScheme.error
    PrintPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    PrintPhase.OFFLINE, PrintPhase.UNKNOWN -> MaterialTheme.colorScheme.outline
}

/** Amber for warnings and pauses; reads on both the light and dark themes. */
val Warning = Color(0xFFE8A33D)

fun speedLabel(level: Int): Int = when (level) {
    1 -> R.string.speed_silent
    3 -> R.string.speed_sport
    4 -> R.string.speed_ludicrous
    else -> R.string.speed_standard
}
