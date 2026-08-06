package org.tamodak.killit.data

/**
 * The three ways of entering the passkey.
 *
 * All three normalise to a single string before hashing, so verification has one code path:
 * a PIN is its digits, a password is its characters, and a pattern is its visited dot indices
 * joined with `-` (see `PatternLock.toPatternString`). The type is stored alongside the hash only
 * so the gate knows which input to show.
 *
 * The enum *name* is what gets persisted, so these constants must not be renamed without a
 * migration — a stored `PATTERN` that no longer parses reads as "no record" and sends the user to
 * setup with their apps still blocked.
 */
enum class LockType {
    PIN,
    PASSWORD,
    PATTERN;

    companion object {
        /** Parses a persisted name, returning null for anything unrecognised or absent. */
        fun fromName(name: String?): LockType? =
            entries.firstOrNull { it.name == name }
    }
}

/**
 * What Killit persists about the passkey. The credential itself is never stored — only a salted,
 * peppered hash of it.
 *
 * @param salt random per-record input, so two identical passkeys do not produce identical hashes.
 * @param hash `SHA-256(salt || credential)`.
 * @param failedAttempts consecutive wrong guesses; reset to zero on success.
 * @param lockoutUntilMillis wall-clock time (`System.currentTimeMillis`) before which no attempt
 *   is accepted. Zero means no lockout.
 */
data class CredentialRecord(
    val lockType: LockType,
    val salt: ByteArray,
    val hash: ByteArray,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L,
) {
    // A data class compares ByteArray fields by identity, not content, so the generated equals()
    // would report two identical records as different. LockRepository.reconcile() compares records
    // to decide whether to rewrite a store, so getting this wrong would mean writing on every read.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialRecord) return false
        return lockType == other.lockType &&
            salt.contentEquals(other.salt) &&
            hash.contentEquals(other.hash) &&
            failedAttempts == other.failedAttempts &&
            lockoutUntilMillis == other.lockoutUntilMillis
    }

    override fun hashCode(): Int {
        var result = lockType.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + failedAttempts
        result = 31 * result + lockoutUntilMillis.hashCode()
        return result
    }
}
