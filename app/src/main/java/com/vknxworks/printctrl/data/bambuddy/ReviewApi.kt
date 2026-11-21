package com.vknxworks.printctrl.data.bambuddy

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import com.vknxworks.printctrl.data.net.text

// Bambuddy calls for judging a finished print, photos of the result, where a model came from, a
// spool's usage per print, drying that starts later, and uploads waiting to be reviewed. Routes and
// fields are the ones in Bambuddy's own source (backend/app/api/routes: archives.py, library.py,
// inventory.py, spoolman_inventory.py, scheduled_dryings.py, pending_uploads.py).

// ---------- Result of a print ----------

/**
 * Records how a finished print turned out: "good", "reject" (failed), or null to clear it. With
 * [failureReason] and [notes] given, those are saved in the same request (a blank one clears it).
 * Bambuddy versions without this verdict ignore the field: the returned print then has no verdict.
 */
suspend fun BambuddyClient.setVerdict(id: Int, verdict: String?, failureReason: String? = null, notes: String? = null): Archive {
    val body = buildJsonObject {
        put("user_verdict", verdict?.let { JsonPrimitive(it) } ?: JsonNull)
        // "dialog": a person answered the question, as on Bambuddy's own page.
        if (verdict != null) put("user_verdict_source", "dialog")
        if (failureReason != null) put("failure_reason", failureReason.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
        if (notes != null) put("notes", notes.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    return parse(send("PATCH", "archives/$id", body.toString()), Archive.serializer())
}

@Serializable
data class PhotoResult(val photos: List<String> = emptyList())

/** Adds a photo of the result. [name] must end in .jpg, .jpeg, .png or .webp (Bambuddy checks). */
suspend fun BambuddyClient.uploadArchivePhoto(id: Int, name: String, file: RequestBody): PhotoResult {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", name, file).build()
    return parse(conn.client.text(Request.Builder().url(url("archives/$id/photos").build()).post(body).build()), PhotoResult.serializer())
}

/** Removes a photo of the result from the server. Needs permission to delete prints. */
suspend fun BambuddyClient.deleteArchivePhoto(id: Int, filename: String) {
    conn.client.text(Request.Builder().url(url("archives/$id/photos").addPathSegment(filename).build()).delete().build())
}

fun BambuddyClient.archivePhotoUrl(id: Int, filename: String): String =
    url("archives/$id/photos").addPathSegment(filename).build().toString()

// ---------- Where a model came from ----------

/** Sets or clears (blank) your own link for a print. */
suspend fun BambuddyClient.setArchiveLink(id: Int, link: String): Archive {
    val body = buildJsonObject { put("external_url", link.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull) }
    return parse(send("PATCH", "archives/$id", body.toString()), Archive.serializer())
}

/** Sets or clears (blank) your own link for a library file. Bambuddy only accepts http:// and https:// links. */
suspend fun BambuddyClient.setLibraryLink(id: Int, link: String): LibraryFile {
    // An empty string is what clears it on this route.
    val body = buildJsonObject { put("external_url", link.trim()) }
    return parse(send("PUT", "library/files/$id", body.toString()), LibraryFile.serializer())
}

// ---------- Spool usage ----------

/** One print's use of a spool, as Bambuddy recorded it. PrintCtrl only reads these. */
@Serializable
data class SpoolUsage(
    val id: Int = 0,
    val printerId: Int? = null,
    val printName: String? = null,
    val weightUsed: Double = 0.0,
    val percentUsed: Int = 0,
    /** completed, failed, … */
    val status: String = "",
    val cost: Double? = null,
    val createdAt: String? = null,
)

/** Usage of a spool in Bambuddy's own inventory, newest first. */
suspend fun BambuddyClient.inventorySpoolUsage(spoolId: Int, limit: Int = 100): List<SpoolUsage> =
    get("inventory/spools/$spoolId/usage", ListSerializer(SpoolUsage.serializer()), mapOf("limit" to limit.toString()))

/** Usage of a Spoolman spool as Bambuddy charged it, newest first. */
suspend fun BambuddyClient.spoolmanSpoolUsage(spoolmanId: Int, limit: Int = 100): List<SpoolUsage> =
    get("spoolman/inventory/spools/$spoolmanId/usage", ListSerializer(SpoolUsage.serializer()), mapOf("limit" to limit.toString()))

// ---------- Drying that starts later ----------

@Serializable
data class ScheduledDrying(
    val id: Int = 0,
    val printerId: Int = 0,
    val amsId: Int = 0,
    val temp: Int = 0,
    val durationHours: Int = 0,
    val filament: String = "",
    val rotateTray: Boolean = false,
    /** ISO time ending in Z, or null for "as soon as the printer is free". */
    val startAfter: String? = null,
    /** pending, running, failed. */
    val status: String = "",
    val waitingReason: String? = null,
    val errorMessage: String? = null,
)

suspend fun BambuddyClient.scheduledDryings(printerId: Int): List<ScheduledDrying> =
    get("scheduled-dryings", ListSerializer(ScheduledDrying.serializer()), mapOf("printer_id" to printerId.toString()))

/** Has Bambuddy start a drying run at [startAfterUtc] ("2026-11-21T22:00:00", UTC without a zone, as Bambuddy stores it). */
suspend fun BambuddyClient.scheduleDrying(
    printerId: Int,
    amsId: Int,
    temp: Int,
    hours: Int,
    filament: String,
    rotateTray: Boolean,
    startAfterUtc: String,
): ScheduledDrying {
    val body = buildJsonObject {
        put("printer_id", printerId)
        put("ams_id", amsId)
        put("temp", temp)
        put("duration_hours", hours)
        put("filament", filament.take(50))
        put("rotate_tray", rotateTray)
        put("start_after", startAfterUtc)
    }
    return parse(post("scheduled-dryings", json = body.toString()), ScheduledDrying.serializer())
}

/** Cancels a drying run that hasn't started (or stops one that runs), or dismisses a failed one. */
suspend fun BambuddyClient.cancelScheduledDrying(id: Int) {
    delete("scheduled-dryings/$id")
}

// ---------- Uploads waiting for review ----------

/** A print file sent to Bambuddy's virtual printer that waits for someone to keep or discard it. */
@Serializable
data class PendingUpload(
    val id: Int = 0,
    val filename: String = "",
    val displayName: String = "",
    val fileSize: Long = 0,
    val sourceIp: String? = null,
    val status: String = "",
    val tags: String? = null,
    val notes: String? = null,
    val uploadedAt: String? = null,
) {
    val name: String get() = displayName.ifBlank { filename }
}

suspend fun BambuddyClient.pendingUploads(): List<PendingUpload> = get("pending-uploads/", ListSerializer(PendingUpload.serializer()))

/** Approves an upload: Bambuddy moves it into the archive. */
suspend fun BambuddyClient.approveUpload(id: Int) {
    post("pending-uploads/$id/archive")
}

/** Discards an upload: Bambuddy deletes the file. */
suspend fun BambuddyClient.discardUpload(id: Int) {
    delete("pending-uploads/$id")
}
