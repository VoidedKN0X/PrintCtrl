package com.vknxworks.printctrl

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vknxworks.printctrl.notify.Notifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.printctrl.ui.components.ScreenPrivacy
import com.vknxworks.printctrl.ui.lock.AppLockGate
import com.vknxworks.printctrl.ui.lock.lockCovering
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.ui.nav.AppRoot
import com.vknxworks.printctrl.ui.theme.PrintCtrlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A blank Recents card until the settings have been read ("Hide in recent apps" below).
        setRecentsScreenshotEnabled(false)
        // Bar icons follow the device theme (light icons when dark, dark icons when light).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val c = container
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // Ctrl / Signal follows the device's dark or light setting; the bar icons switch with it.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // "Hide in screenshots": FLAG_SECURE (also blanks the Recents preview). Always on, whatever the
            // switch says, while an API key or password is typed in, or the lock screen or a PIN screen shows.
            val secure = settings.hideScreenshots || ScreenPrivacy.forced > 0 || lockCovering(c.vault)
            // "Hide in recent apps": a blank Recents card; screenshots still work. Always with the app lock on,
            // so Recents never shows what's behind the lock.
            val blankRecents = settings.hideRecents || c.vault.enabled
            LaunchedEffect(secure, blankRecents) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                setRecentsScreenshotEnabled(!blankRecents)
            }
            PrintCtrlTheme(settings.theme, settings.dynamicColor, dark = dark) {
                // Nothing of the app is shown (or kept on screen) while the app lock is locked.
                AppLockGate { AppRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Opens the printer a notification, the widget or the tile pointed at. */
    private fun handle(intent: Intent?) {
        // A 3MF, G-code or STL shared from another app (or opened with PrintCtrl): offer to upload it.
        // Only content:// from another app: a file:// address could point PrintCtrl at its own private files.
        val shared = when (intent?.action) {
            Intent.ACTION_SEND -> intent?.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            Intent.ACTION_VIEW -> intent?.data
            else -> null
        }
        if (shared != null) {
            if (isForeignContentUri(shared)) container.sharedFile.value = shared
            return
        }
        val spool = intent?.getIntExtra(Notifier.EXTRA_OPEN_SPOOL, -1) ?: -1
        if (spool >= 0 || intent?.getBooleanExtra(Notifier.EXTRA_OPEN_SPOOLS, false) == true) {
            container.openSpoolRequest.value = spool
            intent?.removeExtra(Notifier.EXTRA_OPEN_SPOOL)
            intent?.removeExtra(Notifier.EXTRA_OPEN_SPOOLS)
            return
        }
        val id = intent?.getIntExtra(Notifier.EXTRA_OPEN_PRINTER, -1) ?: -1
        if (id < 0) return
        // "Stop the print?" straight away only for PrintCtrl's own notifications, not for other apps.
        val confirmStop = intent!!.getBooleanExtra(Notifier.EXTRA_CONFIRM_STOP, false) &&
            intent.getStringExtra(Notifier.EXTRA_INTERNAL) == Notifier.internalToken(this)
        container.openRequest.value = id to confirmStop
        intent.removeExtra(Notifier.EXTRA_OPEN_PRINTER)
        intent.removeExtra(Notifier.EXTRA_CONFIRM_STOP)
        intent.removeExtra(Notifier.EXTRA_INTERNAL)
    }

    /**
     * Only content:// from another app's provider: never file:// and never a provider that belongs to
     * PrintCtrl itself.
     */
    private fun isForeignContentUri(uri: android.net.Uri): Boolean {
        if (!uri.scheme.equals(android.content.ContentResolver.SCHEME_CONTENT, ignoreCase = true)) return false
        // "content://0@<authority>" still reaches <authority> (the number is a user id), so look past it.
        val authority = uri.authority?.substringAfterLast('@')?.takeIf { it.isNotBlank() } ?: return false
        if (authority.startsWith(packageName)) return false
        // Whatever its name, a provider that belongs to this app is never "another app's".
        val owner = runCatching {
            packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0))?.packageName
        }.getOrNull()
        return owner != packageName
    }
}
