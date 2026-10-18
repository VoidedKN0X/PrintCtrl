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
import okhttp3.HttpUrl
import okhttp3.Request

// Spoolman leaves null fields out of its answers, so everything here is optional.

@Serializable
data class SpoolmanInfo(val version: String = "", val externalDbName: String? = null)

@Serializable
data class Vendor(val id: Int = 0, val name: String = "", val emptySpoolWeight: Double? = null)

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
    val weight: Double? = null,
    val spoolWeight: Double? = null,
    val diameter: Double? = null,
)

@Serializable
data class Spool(
    val id: Int = 0,
    val filament: Filament = Filament(),
    /** Grams of filament left. */
    val remainingWeight: Double? = null,
    val initialWeight: Double? = null,
    val usedWeight: Double = 0.0,
    /** Millimetres. */
    val remainingLength: Double? = null,
    val location: String? = null,
    val lotNr: String? = null,
    val comment: String? = null,
    val archived: Boolean = false,
    val lastUsed: String? = null,
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
}

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

    suspend fun info(): SpoolmanInfo = parse(http.text(Request.Builder().url(url("info").build()).build()), SpoolmanInfo.serializer())

    /**
     * A page of spools and the total count (from the x-total-count header). Archived spools are left
     * out unless [archived] is set.
     */
    suspend fun spools(offset: Int, limit: Int, sort: String = "filament.material:asc,filament.name:asc", archived: Boolean = false): Pair<List<Spool>, Int> {
        val u = url("spool")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("sort", sort)
            .apply { if (archived) addQueryParameter("allow_archived", "true") }
            .build()
        http.newCall(Request.Builder().url(u).build()).await().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, null)
            val list = parse(body, ListSerializer(Spool.serializer()))
            val total = r.header("x-total-count")?.toIntOrNull() ?: (offset + list.size)
            return list to total
        }
    }
}
