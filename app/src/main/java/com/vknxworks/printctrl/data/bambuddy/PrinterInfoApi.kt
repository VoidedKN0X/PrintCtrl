package com.vknxworks.printctrl.data.bambuddy

import kotlinx.serialization.Serializable

// Printer history and information from Bambuddy: heater and AMS sensor history, firmware updates and
// pressure advance (K-factor) profiles. All of it is read-only and needs "Read Status" on the API key.
// Bambuddy keeps K-profile changes and firmware uploads to logged-in admins, so API keys can't do them.

@Serializable
data class HeaterPoint(
    val recordedAt: String = "",
    val value: Double? = null,
    val target: Double? = null,
)

@Serializable
data class HeaterSeries(
    /** "nozzle", "nozzle_2" (second nozzle of an H2D), "bed" or "chamber". */
    val sensorKind: String = "",
    val data: List<HeaterPoint> = emptyList(),
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val avgValue: Double? = null,
)

@Serializable
data class SensorHistory(val printerId: Int = 0, val series: List<HeaterSeries> = emptyList())

@Serializable
data class AmsHistoryPoint(
    val recordedAt: String = "",
    val humidity: Double? = null,
    val temperature: Double? = null,
)

@Serializable
data class AmsHistory(
    val printerId: Int = 0,
    val amsId: Int = 0,
    val data: List<AmsHistoryPoint> = emptyList(),
    val minHumidity: Double? = null,
    val maxHumidity: Double? = null,
    val avgHumidity: Double? = null,
    val minTemperature: Double? = null,
    val maxTemperature: Double? = null,
    val avgTemperature: Double? = null,
)

@Serializable
data class FirmwareVersion(
    val version: String = "",
    val releaseNotes: String? = null,
    val releaseTime: String? = null,
)

@Serializable
data class FirmwareInfo(
    val printerId: Int = 0,
    val model: String? = null,
    val currentVersion: String? = null,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
    val releaseNotes: String? = null,
    val availableVersions: List<FirmwareVersion> = emptyList(),
)

@Serializable
data class KProfile(
    val slotId: Int = 0,
    val extruderId: Int = 0,
    /** e.g. "HS00-0.4": hardened steel, 0.4 mm. */
    val nozzleId: String = "",
    val nozzleDiameter: String = "",
    val filamentId: String = "",
    val name: String = "",
    /** Pressure advance as text, e.g. "0.020000". */
    val kValue: String = "",
    val nCoef: String = "",
    val amsId: Int = 0,
    val trayId: Int = -1,
    val settingId: String? = null,
)

@Serializable
data class KProfiles(val profiles: List<KProfile> = emptyList(), val nozzleDiameter: String = "")

@Serializable
data class KProfileNotes(val notes: Map<String, String> = emptyMap())

/** Heater history for the last [hours] (1 to 168). */
suspend fun BambuddyClient.sensorHistory(printerId: Int, hours: Int): SensorHistory =
    get("printer-sensor-history/$printerId", SensorHistory.serializer(), mapOf("hours" to hours.coerceIn(1, 168).toString()))

/** Humidity and temperature of one AMS unit (AMS 2 Pro, AMS HT, the original AMS; not the AMS lite). */
suspend fun BambuddyClient.amsHistory(printerId: Int, amsId: Int, hours: Int): AmsHistory =
    get("ams-history/$printerId/$amsId", AmsHistory.serializer(), mapOf("hours" to hours.coerceIn(1, 168).toString()))

/** The printer's firmware and whether Bambu Lab has a newer one. Only checked when Bambuddy's firmware check is on. */
suspend fun BambuddyClient.firmware(printerId: Int): FirmwareInfo = get("firmware/updates/$printerId", FirmwareInfo.serializer())

/** Pressure advance profiles stored on the printer for one nozzle size ("0.4"). The printer must be online. */
suspend fun BambuddyClient.kProfiles(printerId: Int, nozzleDiameter: String): KProfiles =
    get("printers/$printerId/kprofiles/", KProfiles.serializer(), mapOf("nozzle_diameter" to nozzleDiameter))

/** Notes the user wrote for profiles in Bambuddy, by setting id. */
suspend fun BambuddyClient.kProfileNotes(printerId: Int): Map<String, String> =
    get("printers/$printerId/kprofiles/notes", KProfileNotes.serializer()).notes
