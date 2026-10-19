package com.vknxworks.printctrl.ui.spools

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.spoolman.ExternalFilament
import com.vknxworks.printctrl.data.spoolman.Filament
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.PillButton
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.AddSpoolRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.SpoolRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Adds a spool: pick its filament (one you have, one from SpoolmanDB, or a new one), then the spool details. */
@Composable
fun AddSpoolScreen() {
    val c = LocalContext.current.container
    val client by c.spoolman.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<Filament?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.add_spool)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val api = client
            val f = chosen
            when {
                api == null -> LoadingView()
                f == null -> FilamentPicker(api) { chosen = it }
                else -> SpoolForm(api, f, onBack = { chosen = null })
            }
        }
    }
}

@Composable
private fun FilamentPicker(api: SpoolmanClient, onPick: (Filament) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
        Tab(tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.add_mine)) })
        Tab(tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.add_catalogue)) })
        Tab(tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.add_new)) })
    }
    when (tab) {
        0 -> MyFilaments(api, onPick)
        1 -> Catalogue(api, onPick)
        else -> NewFilament(api, onPick)
    }
}

@Composable
private fun MyFilaments(api: SpoolmanClient, onPick: (Filament) -> Unit) {
    val context = LocalContext.current
    val loader = rememberLoader("filaments:${api.conn.key}") { api.filaments() }
    var filter by remember { mutableStateOf("") }
    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> {
            val list = s.value.filter { filter.isBlank() || "${it.displayName} ${it.material}".contains(filter.trim(), ignoreCase = true) }
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    filter, { filter = it },
                    placeholder = { Text(stringResource(R.string.filter_filaments)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                )
                if (s.value.isEmpty()) {
                    MessageView(Icons.Outlined.Inventory2, stringResource(R.string.no_filaments), stringResource(R.string.no_filaments_body))
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(list, key = { it.id }) { f ->
                            FilamentRow(f.displayName.ifEmpty { "#${f.id}" }, listOfNotNull(f.material, f.weight?.let { "${it.toInt()} g" }), listOfNotNull(f.colorHex) + f.multiColorHexes?.split(',').orEmpty()) { onPick(f) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Catalogue(api: SpoolmanClient, onPick: (Filament) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ExternalFilament>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(400)
        runCatching { api.searchCatalogue(query.trim()) }
            .onSuccess { results = it; error = null }
            .onFailure { error = it.friendlyMessage(context) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text(stringResource(R.string.catalogue_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
        )
        Text(
            stringResource(R.string.catalogue_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Gutter),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Gutter)) }
        if (importing) LoadingView()
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results.orEmpty(), key = { it.id }) { e ->
                FilamentRow(
                    "${e.manufacturer} ${e.name}",
                    listOf(e.material, "${e.weight.toInt()} g", "${e.diameter} mm"),
                    listOfNotNull(e.colorHex) + e.colorHexes.orEmpty(),
                ) {
                    importing = true
                    scope.launch {
                        runCatching { api.importFilament(e) }
                            .onSuccess(onPick)
                            .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                        importing = false
                    }
                }
            }
        }
    }
}

@Composable
private fun NewFilament(api: SpoolmanClient, onPick: (Filament) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vendor by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf("PLA") }
    var color by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("1000") }
    var spoolWeight by rememberSaveable { mutableStateOf("") }
    var density by rememberSaveable { mutableStateOf("1.24") }
    var diameter by rememberSaveable { mutableStateOf("1.75") }
    var busy by remember { mutableStateOf(false) }
    val colorOk = color.isBlank() || parseHexColor(color) != null
    val dens = density.replace(',', '.').toDoubleOrNull()
    val dia = diameter.replace(',', '.').toDoubleOrNull()
    val ok = name.isNotBlank() && dens != null && dens > 0 && dia != null && dia > 0 && colorOk && !busy

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(vendor, { vendor = it }, label = { Text(stringResource(R.string.vendor)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.filament_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(material, { material = it }, label = { Text(stringResource(R.string.material)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            color, { color = it },
            label = { Text(stringResource(R.string.color_hex)) },
            placeholder = { Text("FF6A00") },
            isError = !colorOk,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(weight, { weight = it }, label = { Text(stringResource(R.string.net_weight)) }, suffix = { Text("g") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(spoolWeight, { spoolWeight = it }, label = { Text(stringResource(R.string.empty_spool_weight)) }, suffix = { Text("g") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(density, { density = it }, label = { Text(stringResource(R.string.density)) }, suffix = { Text("g/cm³") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            OutlinedTextField(diameter, { diameter = it }, label = { Text(stringResource(R.string.diameter)) }, suffix = { Text("mm") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        }
        PillButton(stringResource(R.string.next), enabled = ok, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true
            scope.launch {
                runCatching {
                    val vendorId = vendor.trim().takeIf { it.isNotEmpty() }?.let { v ->
                        (api.vendors().firstOrNull { it.name.equals(v, ignoreCase = true) } ?: api.createVendor(buildJsonObject { put("name", v.take(64)) })).id
                    }
                    api.createFilament(buildJsonObject {
                        put("name", name.trim().take(64))
                        vendorId?.let { put("vendor_id", it) }
                        material.trim().takeIf { it.isNotEmpty() }?.let { put("material", it.take(64)) }
                        put("density", dens!!)
                        put("diameter", dia!!)
                        weight.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { put("weight", it) }
                        spoolWeight.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { put("spool_weight", it) }
                        color.trim().removePrefix("#").takeIf { it.isNotEmpty() }?.let { put("color_hex", it.uppercase()) }
                    })
                }.onSuccess(onPick).onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                busy = false
            }
        })
    }
}

/** Spool details for the chosen filament, then create it. */
@Composable
private fun SpoolForm(api: SpoolmanClient, filament: Filament, onBack: () -> Unit) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var initial by rememberSaveable { mutableStateOf(filament.weight?.toInt()?.toString().orEmpty()) }
    var remaining by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var lot by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val initialG = initial.replace(',', '.').toDoubleOrNull()
    val remainingG = remaining.replace(',', '.').toDoubleOrNull()
    val ok = !busy && (remaining.isBlank() || (remainingG != null && remainingG >= 0 && (initialG == null || remainingG <= initialG)))

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(listOfNotNull(parseHexColor(filament.colorHex)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(filament.displayName.ifEmpty { "#${filament.id}" }, style = MaterialTheme.typography.titleMedium)
                MetaRow(listOfNotNull(filament.material))
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.change)) }
        }
        OutlinedTextField(initial, { initial = it }, label = { Text(stringResource(R.string.net_weight)) }, suffix = { Text("g") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            remaining, { remaining = it },
            label = { Text(stringResource(R.string.remaining_optional)) },
            supportingText = { Text(stringResource(R.string.remaining_help)) },
            suffix = { Text("g") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lot, { lot = it }, label = { Text(stringResource(R.string.lot)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        PillButton(stringResource(R.string.add_spool), enabled = ok, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true
            scope.launch {
                runCatching {
                    api.createSpool(buildJsonObject {
                        put("filament_id", filament.id)
                        initialG?.takeIf { it > 0 }?.let { put("initial_weight", it) }
                        remainingG?.let { put("remaining_weight", it) }
                        location.trim().takeIf { it.isNotEmpty() }?.let { put("location", it.take(64)) }
                        lot.trim().takeIf { it.isNotEmpty() }?.let { put("lot_nr", it.take(64)) }
                    })
                }.onSuccess { spool ->
                    context.container.telemetry.spoolAction("add")
                    nav.nav.navigate(SpoolRoute(spool.id)) { popUpTo<AddSpoolRoute> { inclusive = true } }
                }.onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                busy = false
            }
        })
    }
}

@Composable
private fun FilamentRow(title: String, facts: List<String>, colors: List<String>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(colors.mapNotNull { parseHexColor(it.trim()) })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            MetaRow(facts)
        }
    }
}
