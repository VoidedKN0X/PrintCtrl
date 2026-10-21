package com.vknxworks.printctrl.notify

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vknxworks.printctrl.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import com.vknxworks.printctrl.R

/**
 * Push through ntfy only: the ntfy app on the phone keeps one connection to your ntfy server and hands
 * PrintCtrl the messages Bambuddy posts to its endpoint. This is the app side of the UnifiedPush
 * Android protocol (https://unifiedpush.org/developers/spec/android/), written out here instead of
 * pulling in a library, and it only ever talks to the ntfy app.
 */
object UnifiedPush {
    /** The ntfy app, the only push app PrintCtrl registers with. */
    const val NTFY = "io.heckel.ntfy"

    /** Where to get ntfy. */
    const val NTFY_DOWNLOAD = "https://f-droid.org/packages/io.heckel.ntfy/"

    private const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    private const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val ACTION_TEMP_UNAVAILABLE = "org.unifiedpush.android.connector.TEMP_UNAVAILABLE"
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
    private const val KEY_TEMP_UNAVAILABLE = "temp_unavailable"

    /**
     * [distributor] is set once PrintCtrl asked ntfy for an address; [tempUnavailable] means ntfy said
     * your ntfy server can't be reached for now (the address stays valid).
     */
    data class State(val distributor: String?, val endpoint: String?, val error: String?, val tempUnavailable: Boolean = false)

    private val _state = MutableStateFlow(State(null, null, null))
    val state: StateFlow<State> = _state.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Reads the saved registration. A registration with another push app (possible before PrintCtrl
     * became ntfy-only; it only exists while push is on) is cancelled there, and PrintCtrl registers
     * with ntfy instead if it's installed. Otherwise push stays off until ntfy is installed.
     */
    fun load(context: Context) {
        val p = prefs(context)
        val distributor = p.getString(KEY_DISTRIBUTOR, null)
        if (distributor != null && distributor != NTFY) {
            p.getString(KEY_TOKEN, null)?.let { sendUnregister(context, distributor, it) }
            p.edit().clear().apply()
            if (ntfyInstalled(context)) {
                register(context)
                return
            }
        }
        publish(context)
    }

    private fun publish(context: Context) {
        val p = prefs(context)
        _state.value = State(
            p.getString(KEY_DISTRIBUTOR, null),
            p.getString(KEY_ENDPOINT, null),
            p.getString(KEY_ERROR, null),
            p.getBoolean(KEY_TEMP_UNAVAILABLE, false),
        )
    }

    /** Whether the ntfy app is installed and can hand out push addresses. */
    fun ntfyInstalled(context: Context): Boolean =
        context.packageManager.queryBroadcastReceivers(Intent(ACTION_REGISTER).setPackage(NTFY), 0).isNotEmpty()

    /** The random secret that proves a broadcast really comes from ntfy for this registration. */
    fun token(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    /** Asks ntfy for an endpoint. The answer arrives in [PushReceiver]. */
    fun register(context: Context) {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString().also { p.edit().putString(KEY_TOKEN, it).apply() }
        p.edit().putString(KEY_DISTRIBUTOR, NTFY).remove(KEY_ERROR).remove(KEY_TEMP_UNAVAILABLE).apply()
        publish(context)
        val intent = Intent(ACTION_REGISTER).apply {
            putExtra(EXTRA_TOKEN, token)
            putExtra("features", arrayOf(FEATURE_BYTES_MESSAGE))
            putExtra("application", context.packageName)
            putExtra(EXTRA_MESSAGE, context.getString(R.string.push_registration_message))
        }
        sendToDistributor(context, NTFY, intent)
    }

    fun unregister(context: Context) {
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null)
        val distributor = p.getString(KEY_DISTRIBUTOR, null)
        if (token != null && distributor != null) sendUnregister(context, distributor, token)
        // A fresh token next time, so an old endpoint can't reach the app any more.
        p.edit().clear().apply()
        publish(context)
    }

    private fun sendUnregister(context: Context, distributor: String, token: String) {
        runCatching { sendToDistributor(context, distributor, Intent(ACTION_UNREGISTER).putExtra(EXTRA_TOKEN, token)) }
    }

    /**
     * Sends [intent] to [distributor] with PrintCtrl's identity: shared with the broadcast from
     * Android 14, through a pending intent before that.
     */
    private fun sendToDistributor(context: Context, distributor: String, intent: Intent) {
        intent.setPackage(distributor)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            context.sendBroadcast(intent, null, options.toBundle())
        } else {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent("org.unifiedpush.dummy_app").setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            context.sendBroadcast(intent.putExtra("pi", pi))
        }
    }

    internal fun onEndpoint(context: Context, endpoint: String) {
        prefs(context).edit().putString(KEY_ENDPOINT, endpoint).remove(KEY_ERROR).remove(KEY_TEMP_UNAVAILABLE).apply()
        publish(context)
    }

    internal fun onFailed(context: Context, reason: String?) {
        val p = prefs(context)
        // Ignored once ntfy has given an address for this token.
        if (p.getString(KEY_ENDPOINT, null) != null) return
        // A new token for the next try.
        p.edit().remove(KEY_TOKEN).putString(KEY_ERROR, reason ?: "UNKNOWN").apply()
        publish(context)
    }

    internal fun onUnregistered(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN).remove(KEY_ENDPOINT).remove(KEY_TEMP_UNAVAILABLE)
            .putString(KEY_ERROR, "UNREGISTERED")
            .apply()
        publish(context)
    }

    internal fun onTempUnavailable(context: Context) {
        prefs(context).edit().putBoolean(KEY_TEMP_UNAVAILABLE, true).apply()
        publish(context)
    }

    internal fun ack(context: Context, id: String?) {
        if (id == null) return
        val token = token(context) ?: return
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_MESSAGE_ACK).apply {
                    `package` = NTFY
                    putExtra(EXTRA_TOKEN, token)
                    putExtra(EXTRA_MESSAGE_ID, id)
                },
            )
        }
    }
}

/**
 * Receives ntfy's answers and Bambuddy's messages. Exported (ntfy is another app), so every broadcast
 * must carry our secret token, and from Android 14 come from the ntfy app, or it's ignored.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val from = sentFromPackage
            if (from != null && from != UnifiedPush.NTFY) return
        }
        val token = intent.getStringExtra(UnifiedPush.EXTRA_TOKEN) ?: return
        if (token != UnifiedPush.token(context)) return
        when (intent.action) {
            UnifiedPush.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra(UnifiedPush.EXTRA_ENDPOINT) ?: return
                UnifiedPush.onEndpoint(context, endpoint)
                UnifiedPush.ack(context, intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE_ID))
            }
            UnifiedPush.ACTION_REGISTRATION_FAILED -> UnifiedPush.onFailed(context, intent.getStringExtra(UnifiedPush.EXTRA_REASON))
            UnifiedPush.ACTION_UNREGISTERED -> UnifiedPush.onUnregistered(context)
            UnifiedPush.ACTION_TEMP_UNAVAILABLE -> UnifiedPush.onTempUnavailable(context)
            UnifiedPush.ACTION_MESSAGE -> {
                val bytes = intent.getByteArrayExtra(UnifiedPush.EXTRA_BYTES_MESSAGE)
                    ?: intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE)?.toByteArray()
                    ?: return
                UnifiedPush.ack(context, intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE_ID))
                val pending = goAsync()
                val app = context.applicationContext
                app.container.appScope.launch {
                    try {
                        PushMessages.handle(app, bytes)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
