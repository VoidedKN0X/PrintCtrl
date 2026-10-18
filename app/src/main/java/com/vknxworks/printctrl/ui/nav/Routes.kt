package com.vknxworks.printctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object PrintersRoute
@Serializable data object SpoolsRoute

@Serializable data class PrinterRoute(val id: Int)
@Serializable data class CameraRoute(val id: Int)

@Serializable data object AccountRoute
@Serializable data object SettingsRoute

/** [kind] is a ServerKind name. */
@Serializable data class ServerSetupRoute(val kind: String)
