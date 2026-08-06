package org.tamodak.killit.data

import org.tamodak.killit.core.KillitLog
import java.security.MessageDigest

/**
 * A companion device that has to approve before this one can be unlocked.
 *
 * Only the peer's **public** key is stored — it is all this device needs to check the peer's
 * signatures, and it is not a secret. The peer's private key never leaves the peer.
 *
 * @param publicKey the peer's P-256 point, compressed, 33 bytes.
 * @param label what the peer called itself when pairing, so a user with two companions can tell
 *   them apart. Purely presentational; nothing is decided by it.
 */
data class PairedPeer(
    val publicKey: ByteArray,
    val label: String,
    val pairedAtMillis: Long,
) {
    /**
     * Short one-way identifier for this peer, used inside signed payloads so that one peer's
     * signature cannot be presented in place of another's.
     */
    val fingerprint: ByteArray get() = fingerprintOf(publicKey)

    // ByteArray compares by identity, so a data class would report two identical peers as
    // different — and the code that decides "is this peer already paired" would add duplicates.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairedPeer) return false
        return publicKey.contentEquals(other.publicKey) &&
            label == other.label &&
            pairedAtMillis == other.pairedAtMillis
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + pairedAtMillis.hashCode()
        return result
    }

    companion object {
        /** Bytes of fingerprint carried in signed payloads. 64 bits is ample to bind an identity. */
        const val FINGERPRINT_BYTES = 8

        fun fingerprintOf(publicKey: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(publicKey).copyOf(FINGERPRINT_BYTES)

        /**
         * Serialises a peer list to a single string for storage.
         *
         * One line per peer, fields separated by `|`. The label is Base64-encoded along with the
         * key so that a device named `"Ali|Ev"` cannot break the format — user text is never
         * written raw into a delimited record.
         */
        fun serialize(peers: List<PairedPeer>): String =
            peers.joinToString(separator = "\n") { peer ->
                listOf(
                    peer.publicKey.encodeBase64(),
                    peer.label.toByteArray(Charsets.UTF_8).encodeBase64(),
                    peer.pairedAtMillis.toString(),
                ).joinToString(separator = "|")
            }

        /**
         * Inverse of [serialize]. Skips any line that does not parse rather than failing the whole
         * list — losing one corrupt entry is recoverable, losing every pairing is not.
         */
        fun deserialize(raw: String?): List<PairedPeer> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.lineSequence().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|")
                if (parts.size != 3) {
                    KillitLog.w(KillitLog.PAIRING, "Skipping malformed peer entry (${parts.size} fields)")
                    return@mapNotNull null
                }
                val key = parts[0].decodeBase64()
                val label = parts[1].decodeBase64()
                val pairedAt = parts[2].toLongOrNull()
                if (key == null || label == null || pairedAt == null) {
                    KillitLog.w(KillitLog.PAIRING, "Skipping unparseable peer entry")
                    return@mapNotNull null
                }
                PairedPeer(
                    publicKey = key,
                    label = String(label, Charsets.UTF_8),
                    pairedAtMillis = pairedAt,
                )
            }.toList()
        }
    }
}
