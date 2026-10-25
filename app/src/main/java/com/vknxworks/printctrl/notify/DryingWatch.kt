package com.vknxworks.printctrl.notify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vknxworks.printctrl.data.bambuddy.PrintPhase
import com.vknxworks.printctrl.data.bambuddy.PrinterStatus
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.settings.NotifyMode
import com.vknxworks.printctrl.ui.components.amsUnitName
import java.util.concurrent.TimeUnit

/**
 * Tells when an AMS 2 Pro or AMS HT finishes drying. Bambuddy has no notification of its own for that,
 * so it's worked out here from the AMS state, in push mode too. What was drying is kept on disk, so a
 * background check after the app was closed still knows. A cycle stopped from PrintCtrl isn't reported.
 */
class DryingWatch(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The user stopped drying [amsId] from PrintCtrl: no "finished" notification for it. */
    fun stoppedByUser(printerId: Int, amsId: Int) {
        prefs.edit().putBoolean("stop:$printerId:$amsId", true).apply()
    }

    @Synchronized
    fun observe(printerId: Int, printerName: String, status: PrinterStatus, settings: AppSettings) {
        if (status.phase == PrintPhase.OFFLINE || status.phase == PrintPhase.UNKNOWN) return
        val units = status.ams ?: return
        val key = "p$printerId"
        val dryingNow = units.filter { it.drying }.map { it.id }.toSet()
        val before = prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()
        prefs.edit().putString(key, dryingNow.joinToString(",")).apply()

        // While a cycle runs, a one-off check is planned for just after it should end, so the
        // notification comes on time even when PrintCtrl is closed.
        val longest = units.filter { it.drying }.maxOfOrNull { it.dryTime } ?: 0
        if (longest > 0 && settings.dryingDone && settings.notifyMode != NotifyMode.OFF) planCheck(printerId, longest)

        if (before == null) return
        for (amsId in before - dryingNow) {
            val stopped = prefs.getBoolean("stop:$printerId:$amsId", false)
            prefs.edit().remove("stop:$printerId:$amsId").apply()
            if (stopped || !settings.dryingDone || settings.notifyMode == NotifyMode.OFF) continue
            val unit = units.firstOrNull { it.id == amsId }
            Notifier.dryingDone(context, printerId, printerName, amsId, amsUnitName(amsId), unit?.dryFilament, failed = unit?.dryStatus == 5)
        }
    }

    private fun planCheck(printerId: Int, minutesLeft: Int) {
        val endAt = System.currentTimeMillis() + (minutesLeft + 3) * 60_000L
        val planned = prefs.getLong("plan:$printerId", 0L)
        // Re-planned only when the end moved noticeably, not on every update.
        if (kotlin.math.abs(planned - endAt) < 10 * 60_000L) return
        prefs.edit().putLong("plan:$printerId", endAt).apply()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "drying-check-$printerId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PollWorker>()
                .setInitialDelay((minutesLeft + 3).toLong(), TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build(),
        )
    }

    companion object {
        const val PREFS = "drying_watch"
    }
}
