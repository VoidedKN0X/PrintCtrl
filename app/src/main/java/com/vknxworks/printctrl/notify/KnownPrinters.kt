package com.vknxworks.printctrl.notify

import android.content.Context
import com.vknxworks.printctrl.data.crypto.DataAtRest
import com.vknxworks.printctrl.data.crypto.SealedText
import com.vknxworks.printctrl.data.bambuddy.LivePrinter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A printer's id and name, nothing else. */
@Serializable
data class KnownPrinter(val id: Int, val name: String)

/**
 * Remembers which printer id belongs to which printer name. A push message from Bambuddy names the
 * printer; when it arrives while PrintCtrl isn't running, the printers haven't been loaded yet, and this
 * list lets the notification still open the right printer page. Only ids and names are kept: no job
 * names, progress or pictures. The saved list is encrypted at rest with the app's data key ([SealedText]);
 * a list that can't be opened counts as empty until the printers load again. Reading and saving ask for
 * that key, which can be slow the first time: never on the main thread.
 */
object KnownPrinters {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(KnownPrinter.serializer())
    private const val PREFS = "known_printers"
    private const val KEY = "printers"

    /** Where the removed home-screen widget kept its copy of the printers (with job names and progress). */
    private const val OLD_PREFS = "widget"
    private const val OLD_COVER_PREFIX = "widget-cover-"

    @Volatile
    private var last: List<KnownPrinter>? = null

    fun read(context: Context): List<KnownPrinter> {
        last?.let { return it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            // Until the first printer update after updating the app: the names the widget had saved.
            ?: context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        // The key is only asked for when the saved list is encrypted.
        val plain = (if (SealedText.isSealed(raw)) SealedText.open(DataAtRest.key(context), raw) else raw)
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, plain) }.getOrDefault(emptyList())
    }

    /** Saves the ids and names of [printers]; writes only when they changed. */
    fun save(context: Context, printers: List<LivePrinter>) {
        val now = printers.map { KnownPrinter(it.id, it.name) }
        if (now == last) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, SealedText.seal(DataAtRest.key(context), json.encodeToString(serializer, now))).apply()
        last = now
        dropWidgetLeftovers(context)
    }

    /** Forgets every printer (the Bambuddy server was removed). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        last = null
        dropWidgetLeftovers(context)
    }

    /** What the removed widget left on the phone: its saved printer states and the job pictures it cached. */
    private fun dropWidgetLeftovers(context: Context) {
        runCatching {
            val old = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
            if (old.contains(KEY)) old.edit().clear().apply()
            context.cacheDir.listFiles()?.filter { it.name.startsWith(OLD_COVER_PREFIX) }?.forEach { it.delete() }
        }
    }
}
