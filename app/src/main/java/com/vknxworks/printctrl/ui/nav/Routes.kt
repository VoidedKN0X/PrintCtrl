package com.vknxworks.printctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object PrintersRoute
@Serializable data object QueueRoute
@Serializable data object ArchiveRoute
@Serializable data object SpoolsRoute

/** [confirmStop] opens straight into "Stop the print?" (from the notification's Stop button). */
@Serializable data class PrinterRoute(val id: Int, val confirmStop: Boolean = false)
@Serializable data class CameraRoute(val id: Int)
@Serializable data class PrinterFilesRoute(val id: Int)

@Serializable data class SpoolRoute(val id: Int)
@Serializable data object AddSpoolRoute
@Serializable data object ScanRoute
/** Spoolman's filament types and manufacturers, to delete ones no longer needed. */
@Serializable data object FilamentsRoute
/** Put spool [spoolId] in an AMS slot. */
@Serializable data class AssignSlotRoute(val spoolId: Int)

@Serializable data object LibraryRoute
/** Send a library file (or, with [archive], an earlier print) to a printer. */
@Serializable data class SendRoute(val id: Int, val archive: Boolean = false)
@Serializable data class ArchiveDetailRoute(val id: Int)
/** Bambuddy's library trash: restore or delete for good. */
@Serializable data object LibraryTrashRoute

@Serializable data object ProjectsRoute
@Serializable data class ProjectRoute(val id: Int)

/** Temperature (and other sensor) history of one printer. */
@Serializable data class PrinterChartsRoute(val id: Int)
/** Pressure advance (K-factor) profiles of one printer. */
@Serializable data class KProfilesRoute(val id: Int)

/** Bambuddy's built-in spool inventory, used when Spoolman isn't set up. */
@Serializable data class InventorySpoolRoute(val id: Int)
@Serializable data object AddInventorySpoolRoute
@Serializable data class TimelapseRoute(val archiveId: Int)

@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute

/** [kind] is a ServerKind name. */
@Serializable data class ServerSetupRoute(val kind: String)
