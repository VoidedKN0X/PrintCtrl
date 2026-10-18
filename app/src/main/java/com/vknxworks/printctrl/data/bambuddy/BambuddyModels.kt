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
    val dryTime: Int = 0,
    val moduleType: String = "",
)

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
    val supportsDrying: Boolean = false,
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
