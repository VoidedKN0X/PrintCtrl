package com.vknxworks.printctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.settings.ThemeChoice
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupChoice
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.GroupSwitch
import com.vknxworks.printctrl.ui.nav.LocalNavigator
import com.vknxworks.printctrl.ui.nav.NotificationsRoute
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.VisibilityOff
import android.widget.Toast
import androidx.compose.runtime.remember
import com.vknxworks.printctrl.data.lock.LockVault
import com.vknxworks.printctrl.data.settings.LockTimeout
import com.vknxworks.printctrl.ui.lock.AppLock
import com.vknxworks.printctrl.ui.lock.PinRequest
import com.vknxworks.printctrl.ui.lock.findActivity
import com.vknxworks.printctrl.ui.components.ScreenTopBar
import com.vknxworks.printctrl.ui.components.readableWidth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val fpsOptions = listOf(1, 2, 5, 10).map { it to pluralStringResource(R.plurals.camera_fps, it, it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.settings)) },
    ) { padding ->
        Column(Modifier.padding(padding).readableWidth().fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group(stringResource(R.string.settings_appearance)) {
                GroupChoice(
                    stringResource(R.string.settings_theme),
                    ThemeChoice.entries.map { it to stringResource(it.label) },
                    s.theme,
                ) { v -> update { it.copy(theme = v) } }
                GroupSwitch(stringResource(R.string.settings_wallpaper), stringResource(R.string.settings_wallpaper_sub), s.dynamicColor) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }
            Group {
                GroupRow(
                    stringResource(R.string.notifications),
                    stringResource(s.notifyMode.label),
                    Icons.Outlined.Notifications,
                    showChevron = true,
                    onClick = { nav.navigate(NotificationsRoute) },
                )
            }
            PrivacyAndSecurityGroup(s)
            Group(stringResource(R.string.tab_spools), footer = stringResource(R.string.settings_low_stock_footer)) {
                GroupChoice(
                    stringResource(R.string.settings_low_stock),
                    listOf(50, 100, 150, 200, 250, 300, 500).map { it to stringResource(R.string.grams_value, it) },
                    s.lowStockGrams,
                ) { v -> update { it.copy(lowStockGrams = v) } }
            }
            Group(stringResource(R.string.settings_costs), footer = stringResource(R.string.settings_currency_footer)) {
                val codes = listOf("EUR", "USD", "GBP", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "CAD", "AUD", "NZD", "JPY", "CNY", "INR", "BRL")
                GroupChoice(
                    stringResource(R.string.settings_currency),
                    listOf("" to stringResource(R.string.settings_currency_server)) + codes.map { code ->
                        val cur = java.util.Currency.getInstance(code)
                        code to "${cur.getDisplayName(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }} (${cur.getSymbol(java.util.Locale.getDefault())})"
                    },
                    s.currency,
                ) { v -> update { it.copy(currency = v) } }
            }
            Group(stringResource(R.string.settings_camera), footer = stringResource(R.string.settings_camera_footer)) {
                GroupChoice(stringResource(R.string.settings_camera_fps), fpsOptions, s.cameraFps) { v -> update { it.copy(cameraFps = v) } }
                GroupSwitch(stringResource(R.string.settings_keep_screen_on), checked = s.keepScreenOnCamera) { v ->
                    update { it.copy(keepScreenOnCamera = v) }
                }
            }
            BackupGroup()
        }
    }
}

/** Privacy and security: the shared Ctrl series app lock, then hiding PrintCtrl in screenshots. */
@Composable
private fun PrivacyAndSecurityGroup(s: AppSettings) {
    val context = LocalContext.current
    val c = context.container
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    val lockOn = c.vault.enabled
    Group(
        stringResource(R.string.group_privacy),
        footer = stringResource(if (lockOn && s.backgroundUse) R.string.app_lock_footer_background else R.string.app_lock_footer),
    ) {
        GroupSwitch(
            stringResource(R.string.app_lock),
            stringResource(R.string.app_lock_subtitle),
            checked = lockOn,
            icon = Icons.Outlined.Lock,
        ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
        if (lockOn) {
            GroupRow(
                stringResource(R.string.lock_change_pin),
                stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                icon = Icons.Outlined.Password,
                showChevron = true,
                onClick = { AppLock.request = PinRequest.CHANGE },
            )
            GroupSwitch(
                stringResource(R.string.lock_fingerprint),
                stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_subtitle else R.string.lock_fingerprint_unavailable),
                checked = c.vault.biometricEnabled,
                icon = Icons.Outlined.Fingerprint,
            ) { on ->
                if (!on) {
                    c.vault.disableBiometric()
                } else if (bioAvailable) {
                    val activity = context.findActivity()
                    val cipher = c.vault.biometricEnrolCipher()
                    if (activity == null || cipher == null) {
                        Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        AppLock.biometricPrompt(
                            activity,
                            context.getString(R.string.lock_fingerprint_enable_title),
                            context.getString(R.string.lock_fingerprint_enable_subtitle),
                            cipher,
                        ) { unlocked ->
                            if (!c.vault.finishBiometricEnrol(unlocked)) {
                                Toast.makeText(context, context.getString(R.string.lock_fingerprint_setup_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            GroupChoice(
                stringResource(R.string.lock_again),
                LockTimeout.entries.map { it to stringResource(it.label) },
                s.lockTimeout,
                Icons.Outlined.Timer,
            ) { t -> c.appScope.launch { c.settings.update { it.copy(lockTimeout = t) } } }
        }
        GroupSwitch(
            stringResource(R.string.hide_screenshots),
            stringResource(R.string.hide_screenshots_subtitle),
            checked = s.hideScreenshots,
            icon = Icons.Outlined.VisibilityOff,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideScreenshots = on) } } }
        GroupSwitch(
            stringResource(R.string.hide_recents),
            stringResource(R.string.hide_recents_subtitle),
            checked = s.hideRecents,
            icon = Icons.Outlined.Layers,
        ) { on -> c.appScope.launch { c.settings.update { it.copy(hideRecents = on) } } }
    }
}
