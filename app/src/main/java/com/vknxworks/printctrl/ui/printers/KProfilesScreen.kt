package com.vknxworks.printctrl.ui.printers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.KProfile
import com.vknxworks.printctrl.data.bambuddy.kProfileNotes
import com.vknxworks.printctrl.data.bambuddy.kProfiles
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.theme.Gutter

private val NOZZLES = listOf("0.2", "0.4", "0.6", "0.8")

/**
 * The pressure advance (K-factor) calibrations stored on a printer, per nozzle size, grouped by nozzle
 * type. Read-only: Bambuddy lets only a logged-in admin change them, never an API key.
 */
@Composable
fun KProfilesScreen(printerId: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val live by remember(printerId) { c.printers.printer(printerId) }.collectAsStateWithLifecycle(null)
    var nozzle by rememberSaveable { mutableStateOf("0.4") }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.kprofiles_title)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            live?.name?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NOZZLES.forEach { d ->
                    FilterChip(selected = d == nozzle, onClick = { nozzle = d }, label = { Text(stringResource(R.string.mm_value, d)) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                val api = client
                if (api == null) LoadingView() else ProfileList(api, printerId, nozzle)
            }
        }
    }
}

@Composable
private fun ProfileList(api: BambuddyClient, printerId: Int, nozzle: String) {
    val context = LocalContext.current
    val loader = rememberLoader("kprofiles:${api.conn.key}:$printerId:$nozzle") {
        val profiles = api.kProfiles(printerId, nozzle).profiles
        val notes = runCatching { api.kProfileNotes(printerId) }.getOrDefault(emptyMap())
        profiles to notes
    }
    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> {
            val e = s.error
            val msg = if (e is ApiException && e.code == 403) context.getString(R.string.kprofiles_needs_read) else e.friendlyMessage(context)
            ErrorView(msg) { loader.reload() }
        }
        is Load.Ok -> PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload(silent = true) }, modifier = Modifier.fillMaxSize()) {
            val (profiles, notes) = s.value
            if (profiles.isEmpty()) {
                MessageView(Icons.Outlined.Tune, stringResource(R.string.kprofiles_empty), stringResource(R.string.kprofiles_empty_body, nozzle))
            } else {
                // One group per nozzle type (hardened steel, stainless…) and extruder, profiles by name.
                val groups = profiles.groupBy { it.extruderId to it.nozzleId }.toSortedMap(compareBy<Pair<Int, String>>({ it.first }, { it.second }))
                val dual = profiles.any { it.extruderId > 0 }
                LazyColumn(Modifier.fillMaxSize()) {
                    groups.forEach { (key, list) ->
                        item(key = "g:${key.first}:${key.second}") {
                            Group(nozzleTitle(key.second, if (dual) key.first else null)) {
                                list.sortedBy { it.name.lowercase() }.forEach { p -> ProfileRow(p, p.settingId?.let { notes[it] }) }
                            }
                        }
                    }
                    item(key = "footer") {
                        Text(
                            stringResource(R.string.kprofiles_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp).padding(bottom = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "HS00-0.4" → "Hardened steel nozzle"; extruder named on dual-nozzle printers. */
@Composable
private fun nozzleTitle(nozzleId: String, extruder: Int?): String {
    val type = when (nozzleId.take(2).uppercase()) {
        "HS" -> stringResource(R.string.nozzle_hardened)
        "HH" -> stringResource(R.string.nozzle_high_flow)
        "SS", "HT" -> stringResource(R.string.nozzle_stainless)
        else -> nozzleId.ifBlank { stringResource(R.string.nozzle) }
    }
    return if (extruder == null) type else stringResource(R.string.kprofiles_extruder, type, if (extruder == 0) stringResource(R.string.kprofiles_right) else stringResource(R.string.kprofiles_left))
}

@Composable
private fun ProfileRow(p: KProfile, note: String?) {
    val k = p.kValue.toDoubleOrNull()?.let { "%.3f".format(it) } ?: p.kValue
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name.ifBlank { p.filamentId }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.kprofiles_k, k), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        MetaRow(
            listOfNotNull(
                p.filamentId.takeIf { it.isNotBlank() },
                p.trayId.takeIf { it >= 0 }?.let { stringResource(R.string.kprofiles_slot, p.amsId + 1, it + 1) },
            ),
        )
        note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
