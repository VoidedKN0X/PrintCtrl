package com.vknxworks.printctrl.ui.settings

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
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.vknxworks.printctrl.MainActivity
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.settings.NotifyMode
import com.vknxworks.printctrl.data.settings.PrintEvent
import com.vknxworks.printctrl.notify.Notifier
import com.vknxworks.printctrl.notify.UnifiedPush
import com.vknxworks.printctrl.ui.components.ChoiceSheet
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupChoice
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.GroupSwitch
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.theme.Gutter
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
    var pickDistributor by remember { mutableStateOf(false) }
    // Re-read on return from Android's settings screens.
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val allowed = remember(resumes) { Notifier.canPost(context) }
    val liveUpdates = remember(resumes) { Notifier.liveUpdatesAllowed(context) }
    val unrestricted = remember(resumes) { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.notifications)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (!allowed) {
                Group {
                    GroupRow(
                        stringResource(R.string.notif_permission),
                        stringResource(R.string.notif_permission_sub),
                        Icons.Outlined.NotificationsOff,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = {
                            (context as? MainActivity)?.ensureNotificationPermission()
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        },
                    )
                }
            }

            Group(stringResource(R.string.notif_progress_group), footer = stringResource(R.string.notif_progress_footer)) {
                GroupSwitch(stringResource(R.string.notif_progress_switch), stringResource(R.string.notif_progress_sub), s.progressNotification) { v ->
                    if (v) (context as? MainActivity)?.ensureNotificationPermission()
                    update { it.copy(progressNotification = v) }
                }
                GroupSwitch(stringResource(R.string.first_layer_switch), stringResource(R.string.first_layer_sub), s.firstLayerCheck) { v ->
                    if (v) (context as? MainActivity)?.ensureNotificationPermission()
                    update { it.copy(firstLayerCheck = v) }
                }
                if (s.progressNotification && liveUpdates == false) {
                    GroupRow(
                        stringResource(R.string.live_update_off),
                        stringResource(R.string.live_update_off_sub),
                        Icons.Outlined.NotificationsOff,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        },
                    )
                }
            }

            Group(stringResource(R.string.notif_alerts), footer = stringResource(R.string.notif_mode_footer)) {
                GroupChoice(stringResource(R.string.notif_mode), NotifyMode.entries.map { it to stringResource(it.label) }, s.notifyMode) { mode ->
                    if (mode != NotifyMode.OFF) (context as? MainActivity)?.ensureNotificationPermission()
                    if (mode != NotifyMode.PUSH && s.notifyMode == NotifyMode.PUSH) UnifiedPush.unregister(context)
                    update { it.copy(notifyMode = mode) }
                    if (mode == NotifyMode.PUSH && push.endpoint == null) pickDistributor = true
                }
            }

            if (s.notifyMode == NotifyMode.PUSH) {
                Group(stringResource(R.string.push_title), footer = stringResource(R.string.push_footer)) {
                    val distributors = UnifiedPush.distributors(context)
                    GroupRow(
                        stringResource(R.string.push_distributor),
                        push.distributor?.let { UnifiedPush.appLabel(context, it) }
                            ?: stringResource(if (distributors.isEmpty()) R.string.push_no_distributor else R.string.push_pick_distributor),
                        showChevron = distributors.isNotEmpty(),
                        onClick = if (distributors.isEmpty()) {
                            { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/io.heckel.ntfy/"))) }
                        } else {
                            { pickDistributor = true }
                        },
                    )
                    val endpoint = push.endpoint
                    when {
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
                        push.error != null -> GroupRow(stringResource(R.string.push_failed), push.error, titleColor = MaterialTheme.colorScheme.error)
                        push.distributor != null -> GroupRow(stringResource(R.string.push_waiting))
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
                        GroupSwitch(stringResource(e.label), checked = e in s.events) { on ->
                            update { it.copy(events = if (on) it.events + e else it.events - e) }
                        }
                    }
                    if (c.servers.setup.value?.spoolman != null) {
                        GroupSwitch(
                            stringResource(R.string.event_low_stock),
                            stringResource(R.string.event_low_stock_sub, s.lowStockGrams),
                            s.lowStockAlerts,
                        ) { on -> update { it.copy(lowStockAlerts = on) } }
                    }
                }
            }

            Group(stringResource(R.string.battery_title), footer = stringResource(R.string.battery_footer)) {
                @SuppressLint("BatteryLife")
                val request = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
                GroupRow(
                    stringResource(if (unrestricted) R.string.battery_ok else R.string.battery_restricted),
                    stringResource(if (unrestricted) R.string.battery_ok_sub else R.string.battery_restricted_sub),
                    if (unrestricted) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                    iconTint = if (unrestricted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    onClick = if (unrestricted) null else request,
                )
                GroupRow(stringResource(R.string.battery_coloros), stringResource(R.string.battery_coloros_sub), onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
        }
    }

    if (pickDistributor) {
        val distributors = UnifiedPush.distributors(context)
        ChoiceSheet(
            stringResource(R.string.push_distributor),
            distributors.map { it to UnifiedPush.appLabel(context, it) },
            push.distributor,
            onDismiss = { pickDistributor = false },
        ) { pkg ->
            pickDistributor = false
            UnifiedPush.register(context, pkg)
        }
    }
}
