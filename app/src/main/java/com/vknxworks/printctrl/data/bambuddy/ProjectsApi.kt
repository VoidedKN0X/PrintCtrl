package com.vknxworks.printctrl.data.bambuddy

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// Bambuddy projects (routes/projects.py). Reading needs "Read Status" on the API key. Bambuddy also
// has a "Manage Projects" key permission for creating, editing and deleting them; the app doesn't
// offer those yet, so it only reads.

/** A project in the list, with Bambuddy's counts. */
@Serializable
data class ProjectBrief(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    /** "#RRGGBB" or null. */
    val color: String? = null,
    /** active, completed or archived. */
    val status: String = "active",
    val targetCount: Int? = null,
    val targetPartsCount: Int? = null,
    val budget: Double? = null,
    val tags: String? = null,
    val dueDate: String? = null,
    /** low, normal, high, urgent. */
    val priority: String = "normal",
    val createdAt: String? = null,
    val archiveCount: Int = 0,
    val totalItems: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val queueCount: Int = 0,
    val progressPercent: Double? = null,
    val parentId: Int? = null,
    val childCount: Int = 0,
    val archives: List<ProjectArchivePreview> = emptyList(),
    val url: String? = null,
    val coverImageFilename: String? = null,
)

@Serializable
data class ProjectArchivePreview(
    val id: Int = 0,
    val printName: String? = null,
    val thumbnailPath: String? = null,
    val status: String = "",
)

@Serializable
data class ProjectStats(
    val totalArchives: Int = 0,
    val totalItems: Int = 0,
    val completedPrints: Int = 0,
    val failedPrints: Int = 0,
    val queuedPrints: Int = 0,
    val inProgressPrints: Int = 0,
    val totalPrintTimeHours: Double = 0.0,
    val totalFilamentGrams: Double = 0.0,
    val progressPercent: Double? = null,
    val partsProgressPercent: Double? = null,
    val estimatedCost: Double = 0.0,
    val totalEnergyKwh: Double = 0.0,
    val totalEnergyCost: Double = 0.0,
    val remainingPrints: Int? = null,
    val remainingParts: Int? = null,
    val bomTotalItems: Int = 0,
    val bomCompletedItems: Int = 0,
    val bomCost: Double = 0.0,
)

@Serializable
data class ProjectChild(
    val id: Int = 0,
    val name: String = "",
    val color: String? = null,
    val status: String = "active",
    val progressPercent: Double? = null,
    val totalArchives: Int = 0,
    val completedPrints: Int = 0,
)

/** One project in full. */
@Serializable
data class Project(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    val color: String? = null,
    val status: String = "active",
    val targetCount: Int? = null,
    val targetPartsCount: Int? = null,
    val notes: String? = null,
    val tags: String? = null,
    val dueDate: String? = null,
    val priority: String = "normal",
    val budget: Double? = null,
    val parentId: Int? = null,
    val parentName: String? = null,
    val children: List<ProjectChild> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val stats: ProjectStats? = null,
    /** Including sub-projects; only set when there are any. */
    val rollupStats: ProjectStats? = null,
    val url: String? = null,
    val coverImageFilename: String? = null,
)

/** A part to buy or make for a project (bill of materials). */
@Serializable
data class BomItem(
    val id: Int = 0,
    val name: String = "",
    val quantityNeeded: Int = 1,
    val quantityAcquired: Int = 0,
    val unitPrice: Double? = null,
    val sourcingUrl: String? = null,
    val archiveName: String? = null,
    val remarks: String? = null,
    val isComplete: Boolean = false,
)

@Serializable
data class TimelineEvent(
    /** archive_added, queue_started, queue_completed, status_changed, note_updated, … */
    val eventType: String = "",
    val timestamp: String? = null,
    val title: String = "",
    val description: String? = null,
)

@Serializable
data class ProjectFileProgress(val fileId: Int = 0, val completedCount: Int = 0)

suspend fun BambuddyClient.projects(): List<ProjectBrief> =
    get("projects/", ListSerializer(ProjectBrief.serializer()))

suspend fun BambuddyClient.project(id: Int): Project = get("projects/$id", Project.serializer())

suspend fun BambuddyClient.projectArchives(id: Int, limit: Int = 100): List<Archive> =
    get("projects/$id/archives", ListSerializer(Archive.serializer()), mapOf("limit" to limit.toString()))

/** Library files in the folders linked to the project. Needs library read access ("Read Status"). */
suspend fun BambuddyClient.projectFiles(id: Int): List<LibraryFile> =
    get("library/files", ListSerializer(LibraryFile.serializer()), mapOf("project_id" to id.toString(), "include_root" to "false"))

/** Completed runs per library file of the project; files with none are left out. */
suspend fun BambuddyClient.projectFileProgress(id: Int): List<ProjectFileProgress> =
    get("projects/$id/file-progress", ListSerializer(ProjectFileProgress.serializer()))

suspend fun BambuddyClient.projectBom(id: Int): List<BomItem> = get("projects/$id/bom", ListSerializer(BomItem.serializer()))

suspend fun BambuddyClient.projectTimeline(id: Int): List<TimelineEvent> =
    get("projects/$id/timeline", ListSerializer(TimelineEvent.serializer()))

/** The project's cover picture; loaded with the API key header like other Bambuddy pictures. */
fun BambuddyClient.projectCoverUrl(p: ProjectBrief): String? =
    if (p.coverImageFilename != null) url("projects/${p.id}/cover-image").build().toString() else null

fun BambuddyClient.projectCoverUrl(p: Project): String? =
    if (p.coverImageFilename != null) url("projects/${p.id}/cover-image").build().toString() else null
