package com.vknxworks.printctrl.ui.printers

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.bambuddy.TempSample
import com.vknxworks.printctrl.ui.components.formatTemp
import kotlin.math.max

/**
 * One temperature over the last 15 minutes: the reading as a solid line, the target as a dashed one.
 * The scale starts at 0 so a heating bed and a cooling nozzle read honestly against each other.
 */
@Composable
fun TempGraph(
    label: String,
    current: Double?,
    target: Double?,
    samples: List<TempSample>,
    value: (TempSample) -> Float?,
    targetOf: (TempSample) -> Float?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.temp_graph_description, label)
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(formatTemp(current, target), style = MaterialTheme.typography.titleMedium, color = color)
        }
        Spacer(Modifier.height(6.dp))
        val grid = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = description }) {
            val now = System.currentTimeMillis()
            val from = now - PrintersRepository.HISTORY_MS
            val top = max(60f, samples.maxOfOrNull { max(value(it) ?: 0f, targetOf(it) ?: 0f) } ?: 0f) * 1.1f
            // Baseline and a mid line for scale.
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            line(samples, from, now, top, targetOf, color.copy(alpha = 0.55f), dashed = true)
            line(samples, from, now, top, value, color, dashed = false)
        }
        Row(Modifier.padding(top = 2.dp)) {
            Text(stringResource(R.string.temp_graph_window), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f).width(0.dp))
            Text(stringResource(R.string.now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun DrawScope.line(
    samples: List<TempSample>,
    from: Long,
    to: Long,
    top: Float,
    pick: (TempSample) -> Float?,
    color: Color,
    dashed: Boolean,
) {
    val span = (to - from).toFloat()
    val path = Path()
    var started = false
    for (s in samples) {
        val v = pick(s)
        if (v == null) {
            started = false
            continue
        }
        val x = ((s.atMs - from) / span).coerceIn(0f, 1f) * size.width
        val y = size.height - (v / top).coerceIn(0f, 1f) * size.height
        if (!started) path.moveTo(x, y) else path.lineTo(x, y)
        started = true
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = if (dashed) 1.5.dp.toPx() else 2.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
        ),
    )
}
