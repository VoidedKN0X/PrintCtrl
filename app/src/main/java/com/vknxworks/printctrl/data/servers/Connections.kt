package com.vknxworks.printctrl.data.servers

import com.vknxworks.printctrl.data.net.Tls
import com.vknxworks.printctrl.data.net.origin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import javax.net.ssl.SSLContext

/** A server address the app is talking to, with an HTTP client set up for it (credentials, pinned certificate). */
class ServerConnection(val kind: ServerKind, val baseUrl: HttpUrl, val client: OkHttpClient) {
    /** Changes whenever the address or its settings change, so screens can reload. */
    val key: String = "${kind.name}:$baseUrl:${System.identityHashCode(client)}"
}

sealed interface ConnState {
    /** The user hasn't set this server up (or skipped it). */
    data object NotSet : ConnState
    /** Set up, on the address the user picked. Whether it answers shows up in the requests themselves. */
    data class Ready(val conn: ServerConnection) : ConnState
}

/** Builds HTTP clients for one server address. */
object ServerClients {
    fun build(
        base: OkHttpClient,
        kind: ServerKind,
        url: HttpUrl,
        apiKey: String?,
        username: String?,
        password: String?,
        pin: String?,
    ): OkHttpClient {
        val origin = url.origin()
        val b = base.newBuilder()
        // Credentials are only ever added to requests for this server; RedirectGuard strips them if a
        // redirect leaves it.
        b.interceptors().add(0, okhttp3.Interceptor { chain ->
            val req = chain.request()
            if (req.url.origin() != origin) return@Interceptor chain.proceed(req)
            val nb = req.newBuilder()
            when (kind) {
                ServerKind.BAMBUDDY -> if (!apiKey.isNullOrBlank()) nb.header("X-API-Key", apiKey)
                ServerKind.SPOOLMAN -> if (!username.isNullOrBlank()) {
                    nb.header("Authorization", Credentials.basic(username, password.orEmpty()))
                }
            }
            // Spoolman refuses writes that carry a browser Origin header; the app never sends one.
            nb.removeHeader("Origin")
            chain.proceed(nb.build())
        })
        if (pin != null && url.isHttps) {
            val tm = Tls.PinnedTrustManager(pin, Tls.systemTrustManager())
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), null)
            b.sslSocketFactory(ctx.socketFactory, tm)
            b.hostnameVerifier(Tls.pinnedHostnameVerifier(pin, url.host))
        }
        return b.build()
    }

    fun build(base: OkHttpClient, kind: ServerKind, url: HttpUrl, config: ServerConfig): OkHttpClient =
        build(base, kind, url, config.apiKeyPlain(), config.username, config.passwordPlain(), config.pins[url.origin()])

}

/**
 * Keeps one connection per configured server, on the address the user picked for it
 * ([ServerConfig.active]). PrintCtrl never tests addresses or switches between them by itself, also
 * not when the phone changes networks; only the user changes the address (Account › Address in use).
 */
class Connections(
    private val base: OkHttpClient,
    private val store: ServerStore,
    scope: CoroutineScope,
) {
    private val _bambuddy = MutableStateFlow<ConnState>(ConnState.NotSet)
    private val _spoolman = MutableStateFlow<ConnState>(ConnState.NotSet)
    val bambuddy: StateFlow<ConnState> = _bambuddy.asStateFlow()
    val spoolman: StateFlow<ConnState> = _spoolman.asStateFlow()

    /** The settings each current connection was built from. */
    private val builtFrom = mutableMapOf<ServerKind, ServerConfig>()

    init {
        scope.launch {
            store.setup.filterNotNull().collect { s ->
                update(ServerKind.BAMBUDDY, s.bambuddy, _bambuddy)
                update(ServerKind.SPOOLMAN, s.spoolman, _spoolman)
            }
        }
    }

    fun state(kind: ServerKind): StateFlow<ConnState> = when (kind) {
        ServerKind.BAMBUDDY -> bambuddy
        ServerKind.SPOOLMAN -> spoolman
    }

    /**
     * Builds both clients again from the saved setup: after the app lock locked (sealed secrets can't be
     * read, so the clients drop them) or unlocked (they can be read again).
     */
    @Synchronized
    fun rebuild() {
        val s = store.setup.value ?: return
        builtFrom.clear()
        update(ServerKind.BAMBUDDY, s.bambuddy, _bambuddy)
        update(ServerKind.SPOOLMAN, s.spoolman, _spoolman)
    }

    /** Uses saved address [index] for [kind] from now on, until the user picks another one. */
    suspend fun useAddress(kind: ServerKind, index: Int) {
        store.setActive(kind, index)
    }

    @Synchronized
    private fun update(kind: ServerKind, cfg: ServerConfig?, out: MutableStateFlow<ConnState>) {
        val url = cfg?.activeAddress()?.toHttpUrlOrNull()
        if (cfg == null || url == null) {
            builtFrom.remove(kind)
            out.value = ConnState.NotSet
            return
        }
        // Same settings and address (only the other server changed): keep the connection and its live streams.
        if (out.value is ConnState.Ready && builtFrom[kind] == cfg) return
        builtFrom[kind] = cfg
        out.value = ConnState.Ready(ServerConnection(kind, url, ServerClients.build(base, kind, url, cfg)))
    }
}
