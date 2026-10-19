package com.vknxworks.printctrl.data.bambuddy

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

/**
 * A spool in Bambuddy's own inventory (Bambuddy › Inventory). Bambuddy answers in this shape both when it
 * keeps the spools itself and when it keeps them in Spoolman, so PrintCtrl can use either through Bambuddy.
 * What's left is [labelWeight] minus [weightUsed]; Bambuddy updates [weightUsed] after every print.
 */
@Serializable
data class InventorySpool(
    val id: Int = 0,
    val material: String = "",
    val subtype: String? = null,
    val colorName: String? = null,
    /** RRGGBBAA. */
    val rgba: String? = null,
    /** More colours for multi-colour filament, comma-separated hex. */
    val extraColors: String? = null,
    val brand: String? = null,
    /** Filament on a full spool, in grams. */
    val labelWeight: Int = 1000,
    /** The empty spool, in grams. */
    val coreWeight: Int = 250,
    val weightUsed: Double = 0.0,
    val note: String? = null,
    val costPerKg: Double? = null,
    /** Set when someone entered the weight by hand; Bambuddy then stops syncing it from the AMS. */
    val weightLocked: Boolean = false,
    val lastScaleWeight: Int? = null,
    val lastWeighedAt: String? = null,
    val storageLocation: String? = null,
    val category: String? = null,
    val slicerFilamentName: String? = null,
    val nozzleTempMin: Int? = null,
    val nozzleTempMax: Int? = null,
    val tagUid: String? = null,
    val trayUuid: String? = null,
    val lastUsed: String? = null,
    val archivedAt: String? = null,
    val createdAt: String? = null,
) {
    val archived: Boolean get() = archivedAt != null
    val remaining: Double get() = max(0.0, labelWeight - weightUsed)
    val fractionLeft: Float? get() = if (labelWeight > 0) (remaining / labelWeight).toFloat().coerceIn(0f, 1f) else null

    /** "Bambu Lab PLA Basic Jade White". */
    val displayName: String
        get() = listOfNotNull(
            brand?.takeIf { it.isNotBlank() },
            material.takeIf { it.isNotBlank() },
            subtype?.takeIf { it.isNotBlank() && !it.equals(material, ignoreCase = true) },
            colorName?.takeIf { it.isNotBlank() },
        ).joinToString(" ")

    /** Hex colours (RRGGBB) for a swatch, main colour first. */
    val colors: List<String>
        get() = (listOfNotNull(rgba?.takeIf { it.length >= 6 }?.take(6)) + extraColors.orEmpty().split(',').map { it.trim().take(6) }.filter { it.length == 6 }).distinct()
}

@Serializable
data class InventoryAssignment(
    val id: Int = 0,
    val spoolId: Int = 0,
    val printerId: Int = 0,
    val printerName: String? = null,
    val amsId: Int = 0,
    val trayId: Int = 0,
    val spool: InventorySpool? = null,
)

/**
 * Bambuddy's spool inventory. Bambuddy keeps spools itself unless Spoolman is turned on in its settings;
 * then the same calls live under spoolman/inventory and Bambuddy passes them on to Spoolman. Needs "Read
 * status" to look and "Manage Inventory" to change anything.
 *
 * PrintCtrl never takes filament off a spool by itself: Bambuddy counts what prints use. Everything here
 * is a deliberate change the user confirmed.
 */
class BambuddyInventory(val api: BambuddyClient, val viaSpoolman: Boolean) {
    private val base = if (viaSpoolman) "spoolman/inventory" else "inventory"

    suspend fun spools(includeArchived: Boolean = false): List<InventorySpool> =
        api.get("$base/spools", ListSerializer(InventorySpool.serializer()), if (includeArchived) mapOf("include_archived" to "true") else emptyMap())

    suspend fun spool(id: Int): InventorySpool = api.get("$base/spools/$id", InventorySpool.serializer())

    suspend fun create(body: JsonObject): InventorySpool = api.parse(api.post("$base/spools", json = body.toString()), InventorySpool.serializer())

    suspend fun update(id: Int, body: JsonObject) {
        api.send("PATCH", "$base/spools/$id", body.toString())
    }

    suspend fun archive(id: Int) = api.post("$base/spools/$id/archive")
    suspend fun restore(id: Int) = api.post("$base/spools/$id/restore")
    suspend fun delete(id: Int) = api.delete("$base/spools/$id")

    /**
     * Weigh-in: [gross] is spool and filament together on a scale. In Bambuddy's own inventory this goes
     * through its scale endpoint, which also records the reading and when it was taken (and doesn't lock
     * the weight against AMS updates, unlike a typed-in amount).
     */
    suspend fun weighIn(spool: InventorySpool, gross: Double) {
        if (viaSpoolman) {
            api.send("PATCH", "$base/spools/${spool.id}/weight", buildJsonObject { put("weight_grams", gross) }.toString())
        } else {
            api.post(
                "spoolbuddy/scale/update-spool-weight",
                json = buildJsonObject {
                    put("spool_id", spool.id)
                    put("weight_grams", gross.coerceAtLeast(0.0))
                }.toString(),
            )
        }
    }

    /** What's left after weighing [gross] grams, worked out the way Bambuddy will. */
    fun leftAfterWeighing(spool: InventorySpool, gross: Double): Double {
        val net = (gross - spool.coreWeight).coerceAtLeast(0.0)
        // Bambuddy's own inventory caps it at the label weight; Spoolman takes the reading as it is.
        return if (viaSpoolman) net else net.coerceAtMost(spool.labelWeight.toDouble())
    }

    /** Sets what's left to [grams] (Bambuddy stores it as the amount used). */
    suspend fun setRemaining(spool: InventorySpool, grams: Double) {
        val left = grams.coerceIn(0.0, spool.labelWeight.toDouble())
        update(spool.id, buildJsonObject { put("weight_used", spool.labelWeight - left) })
    }

    /** Which spool is in which slot of [printerId], as (amsId, trayId) to spool id. */
    suspend fun assignments(printerId: Int): Map<Pair<Int, Int>, Int> =
        if (viaSpoolman) {
            api.slotAssignments(printerId).associate { (it.amsId to it.trayId) to it.spoolmanSpoolId }
        } else {
            api.get("inventory/assignments", ListSerializer(InventoryAssignment.serializer()), mapOf("printer_id" to printerId.toString()))
                .associate { (it.amsId to it.trayId) to it.spoolId }
        }

    /** Puts a spool in a slot; Bambuddy also sets the slot's filament on the printer. */
    suspend fun assign(spoolId: Int, printerId: Int, amsId: Int, trayId: Int) {
        if (viaSpoolman) {
            api.assignSlot(spoolId, printerId, amsId, trayId)
        } else {
            api.post(
                "inventory/assignments",
                json = buildJsonObject {
                    put("spool_id", spoolId)
                    put("printer_id", printerId)
                    put("ams_id", amsId)
                    put("tray_id", trayId)
                }.toString(),
            )
        }
    }

    suspend fun unassign(spoolId: Int, printerId: Int, amsId: Int, trayId: Int) {
        if (viaSpoolman) api.unassignSlot(spoolId) else api.delete("inventory/assignments/$printerId/$amsId/$trayId")
    }

    /** The printer and slot a spool is in, if any. */
    suspend fun slotOf(spoolId: Int): InventoryAssignment? =
        if (viaSpoolman) {
            api.slotAssignments().firstOrNull { it.spoolmanSpoolId == spoolId }
                ?.let { InventoryAssignment(spoolId = spoolId, printerId = it.printerId, printerName = it.printerName, amsId = it.amsId, trayId = it.trayId) }
        } else {
            api.get("inventory/assignments", ListSerializer(InventoryAssignment.serializer())).firstOrNull { it.spoolId == spoolId }
        }
}

/** Bumped after any change to a spool in Bambuddy's inventory, so lists showing it reload. */
object InventoryChanges {
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun changed() {
        version.value++
    }
}

/**
 * Bambuddy's inventory, in whichever mode Bambuddy runs it (its own, or passed on to Spoolman). The two
 * keep separate spools with unrelated ids, so the mode must be known for sure: when Bambuddy's settings
 * can't be read this throws rather than guessing. The answer is kept for a minute; [fresh] asks again
 * (on opening a spool screen or pulling to refresh), so switching Spoolman on or off in Bambuddy is
 * picked up.
 */
suspend fun BambuddyClient.inventory(fresh: Boolean = false): BambuddyInventory = BambuddyInventory(this, inventoryViaSpoolman(fresh))

/** The inventory mode if it was checked in the last minute, without asking Bambuddy. */
fun BambuddyClient.knownInventoryMode(): Boolean? =
    spoolmanModeCache?.takeIf { it.key == conn.key && System.currentTimeMillis() - it.atMs < MODE_TTL_MS }?.viaSpoolman

private class ModeCheck(val key: String, val viaSpoolman: Boolean, val atMs: Long)

@Volatile private var spoolmanModeCache: ModeCheck? = null
private const val MODE_TTL_MS = 60_000L

private suspend fun BambuddyClient.inventoryViaSpoolman(fresh: Boolean): Boolean {
    if (!fresh) knownInventoryMode()?.let { return it }
    val on = settings().spoolmanEnabled
    spoolmanModeCache = ModeCheck(conn.key, on, System.currentTimeMillis())
    return on
}
