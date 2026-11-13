package com.vknxworks.printctrl.data.bambuddy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Editing the print history and managing the file library. Endpoints from Bambuddy's archives.py,
// library.py and library_trash.py. Editing and deleting prints needs "Manage Archives" on the API key;
// anything that changes the library (folders, renaming, moving, trash) needs "Manage Library".

// ---------- Print history ----------

/** A tag used on prints, with how many prints carry it. */
@Serializable
data class ArchiveTag(val name: String = "", val count: Int = 0)

/** What deleting a print takes with it: queue entries that point at it, and whether one is printing. */
@Serializable
data class ArchiveDeleteImpact(val relatedQueueItems: Int = 0, val currentlyPrinting: Int = 0)

/** Tags as Bambuddy stores them: one comma-separated string. */
fun parseTags(tags: String?): List<String> =
    tags.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Changes notes and tags. Blank notes or no tags clear them. */
suspend fun BambuddyClient.updateArchive(id: Int, notes: String?, tags: List<String>): Archive {
    val body = buildJsonObject {
        put("notes", notes?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
        put("tags", tags.joinToString(",").takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    return parse(send("PATCH", "archives/$id", body.toString()), Archive.serializer())
}

/** Flips a print's favourite mark; returns the print as it is now. */
suspend fun BambuddyClient.toggleFavorite(id: Int): Archive = parse(post("archives/$id/favorite"), Archive.serializer())

suspend fun BambuddyClient.archiveTags(): List<ArchiveTag> = get("archives/tags", ListSerializer(ArchiveTag.serializer()))

suspend fun BambuddyClient.archiveDeleteImpact(id: Int): ArchiveDeleteImpact =
    get("archives/$id/delete-impact", ArchiveDeleteImpact.serializer())

/**
 * Deletes a print from the history (its files and linked queue entries too). With [purgeStats] it also
 * stops counting in the statistics; otherwise its filament, time and cost stay in the totals.
 */
suspend fun BambuddyClient.deleteArchive(id: Int, purgeStats: Boolean) {
    send("DELETE", "archives/$id", "", mapOf("purge_stats" to purgeStats.toString()))
}

/**
 * Bumped whenever a print is edited or deleted, so the history list (kept alive behind the print's
 * page) reloads when you come back to it.
 */
object ArchiveChanges {
    val version = MutableStateFlow(0)
    fun changed() {
        version.value++
    }
}

// ---------- Library ----------

/** A library folder with its subfolders, as Bambuddy's folder tree returns it. */
@Serializable
data class LibraryFolder(
    val id: Int = 0,
    val name: String = "",
    val parentId: Int? = null,
    val projectName: String? = null,
    val isExternal: Boolean = false,
    /** A linked folder on a NAS that Bambuddy may only read. */
    val externalReadonly: Boolean = false,
    val fileCount: Int = 0,
    val children: List<LibraryFolder> = emptyList(),
)

/** Every folder in the tree, depth-first, with how deep it sits (0 for a top-level folder). */
fun List<LibraryFolder>.flattenTree(depth: Int = 0): List<Pair<LibraryFolder, Int>> =
    flatMap { listOf(it to depth) + it.children.flattenTree(depth + 1) }

@Serializable
data class TrashedFile(
    val id: Int = 0,
    val filename: String = "",
    val fileSize: Long = 0,
    val folderName: String? = null,
    val deletedAt: String? = null,
    /** When Bambuddy deletes it for good by itself. */
    val autoPurgeAt: String? = null,
)

@Serializable
data class LibraryTrash(val items: List<TrashedFile> = emptyList(), val total: Int = 0, val retentionDays: Int = 0)

suspend fun BambuddyClient.libraryFolders(): List<LibraryFolder> = get("library/folders/", ListSerializer(LibraryFolder.serializer()))

suspend fun BambuddyClient.createFolder(name: String, parentId: Int?) {
    val body = buildJsonObject {
        put("name", name.trim())
        put("parent_id", parentId?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    post("library/folders/", json = body.toString())
}

suspend fun BambuddyClient.renameFolder(id: Int, name: String) {
    send("PUT", "library/folders/$id", buildJsonObject { put("name", name.trim()) }.toString())
}

/** Deletes a folder with everything in it. */
suspend fun BambuddyClient.deleteFolder(id: Int) {
    delete("library/folders/$id")
}

suspend fun BambuddyClient.renameLibraryFile(id: Int, filename: String) {
    send("PUT", "library/files/$id", buildJsonObject { put("filename", filename.trim()) }.toString())
}

@Serializable
data class MoveSkip(val fileId: Int = 0, val code: String = "", val reason: String = "")

@Serializable
data class MoveResult(val moved: Int = 0, val skipped: Int = 0, val skippedReasons: List<MoveSkip> = emptyList())

/** A move Bambuddy answered but didn't do (a name already taken on a NAS folder, a read-only source, …). */
class MoveSkippedException(val reason: String) : Exception(reason)

/**
 * Moves a file into [folderId], or to the top of the library when null. Bambuddy answers "success" even
 * when it skipped the file, so a skip is thrown as [MoveSkippedException] with its reason.
 */
suspend fun BambuddyClient.moveLibraryFile(id: Int, folderId: Int?) {
    val body = buildJsonObject {
        put("file_ids", buildJsonArray { add(JsonPrimitive(id)) })
        put("folder_id", folderId?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    val result = runCatching { parse(post("library/files/move", json = body.toString()), MoveResult.serializer()) }.getOrNull() ?: return
    if (result.moved == 0 && result.skipped > 0) {
        throw MoveSkippedException(result.skippedReasons.firstOrNull()?.reason?.takeIf { it.isNotBlank() } ?: "skipped")
    }
}

/** Moves a file to Bambuddy's trash, where it can be restored until it's emptied. */
suspend fun BambuddyClient.trashLibraryFile(id: Int) {
    delete("library/files/$id")
}

suspend fun BambuddyClient.libraryTrash(): LibraryTrash = get("library/trash", LibraryTrash.serializer(), mapOf("limit" to "500"))

suspend fun BambuddyClient.restoreFromTrash(id: Int) {
    post("library/trash/$id/restore")
}

suspend fun BambuddyClient.deleteFromTrash(id: Int) {
    delete("library/trash/$id")
}

suspend fun BambuddyClient.emptyTrash() {
    delete("library/trash")
}
