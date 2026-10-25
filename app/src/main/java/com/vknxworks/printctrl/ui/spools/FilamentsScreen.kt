package com.vknxworks.printctrl.ui.spools

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.spoolman.Filament
import com.vknxworks.printctrl.data.spoolman.FilamentInUseException
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import com.vknxworks.printctrl.data.spoolman.Vendor
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.CtrlAlertDialog
import com.vknxworks.printctrl.ui.components.CtrlTextField
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.readableWidth
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton

/**
 * Spoolman's filament types and manufacturers, to clean up the ones no longer needed. Deleting asks
 * first and can't be undone; a filament that spools still use can't be deleted at all (Spoolman refuses).
 */
@Composable
fun FilamentsScreen() {
    val c = LocalContext.current.container
    val client by c.spoolman.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.filaments_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().fillMaxSize()) {
            val api = client
            if (api == null) {
                LoadingView()
                return@Column
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.filaments_tab)) })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.vendors_tab)) })
            }
            if (tab == 0) FilamentList(api) else VendorList(api)
        }
    }
}

@Composable
private fun FilamentList(api: SpoolmanClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("manage-filaments:${api.conn.key}") { api.filaments() }
    var filter by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Filament?>(null) }
    var inUse by remember { mutableStateOf<Pair<Filament, Int?>?>(null) }

    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> Column(Modifier.fillMaxSize()) {
            CtrlTextField(
                filter, { filter = it },
                placeholder = { Text(stringResource(R.string.filter_filaments)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
            )
            val list = s.value.filter { filter.isBlank() || "${it.displayName} ${it.material}".contains(filter.trim(), ignoreCase = true) }
            if (s.value.isEmpty()) {
                MessageView(Icons.Outlined.Inventory2, stringResource(R.string.no_filaments), stringResource(R.string.no_filaments_body))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list, key = { it.id }) { f ->
                        ManageRow(
                            title = f.displayName.ifEmpty { "#${f.id}" },
                            facts = listOfNotNull(f.material, f.weight?.let { "${it.toInt()} g" }, stringResource(R.string.filament_id, f.id)),
                            colors = listOfNotNull(f.colorHex) + f.multiColorHexes?.split(',').orEmpty(),
                            deleteLabel = stringResource(R.string.filament_delete),
                        ) { deleting = f }
                    }
                }
            }
        }
    }

    deleting?.let { f ->
        val name = f.displayName.ifEmpty { "#${f.id}" }
        ConfirmDialog(
            stringResource(R.string.filament_delete_title),
            stringResource(R.string.filament_delete_body, name),
            stringResource(R.string.filament_delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            scope.launch {
                runCatching { api.deleteFilament(f.id) }
                    .onSuccess {
                        Toast.makeText(context, context.getString(R.string.filament_deleted, name), Toast.LENGTH_LONG).show()
                        loader.reload(silent = true)
                    }
                    .onFailure { e ->
                        if (e is FilamentInUseException) inUse = f to e.spools
                        else Toast.makeText(context, e.friendlyMessage(context), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
    inUse?.let { (f, count) ->
        val name = f.displayName.ifEmpty { "#${f.id}" }
        CtrlAlertDialog(
            onDismissRequest = { inUse = null },
            title = { Text(stringResource(R.string.filament_in_use_title)) },
            text = {
                Text(
                    if (count != null && count > 0) context.resources.getQuantityString(R.plurals.filament_in_use_body, count, name, count)
                    else stringResource(R.string.filament_in_use_body_unknown, name),
                )
            },
            confirmButton = { TextButton(onClick = { inUse = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun VendorList(api: SpoolmanClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("manage-vendors:${api.conn.key}") { api.vendors() }
    var deleting by remember { mutableStateOf<Vendor?>(null) }

    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> if (s.value.isEmpty()) {
            MessageView(Icons.Outlined.Inventory2, stringResource(R.string.no_vendors), stringResource(R.string.no_vendors_body))
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(s.value, key = { it.id }) { v ->
                    ManageRow(
                        title = v.name.ifEmpty { "#${v.id}" },
                        facts = listOfNotNull(v.emptySpoolWeight?.let { stringResource(R.string.vendor_empty_spool, it.toInt()) }, stringResource(R.string.filament_id, v.id)),
                        colors = null,
                        deleteLabel = stringResource(R.string.vendor_delete),
                    ) { deleting = v }
                }
            }
        }
    }

    deleting?.let { v ->
        val name = v.name.ifEmpty { "#${v.id}" }
        ConfirmDialog(
            stringResource(R.string.vendor_delete_title),
            stringResource(R.string.vendor_delete_body, name),
            stringResource(R.string.vendor_delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            scope.launch {
                runCatching { api.deleteVendor(v.id) }
                    .onSuccess {
                        Toast.makeText(context, context.getString(R.string.vendor_deleted, name), Toast.LENGTH_LONG).show()
                        loader.reload(silent = true)
                    }
                    .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
            }
        }
    }
}

@Composable
private fun ManageRow(title: String, facts: List<String>, colors: List<String>?, deleteLabel: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter - 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (colors != null) {
            Swatch(colors.mapNotNull { parseHexColor(it.trim()) })
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            MetaRow(facts)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, deleteLabel, tint = MaterialTheme.colorScheme.error) }
    }
}
