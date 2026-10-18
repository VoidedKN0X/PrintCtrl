package com.vknxworks.printctrl.ui.spools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.spoolman.Spool
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.RoundAction
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Warning
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.parseHexColor
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.AssignSlotRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * One spool: what's left, and the deliberate changes PrintCtrl can make. Filament use during prints is
 * tracked by Bambuddy; nothing here ever deducts filament by itself.
 */
@Composable
fun SpoolDetailScreen(spoolId: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.spoolman.collectAsStateWithLifecycle()
    val api = client
    val loader = rememberLoader("spool:$spoolId:${api?.conn?.key}") { (api ?: error("Spoolman isn't connected")).spool(spoolId) }

    // Live: weight changes while a print runs show up straight away.
    DisposableEffect(api) {
        val ws = api?.watchSpools(
            onChange = { change -> if (change.spool.id == spoolId && change.type == "updated") loader.reload(silent = true) },
            onEnd = { },
        )
        onDispose { ws?.cancel() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(loader.value?.let { it.filament.displayName.ifEmpty { stringResource(R.string.spool_number, it.id) } } ?: stringResource(R.string.spool)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = loader.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
                is Load.Ok -> if (api != null) SpoolDetail(s.value, api, onChanged = { loader.reload(silent = true) })
            }
        }
    }
}

@Composable
private fun SpoolDetail(spool: Spool, api: SpoolmanClient, onChanged: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val bambuddy by c.bambuddy.collectAsStateWithLifecycle()
    var weigh by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var linkTag by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    val f = spool.filament

    fun run(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { onChanged() }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(horizontal = Gutter, vertical = 12.dp)) {
            BigSwatch(spool.colors.mapNotNull { parseHexColor(it) })
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val left = spool.remainingWeight
                Text(
                    if (left != null) stringResource(R.string.grams_left, left.roundToInt()) else stringResource(R.string.spool_unknown_left),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (left != null && left < settings.lowStockGrams) Warning else MaterialTheme.colorScheme.onSurface,
                )
                spool.fractionLeft?.let { fr ->
                    LinearProgressIndicator(progress = { fr }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {})
                }
                MetaRow(
                    listOfNotNull(
                        spool.initialWeight?.let { stringResource(R.string.spool_of_grams, it.roundToInt()) },
                        stringResource(R.string.grams_used, spool.usedWeight.roundToInt()),
                        spool.remainingLength?.let { stringResource(R.string.metres_left, (it / 1000).roundToInt()) },
                    ),
                )
                if (spool.archived) Text(stringResource(R.string.spool_archived), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        FlowRow(Modifier.padding(horizontal = Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundAction(Icons.Outlined.Scale, stringResource(R.string.spool_weigh), onClick = { weigh = true })
            RoundAction(Icons.Outlined.Tune, stringResource(R.string.spool_adjust), onClick = { adjust = true })
            RoundAction(Icons.Outlined.Edit, stringResource(R.string.edit), onClick = { edit = true })
            if (bambuddy != null) {
                RoundAction(Icons.Outlined.TableRows, stringResource(R.string.spool_assign), onClick = { nav.navigate(AssignSlotRoute(spool.id)) })
            }
            if (nfcAvailable(context)) {
                RoundAction(Icons.Outlined.Nfc, stringResource(R.string.spool_link_tag), onClick = { linkTag = true })
            }
            RoundAction(
                if (spool.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                stringResource(if (spool.archived) R.string.spool_unarchive else R.string.spool_archive),
                onClick = { confirmArchive = true },
            )
        }

        Group(stringResource(R.string.filament)) {
            GroupRow(stringResource(R.string.spool_filament), f.displayName.ifEmpty { "–" })
            f.material?.let { GroupRow(stringResource(R.string.material), it) }
            GroupRow(stringResource(R.string.diameter), stringResource(R.string.mm_value, f.diameter.toString()))
            if (f.settingsExtruderTemp != null || f.settingsBedTemp != null) {
                GroupRow(
                    stringResource(R.string.spool_temps),
                    listOfNotNull(
                        f.settingsExtruderTemp?.let { stringResource(R.string.nozzle_value, "$it°") },
                        f.settingsBedTemp?.let { stringResource(R.string.bed_value, "$it°") },
                    ).joinToString("  ·  "),
                )
            }
            f.articleNumber?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.article_number), it) }
        }
        Group(stringResource(R.string.spool)) {
            GroupRow(stringResource(R.string.spool_id_label), stringResource(R.string.spool_id, spool.id))
            GroupRow(stringResource(R.string.location), spool.location?.takeIf { it.isNotBlank() } ?: stringResource(R.string.none))
            spool.lotNr?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.lot), it) }
            spool.tare?.let { GroupRow(stringResource(R.string.empty_spool_weight), stringResource(R.string.grams_value, it.roundToInt())) }
            if (spool.tags.isNotEmpty()) GroupRow(stringResource(R.string.nfc_tags), spool.tags.joinToString(", ") { it.uid })
            spool.comment?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.comment), it) }
        }
    }

    if (weigh) WeighDialog(spool, onDismiss = { weigh = false }) { gross ->
        weigh = false
        run { api.measure(spool.id, gross) }
    }
    if (adjust) AdjustDialog(onDismiss = { adjust = false }) { grams ->
        adjust = false
        run { api.use(spool.id, grams) }
    }
    if (edit) EditSpoolDialog(spool, onDismiss = { edit = false }) { location, lot, comment ->
        edit = false
        run {
            api.updateSpool(spool.id, buildJsonObject {
                if (location.isBlank()) put("location", JsonNull) else put("location", location.trim().take(64))
                if (lot.isBlank()) put("lot_nr", JsonNull) else put("lot_nr", lot.trim().take(64))
                if (comment.isBlank()) put("comment", JsonNull) else put("comment", comment.trim().take(1024))
            })
        }
    }
    if (linkTag) LinkTagDialog(onDismiss = { linkTag = false }) { uid ->
        linkTag = false
        run { api.linkTag(spool.id, uid) }
    }
    if (confirmArchive) {
        ConfirmDialog(
            stringResource(if (spool.archived) R.string.spool_unarchive_title else R.string.spool_archive_title),
            stringResource(if (spool.archived) R.string.spool_unarchive_body else R.string.spool_archive_body),
            stringResource(if (spool.archived) R.string.spool_unarchive else R.string.spool_archive),
            destructive = false,
            onDismiss = { confirmArchive = false },
        ) {
            confirmArchive = false
            run { api.updateSpool(spool.id, buildJsonObject { put("archived", !spool.archived) }) }
        }
    }
}

@Composable
fun BigSwatch(colors: List<Color>, size: androidx.compose.ui.unit.Dp = 76.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    ) {
        if (colors.isNotEmpty()) Row(Modifier.fillMaxSize()) { colors.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) } }
    }
}

/**
 * Weigh-in: the user puts the spool on a scale and enters the total. Spoolman subtracts the empty-spool
 * weight and sets what's left. Shows the result before anything is sent.
 */
@Composable
private fun WeighDialog(spool: Spool, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val gross = text.replace(',', '.').toDoubleOrNull()
    val tare = spool.tare ?: 0.0
    val start = spool.initialWeight ?: spool.filament.weight
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weigh_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.weigh_body))
                OutlinedTextField(
                    text, { text = it },
                    label = { Text(stringResource(R.string.weigh_field)) },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    stringResource(R.string.weigh_tare, tare.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    start == null -> Text(stringResource(R.string.weigh_no_initial), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    gross != null -> {
                        val left = (gross - tare).coerceAtLeast(0.0)
                        Text(
                            if (left > start) stringResource(R.string.weigh_result_more, left.roundToInt())
                            else stringResource(R.string.weigh_result, left.roundToInt()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { gross?.let(onConfirm) }, enabled = gross != null && gross > 0 && start != null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Manual correction: remove filament (used outside Bambuddy, a failed print) or add it back. */
@Composable
private fun AdjustDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    var giveBack by remember { mutableStateOf(false) }
    val grams = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.adjust_body))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !giveBack, onClick = { giveBack = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.adjust_remove))
                    }
                    SegmentedButton(selected = giveBack, onClick = { giveBack = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.adjust_add))
                    }
                }
                OutlinedTextField(
                    text, { text = it },
                    label = { Text(stringResource(R.string.adjust_field)) },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { grams?.let { onConfirm(if (giveBack) -it else it) } },
                enabled = grams != null && grams > 0,
            ) { Text(stringResource(if (giveBack) R.string.adjust_add_confirm else R.string.adjust_remove_confirm, (grams ?: 0.0).roundToInt())) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EditSpoolDialog(spool: Spool, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var location by remember { mutableStateOf(spool.location.orEmpty()) }
    var lot by remember { mutableStateOf(spool.lotNr.orEmpty()) }
    var comment by remember { mutableStateOf(spool.comment.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_spool)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.location)) }, singleLine = true)
                OutlinedTextField(lot, { lot = it }, label = { Text(stringResource(R.string.lot)) }, singleLine = true)
                OutlinedTextField(comment, { comment = it }, label = { Text(stringResource(R.string.comment)) })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(location, lot, comment) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Waits for an NFC tag, then asks before linking its UID to the spool. */
@Composable
private fun LinkTagDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var uid by remember { mutableStateOf<String?>(null) }
    NfcReader(enabled = uid == null) { read -> uid = read.uid }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_tag_title)) },
        text = { Text(uid?.let { stringResource(R.string.link_tag_found, it) } ?: stringResource(R.string.link_tag_body)) },
        confirmButton = {
            TextButton(onClick = { uid?.let(onConfirm) }, enabled = uid != null) { Text(stringResource(R.string.link_tag_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
