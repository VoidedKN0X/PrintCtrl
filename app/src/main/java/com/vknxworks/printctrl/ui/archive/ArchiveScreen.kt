package com.vknxworks.printctrl.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.vknxworks.printctrl.data.bambuddy.Archive
import com.vknxworks.printctrl.data.bambuddy.ArchiveStats
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.TabTitle
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.components.rememberPagedLoader
import com.vknxworks.printctrl.ui.nav.ArchiveDetailRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** How a past print ended, grouped the way people think about it. */
enum class Outcome(val label: Int) {
    SUCCESS(R.string.outcome_success),
    FAILED(R.string.outcome_failed),
    CANCELLED(R.string.outcome_cancelled),
    PRINTING(R.string.phase_printing);

    companion object {
        fun of(status: String): Outcome = when (status.lowercase()) {
            "completed" -> SUCCESS
            "failed" -> FAILED
            "printing" -> PRINTING
            else -> CANCELLED // aborted, cancelled, stopped
        }
    }
}

@Composable
fun outcomeColor(o: Outcome): Color = when (o) {
    Outcome.SUCCESS -> MaterialTheme.colorScheme.tertiary
    Outcome.FAILED -> MaterialTheme.colorScheme.error
    Outcome.CANCELLED -> Warning
    Outcome.PRINTING -> MaterialTheme.colorScheme.primary
}

/** Archive tab: every print Bambuddy recorded, searchable, plus simple statistics. */
@Composable
fun ArchiveScreen() {
    val c = LocalContext.current.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_archive))
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = Gutter).fillMaxWidth()) {
            SegmentedButton(tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.archive_history)) }
            SegmentedButton(tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.archive_stats)) }
        }
        val api = client
        when {
            api == null -> LoadingView()
            tab == 0 -> History(api)
            else -> Stats(api)
        }
    }
}

@Composable
private fun History(api: BambuddyClient) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val printers by context.container.printers.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf<Outcome?>(null) }
    var printerId by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(query) {
        delay(400)
        applied = query.trim()
    }
    // Bambuddy only filters by result inside a search, so the result filter is applied here.
    val key = "archive:${api.conn.key}:$applied:$printerId"
    val list = rememberPagedLoader<Archive>(key, pageSize = 40) { offset, limit ->
        val page = if (applied.length >= 2) api.searchArchives(applied, null, offset, limit) else api.archives(offset, limit, printerId)
        // No total from the server: a short page means the end.
        page to (if (page.size < limit) offset + page.size else Int.MAX_VALUE)
    }
    val shown = list.items.filter { a -> (outcome == null || Outcome.of(a.status) == outcome) && (printerId == null || a.printerId == printerId) }

    TextField(
        query, { query = it },
        placeholder = { Text(stringResource(R.string.archive_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp).fillMaxWidth(),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Outcome.SUCCESS, Outcome.FAILED, Outcome.CANCELLED).forEach { o ->
            FilterChip(selected = outcome == o, onClick = { outcome = if (outcome == o) null else o }, label = { Text(stringResource(o.label)) })
        }
        if (printers.printers.size > 1) {
            printers.printers.forEach { p ->
                FilterChip(selected = printerId == p.id, onClick = { printerId = if (printerId == p.id) null else p.id }, label = { Text(p.name) })
            }
        }
    }

    when {
        list.items.isEmpty() && list.error != null -> ErrorView(list.error!!.friendlyMessage(context)) { list.refresh() }
        list.items.isEmpty() && list.endReached -> MessageView(Icons.Outlined.History, stringResource(R.string.archive_empty), stringResource(R.string.archive_empty_body))
        list.items.isEmpty() -> LoadingView()
        else -> PullToRefreshBox(isRefreshing = list.refreshing, onRefresh = { list.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp + LocalBottomClearance.current),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(shown, key = { _, a -> a.id }) { i, a ->
                    if (i >= shown.size - 8) LaunchedEffect(list.items.size) { list.loadMore() }
                    ArchiveRow(a, api, printers.printers.firstOrNull { it.id == a.printerId }?.name) { nav.navigate(ArchiveDetailRoute(a.id)) }
                }
                // Filters can hide a whole page; keep loading so the list isn't empty while more exists.
                if (shown.isEmpty() && !list.endReached) item(key = "filler") { LaunchedEffect(list.items.size) { list.loadMore() } }
                if (list.loading && !list.refreshing) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveRow(a: Archive, api: BambuddyClient, printerName: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val outcome = Outcome.of(a.status)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(api.archiveThumbnailUrl(a), Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(outcome.label), style = MaterialTheme.typography.labelMedium, color = outcomeColor(outcome))
                MetaRow(
                    listOfNotNull(
                        (a.completedAt ?: a.startedAt ?: a.createdAt)?.let { formatDay(it) },
                        (a.actualTimeSeconds ?: a.printTimeSeconds)?.let { formatMinutes(context, (it / 60.0).roundToInt()) },
                        a.filamentUsedGrams?.let { stringResource(R.string.grams_value, it.roundToInt()) },
                        printerName,
                    ),
                )
            }
        }
    }
}

fun formatDay(iso: String): String? = PrintersRepository.parseInstant(iso)?.let {
    DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
}

/** Prints per week for the last eight weeks, and filament grams per material, counted from the archive. */
private class ArchiveScan(val weeks: List<Pair<LocalDate, Int>>, val gramsByMaterial: List<Pair<String, Double>>, val complete: Boolean)

/**
 * Goes through the archive (newest first, up to [MAX_SCAN] prints). Grams use what Bambuddy measured
 * when it has it, otherwise the slicer's estimate for completed prints; a print with more than one
 * material is listed under its combination ("PLA + PETG"), since its split isn't recorded.
 */
private suspend fun scan(api: BambuddyClient): ArchiveScan {
    val zone = ZoneId.systemDefault()
    val thisWeek = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val from = thisWeek.minusWeeks(7)
    val counts = IntArray(8)
    val grams = mutableMapOf<String, Double>()
    var offset = 0
    var complete = false
    while (offset < MAX_SCAN) {
        val page = api.archives(offset, 100)
        page.forEach { a ->
            val day = (a.startedAt ?: a.createdAt)?.let { PrintersRepository.parseInstant(it) }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            if (day != null && !day.isBefore(from)) {
                val week = ChronoUnit.WEEKS.between(from, day.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))).toInt()
                if (week in 0..7) counts[week]++
            }
            val g = a.totalFilamentActualGrams?.takeIf { it > 0 }
                ?: a.filamentUsedGrams?.takeIf { Outcome.of(a.status) == Outcome.SUCCESS }
            if (g != null) {
                val materials = a.filamentType.orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val key = materials.joinToString(" + ").ifEmpty { "?" }
                grams[key] = (grams[key] ?: 0.0) + g
            }
        }
        if (page.size < 100) {
            complete = true
            break
        }
        offset += 100
    }
    return ArchiveScan(
        (0..7).map { from.plusWeeks(it.toLong()) to counts[it] },
        grams.entries.sortedByDescending { it.value }.map { it.key to it.value },
        complete,
    )
}

private const val MAX_SCAN = 2000

@Composable
private fun Stats(api: BambuddyClient) {
    val context = LocalContext.current
    val loader = rememberLoader("stats:${api.conn.key}") { api.stats() to scan(api) }
    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> {
            val (stats, archiveScan) = s.value
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp + LocalBottomClearance.current)) {
                StatTiles(stats)
                Group(stringResource(R.string.stats_per_week)) { WeekBars(archiveScan.weeks) }
                if (archiveScan.gramsByMaterial.isNotEmpty()) {
                    Group(
                        stringResource(R.string.stats_grams_by_material),
                        footer = stringResource(if (archiveScan.complete) R.string.stats_grams_note else R.string.stats_grams_note_partial, MAX_SCAN),
                    ) {
                        val maxG = archiveScan.gramsByMaterial.maxOf { it.second }.coerceAtLeast(1.0)
                        archiveScan.gramsByMaterial.forEach { (material, g) ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row {
                                    Text(material, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text(
                                        if (g >= 1000) "%.2f kg".format(g / 1000) else "${g.roundToInt()} g",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (g / maxG).toFloat() },
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape),
                                    drawStopIndicator = {},
                                )
                            }
                        }
                    }
                }
                if (stats.printsByFilamentType.isNotEmpty()) {
                    Group(stringResource(R.string.stats_prints_by_material)) {
                        val max = stats.printsByFilamentType.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                        stats.printsByFilamentType.entries.sortedByDescending { it.value }.forEach { (material, count) ->
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row {
                                    Text(material.ifBlank { "?" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Text(context.resources.getQuantityString(R.plurals.prints_count, count, count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LinearProgressIndicator(progress = { count.toFloat() / max }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape), drawStopIndicator = {})
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.stats_material_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter + 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTiles(s: ArchiveStats) {
    val finished = s.successfulPrints + s.failedPrints + s.cancelledPrints
    val rate = if (finished > 0) (s.successfulPrints * 100.0 / finished).roundToInt() else null
    val tiles = listOf(
        stringResource(R.string.stats_prints) to s.totalPrints.toString(),
        stringResource(R.string.stats_success) to (rate?.let { "$it%" } ?: "–"),
        stringResource(R.string.stats_hours) to s.totalPrintTimeHours.roundToInt().toString(),
        stringResource(R.string.stats_filament) to if (s.totalFilamentGrams >= 1000) "%.1f kg".format(s.totalFilamentGrams / 1000) else "${s.totalFilamentGrams.roundToInt()} g",
    )
    Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(weeks: List<Pair<LocalDate, Int>>) {
    val max = weeks.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Row(Modifier.fillMaxWidth().height(150.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEach { (week, count) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth()
                        .height((80 * count / max).coerceAtLeast(2).dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(week.format(fmt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
