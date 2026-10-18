package com.vknxworks.printctrl.notify

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * UnifiedPush without Google: a distributor app on the phone (ntfy) keeps one connection to your ntfy
 * server and hands PrintCtrl the messages Bambuddy posts to its endpoint. This is the receiving side of
 * the UnifiedPush Android protocol (https://unifiedpush.org/developers/spec/android/), written out here
 * instead of pulling in a library.
 */
object UnifiedPush {
    private const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    private const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    private const val FEATURE_BYTES_MESSAGE = "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_ENDPOINT = "endpoint"
    const val EXTRA_BYTES_MESSAGE = "bytesMessage"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_MESSAGE_ID = "id"
    const val EXTRA_REASON = "reason"

    private const val PREFS = "unifiedpush"
    private const val KEY_TOKEN = "token"
    private const val KEY_DISTRIBUTOR = "distributor"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_ERROR = "error"

    data class State(val distributor: String?, val endpoint: String?, val error: String?)

    private val _state = MutableStateFlow(State(null, null, null))
    val state: StateFlow<State> = _state.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        _state.value = State(p.getString(KEY_DISTRIBUTOR, null), p.getString(KEY_ENDPOINT, null), p.getString(KEY_ERROR, null))
    }

    /** Package names of installed distributors (ntfy, …). */
    fun distributors(context: Context): List<String> =
        context.packageManager.queryBroadcastReceivers(Intent(ACTION_REGISTER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()

    fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The random secret that proves a broadcast really comes from the distributor we registered with. */
    fun token(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    /** Asks [distributor] for an endpoint. The answer arrives in [PushReceiver]. */
    fun register(context: Context, distributor: String) {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_TOKEN, it).apply() }
        p.edit().putString(KEY_DISTRIBUTOR, distributor).remove(KEY_ERROR).apply()
        load(context)
        // The pending intent tells the distributor who is asking (needed before Android 14).
        val pi = PendingIntent.getBroadcast(context, 0, Intent("org.unifiedpush.dummy_app"), PendingIntent.FLAG_IMMUTABLE)
        val intent = Intent(ACTION_REGISTER).apply {
            `package` = distributor
            putExtra(EXTRA_TOKEN, token)
            putExtra("features", arrayOf(FEATURE_BYTES_MESSAGE))
            putExtra("application", context.packageName)
            putExtra("pi", pi)
            putExtra(EXTRA_MESSAGE, "PrintCtrl print notifications")
        }
        // Sharing the sender's identity (and this sendBroadcast overload) exist from Android 14 only.
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            context.sendBroadcast(intent, null, options.toBundle())
        } else {
            context.sendBroadcast(intent)
        }
    }

    fun unregister(context: Context) {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null)
        val distributor = p.getString(KEY_DISTRIBUTOR, null)
        if (token != null && distributor != null) {
            val pi = PendingIntent.getBroadcast(context, 0, Intent("org.unifiedpush.dummy_app"), PendingIntent.FLAG_IMMUTABLE)
            context.sendBroadcast(
                Intent(ACTION_UNREGISTER).apply {
                    `package` = distributor
                    putExtra(EXTRA_TOKEN, token)
                    putExtra("pi", pi)
                },
            )
        }
        // A fresh token next time, so an old endpoint can't reach the app any more.
        p.edit().clear().apply()
        load(context)
    }

    internal fun onEndpoint(context: Context, endpoint: String) {
        prefs(context).edit().putString(KEY_ENDPOINT, endpoint).remove(KEY_ERROR).apply()
        load(context)
    }

    internal fun onFailed(context: Context, reason: String?) {
        prefs(context).edit().remove(KEY_ENDPOINT).putString(KEY_ERROR, reason ?: "UNKNOWN").apply()
        load(context)
    }

    internal fun ack(context: Context, id: String?) {
        val distributor = prefs(context).getString(KEY_DISTRIBUTOR, null) ?: return
        val token = token(context) ?: return
        if (id == null) return
        context.sendBroadcast(
            Intent(ACTION_MESSAGE_ACK).apply {
                `package` = distributor
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_MESSAGE_ID, id)
            },
        )
    }
}

/**
 * Receives the distributor's answers and Bambuddy's messages. Exported (the distributor is another
 * app), so every broadcast must carry our secret token or it's ignored.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPush.EXTRA_TOKEN) ?: return
        if (token != UnifiedPush.token(context)) return
        when (intent.action) {
            UnifiedPush.ACTION_NEW_ENDPOINT -> intent.getStringExtra(UnifiedPush.EXTRA_ENDPOINT)?.let { UnifiedPush.onEndpoint(context, it) }
            UnifiedPush.ACTION_REGISTRATION_FAILED -> UnifiedPush.onFailed(context, intent.getStringExtra(UnifiedPush.EXTRA_REASON))
            UnifiedPush.ACTION_UNREGISTERED -> UnifiedPush.onFailed(context, "UNREGISTERED")
            UnifiedPush.ACTION_MESSAGE -> {
                val bytes = intent.getByteArrayExtra(UnifiedPush.EXTRA_BYTES_MESSAGE)
                    ?: intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE)?.toByteArray()
                    ?: return
                UnifiedPush.ack(context, intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE_ID))
                PushMessages.handle(context, bytes)
            }
        }
    }
}
