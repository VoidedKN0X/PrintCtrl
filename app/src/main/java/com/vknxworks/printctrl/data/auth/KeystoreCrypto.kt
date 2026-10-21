package com.vknxworks.printctrl.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vknxworks.printctrl.data.lock.LockVault
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts saved API keys and passwords with a non-exportable AES key held in the Android Keystore.
 * With the app lock sealing them, they're first encrypted with the lock's vault key too, so they can't
 * be read while PrintCtrl is locked.
 */
object KeystoreCrypto {
    /** The app lock's vault (set by AppContainer). */
    @Volatile
    var vault: LockVault? = null

    /** Whether newly saved secrets are sealed with the app lock (set by AppContainer). */
    @Volatile
    var sealing: () -> Boolean = { false }

    /** Encrypts a secret to save it: sealed with the app lock when [sealing] says so, then with the Keystore key. */
    fun encryptSecret(plain: String, seal: Boolean = sealing()): String =
        encrypt(if (seal) vault?.seal(plain) ?: plain else plain)

    /** Decrypts what [encryptSecret] made; null while it's sealed and PrintCtrl is locked. */
    fun decryptSecret(encoded: String): String? =
        decrypt(encoded)?.let { if (LockVault.isSealed(it)) vault?.open(it) else it }

    /** Whether [encoded] is sealed with the app lock. */
    fun isSealed(encoded: String): Boolean = decrypt(encoded)?.let(LockVault::isSealed) == true

    private const val ALIAS = "printctrl_credentials"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, 12)
        val ct = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}
