package com.vknxworks.printctrl.ui.printers

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One reading at a time; [target] is the set temperature, when there is one. */
data class ChartPoint(val atMs: Long, val value: Float?, val target: Float? = null)

/**
 * A line chart of [points] between [fromMs] and [toMs]: the reading as a solid line, the target as a
 * dashed one, with the scale on the left and times underneath. [unit] follows each scale value ("°", "%").
 * Gaps longer than a tenth of the window (the printer was off) break the line instead of bridging it.
 */
@Composable
fun HistoryChart(
    points: List<ChartPoint>,
    fromMs: Long,
    toMs: Long,
    color: Color,
    unit: String,
    description: String,
    modifier: Modifier = Modifier,
    startAtZero: Boolean = true,
) {
    val context = LocalContext.current
    val shown = remember(points) { downsample(points, MAX_POINTS) }
    val values = shown.flatMap { listOfNotNull(it.value, it.target?.takeIf { t -> t > 0f }) }
    val rawMin = if (startAtZero) 0f else (values.minOrNull() ?: 0f)
    val rawMax = values.maxOrNull() ?: 1f
    // Round the scale to tidy steps so the labels read well.
    val step = niceStep(max(rawMax - rawMin, 1f) / 2f)
    val low = floor(rawMin / step) * step
    val high = max(low + step, ceil(rawMax * 1.05f / step) * step)
    val labelStyle = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val timeFmt = remember(fromMs, toMs) {
        val days = toMs - fromMs > 26 * 3_600_000L
        val clock = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        DateTimeFormatter.ofPattern(if (days) "EEE $clock" else clock).withZone(ZoneId.systemDefault())
    }

    Column(modifier) {
        Row {
            Column(Modifier.height(CHART_HEIGHT).padding(end = 6.dp)) {
                Text(fmt(high, unit), style = labelStyle, color = muted)
                Spacer(Modifier.weight(1f))
                Text(fmt((high + low) / 2, unit), style = labelStyle, color = muted)
                Spacer(Modifier.weight(1f))
                Text(fmt(low, unit), style = labelStyle, color = muted)
            }
            Canvas(Modifier.weight(1f).height(CHART_HEIGHT).semantics { contentDescription = description }) {
                val stroke = 1.dp.toPx()
                for (i in 0..2) {
                    val y = size.height * i / 2f
                    drawLine(grid.copy(alpha = if (i == 2) 1f else 0.5f), Offset(0f, y), Offset(size.width, y), stroke)
                }
                plot(shown, fromMs, toMs, low, high, { it.target?.takeIf { t -> t > 0f } }, color.copy(alpha = 0.55f), dashed = true)
                plot(shown, fromMs, toMs, low, high, { it.value }, color, dashed = false)
            }
        }
        Row(Modifier.padding(top = 2.dp)) {
            Spacer(Modifier.width(LABEL_GUTTER))
            Text(timeFmt.format(Instant.ofEpochMilli(fromMs)), style = labelStyle, color = muted)
            Spacer(Modifier.weight(1f))
            Text(timeFmt.format(Instant.ofEpochMilli((fromMs + toMs) / 2)), style = labelStyle, color = muted)
            Spacer(Modifier.weight(1f))
            Text(timeFmt.format(Instant.ofEpochMilli(toMs)), style = labelStyle, color = muted)
        }
    }
}

private val CHART_HEIGHT = 140.dp
private val LABEL_GUTTER = 36.dp
private const val MAX_POINTS = 480

private fun fmt(v: Float, unit: String): String =
    if (v >= 10 || v == floor(v)) "${v.toInt()}$unit" else "%.1f%s".format(v, unit)

private fun niceStep(raw: Float): Float {
    val steps = floatArrayOf(1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f, 200f)
    return steps.firstOrNull { it >= raw } ?: 500f
}

/** Keeps at most [max] points, taking the highest reading of each bucket so peaks stay visible. */
private fun downsample(points: List<ChartPoint>, max: Int): List<ChartPoint> {
    if (points.size <= max) return points
    val bucket = ceil(points.size / max.toDouble()).toInt()
    return points.chunked(bucket).map { chunk -> chunk.maxByOrNull { it.value ?: Float.MIN_VALUE } ?: chunk.first() }
}

private fun DrawScope.plot(
    points: List<ChartPoint>,
    from: Long,
    to: Long,
    low: Float,
    high: Float,
    pick: (ChartPoint) -> Float?,
    color: Color,
    dashed: Boolean,
) {
    if (points.isEmpty() || to <= from) return
    val span = (to - from).toFloat()
    val maxGap = (to - from) / 10
    val path = Path()
    var started = false
    var lastAt = Long.MIN_VALUE
    points.forEach { p ->
        val v = pick(p)
        if (v == null || p.atMs < from || (started && p.atMs - lastAt > maxGap)) {
            started = false
        }
        if (v == null) return@forEach
        val x = (p.atMs - from) / span * size.width
        val y = size.height - ((min(max(v, low), high) - low) / (high - low)) * size.height
        if (!started) path.moveTo(x, y) else path.lineTo(x, y)
        started = true
        lastAt = p.atMs
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
        ),
    )
}
