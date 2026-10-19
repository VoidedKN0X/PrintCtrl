package com.vknxworks.printctrl.ui.projects

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.Archive
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.BomItem
import com.vknxworks.printctrl.data.bambuddy.LibraryFile
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.bambuddy.Project
import com.vknxworks.printctrl.data.bambuddy.TimelineEvent
import com.vknxworks.printctrl.data.bambuddy.project
import com.vknxworks.printctrl.data.bambuddy.projectArchives
import com.vknxworks.printctrl.data.bambuddy.projectBom
import com.vknxworks.printctrl.data.bambuddy.projectCoverUrl
import com.vknxworks.printctrl.data.bambuddy.projectFileProgress
import com.vknxworks.printctrl.data.bambuddy.projectFiles
import com.vknxworks.printctrl.data.bambuddy.projectTimeline
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.formatClock
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.formatMoney
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.ArchiveDetailRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ProjectRoute
import com.vknxworks.printctrl.ui.nav.SendRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

private class ProjectData(
    val project: Project,
    val archives: List<Archive>,
    /** Library files linked to the project, with how often each was printed. */
    val files: List<Pair<LibraryFile, Int>>,
    val bom: List<BomItem>,
    val timeline: List<TimelineEvent>,
    val currency: String?,
)

private const val ARCHIVE_LIMIT = 100

/** One project: progress, totals, its files, prints, parts and what happened when. */
@Composable
fun ProjectScreen(id: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val api = client
    val loader = rememberLoader("project:$id:${api?.conn?.key}") {
        val a = api ?: error("Bambuddy isn't connected")
        coroutineScope {
            // The project itself must load; the rest is shown when it can be.
            val archives = async { runCatching { a.projectArchives(id, ARCHIVE_LIMIT) }.getOrDefault(emptyList()) }
            val files = async { runCatching { a.projectFiles(id) }.getOrDefault(emptyList()) }
            val progress = async { runCatching { a.projectFileProgress(id) }.getOrDefault(emptyList()) }
            val bom = async { runCatching { a.projectBom(id) }.getOrDefault(emptyList()) }
            val timeline = async { runCatching { a.projectTimeline(id) }.getOrDefault(emptyList()) }
            val currency = async { a.currency() }
            val project = a.project(id)
            val runs = progress.await().associate { it.fileId to it.completedCount }
            ProjectData(
                project,
                archives.await(),
                files.await().sortedBy { it.name.lowercase() }.map { it to (runs[it.id] ?: 0) },
                bom.await(),
                timeline.await(),
                currency.await(),
            )
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(loader.value?.project?.name ?: stringResource(R.string.projects)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = loader.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(projectsError(context, s.error)) { loader.reload() }
                is Load.Ok -> if (api != null) {
                    PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                        ProjectContent(s.value, api)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectContent(d: ProjectData, api: BambuddyClient) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val settings by context.container.settings.settings.collectAsStateWithLifecycle()
    val printers by context.container.printers.state.collectAsStateWithLifecycle()
    val currency = settings.currency.ifEmpty { null } ?: d.currency
    val p = d.project
    val accent = parseHexColor(p.color) ?: MaterialTheme.colorScheme.primary
    // Totals including sub-projects when there are any.
    val st = p.rollupStats ?: p.stats

    Column(Modifier.readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        api.projectCoverUrl(p)?.let {
            Thumb(it, Modifier.padding(horizontal = Gutter, vertical = 8.dp).fillMaxWidth().aspectRatio(1.6f), ContentScale.Crop)
        }
        Row(Modifier.padding(horizontal = Gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(
                    stringResource(statusLabel(p.status)),
                    priorityLabel(p.priority)?.let { stringResource(it) },
                    dueText(context, p.dueDate, p.status),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.titleSmall,
                color = statusColor(p.status, accent),
            )
        }
        p.parentName?.let { parent ->
            Text(
                stringResource(R.string.project_part_of, parent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = Gutter)
                    .clickable(enabled = p.parentId != null) { p.parentId?.let { nav.navigate(ProjectRoute(it)) } }
                    .padding(vertical = 4.dp),
            )
        }
        p.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp))
        }

        // Progress towards the targets, worked out by Bambuddy: with sub-projects, the totals of the whole
        // tree against the targets summed over it; otherwise the project's own. Prints count every run,
        // parts count completed pieces.
        val platesFrom = listOfNotNull(p.rollupStats, p.stats).firstOrNull { it.progressPercent != null }
        val partsFrom = listOfNotNull(p.rollupStats, p.stats).firstOrNull { it.partsProgressPercent != null }
        if (platesFrom != null || partsFrom != null) {
            Group(stringResource(R.string.project_progress)) {
                if (platesFrom != null) {
                    val pct = platesFrom.progressPercent ?: 0.0
                    ProgressRow(
                        stringResource(R.string.project_prints_target),
                        stringResource(R.string.project_progress_plates, platesFrom.totalArchives, targetOf(platesFrom.totalArchives, platesFrom.remainingPrints, pct)),
                        percent(pct) ?: 0,
                        accent,
                    )
                }
                if (partsFrom != null) {
                    val pct = partsFrom.partsProgressPercent ?: 0.0
                    ProgressRow(
                        stringResource(R.string.project_parts_target),
                        stringResource(R.string.project_progress_parts, partsFrom.completedPrints, targetOf(partsFrom.completedPrints, partsFrom.remainingParts, pct)),
                        percent(pct) ?: 0,
                        accent,
                    )
                }
            }
        }

        if (st != null) {
            Group(stringResource(R.string.project_overview)) {
                GroupRow(stringResource(R.string.project_done), st.completedPrints.toString())
                if (st.failedPrints > 0) GroupRow(stringResource(R.string.project_failed), st.failedPrints.toString())
                if (st.inProgressPrints > 0) GroupRow(stringResource(R.string.project_printing), st.inProgressPrints.toString())
                if (st.queuedPrints > 0) GroupRow(stringResource(R.string.project_queued), st.queuedPrints.toString())
                if (st.totalPrintTimeHours > 0) {
                    GroupRow(stringResource(R.string.project_print_time), formatMinutes(context, (st.totalPrintTimeHours * 60).roundToInt()))
                }
                if (st.totalFilamentGrams > 0) {
                    val g = st.totalFilamentGrams
                    GroupRow(stringResource(R.string.project_filament), if (g >= 1000) "%.2f kg".format(g / 1000) else stringResource(R.string.grams_value, g.roundToInt()))
                }
                if (st.estimatedCost > 0 || st.totalEnergyCost > 0) {
                    GroupRow(
                        stringResource(R.string.project_cost),
                        stringResource(R.string.project_cost_value, formatMoney(st.estimatedCost, currency), formatMoney(st.totalEnergyCost, currency)),
                    )
                }
                if (st.bomCost > 0) GroupRow(stringResource(R.string.project_parts_cost), formatMoney(st.bomCost, currency))
                p.budget?.takeIf { it > 0 }?.let { budget ->
                    val spent = st.estimatedCost + st.totalEnergyCost + st.bomCost
                    GroupRow(stringResource(R.string.project_budget), stringResource(R.string.project_budget_value, formatMoney(spent, currency), formatMoney(budget, currency)))
                }
            }
        }

        val notes = p.notes?.takeIf { it.isNotBlank() }
        val tags = p.tags?.takeIf { it.isNotBlank() }
        val link = p.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (notes != null || tags != null || link != null) {
            Group {
                notes?.let { GroupRow(stringResource(R.string.project_notes), it) }
                tags?.let { GroupRow(stringResource(R.string.project_tags), it.split(',').map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.joinToString("  ·  ")) }
                link?.let { url ->
                    GroupRow(stringResource(R.string.project_link), url, Icons.AutoMirrored.Outlined.OpenInNew, onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    })
                }
            }
        }

        if (p.children.isNotEmpty()) {
            Group(stringResource(R.string.project_sub_projects)) {
                p.children.forEach { child ->
                    val childAccent = parseHexColor(child.color) ?: MaterialTheme.colorScheme.primary
                    GroupRow(
                        child.name,
                        listOfNotNull(
                            stringResource(statusLabel(child.status)),
                            percent(child.progressPercent)?.let { "$it%" },
                            stringResource(R.string.project_done_count, child.completedPrints),
                        ).joinToString("  ·  "),
                        icon = Icons.Outlined.Circle,
                        iconTint = childAccent,
                        showChevron = true,
                        onClick = { nav.navigate(ProjectRoute(child.id)) },
                    )
                }
            }
        }

        if (d.files.isNotEmpty()) {
            Group(stringResource(R.string.project_files), footer = stringResource(R.string.project_files_footer)) {
                d.files.forEach { (f, runs) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate(SendRoute(f.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(api.libraryThumbnailUrl(f), Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            MetaRow(
                                listOfNotNull(
                                    f.fileType.uppercase().takeIf { it.isNotBlank() },
                                    f.printTimeSeconds?.let { formatMinutes(context, (it / 60.0).roundToInt()) },
                                    f.filamentUsedGrams?.let { stringResource(R.string.grams_value, it.roundToInt()) },
                                    runs.takeIf { it > 0 }?.let { stringResource(R.string.project_file_runs, it) },
                                    if (!f.printable && f.sliceable) stringResource(R.string.needs_slicing) else null,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (d.archives.isNotEmpty()) {
            Group(
                stringResource(R.string.project_prints),
                footer = if (d.archives.size >= ARCHIVE_LIMIT) stringResource(R.string.project_prints_more, ARCHIVE_LIMIT) else null,
            ) {
                d.archives.forEach { a ->
                    PrintRow(a, api, printers.printers.firstOrNull { it.id == a.printerId }?.name) { nav.navigate(ArchiveDetailRoute(a.id)) }
                }
            }
        }

        if (d.bom.isNotEmpty()) {
            Group(stringResource(R.string.project_bom)) {
                d.bom.forEach { b ->
                    val done = b.isComplete || b.quantityAcquired >= b.quantityNeeded
                    GroupRow(
                        b.name,
                        listOfNotNull(
                            stringResource(R.string.project_bom_qty, b.quantityAcquired, b.quantityNeeded),
                            b.unitPrice?.takeIf { it > 0 }?.let { stringResource(R.string.project_bom_each, formatMoney(it, currency)) },
                            b.archiveName,
                            b.remarks?.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  "),
                        icon = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        iconTint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = b.sourcingUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url ->
                            { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }; Unit }
                        },
                    )
                }
            }
        }

        if (d.timeline.isNotEmpty()) {
            Group(stringResource(R.string.project_timeline)) {
                d.timeline.take(30).forEach { e ->
                    GroupRow(
                        e.title.ifBlank { e.eventType.replace('_', ' ') },
                        listOfNotNull(
                            e.timestamp?.let { PrintersRepository.parseInstant(it) }?.let { formatClock(context, it) },
                            e.description?.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  "),
                    )
                }
            }
        }

        Text(
            stringResource(R.string.projects_edit_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ProgressRow(title: String, value: String, percent: Int, color: Color) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$value  ·  $percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = color,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(CircleShape),
            drawStopIndicator = {},
        )
    }
}

/** A print of the project: picture, name, result, date, time and printer. */
@Composable
private fun PrintRow(a: Archive, api: BambuddyClient, printerName: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val (label, color) = when (a.status.lowercase()) {
        "completed" -> R.string.outcome_success to MaterialTheme.colorScheme.tertiary
        "failed" -> R.string.outcome_failed to MaterialTheme.colorScheme.error
        "printing" -> R.string.project_printing to MaterialTheme.colorScheme.primary
        else -> R.string.outcome_cancelled to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(api.archiveThumbnailUrl(a), Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
            MetaRow(
                listOfNotNull(
                    (a.completedAt ?: a.startedAt ?: a.createdAt)?.let { PrintersRepository.parseInstant(it) }?.let { formatClock(context, it) },
                    (a.actualTimeSeconds ?: a.printTimeSeconds)?.let { formatMinutes(context, (it / 60.0).roundToInt()) },
                    a.filamentUsedGrams?.let { stringResource(R.string.grams_value, it.roundToInt()) },
                    printerName,
                ),
            )
        }
    }
}

/**
 * The target behind Bambuddy's numbers: done plus what's left while short of it, or worked back from the
 * percentage once reached (Bambuddy then reports nothing left). For a project with sub-projects this is
 * the targets summed over all of them, as Bambuddy uses.
 */
private fun targetOf(done: Int, remaining: Int?, percent: Double): Int = when {
    remaining != null && remaining > 0 -> done + remaining
    percent > 0 -> kotlin.math.round(done * 100 / percent).toInt().coerceAtLeast(1)
    else -> done.coerceAtLeast(1)
}
