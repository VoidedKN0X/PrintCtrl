package com.vknxworks.printctrl.notify

import android.content.Context
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.bambuddy.PrinterStatus
import com.vknxworks.printctrl.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Once per print, as soon as the first layer is down, posts a camera picture so a bad start (not
 * sticking, spaghetti) can be caught early. Only fires while the print is still near its start: a print
 * first seen further along (the app was closed, a 15-minute check came late) is skipped, not reported late.
 */
class FirstLayerCheck(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("first_layer", Context.MODE_PRIVATE)

    @Synchronized
    fun observe(printerId: Int, printerName: String, status: PrinterStatus, settings: AppSettings) {
        val key = "p$printerId"
        val phase = status.phase
        if (phase == PrintPhase.OFFLINE || phase == PrintPhase.UNKNOWN) return
        // The print ended: take the picture down and forget it.
        if (!phase.isActive) {
            if (prefs.contains(key)) {
                prefs.edit().remove(key).apply()
                Notifier.cancelFirstLayer(context, printerId)
            }
            return
        }
        // A new print getting ready or on its first layer: forget the last one, so a reprint of the same file counts again.
        val layer = status.layerNum ?: return
        if (phase == PrintPhase.PREPARING || layer == 1) {
            if (prefs.contains(key)) prefs.edit().remove(key).apply()
            return
        }
        // Paused before the first check: wait for it to carry on.
        if (layer < 2 || phase != PrintPhase.PRINTING) return
        val job = "${status.currentArchiveId ?: ""}|${status.jobName.orEmpty()}"
        if (prefs.getString(key, null) == job) return
        prefs.edit().putString(key, job).apply()
        if (!settings.firstLayerCheck) return
        val total = status.totalLayers ?: 0
        val early = layer <= maxOf(EARLY_LAYERS, total / 10) && (status.progress ?: 0.0) <= EARLY_PERCENT
        if (!early) return
        scope.launch {
            val picture = CameraSnapshot.fetch(context, printerId, sample = 2)
            // Not if the print ended (or another started) while the picture was on its way.
            if (prefs.getString(key, null) == job) Notifier.firstLayer(context, printerId, printerName, status.jobName, picture)
        }
    }

    private companion object {
        const val EARLY_LAYERS = 4
        const val EARLY_PERCENT = 15.0
    }
}
