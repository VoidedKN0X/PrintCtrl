package com.vknxworks.printctrl.widget

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.printctrl.MainActivity
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.notify.Notifier
import com.vknxworks.printctrl.ui.components.phaseLabel

/**
 * Quick Settings tile: the running print's progress at a glance; tap to open that printer. While the app
 * lock is on and PrintCtrl is locked, it only says "Locked" and opens PrintCtrl behind its lock screen.
 */
class PrintTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val c = container
        // Away for longer than the lock timeout: lock first. While locked, no printer, job or progress.
        runCatching { c.lockIfExpired() }
        if (c.lockedNow) {
            tile.label = getString(R.string.app_name)
            tile.subtitle = getString(R.string.tile_locked)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
            return
        }
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
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }
}
