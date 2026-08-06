package org.tamodak.killit.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hashes the passkey for storage.
 *
 * Salted SHA-256, one pass. The credential itself is never written anywhere, and that is the whole
 * goal here — not resistance to an offline attack on the hash.
 *
 * ### Why this is deliberately plain
 *
 * A slow KDF exists to make guessing the *hash* expensive for someone who has stolen it. Reading
 * Killit's stored hash means root or adb on the device, and anyone with that can simply remove the
 * device admin or unsuspend the packages directly — the passkey stops being the obstacle. So the
 * work factor would have been buying protection against an attacker who has already won, at the
 * cost of making every unlock slow on real hardware.
 *
 * The salt stays because it costs nothing and stops two identical passkeys producing identical
 * hashes.
 *
 * Fast enough that it needs no dispatcher: hashing a short string is microseconds, so callers can
 * invoke this directly from any thread.
 */
class CredentialStore {

    /** A fresh random salt for a new record. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** `SHA-256(salt || credential)`. */
    fun hash(credential: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance(ALGORITHM).run {
            update(salt)
            digest(credential.toByteArray(Charsets.UTF_8))
        }

    /**
     * Constant-time comparison.
     *
     * [MessageDigest.isEqual] compares every byte rather than returning early on the first
     * mismatch, so how long it takes reveals nothing about how much of the hash a guess got right.
     * Free, so there is no reason to use a plain `contentEquals`.
     */
    fun matches(candidate: ByteArray, stored: ByteArray): Boolean =
        MessageDigest.isEqual(candidate, stored)

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val SALT_BYTES = 16
    }
}
