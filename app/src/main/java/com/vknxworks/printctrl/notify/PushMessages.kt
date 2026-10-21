package com.vknxworks.printctrl.notify

import android.content.Context
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.net.ApiJson
import com.vknxworks.printctrl.data.settings.NotifyMode
import com.vknxworks.printctrl.data.settings.PrintEvent
import com.vknxworks.printctrl.widget.WidgetState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Messages from Bambuddy's generic webhook notification provider, delivered through UnifiedPush. The
 * body is Bambuddy's JSON: title, message, the event name and its variables, with the printer by name.
 * Anything else (a plain-text ntfy message, say) is still shown, with its text as it came.
 */
object PushMessages {
    suspend fun handle(context: Context, bytes: ByteArray) {
        val c = context.container
        // The stored settings: a push often starts PrintCtrl, before the settings have been read.
        val settings = c.settings.current()
        if (settings.notifyMode != NotifyMode.PUSH) return
        val text = bytes.toString(Charsets.UTF_8)
        val obj = runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        // Anyone who knows the push address can post to it, so what gets shown is kept short.
        fun str(key: String) = (obj?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.take(MAX_TEXT)

        val eventName = str("event")
        val title = str("title")
        val message = str("message") ?: if (obj == null) text.take(MAX_TEXT) else null
        val printerName = str("printer")
        val printerId = printerName?.let { name ->
            c.printers.state.value.printers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: WidgetState.read(context).firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
        }

        // Every message is also a hint to look at the printers now (progress notification, widget).
        PollWorker.now(context)
        if (eventName == "print_start") {
            PrintMonitorService.start(context)
            return
        }
        val event = eventName?.let { eventFor(it) }
        when {
            event != null -> if (event in settings.events) {
                Notifier.event(context, printerId ?: -1, printerName ?: title.orEmpty(), event, message ?: str("filename"))
            }
            // Bambuddy's test message, or any other event: show it as it came, so a setup can be checked.
            title != null || message != null -> Notifier.plain(context, printerId ?: -1, title ?: "Bambuddy", message.orEmpty())
        }
    }

    private const val MAX_TEXT = 500

    /** Bambuddy's event names and the toggle each falls under. */
    fun eventFor(name: String): PrintEvent? = when (name) {
        "print_complete" -> PrintEvent.FINISHED
        "print_failed", "print_stopped" -> PrintEvent.FAILED
        "printer_error" -> PrintEvent.HMS
        "ai_failure_detection" -> PrintEvent.AI
        "ams_humidity_high", "ams_temperature_high", "ams_drying_suspended", "ams_ht_humidity_high", "ams_ht_temperature_high",
        "print_missing_spool_assignment" -> PrintEvent.AMS
        "filament_low" -> PrintEvent.RUNOUT
        else -> null
    }
}
