package com.vknxworks.printctrl.data.servers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.printctrl.data.auth.KeystoreCrypto
import com.vknxworks.printctrl.data.net.CleartextPolicy
import com.vknxworks.printctrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ServerKind { BAMBUDDY, SPOOLMAN }

/**
 * One server as the user set it up: one or two [addresses] (for example a home and a remote one), of
 * which the user picks the one in use ([active]). PrintCtrl never switches between them by itself.
 * Secrets are stored encrypted with a key held in the Android Keystore.
 */
@Serializable
data class ServerConfig(
    val addresses: List<String> = emptyList(),
    /**
     * Index in [addresses] of the address the user picked. Setups saved by older versions don't have
     * it and use their first address.
     */
    val active: Int = 0,
    /** Bambuddy API key, encrypted. */
    val apiKey: String? = null,
    /** Login asked for by a reverse proxy in front of the server (Basic auth), if any. */
    val username: String? = null,
    /** Encrypted. */
    val password: String? = null,
    /** SHA-256 fingerprints of self-signed certificates the user trusted, by "host:port". */
    val pins: Map<String, String> = emptyMap(),
) {
    /** The address in use: the one the user picked, or the first one if that's gone. */
    fun activeAddress(): String? = addresses.getOrNull(active) ?: addresses.firstOrNull()

    /** Index of [activeAddress] in [addresses]. */
    val activeIndex: Int get() = if (active in addresses.indices) active else 0

    fun apiKeyPlain(): String? = apiKey?.let { KeystoreCrypto.decrypt(it) }
    fun passwordPlain(): String? = password?.let { KeystoreCrypto.decrypt(it) }
}

/** Both servers are optional, but the app needs at least one. */
@Serializable
data class ServerSetup(
    val bambuddy: ServerConfig? = null,
    val spoolman: ServerConfig? = null,
) {
    operator fun get(kind: ServerKind): ServerConfig? = when (kind) {
        ServerKind.BAMBUDDY -> bambuddy
        ServerKind.SPOOLMAN -> spoolman
    }

    val isEmpty: Boolean get() = bambuddy == null && spoolman == null
}

private val StoreJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

class ServerStore(private val context: Context, scope: CoroutineScope) {
    private val key = stringPreferencesKey("servers")

    /** Null until loaded from disk. */
    val setup: StateFlow<ServerSetup?> = context.appDataStore.data
        .map { prefs ->
            val s = prefs[key]?.let { runCatching { StoreJson.decodeFromString(ServerSetup.serializer(), it) }.getOrNull() } ?: ServerSetup()
            // Plain HTTP stays allowed for addresses the user chose before.
            listOfNotNull(s.bambuddy, s.spoolman).flatMap { it.addresses }.forEach(CleartextPolicy::allowServer)
            s
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun save(kind: ServerKind, config: ServerConfig?) {
        context.appDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { StoreJson.decodeFromString(ServerSetup.serializer(), it) }.getOrNull() } ?: ServerSetup()
            val next = when (kind) {
                ServerKind.BAMBUDDY -> current.copy(bambuddy = config)
                ServerKind.SPOOLMAN -> current.copy(spoolman = config)
            }
            prefs[key] = StoreJson.encodeToString(ServerSetup.serializer(), next)
        }
    }

    /** Makes saved address [index] the one used for [kind], until the user picks another one. */
    suspend fun setActive(kind: ServerKind, index: Int) {
        context.appDataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { StoreJson.decodeFromString(ServerSetup.serializer(), it) }.getOrNull() } ?: return@edit
            val cfg = current[kind] ?: return@edit
            if (index !in cfg.addresses.indices || cfg.active == index) return@edit
            val updated = cfg.copy(active = index)
            val next = when (kind) {
                ServerKind.BAMBUDDY -> current.copy(bambuddy = updated)
                ServerKind.SPOOLMAN -> current.copy(spoolman = updated)
            }
            prefs[key] = StoreJson.encodeToString(ServerSetup.serializer(), next)
        }
    }
}
