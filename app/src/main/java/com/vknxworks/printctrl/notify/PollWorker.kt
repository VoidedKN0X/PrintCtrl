package com.vknxworks.printctrl.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.printctrl.container
import com.vknxworks.printctrl.data.settings.NotifyMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Checks the printers in the background: posts notifications for what changed since the last check
 * (in "check every 15 minutes" mode) and starts the progress notification when a print is running.
 * Also run once right away when a push from Bambuddy arrives.
 */
class PollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.settings.settings.value
        val setup = withTimeoutOrNull(10_000) { c.servers.setup.filterNotNull().first() }
        var failed = false

        // Printers: notifications for what changed, and the progress notification for a running print.
        if (setup?.bambuddy != null) {
            val api = withTimeoutOrNull(20_000) { c.bambuddy.filterNotNull().first() }
            if (api == null) {
                failed = true
            } else {
                try {
                    val printers = api.printers().filter { it.isActive }
                    val statuses = coroutineScope {
                        printers.map { p -> async { p to runCatching { api.status(p.id) }.getOrNull() } }.awaitAll()
                    }
                    var anyActive = false
                    statuses.forEach { (p, s) ->
                        if (s == null) return@forEach
                        c.events.observe(p.id, p.name.ifBlank { s.name }, s, settings)
                        if (s.phase.isActive) anyActive = true
                    }
                    if (anyActive) PrintMonitorService.start(applicationContext)
                } catch (e: Exception) {
                    failed = true
                }
            }
        }

        // Spools: tell about any that dropped below the low-stock threshold.
        if (setup?.spoolman != null) {
            val sm = withTimeoutOrNull(20_000) { c.spoolman.filterNotNull().first() }
            if (sm != null) runCatching { LowStock(applicationContext).check(sm, settings) }
        }
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "poll"
        private const val NOW = "poll-now"

        /** Matches the background checks to the chosen notification mode. */
        fun schedule(context: Context, mode: NotifyMode) {
            val wm = WorkManager.getInstance(context)
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            if (mode == NotifyMode.OFF) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            // Push mode still checks now and then, so the progress notification starts even if a push was missed.
            val every = if (mode == NotifyMode.POLL) 15L else 60L
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PollWorker>(every, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        }

        /** One check as soon as possible (after a push from Bambuddy). */
        fun now(context: Context) {
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PollWorker>()
                    .setConstraints(online)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }
    }
}
