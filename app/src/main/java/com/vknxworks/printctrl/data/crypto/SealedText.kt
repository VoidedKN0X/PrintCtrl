package com.vknxworks.printctrl.data.crypto

import java.util.Base64

/**
 * Short texts encrypted at rest inside a settings file (SharedPreferences), where a value has to be text:
 *
 *     "PRINTCTRL-REST1:" + Base64 of a [SealedFile] blob
 *
 * A value that doesn't start with that marker is an ordinary value from before encryption (or from a run
 * without a data key) and is used as it is, so both kinds work side by side.
 */
object SealedText {
    private const val PREFIX = "PRINTCTRL-REST1:"

    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    /**
     * [plain] sealed with [key]. Without a key, or when the sealed copy can't be opened again, [plain] itself:
     * the value is then saved the ordinary way, exactly as before encryption.
     */
    fun seal(key: ByteArray?, plain: String): String {
        if (key == null) return plain
        return try {
            val sealed = SealedFile.sealChecked(key, plain.toByteArray(Charsets.UTF_8)) ?: return plain
            PREFIX + Base64.getEncoder().encodeToString(sealed)
        } catch (e: Throwable) {
            plain
        }
    }

    /**
     * The text behind [stored]: [stored] itself when it's an ordinary value, the decrypted text when it's
     * sealed, or null when it's sealed and can't be opened (no key, another key, or damaged).
     */
    fun open(key: ByteArray?, stored: String): String? {
        if (!isSealed(stored)) return stored
        if (key == null) return null
        return try {
            String(SealedFile.open(key, Base64.getDecoder().decode(stored.substring(PREFIX.length))), Charsets.UTF_8)
        } catch (e: Throwable) {
            null
        }
    }
}
