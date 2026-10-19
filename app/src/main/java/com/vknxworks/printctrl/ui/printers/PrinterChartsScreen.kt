package com.vknxworks.printctrl.ui.printers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.AmsHistory
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.HeaterSeries
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.bambuddy.amsHistory
import com.vknxworks.printctrl.data.bambuddy.sensorHistory
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** How far back to look; Bambuddy keeps up to a week. */
private val RANGES = listOf(1 to R.string.range_1h, 6 to R.string.range_6h, 24 to R.string.range_24h, 168 to R.string.range_7d)

private class ChartsData(val heaters: List<HeaterSeries>, val ams: List<AmsHistory>, val fromMs: Long, val toMs: Long)

/**
 * A printer's temperature history as Bambuddy recorded it: nozzle, bed and (where the printer has one)
 * chamber, each with its target, and the humidity and temperature inside AMS units that report them.
 */
@Composable
fun PrinterChartsScreen(printerId: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val live by remember(printerId) { c.printers.printer(printerId) }.collectAsStateWithLifecycle(null)
    var hours by rememberSaveable { mutableIntStateOf(6) }
    // AMS units that report humidity; the AMS lite doesn't, so it has no history to show.
    val amsIds = live?.status?.ams.orEmpty().filter { it.humidity != null }.map { it.id }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.charts_title, live?.name.orEmpty()).trim()) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val api = client
            if (api == null) LoadingView() else Charts(api, printerId, hours, amsIds) { hours = it }
        }
    }
}

@Composable
private fun Charts(api: BambuddyClient, printerId: Int, hours: Int, amsIds: List<Int>, onHours: (Int) -> Unit) {
    val context = LocalContext.current
    val loader = rememberLoader("charts:${api.conn.key}:$printerId:$hours:${amsIds.joinToString()}") {
        coroutineScope {
            val ams = amsIds.map { id -> async { runCatching { api.amsHistory(printerId, id, hours) }.getOrNull() } }
            val heaters = api.sensorHistory(printerId, hours).series
            val now = System.currentTimeMillis()
            ChartsData(heaters, ams.awaitAll().filterNotNull(), now - hours * 3_600_000L, now)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RANGES.forEach { (h, label) ->
                FilterChip(selected = h == hours, onClick = { onHours(h) }, label = { Text(stringResource(label)) })
            }
        }
        when (val s = loader.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> {
                val e = s.error
                val msg = if (e is ApiException && e.code == 403) context.getString(R.string.charts_needs_read) else e.friendlyMessage(context)
                ErrorView(msg) { loader.reload() }
            }
            is Load.Ok -> PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                val d = s.value
                val heaters = d.heaters.filter { series -> series.data.any { it.value != null } }
                val ams = d.ams.filter { h -> h.data.any { it.humidity != null || it.temperature != null } }
                if (heaters.isEmpty() && ams.isEmpty()) {
                    MessageView(Icons.Outlined.Timeline, stringResource(R.string.charts_empty), stringResource(R.string.charts_empty_body))
                } else {
                    Column(Modifier.readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        heaters.forEach { series -> HeaterCard(series, d.fromMs, d.toMs) }
                        ams.forEach { h -> AmsCard(h, d.fromMs, d.toMs) }
                        Text(
                            stringResource(R.string.charts_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaterCard(series: HeaterSeries, fromMs: Long, toMs: Long) {
    val title = stringResource(
        when (series.sensorKind) {
            "nozzle" -> R.string.nozzle
            "nozzle_2" -> R.string.charts_nozzle_2
            "bed" -> R.string.bed
            "chamber" -> R.string.charts_chamber
            else -> R.string.charts_sensor
        },
    )
    val color = when (series.sensorKind) {
        "bed" -> MaterialTheme.colorScheme.tertiary
        "chamber" -> Warning
        "nozzle_2" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val points = series.data.mapNotNull { p ->
        PrintersRepository.parseInstant(p.recordedAt)?.let { ChartPoint(it, p.value?.toFloat(), p.target?.toFloat()) }
    }
    Group(title) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Stats(series.minValue, series.avgValue, series.maxValue, "°")
            HistoryChart(points, fromMs, toMs, color, "°", stringResource(R.string.charts_description, title))
            if (points.any { (it.target ?: 0f) > 0f }) {
                Text(stringResource(R.string.charts_target_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AmsCard(h: AmsHistory, fromMs: Long, toMs: Long) {
    val title = stringResource(R.string.charts_ams, h.amsId + 1)
    val humidity = h.data.mapNotNull { p -> PrintersRepository.parseInstant(p.recordedAt)?.let { ChartPoint(it, p.humidity?.toFloat()) } }
    val temperature = h.data.mapNotNull { p -> PrintersRepository.parseInstant(p.recordedAt)?.let { ChartPoint(it, p.temperature?.toFloat()) } }
    Group(title) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (humidity.any { it.value != null }) {
                Text(stringResource(R.string.charts_humidity), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stats(h.minHumidity, h.avgHumidity, h.maxHumidity, "%")
                HistoryChart(humidity, fromMs, toMs, Color(0xFF5EB1E8), "%", stringResource(R.string.charts_description, title), startAtZero = false)
            }
            if (temperature.any { it.value != null }) {
                Text(stringResource(R.string.charts_ams_temperature), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stats(h.minTemperature, h.avgTemperature, h.maxTemperature, "°")
                HistoryChart(temperature, fromMs, toMs, Warning, "°", stringResource(R.string.charts_description, title), startAtZero = false)
            }
        }
    }
}

@Composable
private fun Stats(min: Double?, avg: Double?, max: Double?, unit: String) {
    fun v(x: Double) = if (unit == "%") "${x.toInt()}%" else "${x.toInt()}°"
    MetaRow(
        listOfNotNull(
            min?.let { stringResource(R.string.charts_min, v(it)) },
            avg?.let { stringResource(R.string.charts_avg, v(it)) },
            max?.let { stringResource(R.string.charts_max, v(it)) },
        ),
    )
}
