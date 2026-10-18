package com.vknxworks.printctrl.ui.spools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.vknxworks.printctrl.data.servers.ConnState
import com.vknxworks.printctrl.data.spoolman.Spool
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.TabTitle
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberPagedLoader
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlin.math.roundToInt

/** Spools tab: the Spoolman inventory, read-only for now. */
@Composable
fun SpoolsScreen() {
    val context = LocalContext.current
    val c = context.container
    val conn by c.connections.spoolman.collectAsStateWithLifecycle()
    val client by c.spoolman.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_spools))
        val api = client
        when {
            api != null -> SpoolList(api.conn.key) { offset, limit -> api.spools(offset, limit) }
            conn is ConnState.Unreachable -> ErrorView((conn as ConnState.Unreachable).error.friendlyMessage(context)) { c.connections.retry() }
            else -> LoadingView()
        }
    }
}

@Composable
private fun SpoolList(key: String, fetch: suspend (Int, Int) -> Pair<List<Spool>, Int>) {
    val context = LocalContext.current
    val list = rememberPagedLoader("spools:$key", pageSize = 50, fetch = fetch)
    when {
        list.items.isEmpty() && list.error != null -> ErrorView(list.error!!.friendlyMessage(context)) { list.refresh() }
        list.items.isEmpty() && list.endReached -> MessageView(Icons.Outlined.Inventory2, stringResource(R.string.spools_empty), stringResource(R.string.spools_empty_body))
        list.items.isEmpty() -> LoadingView()
        else -> PullToRefreshBox(isRefreshing = list.refreshing, onRefresh = { list.refresh() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(list.items, key = { _, s -> s.id }) { i, spool ->
                    if (i >= list.items.size - 10) LaunchedEffect(list.items.size) { list.loadMore() }
                    SpoolRow(spool)
                }
                if (list.loading && !list.refreshing) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                }
                list.error?.let { e ->
                    item(key = "error") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(e.friendlyMessage(context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { list.loadMore() }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpoolRow(spool: Spool) {
    val f = spool.filament
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(spool.colors.mapNotNull { parseHexColor(it) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOfNotNull(f.vendor?.name?.takeIf { it.isNotBlank() }, f.name?.takeIf { it.isNotBlank() }).joinToString(" ")
                        .ifEmpty { stringResource(R.string.spool_number, spool.id) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(
                    listOfNotNull(
                        f.material,
                        spool.remainingWeight?.let { stringResource(R.string.grams_left, it.roundToInt()) },
                        spool.location?.takeIf { it.isNotBlank() },
                        stringResource(R.string.spool_id, spool.id),
                    ),
                )
                spool.fractionLeft?.let { left ->
                    LinearProgressIndicator(
                        progress = { left },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

/** A round colour swatch; multi-colour filament shows its colours as stripes. */
@Composable
private fun Swatch(colors: List<Color>) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    ) {
        if (colors.isNotEmpty()) {
            Row(Modifier.fillMaxSize()) {
                colors.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
            }
        }
    }
}
