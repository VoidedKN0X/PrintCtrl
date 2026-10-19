package com.vknxworks.printctrl.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.InventorySpool
import com.vknxworks.printctrl.data.bambuddy.inventory
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.spools.Swatch
import kotlin.math.roundToInt

/**
 * Pick a spool from Bambuddy's inventory for an AMS slot; spools of the slot's [material] come first.
 * Used on the printer page when Spoolman isn't set up in PrintCtrl.
 */
@Composable
fun InventoryPickerSheet(api: BambuddyClient, slotTitle: String, material: String?, onDismiss: () -> Unit, onPick: (InventorySpool) -> Unit) {
    val context = LocalContext.current
    val loader = rememberLoader("pick-inventory:${api.conn.key}") { api.inventory(fresh = true).spools().filter { !it.archived } }
    var filter by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.pick_spool_title, slotTitle), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            OutlinedTextField(
                filter, { filter = it },
                placeholder = { Text(stringResource(R.string.spools_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            when (val st = loader.state) {
                is Load.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Load.Err -> Text(st.error.inventoryMessage(context, write = false), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                is Load.Ok -> {
                    val wanted = material?.trim()?.lowercase()
                    val list = st.value
                        .filter { s -> filter.isBlank() || "${s.displayName} ${s.storageLocation.orEmpty()} #${s.id}".contains(filter.trim(), ignoreCase = true) }
                        .sortedWith(compareBy({ s -> if (wanted != null && s.material.lowercase() == wanted) 0 else 1 }, { it.material.lowercase() }, { it.displayName.lowercase() }))
                    if (list.isEmpty()) Text(stringResource(R.string.spools_no_match), modifier = Modifier.padding(24.dp))
                    LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(list, key = { it.id }) { spool ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(spool) }.padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Swatch(spool.colors.mapNotNull { parseHexColor(it) })
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(spool.displayName.ifEmpty { stringResource(R.string.spool_number, spool.id) }, style = MaterialTheme.typography.bodyLarge)
                                    MetaRow(
                                        listOfNotNull(
                                            spool.material.takeIf { it.isNotBlank() },
                                            stringResource(R.string.grams_left, spool.remaining.roundToInt()),
                                            spool.storageLocation?.takeIf { it.isNotBlank() },
                                            stringResource(R.string.spool_id, spool.id),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
