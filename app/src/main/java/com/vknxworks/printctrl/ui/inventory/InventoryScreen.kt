package com.vknxworks.printctrl.ui.inventory

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
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
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.InventorySpool
import com.vknxworks.printctrl.data.bambuddy.inventory
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.ui.components.ChoiceSheet
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.TabTitle
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.AddInventorySpoolRoute
import com.vknxworks.printctrl.ui.nav.InventorySpoolRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.ServerSetupRoute
import com.vknxworks.printctrl.ui.spools.Swatch
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlin.math.roundToInt

/** A refused change names the API key permission it needs. */
fun Throwable.inventoryMessage(context: Context, write: Boolean = true): String =
    if (this is ApiException && code == 403) context.getString(if (write) R.string.inv_needs_manage else R.string.inv_needs_read)
    else friendlyMessage(context)

/**
 * Spools tab when Spoolman isn't set up in PrintCtrl: the spools Bambuddy keeps in its own inventory
 * (or, when Bambuddy itself uses Spoolman, the ones it passes on from there).
 */
@Composable
fun InventoryScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val client by c.bambuddy.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_spools), trailing = {
            IconButton(onClick = { nav.navigate(AddInventorySpoolRoute) }, enabled = client != null) { Icon(Icons.Filled.Add, stringResource(R.string.add_spool)) }
        })
        val api = client
        when {
            api != null -> InventoryBrowser(api)
            else -> LoadingView()
        }
    }
}

@Composable
private fun InventoryBrowser(api: BambuddyClient) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf<String?>(null) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var pickMaterial by rememberSaveable { mutableStateOf(false) }
    val loader = rememberLoader("inventory:${api.conn.key}:$archived") {
        val inv = api.inventory()
        inv.viaSpoolman to inv.spools(includeArchived = archived).filter { archived || !it.archived }
    }

    Column(Modifier.readableWidth().fillMaxSize()) {
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
            FilterChip(selected = material != null, onClick = { pickMaterial = true }, label = { Text(material ?: stringResource(R.string.material)) })
            FilterChip(selected = archived, onClick = { archived = !archived }, label = { Text(stringResource(R.string.show_archived)) })
        }

        when (val st = loader.state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(st.error.inventoryMessage(context, write = false)) { loader.reload() }
            is Load.Ok -> {
                val (viaSpoolman, all) = st.value
                val q = query.trim()
                val list = all
                    .filter { material == null || it.material.equals(material, ignoreCase = true) }
                    .filter { q.isEmpty() || "${it.displayName} ${it.storageLocation.orEmpty()} ${it.note.orEmpty()} #${it.id}".contains(q, ignoreCase = true) }
                    .sortedWith(compareBy({ it.material.lowercase() }, { it.displayName.lowercase() }))
                PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
                    if (list.isEmpty()) {
                        val filtered = q.isNotEmpty() || material != null
                        MessageView(
                            Icons.Outlined.Inventory2,
                            stringResource(if (filtered) R.string.spools_no_match else R.string.spools_empty),
                            stringResource(if (filtered) R.string.spools_no_match_body else R.string.inv_empty_body),
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (viaSpoolman) {
                                item(key = "via-spoolman") {
                                    Column {
                                        Text(
                                            stringResource(R.string.inv_via_spoolman),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(onClick = { nav.navigate(ServerSetupRoute(ServerKind.SPOOLMAN.name)) }) { Text(stringResource(R.string.inv_add_spoolman)) }
                                    }
                                }
                            }
                            items(list, key = { it.id }) { spool ->
                                InventoryRow(spool, settings.lowStockGrams) { nav.navigate(InventorySpoolRoute(spool.id)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickMaterial) {
        val materials = loader.value?.second.orEmpty().map { it.material }.filter { it.isNotBlank() }.distinctBy { it.uppercase() }.sorted()
        ChoiceSheet(
            stringResource(R.string.material),
            listOf<String?>(null).plus(materials).map { it to (it ?: stringResource(R.string.all)) },
            material,
            onDismiss = { pickMaterial = false },
        ) {
            material = it
            pickMaterial = false
        }
    }
}

@Composable
fun InventoryRow(spool: InventorySpool, lowStockGrams: Int, onClick: () -> Unit) {
    val left = spool.remaining
    val low = left < lowStockGrams && !spool.archived
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
                    spool.displayName.ifEmpty { stringResource(R.string.spool_number, spool.id) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(
                    listOfNotNull(
                        spool.material.takeIf { it.isNotBlank() },
                        stringResource(R.string.grams_left, left.roundToInt()),
                        spool.storageLocation?.takeIf { it.isNotBlank() },
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
                if (spool.archived) Text(stringResource(R.string.spool_archived), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
