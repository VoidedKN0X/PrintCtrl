package com.vknxworks.printctrl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vknxworks.printctrl.notify.Notifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.printctrl.ui.components.ScreenPrivacy
import com.vknxworks.printctrl.ui.lock.AppLockGate
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.printctrl.data.settings.ThemeChoice
import com.vknxworks.printctrl.ui.nav.AppRoot
import com.vknxworks.printctrl.ui.theme.PrintCtrlTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Camera pictures, job names and the API key on the setup screen stay out of the recent-apps preview.
        setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        val c = container
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // FLAG_SECURE follows "Hide in screenshots and recent apps", and is always on while an API key
            // or password is typed in.
            val secure = settings.hideContent || ScreenPrivacy.forced > 0
            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            PrintCtrlTheme(settings.theme, settings.dynamicColor) {
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
            val fromOtherApp = shared.scheme == android.content.ContentResolver.SCHEME_CONTENT &&
                shared.authority?.startsWith(packageName) == false
            if (fromOtherApp) container.sharedFile.value = shared
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

    /** Asked when notifications are turned on in settings. */
    fun ensureNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
