package com.vknxworks.printctrl.data.bambuddy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// Field names follow Bambuddy's API (snake_case on the wire, see ApiJson). Everything has a default,
// so a field Bambuddy drops or renames never breaks parsing.

@Serializable
data class BambuddyVersion(val version: String = "")

@Serializable
data class AuthStatus(val authEnabled: Boolean = true, val requiresSetup: Boolean = false)

@Serializable
data class Token(val token: String = "")

@Serializable
data class Printer(
    val id: Int = 0,
    val name: String = "",
    val model: String? = null,
    val serialNumber: String? = null,
    val location: String? = null,
    val isActive: Boolean = true,
    val nozzleCount: Int = 1,
    val cameraRotation: Int = 0,
    val externalCameraEnabled: Boolean = false,
)

@Serializable
data class HmsError(
    val code: String = "",
    val attr: Long = 0,
    val module: Int = 0,
    /** 1 error (print stopped), 2 warning (paused), 3 notice. */
    val severity: Int = 0,
    val actions: List<String> = emptyList(),
    val fullCode: String = "",
    /** Bambu's own sentence for the code (English). Null when Bambu publishes none, not "no fault". */
    val description: String? = null,
)

@Serializable
data class AmsTray(
    val id: Int = 0,
    /** RRGGBBAA. */
    val trayColor: String? = null,
    val trayType: String? = null,
    val traySubBrands: String? = null,
    /** Percent left as reported by the AMS; -1 or 0 when unknown (spools without an RFID tag). */
    val remain: Int = -1,
    val tagUid: String? = null,
    val trayUuid: String? = null,
    /** 9 = empty, 10 = spool present but not loaded, 11 = loaded. */
    val state: Int? = null,
    val exists: Boolean? = null,
)

@Serializable
data class AmsUnit(
    val id: Int = 0,
    /** Percent; null on units that don't report it (AMS lite). */
    val humidity: Int? = null,
    val temp: Double? = null,
    val isAmsHt: Boolean = false,
    val tray: List<AmsTray> = emptyList(),
    /** Minutes of drying left; 0 when not drying (and through the cooling phase that ends a cycle). */
    val dryTime: Int = 0,
    /** "ams" (AMS / AMS lite), "n3f" (AMS 2 Pro) or "n3s" (AMS HT); empty when Bambuddy doesn't know yet. */
    val moduleType: String = "",
    /** 0 off, 1 checking, 2 drying, 3 cooling, 4 stopping, 5 error, 6 heater out of control. */
    val dryStatus: Int = 0,
    /** 0 off, 1 heating, 2 dehumidifying. */
    val drySubStatus: Int = 0,
    /** Why the AMS can't dry right now (firmware codes, see [DryingReasons]). */
    val drySfReason: List<Int> = emptyList(),
    /** Target of the running cycle, when Bambuddy started it (the printer doesn't report it). */
    val dryTargetTemp: Int? = null,
    val dryFilament: String? = null,
    /** A countdown is set but isn't ticking: the cycle never started or is paused. */
    val dryCountdownStalled: Boolean = false,
) {
    /** Only the AMS 2 Pro and the AMS HT have a heater; the AMS and AMS lite can't dry. */
    val canDry: Boolean get() = moduleType == "n3f" || moduleType == "n3s"

    /** Highest drying temperature: 85° on an AMS HT, 65° on an AMS 2 Pro. */
    val maxDryTemp: Int get() = if (moduleType == "n3s") 85 else 65

    /** A drying cycle is running (checking, drying or cooling down). */
    val drying: Boolean get() = dryTime > 0 || dryStatus in 1..3
}

/**
 * Live state of one printer, from GET /printers/{id}/status or a WebSocket "printer_status" message.
 * The WebSocket version leaves out [id], [name], [coverUrl] and [currentArchiveId]; [merge] keeps
 * them from the last full status.
 */
@Serializable
data class PrinterStatus(
    val id: Int = 0,
    val name: String = "",
    val connected: Boolean = false,
    /** IDLE, PREPARE, SLICING, RUNNING, PAUSE, FINISH, FAILED (or "unknown"). */
    val state: String? = null,
    val currentPrint: String? = null,
    val subtaskName: String? = null,
    val gcodeFile: String? = null,
    val progress: Double? = null,
    /** Minutes. */
    val remainingTime: Int? = null,
    val layerNum: Int? = null,
    val totalLayers: Int? = null,
    val temperatures: JsonObject? = null,
    val coverUrl: String? = null,
    val hmsErrors: List<HmsError> = emptyList(),
    val ams: List<AmsUnit>? = null,
    val vtTray: List<AmsTray> = emptyList(),
    /** Global tray id loaded now: ams * 4 + slot, 254 = external spool, 255 = none. */
    val trayNow: Int = 255,
    /** 1 silent, 2 standard, 3 sport, 4 ludicrous. */
    val speedLevel: Int = 2,
    val chamberLight: Boolean = false,
    /** Percent; null when this model has no such fan. */
    val coolingFanSpeed: Int? = null,
    val bigFan1Speed: Int? = null,
    val bigFan2Speed: Int? = null,
    val heatbreakFanSpeed: Int? = null,
    val awaitingPlateClear: Boolean = false,
    /** While paused for a filament change: the slot the printer now wants filament in (runout). */
    val expectedTray: Int? = null,
    /** This printer accepts drying commands from Bambuddy (H2D, X1, P2S, …; not the A1 or P1). */
    val supportsDrying: Boolean = false,
    /** Drying can run while a print is going on (only some models and firmware). */
    val supportsDryingWhilePrinting: Boolean = false,
    /** The AMS can dry, but only from the printer's own screen (P1 series). */
    val dryingScreenOnly: Boolean = false,
    val currentArchiveId: Int? = null,
    val currentPlateId: Int? = null,
    /** What the printer is doing right now, e.g. "Auto bed leveling". */
    val stgCurName: String? = null,
    val wifiSignal: Int? = null,
    val doorOpen: Boolean = false,
    val firmwareVersion: String? = null,
) {
    fun temp(key: String): Double? = (temperatures?.get(key) as? JsonPrimitive)?.doubleOrNull

    val nozzle: Double? get() = temp("nozzle")
    val nozzleTarget: Double? get() = temp("nozzle_target")
    val bed: Double? get() = temp("bed")
    val bedTarget: Double? get() = temp("bed_target")
    val chamber: Double? get() = temp("chamber")

    val jobName: String? get() = subtaskName?.takeIf { it.isNotBlank() } ?: currentPrint?.takeIf { it.isNotBlank() }

    val phase: PrintPhase get() = PrintPhase.of(connected, state)

    /** Fills in what a WebSocket update leaves out from the previous full status. */
    fun merge(previous: PrinterStatus?): PrinterStatus {
        if (previous == null) return this
        return copy(
            id = if (id == 0) previous.id else id,
            name = name.ifEmpty { previous.name },
            coverUrl = coverUrl ?: previous.coverUrl,
            // A frame without AMS data means "unchanged", not "AMS removed".
            ams = ams ?: previous.ams,
            currentArchiveId = currentArchiveId ?: previous.currentArchiveId,
        )
    }
}

enum class PrintPhase {
    OFFLINE, IDLE, PREPARING, PRINTING, PAUSED, FINISHED, FAILED, UNKNOWN;

    val isActive: Boolean get() = this == PREPARING || this == PRINTING || this == PAUSED

    companion object {
        fun of(connected: Boolean, state: String?): PrintPhase = when {
            !connected -> OFFLINE
            else -> when (state?.uppercase()) {
                "IDLE" -> IDLE
                "PREPARE", "SLICING" -> PREPARING
                "RUNNING" -> PRINTING
                "PAUSE" -> PAUSED
                "FINISH" -> FINISHED
                "FAILED" -> FAILED
                else -> UNKNOWN
            }
        }
    }
}

/** The bits of an archive entry needed for the running job (start time). */
@Serializable
data class ArchiveBrief(
    val id: Int = 0,
    val startedAt: String? = null,
    val printName: String? = null,
)

// ---------- Queue ----------

@Serializable
data class QueueItem(
    val id: Int = 0,
    val printerId: Int? = null,
    val targetModel: String? = null,
    val archiveId: Int? = null,
    val libraryFileId: Int? = null,
    val position: Int = 0,
    /** ISO time ending in Z, or null for "as soon as possible". */
    val scheduledTime: String? = null,
    val manualStart: Boolean = false,
    val plateId: Int? = null,
    val amsMapping: List<Int>? = null,
    /** pending, printing, completed, failed, skipped, cancelled. */
    val status: String = "pending",
    val waitingReason: String? = null,
    val errorMessage: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val archiveName: String? = null,
    /** A file path on the server; only says whether a picture exists. */
    val archiveThumbnail: String? = null,
    val libraryFileName: String? = null,
    val libraryFileThumbnail: String? = null,
    val printerName: String? = null,
    val printTimeSeconds: Int? = null,
    val filamentUsedGrams: Double? = null,
    val filamentType: String? = null,
) {
    val name: String get() = archiveName ?: libraryFileName ?: "#$id"
}

// ---------- Library ----------

@Serializable
data class LibraryFile(
    val id: Int = 0,
    val folderId: Int? = null,
    val filename: String = "",
    /** 3mf, gcode.3mf, gcode, stl, step, … */
    val fileType: String = "",
    val fileSize: Long = 0,
    val thumbnailPath: String? = null,
    val printCount: Int = 0,
    val createdAt: String? = null,
    val printName: String? = null,
    val printTimeSeconds: Int? = null,
    val filamentUsedGrams: Double? = null,
    val slicedForModel: String? = null,
) {
    val name: String get() = printName?.takeIf { it.isNotBlank() } ?: filename
    /** Sliced and ready for a printer. */
    val printable: Boolean
        get() = fileType.contains("gcode", ignoreCase = true) || filename.endsWith(".gcode.3mf", ignoreCase = true) ||
            filename.endsWith(".gcode", ignoreCase = true)

    /** A model Bambuddy's slicer can turn into something printable. */
    val sliceable: Boolean
        get() = !printable && (fileType.equals("stl", true) || fileType.equals("3mf", true) ||
            filename.endsWith(".stl", true) || filename.endsWith(".3mf", true))
}

@Serializable
data class PlateFilament(val slotId: Int = 0, val type: String = "", val color: String = "", val usedGrams: Double = 0.0)

@Serializable
data class Plate(
    val index: Int = 1,
    val name: String? = null,
    val objectCount: Int = 0,
    val hasThumbnail: Boolean = false,
    val printTimeSeconds: Int? = null,
    val filamentUsedGrams: Double? = null,
    val filaments: List<PlateFilament> = emptyList(),
)

@Serializable
data class Plates(val isMultiPlate: Boolean = false, val plates: List<Plate> = emptyList())

@Serializable
data class FilamentRequirement(
    /** 1-based filament slot in the project. */
    val slotId: Int = 1,
    val type: String = "",
    /** #RRGGBB */
    val color: String = "",
    val usedGrams: Double = 0.0,
    val usedInPlate: Boolean = true,
)

@Serializable
data class FilamentRequirements(val filaments: List<FilamentRequirement> = emptyList())

@Serializable
data class UploadResult(val id: Int = 0, val filename: String = "", val fileType: String = "")

@Serializable
data class SliceStarted(val jobId: Int = 0, val status: String = "")

@Serializable
data class SliceResult(val libraryFileId: Int = 0, val name: String = "")

@Serializable
data class SliceJob(
    val jobId: Int = 0,
    /** pending, running, completed, failed. */
    val status: String = "",
    val result: SliceResult? = null,
    val errorDetail: String? = null,
)

@Serializable
data class SlicerPreset(val id: String = "", val name: String = "", val source: String = "")

@Serializable
data class PresetGroup(
    val printer: List<SlicerPreset> = emptyList(),
    val process: List<SlicerPreset> = emptyList(),
    val filament: List<SlicerPreset> = emptyList(),
)

@Serializable
data class SlicerPresets(
    val orcaCloud: PresetGroup? = null,
    val cloud: PresetGroup? = null,
    val local: PresetGroup? = null,
    val standard: PresetGroup? = null,
) {
    private val groups get() = listOfNotNull(local, orcaCloud, cloud, standard)
    val printers get() = groups.flatMap { it.printer }
    val processes get() = groups.flatMap { it.process }
    val filaments get() = groups.flatMap { it.filament }
}

/** The few server settings PrintCtrl looks at. */
@Serializable
data class ServerSettings(
    val useSlicerApi: Boolean = false,
    /** JSON of drying presets per material set in Bambuddy; empty means its built-in ones. */
    val dryingPresets: String = "",
    val spoolmanEnabled: Boolean = false,
    /** ISO code like "EUR"; Bambuddy's costs are in this currency. */
    val currency: String = "USD",
)

// ---------- Archive ----------

@Serializable
data class Archive(
    val id: Int = 0,
    val printerId: Int? = null,
    val filename: String = "",
    val printName: String? = null,
    val thumbnailPath: String? = null,
    val timelapsePath: String? = null,
    val plateId: Int? = null,
    val printTimeSeconds: Int? = null,
    val actualTimeSeconds: Int? = null,
    val filamentUsedGrams: Double? = null,
    val filamentType: String? = null,
    val filamentColor: String? = null,
    val layerHeight: Double? = null,
    val totalLayers: Int? = null,
    /** printing, completed, failed, aborted, cancelled, stopped. */
    val status: String = "",
    val startedAt: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val failureReason: String? = null,
    val notes: String? = null,
    /** Filament cost, worked out by Bambuddy from spool or filament prices. */
    val cost: Double? = null,
    val energyKwh: Double? = null,
    /** Electricity cost, from a smart plug's reading and the price per kWh set in Bambuddy. */
    val energyCost: Double? = null,
    val runCount: Int = 0,
    /** Filament actually used across this file's runs, when Bambuddy measured it. */
    val totalFilamentActualGrams: Double? = null,
    val isFavorite: Boolean = false,
    /** Comma-separated, as Bambuddy stores them. */
    val tags: String? = null,
) {
    val name: String get() = printName?.takeIf { it.isNotBlank() } ?: filename
}

@Serializable
data class ArchiveStats(
    val totalPrints: Int = 0,
    val successfulPrints: Int = 0,
    val failedPrints: Int = 0,
    val cancelledPrints: Int = 0,
    val totalPrintTimeHours: Double = 0.0,
    val totalFilamentGrams: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalEnergyKwh: Double = 0.0,
    val totalEnergyCost: Double = 0.0,
    val printsByFilamentType: Map<String, Int> = emptyMap(),
)

// ---------- Maintenance and smart plugs ----------

@Serializable
data class MaintenanceItem(
    val id: Int = 0,
    val maintenanceTypeName: String = "",
    val maintenanceTypeWikiUrl: String? = null,
    val enabled: Boolean = true,
    /** Hours, or days when [intervalType] is "days". */
    val intervalHours: Double = 0.0,
    val intervalType: String = "hours",
    val hoursSinceMaintenance: Double = 0.0,
    val hoursUntilDue: Double = 0.0,
    val daysSinceMaintenance: Double? = null,
    val daysUntilDue: Double? = null,
    val isDue: Boolean = false,
    val isWarning: Boolean = false,
    val lastPerformedAt: String? = null,
) {
    val byDays: Boolean get() = intervalType == "days"

    /** How far along the interval is, 0..1+. */
    val progress: Float
        get() {
            if (intervalHours <= 0) return 0f
            val since = if (byDays) daysSinceMaintenance ?: 0.0 else hoursSinceMaintenance
            return (since / intervalHours).toFloat()
        }
}

@Serializable
data class MaintenanceOverview(
    val totalPrintHours: Double = 0.0,
    val maintenanceItems: List<MaintenanceItem> = emptyList(),
    val dueCount: Int = 0,
    val warningCount: Int = 0,
)

@Serializable
data class SmartPlug(
    val id: Int = 0,
    val name: String = "",
    val plugType: String = "",
    val printerId: Int? = null,
    val haEntityId: String? = null,
    val enabled: Boolean = true,
) {
    /** MQTT plugs only report; Home Assistant scripts can't be switched either. */
    val switchable: Boolean get() = plugType != "mqtt" && haEntityId?.startsWith("script.") != true
}

@Serializable
data class PlugEnergy(val power: Double? = null, val today: Double? = null)

@Serializable
data class PlugStatus(val state: String? = null, val reachable: Boolean = true, val energy: PlugEnergy? = null) {
    val on: Boolean get() = state.equals("ON", ignoreCase = true)
}

// ---------- Spoolman through Bambuddy ----------

@Serializable
data class SlotAssignment(
    val printerId: Int = 0,
    val printerName: String? = null,
    val amsId: Int = 0,
    val trayId: Int = 0,
    val spoolmanSpoolId: Int = 0,
)

@Serializable
data class BambuddySpoolmanSpool(val id: Int = 0, val tagUid: String? = null, val trayUuid: String? = null)

// ---------- Objects (skip) and AI detection ----------

@Serializable
data class PrintObject(val id: Int = 0, val name: String = "", val skipped: Boolean = false)

@Serializable
data class PrintObjects(val objects: List<PrintObject> = emptyList(), val isPrinting: Boolean = false)

@Serializable
data class ObicoPrinter(@get:JvmName("getVerdictClass") val `class`: String = "unknown", val score: Double = 0.0, val frameCount: Int = 0)

@Serializable
data class ObicoStatus(val enabled: Boolean = false, val perPrinter: Map<String, ObicoPrinter> = emptyMap())

// ---------- SD card ----------

@Serializable
data class PrinterFile(
    val name: String = "",
    val isDirectory: Boolean = false,
    val size: Long = 0,
    val path: String = "",
    val mtime: String? = null,
)

@Serializable
data class PrinterFiles(val path: String = "/", val files: List<PrinterFile> = emptyList(), val warnings: List<String> = emptyList())

@Serializable
data class PrinterStorage(val usedBytes: Long? = null, val freeBytes: Long? = null)
