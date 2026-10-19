package com.vknxworks.printctrl.ui.queue

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.PrintersRepository
import com.vknxworks.printctrl.data.bambuddy.QueueItem
import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.LocalBottomClearance
import com.vknxworks.printctrl.ui.components.MessageView
import com.vknxworks.printctrl.ui.components.MetaRow
import com.vknxworks.printctrl.ui.components.TabTitle
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.formatClock
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LibraryRoute
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Queue tab: Bambuddy's print queue. Reorder, start, schedule and remove jobs; add files from the library. */
@Composable
fun QueueScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val client by c.bambuddy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_queue), trailing = {
            IconButton(onClick = { nav.navigate(LibraryRoute) }, enabled = client != null) { Icon(Icons.Filled.Add, stringResource(R.string.queue_add)) }
        })
        val api = client
        if (api == null) LoadingView() else QueueList(api)
    }
}

@Composable
private fun QueueList(api: BambuddyClient) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("queue:${api.conn.key}") { api.queue() }
    var refreshing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<QueueItem?>(null) }
    var scheduling by remember { mutableStateOf<QueueItem?>(null) }
    var removing by remember { mutableStateOf<QueueItem?>(null) }
    var lowFilament by remember { mutableStateOf<QueueItem?>(null) }
    var starting by remember { mutableStateOf<QueueItem?>(null) }

    // The queue changes on its own (jobs start and finish), so it's refreshed while on screen.
    LaunchedEffect(loader) {
        while (true) {
            delay(20_000)
            loader.reload(silent = true)
        }
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            loader.reload(silent = true)
            delay(700)
            refreshing = false
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { loader.reload(silent = true) }
                .onFailure { Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
        }
    }

    fun start(item: QueueItem, anyway: Boolean = false) {
        scope.launch {
            try {
                api.startQueueItem(item.id, anyway)
                loader.reload(silent = true)
            } catch (e: ApiException) {
                if (e.code == 409 && !anyway) lowFilament = item
                else Toast.makeText(context, e.friendlyMessage(context), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.friendlyMessage(context), Toast.LENGTH_LONG).show()
            }
        }
    }

    when (val s = loader.state) {
        is Load.Loading -> LoadingView()
        is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
        is Load.Ok -> {
            val items = s.value
            val pending = items.filter { it.status == "pending" }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true }, modifier = Modifier.fillMaxSize()) {
                if (items.isEmpty()) {
                    MessageView(Icons.Outlined.Queue, stringResource(R.string.queue_empty), stringResource(R.string.queue_empty_body))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 24.dp + LocalBottomClearance.current),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            val index = pending.indexOf(item)
                            QueueRow(
                                item, api,
                                canUp = index > 0,
                                canDown = index >= 0 && index < pending.size - 1,
                                onUp = { act { api.reorderQueue(pending.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) }) } },
                                onDown = { act { api.reorderQueue(pending.map { it.id }.toMutableList().apply { add(index + 1, removeAt(index)) }) } },
                                onClick = { if (item.status == "pending") selected = item },
                            )
                        }
                    }
                }
            }

            selected?.let { item ->
                ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        if (item.manualStart) {
                            GroupRow(stringResource(R.string.queue_start_now), stringResource(R.string.queue_start_now_sub), Icons.Outlined.PlayArrow, onClick = {
                                selected = null
                                starting = item
                            })
                        }
                        val index = pending.indexOf(item)
                        if (index > 0) {
                            GroupRow(stringResource(R.string.queue_to_top), icon = Icons.Outlined.VerticalAlignTop, onClick = {
                                selected = null
                                act { api.reorderQueue(listOf(item.id) + pending.map { it.id }.filter { it != item.id }) }
                            })
                        }
                        GroupRow(
                            stringResource(R.string.queue_schedule),
                            item.scheduledTime?.let { t -> PrintersRepository.parseInstant(t)?.let { stringResource(R.string.queue_scheduled_for, formatClock(context, it)) } }
                                ?: stringResource(R.string.queue_asap),
                            Icons.Outlined.Schedule,
                            onClick = {
                                selected = null
                                scheduling = item
                            },
                        )
                        if (item.scheduledTime != null) {
                            GroupRow(stringResource(R.string.queue_clear_schedule), icon = Icons.Outlined.Schedule, onClick = {
                                selected = null
                                act { api.updateQueueItem(item.id, """{"scheduled_time":null}""") }
                            })
                        }
                        GroupRow(
                            stringResource(R.string.queue_remove),
                            icon = Icons.Outlined.Delete,
                            iconTint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = {
                                selected = null
                                removing = item
                            },
                        )
                    }
                }
            }
            scheduling?.let { item ->
                ScheduleDialog(onDismiss = { scheduling = null }) { epochMs ->
                    scheduling = null
                    val iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))
                    act { api.updateQueueItem(item.id, """{"scheduled_time":"$iso"}""") }
                }
            }
            removing?.let { item ->
                ConfirmDialog(
                    stringResource(R.string.queue_remove_title),
                    stringResource(R.string.queue_remove_body, item.name),
                    stringResource(R.string.remove),
                    onDismiss = { removing = null },
                ) {
                    removing = null
                    act { api.removeQueueItem(item.id) }
                }
            }
            starting?.let { item ->
                ConfirmDialog(
                    stringResource(R.string.queue_start_title, item.name),
                    stringResource(R.string.queue_start_body),
                    stringResource(R.string.queue_start_now),
                    onDismiss = { starting = null },
                    destructive = false,
                ) {
                    starting = null
                    start(item)
                }
            }
            lowFilament?.let { item ->
                ConfirmDialog(
                    stringResource(R.string.queue_low_filament_title),
                    stringResource(R.string.queue_low_filament_body),
                    stringResource(R.string.queue_start_anyway),
                    onDismiss = { lowFilament = null },
                ) {
                    lowFilament = null
                    start(item, anyway = true)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    api: BambuddyClient,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val printing = item.status == "printing"
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (printing) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(api.queueThumbnailUrl(item), Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val where = item.printerName ?: item.targetModel?.let { stringResource(R.string.queue_any_model, it) } ?: stringResource(R.string.queue_any_printer)
                val status = when {
                    printing -> stringResource(R.string.phase_printing)
                    item.manualStart -> stringResource(R.string.queue_waits_for_start)
                    item.scheduledTime != null -> PrintersRepository.parseInstant(item.scheduledTime)?.let { stringResource(R.string.queue_scheduled_for, formatClock(context, it)) }
                    else -> null
                }
                MetaRow(
                    listOfNotNull(
                        where,
                        item.printTimeSeconds?.let { formatMinutes(context, (it / 60.0).roundToInt()) },
                        item.filamentUsedGrams?.let { stringResource(R.string.grams_value, it.roundToInt()) },
                    ),
                )
                status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                item.waitingReason?.takeIf { it.isNotBlank() && !printing }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            if (!printing) {
                Column {
                    IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up)) }
                    IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down)) }
                }
            }
        }
    }
}

/** Picks a day, then a time, for a job to start. */
@Composable
fun ScheduleDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    var day by remember { mutableStateOf<LocalDate?>(null) }
    if (day == null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    } else {
        val now = LocalTime.now(zone)
        val state = rememberTimePickerState(initialHour = (now.hour + 1) % 24, initialMinute = 0, is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.queue_schedule)) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
            confirmButton = {
                TextButton(onClick = {
                    val at = day!!.atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    onPick(at)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
