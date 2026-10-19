package com.vknxworks.printctrl.ui.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.inventory
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.nav.InventorySpoolRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.spools.Swatch
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Adds a spool to Bambuddy's inventory (or, when Bambuddy uses Spoolman, to Spoolman through Bambuddy). */
@Composable
fun AddInventorySpoolScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val client by c.bambuddy.collectAsStateWithLifecycle()
    var brand by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf("") }
    var subtype by rememberSaveable { mutableStateOf("") }
    var colorName by rememberSaveable { mutableStateOf("") }
    var hex by rememberSaveable { mutableStateOf("") }
    var full by rememberSaveable { mutableStateOf("1000") }
    var core by rememberSaveable { mutableStateOf("250") }
    var remaining by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }

    fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
    val cleanHex = hex.trim().removePrefix("#").uppercase()
    val hexOk = cleanHex.isEmpty() || (cleanHex.length == 6 && cleanHex.all { it.isDigit() || it in 'A'..'F' })
    val labelG = num(full)?.takeIf { it >= 1 && it <= 100_000 }
    val coreG = num(core)?.takeIf { it >= 0 && it <= 10_000 }
    val leftG = if (remaining.isBlank()) labelG else num(remaining)?.takeIf { labelG != null && it >= 0 && it <= labelG }
    val costV = if (cost.isBlank()) null else num(cost)
    val ok = !busy && client != null && material.isNotBlank() && hexOk && labelG != null && coreG != null && leftG != null && (cost.isBlank() || (costV != null && costV >= 0))

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.add_spool)) },
    ) { padding ->
        Column(
            Modifier.padding(padding).readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.inv_brand)) }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                material, { material = it },
                label = { Text(stringResource(R.string.material)) },
                supportingText = { if (material.isBlank()) Text(stringResource(R.string.inv_material_required)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                subtype, { subtype = it },
                label = { Text(stringResource(R.string.inv_subtype)) },
                placeholder = { Text(stringResource(R.string.inv_subtype_hint)) },
                singleLine = true,
                keyboardOptions = words,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(colorName, { colorName = it }, label = { Text(stringResource(R.string.inv_color_name)) }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    hex, { hex = it.take(7) },
                    label = { Text(stringResource(R.string.inv_color_hex)) },
                    prefix = { Text("#") },
                    isError = !hexOk,
                    supportingText = { if (!hexOk) Text(stringResource(R.string.inv_hex_invalid)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Swatch(listOfNotNull(if (hexOk && cleanHex.length == 6) parseHexColor(cleanHex) else null))
            }
            val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            OutlinedTextField(full, { full = it }, label = { Text(stringResource(R.string.inv_label_weight)) }, suffix = { Text("g") }, singleLine = true, isError = labelG == null, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(core, { core = it }, label = { Text(stringResource(R.string.inv_core_weight)) }, suffix = { Text("g") }, singleLine = true, isError = coreG == null, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                remaining, { remaining = it },
                label = { Text(stringResource(R.string.inv_remaining)) },
                placeholder = { Text(stringResource(R.string.inv_remaining_hint)) },
                suffix = { Text("g") },
                singleLine = true,
                isError = leftG == null,
                keyboardOptions = decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(cost, { cost = it }, label = { Text(stringResource(R.string.inv_cost_per_kg)) }, singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.location)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.inv_note)) }, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = ok,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                onClick = {
                    val api = client ?: return@Button
                    val lw = labelG ?: return@Button
                    val left = leftG ?: return@Button
                    busy = true
                    scope.launch {
                        runCatching {
                            api.inventory().create(
                                buildJsonObject {
                                    put("material", material.trim().take(50))
                                    if (brand.isNotBlank()) put("brand", brand.trim())
                                    if (subtype.isNotBlank()) put("subtype", subtype.trim())
                                    if (colorName.isNotBlank()) put("color_name", colorName.trim())
                                    if (cleanHex.length == 6) put("rgba", cleanHex + "FF")
                                    put("label_weight", lw.toInt())
                                    put("core_weight", (coreG ?: 250.0).toInt())
                                    put("weight_used", (lw - left).coerceAtLeast(0.0))
                                    costV?.let { put("cost_per_kg", it) }
                                    if (location.isNotBlank()) put("storage_location", location.trim().take(255))
                                    if (note.isNotBlank()) put("note", note.trim().take(1000))
                                },
                            )
                        }.onSuccess { spool ->
                            Toast.makeText(context, R.string.inv_added, Toast.LENGTH_SHORT).show()
                            nav.back()
                            nav.navigate(InventorySpoolRoute(spool.id))
                        }.onFailure {
                            Toast.makeText(context, it.inventoryMessage(context), Toast.LENGTH_LONG).show()
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.add_spool)) }
        }
    }
}
