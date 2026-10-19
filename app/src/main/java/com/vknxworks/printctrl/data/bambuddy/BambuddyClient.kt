package com.vknxworks.printctrl.data.bambuddy

import com.vknxworks.printctrl.data.net.ApiJson
import com.vknxworks.printctrl.data.net.NotThisServerException
import com.vknxworks.printctrl.data.net.below
import com.vknxworks.printctrl.data.net.text
import com.vknxworks.printctrl.data.servers.ServerConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Bambuddy's REST API (everything under /api/v1). The API key goes in the X-API-Key header, added by
 * the connection's client. Endpoints and fields were taken from Bambuddy's router source.
 */
class BambuddyClient(val conn: ServerConnection) {
    private val http = conn.client
    val base: HttpUrl = conn.baseUrl

    fun url(path: String): HttpUrl.Builder = base.below("api/v1/$path")

    private suspend fun <T> get(path: String, serializer: KSerializer<T>, query: Map<String, String> = emptyMap()): T {
        val u = url(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val body = http.text(Request.Builder().url(u).get().build())
        return parse(body, serializer)
    }

    private suspend fun post(path: String, query: Map<String, String> = emptyMap(), json: String = "{}"): String {
        val u = url(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return http.text(Request.Builder().url(u).post(json.toRequestBody(JSON)).build())
    }

    private fun <T> parse(body: String, serializer: KSerializer<T>): T = try {
        ApiJson.decodeFromString(serializer, body)
    } catch (e: Exception) {
        throw NotThisServerException(spoolman = false)
    }

    // ---- Server ----

    suspend fun version(): BambuddyVersion = get("updates/version", BambuddyVersion.serializer())

    suspend fun authStatus(): AuthStatus = get("auth/status", AuthStatus.serializer())

    // ---- Printers ----

    suspend fun printers(): List<Printer> = get("printers/", ListSerializer(Printer.serializer()))

    suspend fun status(printerId: Int): PrinterStatus = get("printers/$printerId/status", PrinterStatus.serializer())

    suspend fun archive(id: Int): ArchiveBrief = get("archives/$id", ArchiveBrief.serializer())

    /** Picture of the plate being printed. Loaded with the API key header, so no token is needed. */
    fun coverUrl(printerId: Int): String = url("printers/$printerId/cover").addQueryParameter("view", "top").build().toString()

    // ---- Controls (need "Control printer" on the API key) ----

    suspend fun pause(printerId: Int) = post("printers/$printerId/print/pause")
    suspend fun resume(printerId: Int) = post("printers/$printerId/print/resume")
    suspend fun stop(printerId: Int) = post("printers/$printerId/print/stop")

    /** 1 silent, 2 standard, 3 sport, 4 ludicrous. */
    suspend fun setSpeed(printerId: Int, level: Int) = post("printers/$printerId/print-speed", mapOf("mode" to level.toString()))

    suspend fun setLight(printerId: Int, on: Boolean) = post("printers/$printerId/chamber-light", mapOf("on" to on.toString()))

    /** [fan] is "part", "aux" or "chamber"; [percent] 0-100. */
    suspend fun setFan(printerId: Int, fan: String, percent: Int) =
        post("printers/$printerId/fan-speed", mapOf("fan" to fan, "speed" to percent.coerceIn(0, 100).toString()))

    suspend fun clearHms(printerId: Int) = post("printers/$printerId/hms/clear")

    /** Tells Bambuddy's queue the plate has been cleared, so the next queued job can start. */
    suspend fun clearPlate(printerId: Int) = post("printers/$printerId/clear-plate")

    // ---- Tokens for the camera and the live updates socket ----
    // Both endpoints only take a ?token= in the address. Tokens last 60 minutes; they're reused for 50.

    private val tokenLock = Mutex()
    private var cachedCameraToken: Pair<String, Long>? = null
    private var cachedSocketToken: Pair<String, Long>? = null

    suspend fun cameraToken(forceNew: Boolean = false): String = tokenLock.withLock {
        val cached = cachedCameraToken?.takeIf { !forceNew && it.second > System.currentTimeMillis() }?.first
        cached ?: parse(post("printers/camera/stream-token"), Token.serializer()).token.also { t ->
            cachedCameraToken = t to System.currentTimeMillis() + TOKEN_REUSE_MS
        }
    }

    suspend fun socketToken(forceNew: Boolean = false): String = tokenLock.withLock {
        val cached = cachedSocketToken?.takeIf { !forceNew && it.second > System.currentTimeMillis() }?.first
        cached ?: parse(post("auth/ws-token"), Token.serializer()).token.also { t ->
            cachedSocketToken = t to System.currentTimeMillis() + TOKEN_REUSE_MS
        }
    }

    fun cameraStreamUrl(printerId: Int, token: String, fps: Int): HttpUrl =
        url("printers/$printerId/camera/stream").addQueryParameter("fps", fps.toString()).addQueryParameter("token", token).build()

    fun cameraSnapshotUrl(printerId: Int, token: String): HttpUrl =
        url("printers/$printerId/camera/snapshot").addQueryParameter("token", token).build()

    /** OkHttp opens WebSockets from http(s) addresses, so the same client (pins, policy) applies. */
    fun socketUrl(token: String): HttpUrl = url("ws").addQueryParameter("token", token).build()

    private suspend fun send(method: String, path: String, json: String, query: Map<String, String> = emptyMap()): String {
        val u = url(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return http.text(Request.Builder().url(u).method(method, json.toRequestBody(JSON)).build())
    }

    private suspend fun delete(path: String): String = http.text(Request.Builder().url(url(path).build()).delete().build())

    // ---- More printer controls (need "Control Printer") ----

    suspend fun setNozzleTemp(printerId: Int, target: Int) =
        post("printers/$printerId/temperature/nozzle", mapOf("target" to target.coerceIn(0, 320).toString()))

    suspend fun setBedTemp(printerId: Int, target: Int) =
        post("printers/$printerId/temperature/bed", mapOf("target" to target.coerceIn(0, 140).toString()))

    suspend fun homeAxes(printerId: Int) = post("printers/$printerId/home-axes")

    suspend fun jogXY(printerId: Int, x: Double, y: Double) =
        post("printers/$printerId/xy-jog", mapOf("x" to x.toString(), "y" to y.toString()))

    /** Positive moves the bed away from the nozzle. */
    suspend fun jogBed(printerId: Int, mm: Double) = post("printers/$printerId/bed-jog", mapOf("distance" to mm.toString()))

    /** Positive extrudes, negative retracts. */
    suspend fun jogExtruder(printerId: Int, mm: Double) = post("printers/$printerId/extruder-jog", mapOf("distance" to mm.toString()))

    /** [trayId] is the global tray: AMS × 4 + slot, or 254 for the external spool. */
    suspend fun loadFilament(printerId: Int, trayId: Int) = post("printers/$printerId/ams/load", mapOf("tray_id" to trayId.toString()))

    suspend fun unloadFilament(printerId: Int) = post("printers/$printerId/ams/unload")

    /** Reads a slot's RFID tag again. */
    suspend fun refreshSlot(printerId: Int, amsId: Int, slot: Int) = post("printers/$printerId/ams/$amsId/slot/$slot/refresh")

    suspend fun printObjects(printerId: Int): PrintObjects = get("printers/$printerId/print/objects", PrintObjects.serializer())

    suspend fun skipObjects(printerId: Int, ids: List<Int>) = post("printers/$printerId/print/skip-objects", json = ids.joinToString(",", "[", "]"))

    suspend fun obico(): ObicoStatus = get("obico/printer-status", ObicoStatus.serializer())

    // ---- Maintenance and smart plugs ----

    suspend fun maintenance(printerId: Int): MaintenanceOverview = get("maintenance/printers/$printerId", MaintenanceOverview.serializer())

    /** Needs "Manage Maintenance" on the API key. */
    suspend fun performMaintenance(itemId: Int) = post("maintenance/items/$itemId/perform", json = "{}")

    /** The printer's main smart plug, or null when it has none. */
    suspend fun plugFor(printerId: Int): SmartPlug? {
        val body = http.text(Request.Builder().url(url("smart-plugs/by-printer/$printerId").build()).build())
        if (body.isBlank() || body.trim() == "null") return null
        return parse(body, SmartPlug.serializer())
    }

    suspend fun plugStatus(plugId: Int): PlugStatus = get("smart-plugs/$plugId/status", PlugStatus.serializer())

    suspend fun switchPlug(plugId: Int, on: Boolean) =
        post("smart-plugs/$plugId/control", json = if (on) """{"action":"on"}""" else """{"action":"off"}""")

    // ---- Settings ----

    suspend fun settings(): ServerSettings = get("settings", ServerSettings.serializer())

    @Volatile private var currencyCode: String? = null

    /** The currency Bambuddy counts costs in (Settings → Cost), or null when it can't be read. */
    suspend fun currency(): String? = currencyCode ?: runCatching { settings().currency }.getOrNull()?.also { currencyCode = it }

    // ---- Spoolman through Bambuddy ----

    /** Which Spoolman spool is in which AMS slot. */
    suspend fun slotAssignments(printerId: Int? = null): List<SlotAssignment> =
        get("spoolman/inventory/slot-assignments/all", ListSerializer(SlotAssignment.serializer()), printerId?.let { mapOf("printer_id" to it.toString()) }.orEmpty())

    /** Puts Spoolman spool [spoolId] in a slot; Bambuddy also sets the slot's filament on the printer. Needs "Manage Inventory". */
    suspend fun assignSlot(spoolId: Int, printerId: Int, amsId: Int, trayId: Int) = post(
        "spoolman/inventory/slot-assignments",
        json = """{"spoolman_spool_id":$spoolId,"printer_id":$printerId,"ams_id":$amsId,"tray_id":$trayId}""",
    )

    suspend fun unassignSlot(spoolId: Int) = delete("spoolman/inventory/slot-assignments/$spoolId")

    /** The Spoolman spool whose tag (RFID UID or Bambu tray UUID) matches, as Bambuddy knows them. */
    suspend fun spoolmanIdForTag(uid: String): Int? {
        val spools = get("spoolman/inventory/spools", ListSerializer(BambuddySpoolmanSpool.serializer()))
        val u = uid.uppercase().filter { it.isLetterOrDigit() }
        return spools.firstOrNull { s ->
            s.tagUid?.uppercase()?.filter { it.isLetterOrDigit() } == u || s.trayUuid?.uppercase()?.filter { it.isLetterOrDigit() } == u
        }?.id
    }

    // ---- Queue (changes need "Manage Queue") ----

    /** The live queue (pending and printing); finished jobs are in the archive. */
    suspend fun queue(): List<QueueItem> {
        val pending = get("queue/", ListSerializer(QueueItem.serializer()), mapOf("status" to "pending"))
        val printing = get("queue/", ListSerializer(QueueItem.serializer()), mapOf("status" to "printing"))
        return printing + pending
    }

    suspend fun addToQueue(body: String): QueueItem = parse(post("queue/", json = body), QueueItem.serializer())

    suspend fun updateQueueItem(id: Int, body: String): QueueItem = parse(send("PATCH", "queue/$id", body), QueueItem.serializer())

    suspend fun removeQueueItem(id: Int) = delete("queue/$id")

    suspend fun reorderQueue(order: List<Int>) = post(
        "queue/reorder",
        json = order.mapIndexed { i, id -> """{"id":$id,"position":${i + 1}}""" }.joinToString(",", """{"items":[""", "]}"),
    )

    /** Starts a job that waits for a manual start. A 409 means not enough filament; [anyway] skips that check. */
    suspend fun startQueueItem(id: Int, anyway: Boolean = false) =
        post("queue/$id/start", if (anyway) mapOf("skip_filament_check" to "true") else emptyMap())

    suspend fun cancelQueueItem(id: Int) = post("queue/$id/cancel")

    fun queueThumbnailUrl(item: QueueItem): String? = when {
        item.archiveThumbnail != null && item.archiveId != null ->
            if (item.plateId != null) url("archives/${item.archiveId}/plate-thumbnail/${item.plateId}").build().toString()
            else url("archives/${item.archiveId}/thumbnail").build().toString()
        item.libraryFileThumbnail != null && item.libraryFileId != null ->
            if (item.plateId != null) url("library/files/${item.libraryFileId}/plate-thumbnail/${item.plateId}").build().toString()
            else url("library/files/${item.libraryFileId}/thumbnail").build().toString()
        else -> null
    }

    // ---- Library ----

    /** Every file in the library, all folders. */
    suspend fun libraryFiles(): List<LibraryFile> =
        get("library/files", ListSerializer(LibraryFile.serializer()), mapOf("include_root" to "false"))

    suspend fun plates(fileId: Int): Plates = get("library/files/$fileId/plates", Plates.serializer())

    suspend fun archivePlates(archiveId: Int): Plates = get("archives/$archiveId/plates", Plates.serializer())

    suspend fun filamentRequirements(fileId: Int, plateId: Int?): FilamentRequirements =
        get("library/files/$fileId/filament-requirements", FilamentRequirements.serializer(), plateId?.let { mapOf("plate_id" to it.toString()) }.orEmpty())

    suspend fun archiveFilamentRequirements(archiveId: Int, plateId: Int?): FilamentRequirements =
        get("archives/$archiveId/filament-requirements", FilamentRequirements.serializer(), plateId?.let { mapOf("plate_id" to it.toString()) }.orEmpty())

    fun libraryThumbnailUrl(file: LibraryFile): String? =
        if (file.thumbnailPath != null) url("library/files/${file.id}/thumbnail").build().toString() else null

    fun plateThumbnailUrl(fileId: Int, plate: Int, archive: Boolean = false): String =
        url(if (archive) "archives/$fileId/plate-thumbnail/$plate" else "library/files/$fileId/plate-thumbnail/$plate").build().toString()

    /** Uploads a file to the library root, streamed from [file]. Needs "Manage Library". */
    suspend fun upload(name: String, file: okhttp3.RequestBody): UploadResult {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", name, file)
            .build()
        return parse(http.text(Request.Builder().url(url("library/files").build()).post(body).build()), UploadResult.serializer())
    }

    suspend fun presets(): SlicerPresets = get("slicer/presets", SlicerPresets.serializer())

    suspend fun slice(fileId: Int, body: String): SliceStarted = parse(post("library/files/$fileId/slice", json = body), SliceStarted.serializer())

    suspend fun sliceJob(jobId: Int): SliceJob = get("slice-jobs/$jobId", SliceJob.serializer())

    // ---- SD card (needs "Control Printer") ----

    suspend fun printerFiles(printerId: Int, path: String): PrinterFiles =
        get("printers/$printerId/files", PrinterFiles.serializer(), mapOf("path" to path))

    suspend fun printerStorage(printerId: Int): PrinterStorage = get("printers/$printerId/storage", PrinterStorage.serializer())

    fun printerFileUrl(printerId: Int, path: String): HttpUrl =
        url("printers/$printerId/files/download").addQueryParameter("path", path).build()

    suspend fun deletePrinterFile(printerId: Int, path: String) =
        http.text(Request.Builder().url(url("printers/$printerId/files").addQueryParameter("path", path).build()).delete().build())

    // ---- Archive ----

    suspend fun archives(offset: Int, limit: Int, printerId: Int? = null): List<Archive> = get(
        "archives/",
        ListSerializer(Archive.serializer()),
        buildMap {
            put("offset", offset.toString())
            put("limit", limit.toString())
            printerId?.let { put("printer_id", it.toString()) }
        },
    )

    /** Full-text search (at least two characters), optionally only one result (completed, failed, …). */
    suspend fun searchArchives(query: String, status: String?, offset: Int, limit: Int): List<Archive> = get(
        "archives/search",
        ListSerializer(Archive.serializer()),
        buildMap {
            put("q", query)
            status?.let { put("status", it) }
            put("offset", offset.toString())
            put("limit", limit.toString())
        },
    )

    suspend fun archiveDetail(id: Int): Archive = get("archives/$id", Archive.serializer())

    /** Totals, optionally from and up to a day (YYYY-MM-DD, both included). */
    suspend fun stats(from: String? = null, to: String? = null): ArchiveStats =
        get("archives/stats", ArchiveStats.serializer(), listOfNotNull(from?.let { "date_from" to it }, to?.let { "date_to" to it }).toMap())

    fun archiveThumbnailUrl(a: Archive): String? = if (a.thumbnailPath != null) url("archives/${a.id}/thumbnail").build().toString() else null

    fun timelapseUrl(a: Archive): String? = if (a.timelapsePath != null) url("archives/${a.id}/timelapse").build().toString() else null

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val TOKEN_REUSE_MS = 50 * 60 * 1000L
    }
}
