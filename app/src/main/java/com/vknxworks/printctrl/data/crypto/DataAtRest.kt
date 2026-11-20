package com.vknxworks.printctrl.data.crypto

import android.content.Context

/**
 * The one [DataKeyStore] of the app: the data key in `data.key` in the app's private folder, wrapped by the
 * Keystore key `printctrl_data_at_rest`. Separate from the keys that protect the login and the app lock.
 */
object DataAtRest {
    private const val ALIAS = "printctrl_data_at_rest"

    @Volatile
    private var store: DataKeyStore? = null

    private fun store(context: Context): DataKeyStore = store ?: synchronized(this) {
        store ?: DataKeyStore(context.applicationContext.filesDir, KeystoreWrapper(ALIAS)).also { store = it }
    }

    /**
     * The data key, made the first time it's needed, or null when there is none to be had (the caller then
     * reads and writes ordinary values as before). Slow the first time in a run (it asks the phone's secure
     * hardware): never call it on the main thread.
     */
    fun key(context: Context): ByteArray? = try {
        store(context).get(create = true)
    } catch (e: Throwable) {
        null
    }
}
