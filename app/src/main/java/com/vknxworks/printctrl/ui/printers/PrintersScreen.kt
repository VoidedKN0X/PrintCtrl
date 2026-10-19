package com.vknxworks.printctrl.ui.printers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.SwitchAddressBar
import com.vknxworks.printctrl.ui.components.TabTitle
import com.vknxworks.printctrl.ui.components.formatClock
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.formatTemp
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.PrinterRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Printers tab: one big card per printer with its live state. */
@Composable
fun PrintersScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val state by c.printers.state.collectAsStateWithLifecycle()
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            c.printers.refresh()
            delay(900)
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "title") {
                TabTitle(stringResource(R.string.tab_printers), trailing = { LiveBadge(state.live) })
            }
            val error = state.error
            // Can't reach Bambuddy and a second address is saved: offer it (never switched automatically).
            if (error != null && state.loaded) item(key = "switch-address") { SwitchAddressBar(ServerKind.BAMBUDDY) }
            when {
                !state.loaded -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(320.dp)) { LoadingView() }
                }
                state.printers.isEmpty() && error != null -> item(key = "error") {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        ErrorView(error.friendlyMessage(context)) { refreshing = true }
                    }
                }
                state.printers.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        MessageView(Icons.Outlined.Print, stringResource(R.string.printers_empty), stringResource(R.string.printers_empty_body))
                    }
                }
                else -> {
                    if (error != null) item(key = "stale") { StaleBanner(error.friendlyMessage(context)) }
                    items(state.printers, key = { it.id }) { p ->
                        PrinterCard(p, client) { nav.navigate(PrinterRoute(p.id)) }
                    }
                }
            }
        }
    }
}

/** "Live" when updates arrive as they happen, nothing otherwise (the list is then refreshed every few seconds). */
@Composable
private fun LiveBadge(live: Boolean) {
    if (!live) return
    Row(
        Modifier.padding(end = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.live), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Shown above the cards when the last refresh failed, so old numbers aren't mistaken for live ones. */
@Composable
private fun StaleBanner(message: String) {
    Row(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.printers_stale, message), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PrinterCard(p: LivePrinter, client: BambuddyClient?, onClick: () -> Unit) {
    val context = LocalContext.current
    val s = p.status
    val phase = s?.phase ?: PrintPhase.UNKNOWN
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.printer.model?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PhaseChip(phase)
            }

            if (s != null && (phase.isActive || phase == PrintPhase.FINISHED || phase == PrintPhase.FAILED) && s.jobName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(client, p, Modifier.size(76.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.jobName.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val progress = ((s.progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            drawStopIndicator = {},
                        )
                        MetaRow(jobFacts(context, p))
                    }
                }
                s.stgCurName?.takeIf { it.isNotBlank() && phase.isActive }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (s != null && s.connected) {
                MetaRow(
                    listOf(
                        stringResource(R.string.nozzle_value, formatTemp(s.nozzle, s.nozzleTarget)),
                        stringResource(R.string.bed_value, formatTemp(s.bed, s.bedTarget)),
                    ),
                )
                val units = s.ams.orEmpty()
                if (units.isNotEmpty()) AmsDots(units, s.trayNow)
            }

            if (s?.awaitingPlateClear == true) {
                Text(stringResource(R.string.plate_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            val hms = s?.hmsErrors.orEmpty()
            if (hms.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        hms.first().description?.takeIf { it.isNotBlank() } ?: pluralStringResource(R.plurals.hms_count, hms.size, hms.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Progress, layers, time left and when it'll be done, as short facts. */
fun jobFacts(context: android.content.Context, p: LivePrinter): List<String> {
    val s = p.status ?: return emptyList()
    val facts = mutableListOf<String>()
    s.progress?.let { facts += context.getString(R.string.percent, it.roundToInt()) }
    val layer = s.layerNum
    val total = s.totalLayers
    if (layer != null && total != null && total > 0) facts += context.getString(R.string.layers_value, layer, total)
    val left = s.remainingTime
    if (left != null && s.phase.isActive) {
        facts += context.getString(R.string.time_left, formatMinutes(context, left))
        if (left > 0) facts += context.getString(R.string.eta_value, formatClock(context, System.currentTimeMillis() + left * 60_000L))
    }
    return facts
}

/** Picture of what's on the plate, from Bambuddy; a printer icon when there isn't one. */
@Composable
fun Cover(client: BambuddyClient?, p: LivePrinter, modifier: Modifier = Modifier) {
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Print, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Box(modifier.clip(MaterialTheme.shapes.medium)) {
        if (client == null) {
            fallback()
        } else {
            // The address stays the same between jobs; the job name in it makes a new job load a new picture.
            val url = client.coverUrl(p.id) + "&job=" + (p.status?.jobName?.hashCode() ?: 0)
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { fallback() },
                loading = { fallback() },
            )
        }
    }
}
