package com.vknxworks.printctrl.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.ui.components.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Pause and resume from the progress notification. Not exported: only PrintCtrl's own notifications reach it. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val printerId = intent.getIntExtra(EXTRA_PRINTER, -1).takeIf { it >= 0 } ?: return
        val c = context.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                // Sealed with the app lock and PrintCtrl is locked: the API key can't be read until it's unlocked.
                if (c.backgroundBlocked()) return@launch
                val api = withTimeoutOrNull(10_000) {
                    var client = c.bambuddy.value
                    while (client == null) {
                        delay(200)
                        client = c.bambuddy.value
                    }
                    client
                } ?: return@launch
                runCatching {
                    when (intent.action) {
                        ACTION_PAUSE -> api.pause(printerId)
                        ACTION_RESUME -> api.resume(printerId)
                        else -> Unit
                    }
                }.onSuccess {
                    c.printers.refresh()
                }
                    .onFailure { e -> withContext(Dispatchers.Main) { Toast.makeText(context, e.friendlyMessage(context), Toast.LENGTH_LONG).show() } }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.vknxworks.printctrl.PAUSE"
        const val ACTION_RESUME = "com.vknxworks.printctrl.RESUME"
        private const val EXTRA_PRINTER = "printer"

        fun intent(context: Context, action: String, printerId: Int): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_PRINTER, printerId)
            return PendingIntent.getBroadcast(context, printerId * 4 + if (action == ACTION_PAUSE) 1 else 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
