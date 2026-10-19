package com.vknxworks.printctrl.ui.spools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.data.spoolman.Spool
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import com.vknxworks.printctrl.ui.components.ChoiceSheet
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.SwitchAddressBar
import com.vknxworks.printctrl.ui.components.TabTitle
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.components.rememberPagedLoader
import com.vknxworks.printctrl.ui.nav.AddSpoolRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ScanRoute
import com.vknxworks.printctrl.ui.nav.SpoolRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class SpoolSort(val query: String, val label: Int) {
    MATERIAL("filament.material:asc,filament.name:asc", R.string.sort_material),
    LEAST_LEFT("remaining_weight:asc", R.string.sort_least_left),
    MOST_LEFT("remaining_weight:desc", R.string.sort_most_left),
    RECENT("last_used:desc", R.string.sort_recent),
    NEWEST("id:desc", R.string.sort_newest),
}

/** Spools tab: the Spoolman inventory with search, filters, live updates and a scanner for labels. */
@Composable
fun SpoolsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val client by c.spoolman.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_spools), trailing = {
            IconButton(onClick = { nav.navigate(ScanRoute) }) { Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.scan_label)) }
            IconButton(onClick = { nav.navigate(AddSpoolRoute) }, enabled = client != null) { Icon(Icons.Filled.Add, stringResource(R.string.add_spool)) }
        })
        val api = client
        when {
            api != null -> SpoolBrowser(api)
            else -> LoadingView()
        }
    }
}

@Composable
private fun SpoolBrowser(api: SpoolmanClient) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf<String?>(null) }
    var location by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SpoolSort.MATERIAL) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    // Typing settles for a moment before the list reloads.
    LaunchedEffect(query) {
        delay(350)
        applied = query.trim()
    }

    val facets = rememberLoader("spool-facets:${api.conn.key}") { api.materials() to api.locations() }
    val key = "spools:${api.conn.key}:$applied:$material:$location:${sort.name}:$archived"
    val list = rememberPagedLoader(key, pageSize = 50) { offset, limit ->
        api.spools(offset, limit, sort.query, archived, material, location, applied.ifEmpty { null })
    }

    // Live: a spool's weight changes while a print runs; replace it in place.
    val current by rememberUpdatedState(list)
    DisposableEffect(api) {
        val ws = api.watchSpools(
            onChange = { change ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val l = current
                    val i = l.items.indexOfFirst { it.id == change.spool.id }
                    when {
                        change.type == "deleted" && i >= 0 -> l.items.removeAt(i)
                        change.type == "updated" && i >= 0 -> l.items[i] = change.spool
                        change.type == "added" -> l.refresh()
                        else -> Unit
                    }
                }
            },
            onEnd = { },
        )
        onDispose { ws.cancel() }
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            query, { query = it },
            placeholder = { Text(stringResource(R.string.spools_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.padding(horizontal = Gutter).fillMaxWidth(),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = true, onClick = { pick = "sort" }, label = { Text(stringResource(sort.label)) })
            FilterChip(selected = material != null, onClick = { pick = "material" }, label = { Text(material ?: stringResource(R.string.material)) })
            FilterChip(selected = location != null, onClick = { pick = "location" }, label = { Text(location ?: stringResource(R.string.location)) })
            FilterChip(selected = archived, onClick = { archived = !archived }, label = { Text(stringResource(R.string.show_archived)) })
        }

        // Can't reach Spoolman and a second address is saved: offer it (never switched automatically).
        if (list.error != null) SwitchAddressBar(ServerKind.SPOOLMAN, Modifier.padding(bottom = 8.dp))
        when {
            list.items.isEmpty() && list.error != null -> ErrorView(list.error!!.friendlyMessage(context)) { list.refresh() }
            list.items.isEmpty() && list.endReached -> MessageView(
                Icons.Outlined.Inventory2,
                stringResource(if (applied.isEmpty() && material == null && location == null) R.string.spools_empty else R.string.spools_no_match),
                stringResource(if (applied.isEmpty() && material == null && location == null) R.string.spools_empty_body else R.string.spools_no_match_body),
            )
            list.items.isEmpty() -> LoadingView()
            else -> PullToRefreshBox(isRefreshing = list.refreshing, onRefresh = { list.refresh() }, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(list.items, key = { _, s -> s.id }) { i, spool ->
                        if (i >= list.items.size - 10) LaunchedEffect(list.items.size) { list.loadMore() }
                        SpoolRow(spool, settings.lowStockGrams) { nav.navigate(SpoolRoute(spool.id)) }
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

    val (materials, locations) = facets.value ?: (emptyList<String>() to emptyList())
    when (pick) {
        "sort" -> ChoiceSheet(stringResource(R.string.sort), SpoolSort.entries.map { it to stringResource(it.label) }, sort, onDismiss = { pick = null }) {
            sort = it
            pick = null
        }
        "material" -> ChoiceSheet(
            stringResource(R.string.material),
            listOf<String?>(null).plus(materials).map { it to (it ?: stringResource(R.string.all)) },
            material,
            onDismiss = { pick = null },
        ) {
            material = it
            pick = null
        }
        "location" -> ChoiceSheet(
            stringResource(R.string.location),
            listOf<String?>(null).plus(locations.filter { it.isNotBlank() }).map { it to (it ?: stringResource(R.string.all)) },
            location,
            onDismiss = { pick = null },
        ) {
            location = it
            pick = null
        }
    }
}

@Composable
fun SpoolRow(spool: Spool, lowStockGrams: Int, onClick: () -> Unit) {
    val f = spool.filament
    val left = spool.remainingWeight
    val low = left != null && left < lowStockGrams && !spool.archived
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(spool.colors.mapNotNull { parseHexColor(it) })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    f.displayName.ifEmpty { stringResource(R.string.spool_number, spool.id) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(
                    listOfNotNull(
                        f.material,
                        left?.let { stringResource(R.string.grams_left, it.roundToInt()) },
                        spool.location?.takeIf { it.isNotBlank() },
                        stringResource(R.string.spool_id, spool.id),
                    ),
                )
                spool.fractionLeft?.let { fr ->
                    LinearProgressIndicator(
                        progress = { fr },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = if (low) Warning else MaterialTheme.colorScheme.primary,
                        drawStopIndicator = {},
                    )
                }
                if (low) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = Warning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.low_stock), style = MaterialTheme.typography.labelMedium, color = Warning)
                    }
                }
            }
        }
    }
}

/** A round colour swatch; multi-colour filament shows its colours as stripes. */
@Composable
fun Swatch(colors: List<Color>) {
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
