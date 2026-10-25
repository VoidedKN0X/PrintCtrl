package com.vknxworks.printctrl.ui.archive

import android.net.Uri
import android.widget.Toast
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.bambuddy.ArchiveChanges
import com.vknxworks.printctrl.data.bambuddy.ArchiveDeleteImpact
import com.vknxworks.printctrl.data.bambuddy.archiveDeleteImpact
import com.vknxworks.printctrl.data.bambuddy.deleteArchive
import com.vknxworks.printctrl.data.bambuddy.parseTags
import com.vknxworks.printctrl.data.bambuddy.toggleFavorite
import com.vknxworks.printctrl.data.net.await
import com.vknxworks.printctrl.ui.components.ErrorView
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Load
import com.vknxworks.printctrl.ui.components.LoadingView
import com.vknxworks.printctrl.ui.components.RoundAction
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.components.formatMinutes
import com.vknxworks.printctrl.ui.components.formatMoney
import com.vknxworks.printctrl.ui.components.friendlyMessage
import com.vknxworks.printctrl.ui.components.rememberLoader
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.SendRoute
import com.vknxworks.printctrl.ui.nav.TimelapseRoute
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import com.vknxworks.printctrl.data.net.UserMessageException

/** One past print: how it went, what it used, and reprinting it. */
@Composable
fun ArchiveDetailScreen(id: Int) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val printers by c.printers.state.collectAsStateWithLifecycle()
    val api = client
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleteImpact by remember { mutableStateOf<ArchiveDeleteImpact?>(null) }
    val loader = rememberLoader("archive:$id:${api?.conn?.key}") {
        val a = api ?: throw UserMessageException(R.string.err_bambuddy_not_connected)
        a.archiveDetail(id) to a.currency()
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(loader.value?.first?.name ?: stringResource(R.string.tab_archive)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = loader.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { loader.reload() }
                is Load.Ok -> if (api != null) {
                    val (a, serverCurrency) = s.value
                    val chosen by c.settings.settings.collectAsStateWithLifecycle()
                    val currency = chosen.currency.ifEmpty { null } ?: serverCurrency
                    val outcome = Outcome.of(a.status)
                    Column(Modifier.readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        Thumb(api.archiveThumbnailUrl(a), Modifier.padding(horizontal = Gutter, vertical = 8.dp).fillMaxWidth().aspectRatio(1.4f), ContentScale.Fit)
                        Text(
                            stringResource(outcome.label),
                            style = MaterialTheme.typography.titleMedium,
                            color = outcomeColor(outcome),
                            modifier = Modifier.padding(horizontal = Gutter),
                        )
                        a.failureReason?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Gutter))
                        }
                        // Five actions don't fit a narrow phone side by side, so the row scrolls.
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter - 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RoundAction(Icons.Outlined.Replay, stringResource(R.string.reprint), onClick = { nav.navigate(SendRoute(a.id, archive = true)) })
                            if (a.timelapsePath != null) {
                                RoundAction(Icons.Outlined.Movie, stringResource(R.string.timelapse), onClick = { nav.navigate(TimelapseRoute(a.id)) })
                            }
                            RoundAction(
                                if (a.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                stringResource(if (a.isFavorite) R.string.archive_unfavorite else R.string.archive_favorite),
                                active = a.isFavorite,
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        runCatching { api.toggleFavorite(a.id) }
                                            .onSuccess { loader.reload(silent = true); ArchiveChanges.changed() }
                                            .onFailure { if (it !is kotlin.coroutines.cancellation.CancellationException) Toast.makeText(context, it.actionMessage(context, R.string.perm_manage_archives), Toast.LENGTH_LONG).show() }
                                        busy = false
                                    }
                                },
                            )
                            RoundAction(Icons.Outlined.Edit, stringResource(R.string.archive_edit), onClick = { editing = true })
                            RoundAction(
                                Icons.Outlined.Delete,
                                stringResource(R.string.archive_delete),
                                enabled = !busy,
                                danger = true,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        // Ask Bambuddy first what goes with it, so the question can say so.
                                        runCatching { api.archiveDeleteImpact(a.id) }
                                            .onSuccess { deleteImpact = it }
                                            .onFailure { if (it !is kotlin.coroutines.cancellation.CancellationException) Toast.makeText(context, it.friendlyMessage(context), Toast.LENGTH_LONG).show() }
                                        busy = false
                                    }
                                },
                            )
                        }
                        if (editing) {
                            EditArchiveDialog(api, a, onDismiss = { editing = false }) {
                                editing = false
                                loader.reload(silent = true)
                                ArchiveChanges.changed()
                            }
                        }
                        deleteImpact?.let { impact ->
                            DeleteArchiveDialog(a.name, impact, busy, onDismiss = { deleteImpact = null }) { purge ->
                                busy = true
                                scope.launch {
                                    runCatching { api.deleteArchive(a.id, purge) }
                                        .onSuccess {
                                            deleteImpact = null
                                            ArchiveChanges.changed()
                                            Toast.makeText(context, R.string.archive_deleted, Toast.LENGTH_SHORT).show()
                                            nav.back()
                                        }
                                        .onFailure {
                                            deleteImpact = null
                                            Toast.makeText(context, it.actionMessage(context, R.string.perm_manage_archives), Toast.LENGTH_LONG).show()
                                        }
                                    busy = false
                                }
                            }
                        }
                        Group(stringResource(R.string.archive_details)) {
                            printers.printers.firstOrNull { it.id == a.printerId }?.let { GroupRow(stringResource(R.string.printer), it.name) }
                            a.startedAt?.let { formatDay(it) }?.let { GroupRow(stringResource(R.string.archive_started), it) }
                            a.actualTimeSeconds?.let { GroupRow(stringResource(R.string.archive_took), formatMinutes(context, (it / 60.0).roundToInt())) }
                            a.printTimeSeconds?.let { GroupRow(stringResource(R.string.archive_estimate), formatMinutes(context, (it / 60.0).roundToInt())) }
                            a.filamentUsedGrams?.let { g ->
                                GroupRow(stringResource(R.string.filament), listOfNotNull(stringResource(R.string.grams_value, g.roundToInt()), a.filamentType).joinToString("  ·  "))
                            }
                            val costs = listOfNotNull(
                                a.cost?.takeIf { it > 0 }?.let { stringResource(R.string.cost_filament, formatMoney(it, currency)) },
                                a.energyCost?.takeIf { it > 0 }?.let { e ->
                                    stringResource(R.string.cost_energy, formatMoney(e, currency)) + (a.energyKwh?.takeIf { it > 0 }?.let { " (%.2f kWh)".format(it) } ?: "")
                                },
                            )
                            if (costs.isNotEmpty()) GroupRow(stringResource(R.string.archive_cost), costs.joinToString("  ·  "))
                            a.totalLayers?.let { GroupRow(stringResource(R.string.archive_layers), it.toString()) }
                            a.layerHeight?.let { GroupRow(stringResource(R.string.archive_layer_height), stringResource(R.string.mm_value, it.toString())) }
                            if (a.runCount > 1) GroupRow(stringResource(R.string.archive_runs), a.runCount.toString())
                            a.notes?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.notes), it) }
                            parseTags(a.tags).takeIf { it.isNotEmpty() }?.let { GroupRow(stringResource(R.string.archive_tags), it.joinToString("  ·  ")) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plays a print's timelapse. It's downloaded through the app's own connection first (with the API key
 * and any trusted certificate), since the phone's video player can't use either.
 */
@Composable
fun TimelapseScreen(archiveId: Int) {
    val context = LocalContext.current
    val c = context.container
    val client by c.bambuddy.collectAsStateWithLifecycle()
    val api = client
    val file = rememberLoader("timelapse:$archiveId:${api?.conn?.key}") {
        val a = api ?: throw UserMessageException(R.string.err_bambuddy_not_connected)
        val url = a.url("archives/$archiveId/timelapse").build()
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, "timelapse-$archiveId.mp4")
            if (!out.exists() || out.length() == 0L) {
                a.conn.client.newCall(Request.Builder().url(url).build()).await().use { r ->
                    if (!r.isSuccessful) throw com.vknxworks.printctrl.data.net.ApiException(r.code, null)
                    val body = r.body ?: throw UserMessageException(R.string.err_empty_answer)
                    val tmp = File(context.cacheDir, "timelapse-$archiveId.part")
                    tmp.outputStream().use { body.byteStream().copyTo(it) }
                    tmp.renameTo(out)
                }
            }
            out
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.timelapse)) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            when (val s = file.state) {
                is Load.Loading -> LoadingView()
                is Load.Err -> ErrorView(s.error.friendlyMessage(context)) { file.reload() }
                is Load.Ok -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(Uri.fromFile(s.value))
                            setOnPreparedListener { it.isLooping = true; start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
