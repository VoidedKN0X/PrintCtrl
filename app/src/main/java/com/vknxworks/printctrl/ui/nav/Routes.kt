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
/** Put spool [spoolId] in an AMS slot. */
@Serializable data class AssignSlotRoute(val spoolId: Int)

@Serializable data object LibraryRoute
/** Send a library file (or, with [archive], an earlier print) to a printer. */
@Serializable data class SendRoute(val id: Int, val archive: Boolean = false)
@Serializable data class ArchiveDetailRoute(val id: Int)
@Serializable data class TimelapseRoute(val archiveId: Int)

@Serializable data object AccountRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute

/** [kind] is a ServerKind name. */
@Serializable data class ServerSetupRoute(val kind: String)
