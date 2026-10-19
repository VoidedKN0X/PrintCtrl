package com.vknxworks.printctrl.notify

import android.content.Context
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.inventory
import com.vknxworks.printctrl.data.settings.AppSettings
import com.vknxworks.printctrl.data.spoolman.SpoolmanClient
import kotlin.math.roundToInt

/**
 * Notices spools that dropped below the low-stock threshold since the last check and notifies once
 * per spool. A spool counts again after it's back above the threshold (refilled, weighed in). The very
 * first check (and the first after changing the threshold) only takes note of which spools are already
 * low, so it doesn't flood the phone; those are marked in the Spools tab.
 *
 * Spools come from Spoolman when it's set up in the app, otherwise from Bambuddy's own inventory. Each
 * source keeps its own record, since their spool numbers are unrelated.
 */
class LowStock(private val context: Context) {
    private val prefs = context.getSharedPreferences("low_stock", Context.MODE_PRIVATE)

    private data class Item(val id: Int, val name: String, val material: String?, val left: Double)

    suspend fun check(spoolman: SpoolmanClient, settings: AppSettings) {
        val spools = spoolman.spools(0, 1000, "id:asc").first.filter { !it.archived }
        val items = spools.mapNotNull { s ->
            val left = s.remainingWeight ?: return@mapNotNull null
            Item(s.id, s.filament.displayName.ifEmpty { "#${s.id}" }, s.filament.material, left)
        }
        evaluate(items, settings, KEY, KEY_THRESHOLD)
    }

    /** The same, for Bambuddy's own spool inventory (used when Spoolman isn't set up in the app). */
    suspend fun checkInventory(api: BambuddyClient, settings: AppSettings) {
        val items = api.inventory().spools().filter { !it.archived }.map { s ->
            Item(s.id, s.displayName.ifEmpty { "#${s.id}" }, s.material.takeIf { it.isNotBlank() }, s.remaining)
        }
        evaluate(items, settings, KEY_INVENTORY, KEY_INVENTORY_THRESHOLD)
    }

    private fun evaluate(items: List<Item>, settings: AppSettings, key: String, thresholdKey: String) {
        val threshold = settings.lowStockGrams.toDouble()
        val low = items.filter { it.left < threshold }
        val lowIds = low.map { it.id.toString() }.toSet()
        val known = prefs.getStringSet(key, null)?.toSet()
        val previousThreshold = prefs.getInt(thresholdKey, -1)
        // Remember what's low now (spools that recovered drop out of the set).
        prefs.edit().putStringSet(key, lowIds).putInt(thresholdKey, settings.lowStockGrams).apply()
        // First run, or the threshold changed: just take note, so a new threshold doesn't flood the phone.
        if (known == null || previousThreshold != settings.lowStockGrams) return
        if (!settings.lowStockAlerts) return
        val fresh = low.filter { it.id.toString() !in known }
        if (fresh.size > MAX_SINGLE) {
            Notifier.lowStockSummary(context, fresh.size, settings.lowStockGrams)
        } else {
            fresh.forEach { Notifier.lowStock(context, it.id, it.name, it.material, it.left.roundToInt(), settings.lowStockGrams) }
        }
    }

    companion object {
        private const val KEY = "low_ids"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_INVENTORY = "inventory_low_ids"
        private const val KEY_INVENTORY_THRESHOLD = "inventory_threshold"
        private const val MAX_SINGLE = 4
    }
}
