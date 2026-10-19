package com.vknxworks.printctrl.ui.projects

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.bambuddy.ProjectBrief
import com.vknxworks.printctrl.data.bambuddy.projectCoverUrl
import com.vknxworks.printctrl.data.bambuddy.projects
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ProjectRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Status filter of the project list; [status] null shows every project. */
private enum class ProjectFilter(val status: String?, val label: Int) {
    ACTIVE("active", R.string.projects_filter_active),
    COMPLETED("completed", R.string.projects_filter_completed),
    ARCHIVED("archived", R.string.projects_filter_archived),
    ALL(null, R.string.projects_filter_all),
}

/** Bambuddy's projects: what each is for, how far along it is, and what's queued for it. Read-only. */
@Composable
fun ProjectsScreen() {
    val c = LocalContext.current.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.projects)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val api = client
            if (api == null) LoadingView() else ProjectList(api)
        }
    }
}

@Composable
private fun ProjectList(api: BambuddyClient) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val loader = rememberLoader("projects:${api.conn.key}") { api.projects() }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filter = ProjectFilter.entries[filterIndex]

    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
            ProjectFilter.entries.forEachIndexed { i, f ->
                SegmentedButton(
                    selected = i == filterIndex,
                    onClick = { filterIndex = i },
                    shape = SegmentedButtonDefaults.itemShape(i, ProjectFilter.entries.size),
                ) { Text(stringResource(f.label), maxLines = 1) }
            }
        }
        when (val s = loader.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(projectsError(context, s.error)) { loader.reload() }
            is Load.Ok -> if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.FolderSpecial, stringResource(R.string.projects_empty), stringResource(R.string.projects_empty_body))
            } else {
                val matching = s.value.filter { filter.status == null || it.status.equals(filter.status, ignoreCase = true) }
                val matchingIds = matching.map { it.id }.toSet()
                // Sub-projects are listed on their parent's page, unless the parent isn't in this list.
                val shown = matching.filter { it.parentId == null || it.parentId !in matchingIds }
                PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) {
                        MessageView(Icons.Outlined.FolderSpecial, stringResource(R.string.projects_empty), stringResource(R.string.projects_empty_filter))
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(shown, key = { it.id }) { p ->
                                ProjectCard(p, api, Modifier.padding(horizontal = Gutter)) { nav.navigate(ProjectRoute(p.id)) }
                            }
                            item(key = "note") {
                                Text(
                                    stringResource(R.string.projects_edit_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(p: ProjectBrief, api: BambuddyClient, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val accent = parseHexColor(p.color) ?: MaterialTheme.colorScheme.primary
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover = api.projectCoverUrl(p) ?: p.archives.firstOrNull { it.thumbnailPath != null }?.let { api.url("archives/${it.id}/thumbnail").build().toString() }
            if (cover != null) {
                Thumb(cover, Modifier.size(64.dp))
            } else {
                Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(accent))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(6.dp))
                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                p.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(stringResource(statusLabel(p.status)), priorityLabel(p.priority)?.let { stringResource(it) }).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(p.status, accent),
                )
                val target = p.targetCount
                if (target != null && target > 0) {
                    LinearProgressIndicator(
                        progress = { (p.completedCount.toFloat() / target).coerceIn(0f, 1f) },
                        color = accent,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(6.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
                MetaRow(
                    listOfNotNull(
                        if (target != null && target > 0) stringResource(R.string.project_progress_plates, p.completedCount, target)
                        else stringResource(R.string.project_done_count, p.completedCount),
                        p.failedCount.takeIf { it > 0 }?.let { stringResource(R.string.project_failed_count, it) },
                        p.queueCount.takeIf { it > 0 }?.let { stringResource(R.string.project_queued_count, it) },
                        p.childCount.takeIf { it > 0 }?.let { stringResource(R.string.project_sub_count, it) },
                        dueText(context, p.dueDate, p.status),
                    ),
                )
            }
        }
    }
}

/** The message for a failed load; a refused API key says which permission it needs. */
internal fun projectsError(context: Context, e: Throwable): String =
    if (e is ApiException && e.code == 403) context.getString(R.string.projects_needs_read) else e.friendlyMessage(context)

internal fun statusLabel(status: String): Int = when (status.lowercase()) {
    "completed" -> R.string.project_status_completed
    "archived" -> R.string.project_status_archived
    else -> R.string.project_status_active
}

internal fun priorityLabel(priority: String): Int? = when (priority.lowercase()) {
    "low" -> R.string.project_priority_low
    "high" -> R.string.project_priority_high
    "urgent" -> R.string.project_priority_urgent
    else -> null
}

@Composable
internal fun statusColor(status: String, accent: Color): Color = when (status.lowercase()) {
    "completed" -> MaterialTheme.colorScheme.tertiary
    "archived" -> MaterialTheme.colorScheme.outline
    else -> accent
}

/** "Due 30 Oct 2026", or "Overdue since …" for an active project past its date. */
internal fun dueText(context: Context, iso: String?, status: String): String? {
    val ms = iso?.let { PrintersRepository.parseInstant(it) } ?: return null
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val text = day.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    val overdue = status.equals("active", true) && day.isBefore(LocalDate.now(zone))
    return context.getString(if (overdue) R.string.project_overdue else R.string.project_due, text)
}

/** Whole-number percent for a 0–100 value. */
internal fun percent(value: Double?): Int? = value?.roundToInt()?.coerceIn(0, 100)
