package com.vknxworks.printctrl.notify

import android.content.Context
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.spoolman.Spool
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import kotlin.math.roundToInt

/**
 * Notices spools that dropped below the low-stock threshold since the last check and notifies once
 * per spool. A spool counts again after it's back above the threshold (refilled, weighed in). The very
 * first check (and the first after changing the threshold) only takes note of which spools are already
 * low, so it doesn't flood the phone; those are marked in the Spools tab.
 */
class LowStock(private val context: Context) {
    private val prefs = context.getSharedPreferences("low_stock", Context.MODE_PRIVATE)

    suspend fun check(spoolman: SpoolmanClient, settings: AppSettings) {
        val threshold = settings.lowStockGrams.toDouble()
        val spools = spoolman.spools(0, 1000, "id:asc").first.filter { !it.archived }
        val low = spools.filter { s -> s.remainingWeight?.let { it < threshold } == true }
        val lowIds = low.map { it.id.toString() }.toSet()
        val known = prefs.getStringSet(KEY, null)?.toSet()
        val previousThreshold = prefs.getInt(KEY_THRESHOLD, -1)
        // Remember what's low now (spools that recovered drop out of the set).
        prefs.edit().putStringSet(KEY, lowIds).putInt(KEY_THRESHOLD, settings.lowStockGrams).apply()
        // First run, or the threshold changed: just take note, so a new threshold doesn't flood the phone.
        if (known == null || previousThreshold != settings.lowStockGrams) return
        if (!settings.lowStockAlerts) return
        val fresh = low.filter { it.id.toString() !in known }
        if (fresh.size > MAX_SINGLE) {
            Notifier.lowStockSummary(context, fresh.size, settings.lowStockGrams)
        } else {
            fresh.forEach { notify(it, settings.lowStockGrams) }
        }
    }

    private fun notify(spool: Spool, threshold: Int) {
        val name = spool.filament.displayName.ifEmpty { "#${spool.id}" }
        val left = (spool.remainingWeight ?: 0.0).roundToInt()
        Notifier.lowStock(context, spool.id, name, spool.filament.material, left, threshold)
    }

    companion object {
        private const val KEY = "low_ids"
        private const val KEY_THRESHOLD = "threshold"
        private const val MAX_SINGLE = 4
    }
}
