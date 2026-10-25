package com.vknxworks.printctrl.data.spoolman

import com.vknxworks.printctrl.data.net.ApiException
import com.vknxworks.printctrl.data.net.ApiJson
import com.vknxworks.printctrl.data.net.NotThisServerException
import com.vknxworks.printctrl.data.net.await
import com.vknxworks.printctrl.data.net.below
import com.vknxworks.printctrl.data.net.text
import com.vknxworks.printctrl.data.servers.ServerConnection
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

// Spoolman leaves null fields out of its answers, so everything here is optional.

@Serializable
data class SpoolmanInfo(val version: String = "", val externalDbName: String? = null)

@Serializable
data class Vendor(val id: Int = 0, val name: String = "", val emptySpoolWeight: Double? = null, val externalId: String? = null)

@Serializable
data class Filament(
    val id: Int = 0,
    val name: String? = null,
    val vendor: Vendor? = null,
    val material: String? = null,
    /** RRGGBB or RRGGBBAA, without '#'. */
    val colorHex: String? = null,
    /** Comma-separated hex colours for multi-colour filament. */
    val multiColorHexes: String? = null,
    /** Net filament weight of a full spool, grams. */
    val weight: Double? = null,
    /** Empty spool weight, grams. */
    val spoolWeight: Double? = null,
    val density: Double = 1.24,
    val diameter: Double = 1.75,
    val settingsExtruderTemp: Int? = null,
    val settingsBedTemp: Int? = null,
    val articleNumber: String? = null,
    val externalId: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(vendor?.name?.takeIf { it.isNotBlank() }, name?.takeIf { it.isNotBlank() }).joinToString(" ")
}

@Serializable
data class SpoolTag(val uid: String = "")

@Serializable
data class Spool(
    val id: Int = 0,
    val filament: Filament = Filament(),
    /** Grams of filament left. */
    val remainingWeight: Double? = null,
    /** Net weight the spool started with. */
    val initialWeight: Double? = null,
    /** Empty spool weight, grams. */
    val spoolWeight: Double? = null,
    val usedWeight: Double = 0.0,
    /** Millimetres. */
    val remainingLength: Double? = null,
    val location: String? = null,
    val lotNr: String? = null,
    val comment: String? = null,
    val archived: Boolean = false,
    val price: Double? = null,
    val registered: String? = null,
    val firstUsed: String? = null,
    val lastUsed: String? = null,
    val tags: List<SpoolTag> = emptyList(),
) {
    /** Share left, 0..1, when the starting weight is known. */
    val fractionLeft: Float?
        get() {
            val start = initialWeight ?: filament.weight ?: return null
            val left = remainingWeight ?: return null
            return if (start > 0) (left / start).toFloat().coerceIn(0f, 1f) else null
        }

    val colors: List<String>
        get() = filament.multiColorHexes?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(filament.colorHex)

    /** Empty-spool weight as Spoolman uses it for weighing: spool, then filament, then vendor. */
    val tare: Double?
        get() = spoolWeight ?: filament.spoolWeight ?: filament.vendor?.emptySpoolWeight
}

/** Spoolman won't delete a filament that spools still use. [spools] is how many, when known. */
class FilamentInUseException(val spools: Int?) : java.io.IOException("Filament still has spools")

/** One entry of SpoolmanDB, the shared catalogue of commercial filaments. */
@Serializable
data class ExternalFilament(
    val id: String = "",
    val manufacturer: String = "",
    val name: String = "",
    val material: String = "",
    val density: Double = 1.24,
    val weight: Double = 1000.0,
    val spoolWeight: Double? = null,
    val diameter: Double = 1.75,
    val colorHex: String? = null,
    val colorHexes: List<String>? = null,
    val extruderTemp: Int? = null,
    val bedTemp: Int? = null,
    val multiColorDirection: String? = null,
)

/** Live change from Spoolman's WebSocket. */
data class SpoolChange(val type: String, val spool: Spool)

/** Spoolman's REST API (everything under /api/v1). Spoolman has no login of its own. */
class SpoolmanClient(val conn: ServerConnection) {
    private val http = conn.client
    val base: HttpUrl = conn.baseUrl

    fun url(path: String): HttpUrl.Builder = base.below("api/v1/$path")

    private fun <T> parse(body: String, serializer: KSerializer<T>): T = try {
        ApiJson.decodeFromString(serializer, body)
    } catch (e: Exception) {
        throw NotThisServerException(spoolman = true)
    }

    private suspend fun <T> get(u: HttpUrl, serializer: KSerializer<T>): T =
        parse(http.text(Request.Builder().url(u).build()), serializer)

    private suspend fun <T> send(method: String, u: HttpUrl, body: JsonObject, serializer: KSerializer<T>): T =
        parse(http.text(Request.Builder().url(u).method(method, body.toString().toRequestBody(JSON)).build()), serializer)

    /** A list and the total from the x-total-count header. */
    private suspend fun <T> page(u: HttpUrl, serializer: KSerializer<T>, offset: Int): Pair<List<T>, Int> {
        http.newCall(Request.Builder().url(u).build()).await().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, messageOf(body))
            val list = parse(body, ListSerializer(serializer))
            val total = r.header("x-total-count")?.toIntOrNull() ?: (offset + list.size)
            return list to total
        }
    }

    private fun messageOf(body: String): String? = runCatching {
        ((ApiJson.parseToJsonElement(body) as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()

    suspend fun info(): SpoolmanInfo = get(url("info").build(), SpoolmanInfo.serializer())

    // ---- Spools ----

    /**
     * A page of spools and the total count. Archived spools are left out unless [archived] is set.
     * [query] narrows by filament name, vendor or material; [material] and [location] match exactly.
     */
    suspend fun spools(
        offset: Int,
        limit: Int,
        sort: String = "filament.material:asc,filament.name:asc",
        archived: Boolean = false,
        material: String? = null,
        location: String? = null,
        name: String? = null,
    ): Pair<List<Spool>, Int> {
        val u = url("spool")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("sort", sort)
            .apply {
                if (archived) addQueryParameter("allow_archived", "true")
                material?.let { addQueryParameter("filament.material", "\"$it\"") }
                location?.let { addQueryParameter("location", "\"$it\"") }
                name?.takeIf { it.isNotBlank() }?.let { addQueryParameter("filament.name", it) }
            }
            .build()
        return page(u, Spool.serializer(), offset)
    }

    suspend fun spool(id: Int): Spool = get(url("spool/$id").build(), Spool.serializer())

    /** Spools whose NFC tag has this UID (hex, any separators). */
    suspend fun spoolsByTag(uid: String): List<Spool> =
        get(url("spool").addQueryParameter("tag", uid).addQueryParameter("allow_archived", "true").build(), ListSerializer(Spool.serializer()))

    /** Spoolman's own word: "uses" [grams] of filament (negative gives it back). Behind a confirmation in the UI. */
    suspend fun use(id: Int, grams: Double): Spool =
        send("PUT", url("spool/$id/use").build(), buildJsonObject { put("use_weight", grams) }, Spool.serializer())

    /** Sets what's left from the [grossGrams] on the scale (spool and filament together). */
    suspend fun measure(id: Int, grossGrams: Double): Spool =
        send("PUT", url("spool/$id/measure").build(), buildJsonObject { put("weight", grossGrams) }, Spool.serializer())

    suspend fun updateSpool(id: Int, body: JsonObject): Spool = send("PATCH", url("spool/$id").build(), body, Spool.serializer())

    suspend fun createSpool(body: JsonObject): Spool = send("POST", url("spool").build(), body, Spool.serializer())

    suspend fun linkTag(id: Int, uid: String) {
        http.text(Request.Builder().url(url("spool/$id/tag").build()).post(buildJsonObject { put("uid", uid) }.toString().toRequestBody(JSON)).build())
    }

    /** Deletes the spool for good (Spoolman has no undo). Archiving is the gentle alternative. */
    suspend fun deleteSpool(id: Int) {
        http.text(Request.Builder().url(url("spool/$id").build()).delete().build())
    }

    /** How many spools, archived ones included, are of filament [filamentId]. */
    suspend fun spoolCount(filamentId: Int): Int {
        val u = url("spool")
            .addQueryParameter("filament.id", filamentId.toString())
            .addQueryParameter("allow_archived", "true")
            .addQueryParameter("limit", "1")
            .build()
        return page(u, Spool.serializer(), 0).second
    }

    // ---- Filaments, vendors, catalogue ----

    /**
     * Deletes the filament type. Spoolman refuses while any spool (archived ones too) still uses it;
     * that's reported as [FilamentInUseException].
     */
    suspend fun deleteFilament(id: Int) {
        try {
            http.text(Request.Builder().url(url("filament/$id").build()).delete().build())
        } catch (e: ApiException) {
            // Spoolman answers 403 "Failed to delete filament" when spools still refer to it.
            if (e.code == 403 || e.code == 409) throw FilamentInUseException(runCatching { spoolCount(id) }.getOrNull())
            throw e
        }
    }

    /** Deletes the manufacturer; Spoolman clears it from the filaments that had it. */
    suspend fun deleteVendor(id: Int) {
        http.text(Request.Builder().url(url("vendor/$id").build()).delete().build())
    }

    suspend fun filaments(): List<Filament> =
        get(url("filament").addQueryParameter("sort", "vendor.name:asc,name:asc").build(), ListSerializer(Filament.serializer()))

    suspend fun createFilament(body: JsonObject): Filament = send("POST", url("filament").build(), body, Filament.serializer())

    suspend fun vendors(): List<Vendor> = get(url("vendor").addQueryParameter("sort", "name:asc").build(), ListSerializer(Vendor.serializer()))

    suspend fun createVendor(body: JsonObject): Vendor = send("POST", url("vendor").build(), body, Vendor.serializer())

    suspend fun materials(): List<String> = get(url("material").build(), ListSerializer(String.serializer()))

    suspend fun locations(): List<String> = get(url("location").build(), ListSerializer(String.serializer()))

    suspend fun searchCatalogue(query: String, limit: Int = 30): List<ExternalFilament> =
        page(
            url("external/filament/search").addQueryParameter("query", query).addQueryParameter("limit", limit.toString()).build(),
            ExternalFilament.serializer(),
            0,
        ).first

    /**
     * Makes sure SpoolmanDB entry [e] exists as a filament here (adding its vendor if needed), and
     * returns it. Matching uses SpoolmanDB's ids, as Spoolman's own import does.
     */
    suspend fun importFilament(e: ExternalFilament): Filament {
        get(url("filament").addQueryParameter("external_id", "\"${e.id}\"").build(), ListSerializer(Filament.serializer()))
            .firstOrNull()?.let { return it }
        val vendor = get(url("vendor").addQueryParameter("external_id", "\"${e.manufacturer}\"").build(), ListSerializer(Vendor.serializer()))
            .firstOrNull()
            ?: vendors().firstOrNull { it.name.equals(e.manufacturer, ignoreCase = true) }
            ?: createVendor(buildJsonObject {
                put("name", e.manufacturer.take(64))
                put("external_id", e.manufacturer)
                e.spoolWeight?.let { put("empty_spool_weight", it) }
            })
        return createFilament(buildJsonObject {
            put("name", e.name.take(64))
            put("vendor_id", vendor.id)
            put("material", e.material)
            put("density", e.density)
            put("diameter", e.diameter)
            put("weight", e.weight)
            e.spoolWeight?.let { put("spool_weight", it) }
            e.extruderTemp?.let { put("settings_extruder_temp", it) }
            e.bedTemp?.let { put("settings_bed_temp", it) }
            val multi = e.colorHexes?.filter { it.isNotBlank() }
            if (multi != null && multi.size >= 2) {
                put("multi_color_hexes", multi.joinToString(",") { it.removePrefix("#") })
                put("multi_color_direction", e.multiColorDirection ?: "coaxial")
            } else {
                (e.colorHex ?: multi?.firstOrNull())?.let { put("color_hex", it.removePrefix("#")) }
            }
            put("external_id", e.id)
        })
    }

    // ---- Live changes ----

    /**
     * Opens Spoolman's WebSocket for spool changes. Spoolman never pings, so the client does every 25
     * seconds to keep proxies from closing the connection.
     */
    fun watchSpools(onChange: (SpoolChange) -> Unit, onEnd: (Throwable?) -> Unit): WebSocket {
        val ws = http.newBuilder().pingInterval(25, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
        return ws.newWebSocket(
            Request.Builder().url(url("spool").build()).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = runCatching { ApiJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                    if ((msg["resource"] as? JsonPrimitive)?.contentOrNull != "spool") return
                    val type = (msg["type"] as? JsonPrimitive)?.contentOrNull ?: return
                    val payload = msg["payload"] ?: return
                    val spool = runCatching { ApiJson.decodeFromJsonElement(Spool.serializer(), payload) }.getOrNull() ?: return
                    onChange(SpoolChange(type, spool))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onEnd(t)
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onEnd(null)
            },
        )
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

/** What a scanned Spoolman QR label points at. */
sealed interface SpoolCode {
    data class SpoolId(val id: Int) : SpoolCode
    data class FilamentId(val id: Int) : SpoolCode

    companion object {
        private val SHORT = Regex("""^web\+spoolman:([sf])-([0-9]+)$""", RegexOption.IGNORE_CASE)
        private val LINK = Regex("""^https?://[^/]+(?:/[^/]+)*/(spool|filament)/show/([0-9]+)/?$""", RegexOption.IGNORE_CASE)

        /** Reads the label formats Spoolman prints: WEB+SPOOLMAN:S-12, or a …/spool/show/12 link. */
        fun parse(text: String): SpoolCode? {
            val t = text.trim()
            SHORT.matchEntire(t)?.let { m ->
                val id = m.groupValues[2].toIntOrNull() ?: return null
                return if (m.groupValues[1].equals("s", true)) SpoolId(id) else FilamentId(id)
            }
            LINK.matchEntire(t)?.let { m ->
                val id = m.groupValues[2].toIntOrNull() ?: return null
                return if (m.groupValues[1].equals("spool", true)) SpoolId(id) else FilamentId(id)
            }
            return null
        }
    }
}
