package com.vknxworks.printctrl.notify

import android.content.Context
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.HmsError
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.bambuddy.PrinterStatus
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.settings.NotifyMode
import com.vknxworks.printctrl.data.settings.PrintEvent

/**
 * Turns successive printer states into events (finished, failed, paused, runout, errors) by comparing
 * each state with the last one seen, which is kept on disk so a check after the app was closed still
 * knows what came before. The first state ever seen for a printer only sets the starting point.
 */
class EventDetector(private val context: Context) {
    private val prefs = context.getSharedPreferences("print_events", Context.MODE_PRIVATE)

    private data class Seen(val phase: PrintPhase, val hms: Set<String>, val job: String?)

    private fun read(id: Int): Seen? {
        val raw = prefs.getString("p$id", null) ?: return null
        val parts = raw.split('\u0001')
        val phase = PrintPhase.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        val hms = parts.getOrNull(1)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        return Seen(phase, hms, parts.getOrNull(2)?.takeIf { it.isNotEmpty() })
    }

    private fun write(id: Int, seen: Seen) {
        prefs.edit().putString("p$id", listOf(seen.phase.name, seen.hms.joinToString(","), seen.job.orEmpty()).joinToString("\u0001")).apply()
    }

    /**
     * Compares [status] with what was seen before and posts notifications for what changed, when the
     * user wants them. With push notifications on, Bambuddy reports most events itself, so only the ones
     * it has no notification for (pauses and filament runout) are posted from here: no doubles.
     */
    @Synchronized
    fun observe(printerId: Int, printerName: String, status: PrinterStatus, settings: AppSettings) {
        val now = Seen(status.phase, status.hmsErrors.mapNotNull { it.key() }.toSet(), status.jobName)
        val before = read(printerId)
        write(printerId, now)
        if (before == null || settings.notifyMode == NotifyMode.OFF) return
        val local = if (settings.notifyMode == NotifyMode.POLL) PrintEvent.entries.toSet() else setOf(PrintEvent.PAUSED, PrintEvent.RUNOUT)
        // Offline and unknown say nothing about the print, so they never count as a change.
        if (now.phase == PrintPhase.OFFLINE || now.phase == PrintPhase.UNKNOWN) {
            write(printerId, before)
            return
        }
        val job = status.jobName ?: before.job
        fun emit(event: PrintEvent, detail: String?) {
            if (event in settings.events && event in local) Notifier.event(context, printerId, printerName, event, detail)
        }
        if (before.phase.isActive && now.phase == PrintPhase.FINISHED) emit(PrintEvent.FINISHED, job)
        if (before.phase.isActive && now.phase == PrintPhase.FAILED) emit(PrintEvent.FAILED, job)
        if (before.phase != PrintPhase.PAUSED && now.phase == PrintPhase.PAUSED) {
            if (status.expectedTray != null) emit(PrintEvent.RUNOUT, context.getString(R.string.notif_runout_body, job ?: ""))
            else emit(PrintEvent.PAUSED, job)
        }
        status.hmsErrors.filter { it.key() !in before.hms && it.severity in 1..2 }.forEach { e ->
            val detail = e.description?.takeIf { it.isNotBlank() } ?: e.fullCode.ifBlank { e.code }
            emit(classify(e), detail)
        }
    }

    private fun HmsError.key(): String? = fullCode.ifBlank { code }.takeIf { it.isNotBlank() }?.uppercase()

    companion object {
        /**
         * HMS codes start with the module: 07 is the AMS, 12 the AMS lite, 0C the camera's AI
         * detectors (spaghetti, first layer, nozzle clumping). Everything else is a printer error.
         */
        fun classify(e: HmsError): PrintEvent {
            val code = e.fullCode.ifBlank { e.code }.uppercase()
            return when {
                code.startsWith("07") || code.startsWith("12") -> PrintEvent.AMS
                code.startsWith("0C") -> PrintEvent.AI
                else -> PrintEvent.HMS
            }
        }
    }
}
