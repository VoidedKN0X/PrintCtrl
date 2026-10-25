package com.vknxworks.printctrl.ui.settings

import com.vknxworks.printctrl.ui.lock.AppLock
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.settings.NotifyMode
import com.vknxworks.printctrl.data.settings.PrintEvent
import com.vknxworks.printctrl.notify.Notifier
import com.vknxworks.printctrl.notify.UnifiedPush
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupChoice
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.GroupSwitch
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.theme.Gutter
import com.vknxworks.printctrl.ui.components.readableWidth
import com.vknxworks.printctrl.ui.components.rememberNotificationAccess
import kotlinx.coroutines.launch

/** How PrintCtrl tells you about prints: the progress notification, alerts and how they arrive. */
@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val push by UnifiedPush.state.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    // Re-read on return from Android's settings screens.
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // Asks Android (or opens its notification settings when it won't ask any more); checked again on every return.
    val access = rememberNotificationAccess()
    val allowed = access.granted
    val liveUpdates = remember(resumes) { Notifier.liveUpdatesAllowed(context) }
    val unrestricted = remember(resumes) { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }
    // Checked again every time the screen comes back, e.g. after installing ntfy.
    val ntfyInstalled = remember(resumes) { UnifiedPush.ntfyInstalled(context) }
    // Push is on but not registered yet (ntfy was just installed, or PrintCtrl used another push app before).
    LaunchedEffect(s.notifyMode, ntfyInstalled) {
        if (s.notifyMode == NotifyMode.PUSH && ntfyInstalled && UnifiedPush.state.value.distributor == null) UnifiedPush.register(context)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.notifications)) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (!allowed) {
                Group {
                    GroupRow(
                        stringResource(R.string.notif_permission),
                        stringResource(R.string.notif_permission_sub),
                        Icons.Outlined.NotificationsOff,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { access.request() },
                        trailing = { TextButton(onClick = { access.request() }) { Text(stringResource(R.string.allow)) } },
                    )
                }
            }

            Group(stringResource(R.string.notif_progress_group), footer = stringResource(R.string.notif_progress_footer)) {
                // Never shown as on while Android blocks notifications: turning one on then asks for them first.
                GroupSwitch(stringResource(R.string.notif_progress_switch), stringResource(R.string.notif_progress_sub), s.progressNotification && allowed) { v ->
                    if (v && !allowed) access.request()
                    update { it.copy(progressNotification = v) }
                }
                GroupSwitch(stringResource(R.string.first_layer_switch), stringResource(R.string.first_layer_sub), s.firstLayerCheck && allowed) { v ->
                    if (v && !allowed) access.request()
                    update { it.copy(firstLayerCheck = v) }
                }
                if (s.progressNotification && liveUpdates == false) {
                    GroupRow(
                        stringResource(R.string.live_update_off),
                        stringResource(R.string.live_update_off_sub),
                        Icons.Outlined.NotificationsOff,
                        onClick = {
                            AppLock.expectReturn(); context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        },
                    )
                }
            }

            Group(stringResource(R.string.notif_alerts), footer = stringResource(R.string.notif_mode_footer)) {
                GroupChoice(stringResource(R.string.notif_mode), NotifyMode.entries.map { it to stringResource(it.label) }, s.notifyMode) { mode ->
                    if (mode != NotifyMode.OFF && !allowed) access.request()
                    if (mode != NotifyMode.PUSH && s.notifyMode == NotifyMode.PUSH) UnifiedPush.unregister(context)
                    update { it.copy(notifyMode = mode) }
                    if (mode == NotifyMode.PUSH && push.endpoint == null && ntfyInstalled) UnifiedPush.register(context)
                }
            }

            if (s.notifyMode == NotifyMode.PUSH) {
                Group(stringResource(R.string.push_title), footer = stringResource(R.string.push_footer)) {
                    if (!ntfyInstalled) {
                        GroupRow(
                            stringResource(R.string.push_install_ntfy),
                            stringResource(R.string.push_no_ntfy),
                            Icons.Outlined.Download,
                            onClick = { AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UnifiedPush.NTFY_DOWNLOAD))) },
                        )
                    }
                    val endpoint = push.endpoint
                    when {
                        !ntfyInstalled -> Unit
                        endpoint != null -> {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(stringResource(R.string.push_endpoint), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(
                                        endpoint,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small).padding(10.dp),
                                    )
                                }
                            }
                            GroupRow(stringResource(R.string.push_copy), icon = Icons.Outlined.ContentCopy, onClick = {
                                // Anyone with this address can send notifications to the phone: marked sensitive, so
                                // Android hides it from the clipboard preview and keyboard clipboard history.
                                val clip = ClipData.newPlainText("PrintCtrl", endpoint).apply {
                                    description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                                }
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            })
                        }
                        push.error != null -> GroupRow(
                            stringResource(R.string.push_failed),
                            stringResource(R.string.push_failed_sub, push.error.orEmpty()),
                            titleColor = MaterialTheme.colorScheme.error,
                            onClick = { UnifiedPush.register(context) },
                        )
                        push.distributor != null -> GroupRow(stringResource(R.string.push_waiting))
                        else -> GroupRow(stringResource(R.string.push_register), onClick = { UnifiedPush.register(context) })
                    }
                    if (ntfyInstalled && push.tempUnavailable) {
                        GroupRow(stringResource(R.string.push_temp_unavailable), stringResource(R.string.push_temp_unavailable_sub))
                    }
                }
                Text(
                    stringResource(R.string.push_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                )
            }

            if (s.notifyMode != NotifyMode.OFF) {
                Group(stringResource(R.string.notif_events)) {
                    PrintEvent.entries.forEach { e ->
                        GroupSwitch(stringResource(e.label), checked = e in s.events && allowed) { on ->
                            if (on && !allowed) access.request()
                            update { it.copy(events = if (on) it.events + e else it.events - e) }
                        }
                    }
                    GroupSwitch(stringResource(R.string.event_drying_done), stringResource(R.string.event_drying_done_sub), s.dryingDone && allowed) { on ->
                        if (on && !allowed) access.request()
                        update { it.copy(dryingDone = on) }
                    }
                    if (c.servers.setup.value?.let { it.spoolman != null || it.bambuddy != null } == true) {
                        GroupSwitch(
                            stringResource(R.string.event_low_stock),
                            stringResource(R.string.event_low_stock_sub, s.lowStockGrams),
                            s.lowStockAlerts && allowed,
                        ) { on ->
                            if (on && !allowed) access.request()
                            update { it.copy(lowStockAlerts = on) }
                        }
                    }
                }
            }

            Group(stringResource(R.string.battery_title), footer = stringResource(R.string.battery_footer)) {
                @SuppressLint("BatteryLife")
                val request = {
                    AppLock.expectReturn(); context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
                GroupRow(
                    stringResource(if (unrestricted) R.string.battery_ok else R.string.battery_restricted),
                    stringResource(if (unrestricted) R.string.battery_ok_sub else R.string.battery_restricted_sub),
                    if (unrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                    iconTint = if (unrestricted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    onClick = if (unrestricted) null else request,
                )
                GroupRow(stringResource(R.string.battery_coloros), stringResource(R.string.battery_coloros_sub), onClick = {
                    AppLock.expectReturn(); context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
        }
    }
}
