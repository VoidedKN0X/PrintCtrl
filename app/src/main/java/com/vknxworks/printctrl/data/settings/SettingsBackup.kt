package com.vknxworks.printctrl.data.settings

import com.vknxworks.printctrl.data.servers.ServerConfig
import com.vknxworks.printctrl.data.servers.ServerKind
import com.vknxworks.printctrl.data.servers.ServerSetup
import com.vknxworks.printctrl.data.servers.ServerStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A backup of PrintCtrl's settings as a JSON file the user keeps: app settings and the server setup
 * (addresses, which one is in use, trusted certificate fingerprints), but never API keys, passwords or
 * proxy logins. Those are encrypted with a key that never leaves this phone, and a settings file is easy
 * to leave lying around, so after a restore they're entered again (or kept, for a server already set up
 * at the same address).
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** PrintCtrl version that wrote the file, for information. */
    val appVersion: String? = null,
    /** ISO time the file was written. */
    val createdAt: String? = null,
    val settings: BackupSettings = BackupSettings(),
    val bambuddy: BackupServer? = null,
    val spoolman: BackupServer? = null,
) {
    companion object {
        const val FORMAT = "printctrl-settings"
        const val VERSION = 1
    }
}

/** App settings; every field is optional so files from older or newer versions still restore what they can. */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val dynamicColor: Boolean? = null,
    val cameraFps: Int? = null,
    val keepScreenOnCamera: Boolean? = null,
    val notifyMode: String? = null,
    val progressNotification: Boolean? = null,
    val events: List<String>? = null,
    val lowStockGrams: Int? = null,
    val lowStockAlerts: Boolean? = null,
    val firstLayerCheck: Boolean? = null,
    val currency: String? = null,
)

/** A server without its secrets. [hadApiKey] and [hadLogin] say what has to be entered again. */
@Serializable
data class BackupServer(
    val addresses: List<String> = emptyList(),
    val active: Int = 0,
    val pins: Map<String, String> = emptyMap(),
    val hadApiKey: Boolean = false,
    val hadLogin: Boolean = false,
)

/** Thrown for a file that isn't a PrintCtrl settings backup, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun export(settings: AppSettings, setup: ServerSetup?, appVersion: String, now: String): String =
        json.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(
                appVersion = appVersion,
                createdAt = now,
                settings = BackupSettings(
                    theme = settings.theme.name,
                    dynamicColor = settings.dynamicColor,
                    cameraFps = settings.cameraFps,
                    keepScreenOnCamera = settings.keepScreenOnCamera,
                    notifyMode = settings.notifyMode.name,
                    progressNotification = settings.progressNotification,
                    events = settings.events.map { it.name }.sorted(),
                    lowStockGrams = settings.lowStockGrams,
                    lowStockAlerts = settings.lowStockAlerts,
                    firstLayerCheck = settings.firstLayerCheck,
                    currency = settings.currency,
                ),
                bambuddy = setup?.bambuddy?.toBackup(),
                spoolman = setup?.spoolman?.toBackup(),
            ),
        )

    private fun ServerConfig.toBackup() = BackupServer(
        addresses = addresses,
        active = activeIndex,
        pins = pins,
        hadApiKey = apiKey != null,
        hadLogin = username != null || password != null,
    )

    /** Reads and checks a backup file. */
    fun parse(text: String): SettingsBackup {
        val b = runCatching { json.decodeFromString(SettingsBackup.serializer(), text) }.getOrNull()
            ?: throw BackupFormatException(newer = false)
        if (b.format != SettingsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (b.version > SettingsBackup.VERSION) throw BackupFormatException(newer = true)
        val servers = listOfNotNull(b.bambuddy, b.spoolman)
        if (servers.any { s -> s.addresses.isEmpty() || s.addresses.any { !it.startsWith("http://") && !it.startsWith("https://") } }) {
            throw BackupFormatException(newer = false)
        }
        return b
    }

    /**
     * Applies [backup]. Servers in the file replace the ones set up here; a server the file doesn't have
     * stays as it is. Secrets are kept when the server here shares an address with the one in the file.
     * Returns the servers whose API key or login has to be entered again.
     */
    suspend fun restore(backup: SettingsBackup, settingsRepo: SettingsRepository, store: ServerStore): List<ServerKind> {
        val s = backup.settings
        settingsRepo.update { cur ->
            cur.copy(
                theme = s.theme?.let { n -> ThemeChoice.entries.firstOrNull { it.name == n } } ?: cur.theme,
                dynamicColor = s.dynamicColor ?: cur.dynamicColor,
                cameraFps = s.cameraFps?.takeIf { it in 1..30 } ?: cur.cameraFps,
                keepScreenOnCamera = s.keepScreenOnCamera ?: cur.keepScreenOnCamera,
                notifyMode = s.notifyMode?.let { n -> NotifyMode.entries.firstOrNull { it.name == n } } ?: cur.notifyMode,
                progressNotification = s.progressNotification ?: cur.progressNotification,
                events = s.events?.mapNotNull { n -> PrintEvent.entries.firstOrNull { it.name == n } }?.toSet() ?: cur.events,
                lowStockGrams = s.lowStockGrams?.takeIf { it in 0..5000 } ?: cur.lowStockGrams,
                lowStockAlerts = s.lowStockAlerts ?: cur.lowStockAlerts,
                firstLayerCheck = s.firstLayerCheck ?: cur.firstLayerCheck,
                currency = s.currency ?: cur.currency,
            )
        }
        val current = store.setup.value
        val missing = mutableListOf<ServerKind>()
        for (kind in ServerKind.entries) {
            val b = when (kind) {
                ServerKind.BAMBUDDY -> backup.bambuddy
                ServerKind.SPOOLMAN -> backup.spoolman
            } ?: continue
            val here = current?.get(kind)
            val same = here != null && here.addresses.any { it in b.addresses }
            val restored = ServerConfig(
                addresses = b.addresses.take(2),
                active = b.active.coerceIn(0, (b.addresses.size - 1).coerceIn(0, 1)),
                apiKey = if (same) here?.apiKey else null,
                username = if (same) here?.username else null,
                password = if (same) here?.password else null,
                pins = (if (same) here?.pins.orEmpty() else emptyMap()) + b.pins,
            )
            store.save(kind, restored)
            val keyGone = b.hadApiKey && restored.apiKey == null
            val loginGone = b.hadLogin && restored.username == null && restored.password == null
            if (keyGone || loginGone) missing += kind
        }
        return missing
    }
}
