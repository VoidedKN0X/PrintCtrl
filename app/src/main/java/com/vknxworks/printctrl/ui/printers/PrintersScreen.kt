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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import com.vknxworks.printctrl.data.bambuddy.PrinterStatus
import com.vknxworks.printctrl.ui.components.CtrlCard
import com.vknxworks.printctrl.ui.components.CtrlHeroCard
import com.vknxworks.printctrl.ui.components.CtrlProgressBar
import com.vknxworks.printctrl.ui.components.HeroNumber
import com.vknxworks.printctrl.ui.components.StatusTag
import com.vknxworks.printctrl.ui.components.phaseLabel
import com.vknxworks.printctrl.ui.theme.CtrlColors
import com.vknxworks.printctrl.ui.theme.CtrlType
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
        // One column on a phone; side by side on wide screens (landscape, tablets, unfolded foldables).
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 380.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                TabTitle(
                    stringResource(R.string.tab_printers),
                    label = stringResource(R.string.bambuddy) + " · " + pluralStringResource(R.plurals.printers_count, state.printers.size, state.printers.size),
                    trailing = { LiveBadge(state.live) },
                )
            }
            val error = state.error
            // Can't reach Bambuddy and a second address is saved: offer it (never switched automatically).
            if (error != null && state.loaded) item(key = "switch-address", span = { GridItemSpan(maxLineSpan) }) { SwitchAddressBar(ServerKind.BAMBUDDY) }
            when {
                !state.loaded -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(320.dp)) { LoadingView() }
                }
                state.printers.isEmpty() && error != null -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        ErrorView(error.friendlyMessage(context)) { refreshing = true }
                    }
                }
                state.printers.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(420.dp)) {
                        MessageView(Icons.Outlined.Print, stringResource(R.string.printers_empty), stringResource(R.string.printers_empty_body))
                    }
                }
                else -> {
                    if (error != null) item(key = "stale", span = { GridItemSpan(maxLineSpan) }) { StaleBanner(error.friendlyMessage(context)) }
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
    StatusTag(stringResource(R.string.live), CtrlColors.AccentText, Modifier.padding(end = 8.dp))
}

/** Shown above the cards when the last refresh failed, so old numbers aren't mistaken for live ones. */
@Composable
private fun StaleBanner(message: String) {
    CtrlCard(
        Modifier.padding(horizontal = Gutter).fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.printers_stale, message), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * One printer. While it prints (or is paused or preparing) it's the accent hero card with the
 * progress as a giant number; otherwise a plain surface card with its state at the end.
 */
@Composable
fun PrinterCard(p: LivePrinter, client: BambuddyClient?, onClick: () -> Unit) {
    val s = p.status
    val phase = s?.phase ?: PrintPhase.UNKNOWN
    val modifier = Modifier.padding(horizontal = Gutter).fillMaxWidth()
    if (s != null && phase.isActive) HeroPrinterCard(p, s, phase, client, modifier, onClick)
    else PlainPrinterCard(p, phase, client, modifier, onClick)
}

@Composable
private fun HeroPrinterCard(p: LivePrinter, s: PrinterStatus, phase: PrintPhase, client: BambuddyClient?, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val ink = CtrlColors.OnAccent
    CtrlHeroCard(
        label = listOfNotNull(p.name, p.printer.model).joinToString(" · "),
        trailingLabel = stringResource(phaseLabel(phase)),
        modifier = modifier,
        onClick = onClick,
    ) {
        val pct = (s.progress ?: 0.0).roundToInt().coerceIn(0, 100)
        Row(verticalAlignment = Alignment.Bottom) {
            HeroNumber(pct.toString(), "%", modifier = Modifier.weight(1f))
            val left = s.remainingTime
            if (left != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMinutes(context, left), style = CtrlType.Readout, color = ink, maxLines = 1)
                    if (left > 0) {
                        Text(
                            formatClock(context, System.currentTimeMillis() + left * 60_000L),
                            style = CtrlType.Data,
                            color = ink.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        CtrlProgressBar(
            progress = { pct / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = ink,
            trackColor = ink.copy(alpha = 0.18f),
        )
        if (s.jobName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(client, p, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.jobName.orEmpty(), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MetaRow(jobFacts(context, p).drop(if (s.progress != null) 1 else 0), color = ink.copy(alpha = 0.72f))
                }
            }
        }
        s.stgCurName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = CtrlType.Data, color = ink.copy(alpha = 0.72f))
        }
        if (s.connected) {
            MetaRow(
                listOf(
                    stringResource(R.string.nozzle_value, formatTemp(s.nozzle, s.nozzleTarget)),
                    stringResource(R.string.bed_value, formatTemp(s.bed, s.bedTarget)),
                ),
                color = ink,
            )
            val units = s.ams.orEmpty()
            // Filament colours stay the real filament colours.
            if (units.isNotEmpty()) AmsDots(units, s.trayNow, loadedColor = ink)
        }
        PrinterNotes(s, onAccent = true)
    }
}

@Composable
private fun PlainPrinterCard(p: LivePrinter, phase: PrintPhase, client: BambuddyClient?, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val s = p.status
    CtrlCard(modifier, onClick = onClick, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.printer.model?.let {
                    Text(it, style = CtrlType.Data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PhaseChip(phase)
        }

        if (s != null && (phase == PrintPhase.FINISHED || phase == PrintPhase.FAILED) && s.jobName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(client, p, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.jobName.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val progress = ((s.progress ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                    CtrlProgressBar(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    MetaRow(jobFacts(context, p))
                }
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
        if (s != null) PrinterNotes(s, onAccent = false)
    }
}

/** "Clear the plate" and HMS errors, under the rest of the card. */
@Composable
private fun PrinterNotes(s: PrinterStatus, onAccent: Boolean) {
    val ink = if (onAccent) CtrlColors.OnAccent else null
    if (s.awaitingPlateClear) {
        Text(stringResource(R.string.plate_waiting), style = MaterialTheme.typography.bodySmall, color = ink ?: MaterialTheme.colorScheme.primary)
    }
    val hms = s.hmsErrors.orEmpty()
    if (hms.isNotEmpty()) {
        val color = ink ?: MaterialTheme.colorScheme.error
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                hms.first().description?.takeIf { it.isNotBlank() } ?: pluralStringResource(R.plurals.hms_count, hms.size, hms.size),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (onAccent) FontWeight.SemiBold else null,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
        Box(Modifier.fillMaxSize().background(CtrlColors.Raised), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Print, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
