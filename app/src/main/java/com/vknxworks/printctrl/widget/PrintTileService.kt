package com.vknxworks.printctrl.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.printctrl.MainActivity
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.notify.Notifier
import com.vknxworks.printctrl.ui.components.phaseLabel

/** Quick Settings tile: the running print's progress at a glance; tap to open that printer. */
class PrintTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val printers = WidgetState.read(this)
        val p = printers.firstOrNull { it.printPhase.isActive } ?: printers.firstOrNull()
        tile.label = p?.name ?: getString(R.string.app_name)
        tile.subtitle = when {
            p == null -> getString(R.string.widget_empty_short)
            p.printPhase.isActive && p.progress != null -> getString(R.string.tile_progress, getString(phaseLabel(p.printPhase)), p.progress)
            else -> getString(phaseLabel(p.printPhase))
        }
        tile.state = if (p?.printPhase?.isActive == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // The printer page has the camera and the print controls: only after the phone is unlocked.
        if (isLocked) unlockAndRun { openApp() } else openApp()
    }

    private fun openApp() {
        val p = WidgetState.read(this).let { list -> list.firstOrNull { it.printPhase.isActive } ?: list.firstOrNull() }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            p?.let { putExtra(Notifier.EXTRA_OPEN_PRINTER, it.id) }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
