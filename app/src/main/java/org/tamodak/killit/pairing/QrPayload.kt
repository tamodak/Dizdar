package org.tamodak.killit.pairing

import org.tamodak.killit.core.KillitLog
import org.tamodak.killit.data.PairedPeer

sealed interface QrPayload {

    data class Pairing(val publicKey: ByteArray, val label: String) : QrPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pairing) return false
            return publicKey.contentEquals(other.publicKey) && label == other.label
        }

        override fun hashCode(): Int = 31 * publicKey.contentHashCode() + label.hashCode()
    }

    data class Challenge(
        val nonce: ByteArray,
        val requesterFingerprint: ByteArray,
        val purpose: ChallengePurpose,
        val expiresAtEpochSeconds: Long,
    ) : QrPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Challenge) return false
            return nonce.contentEquals(other.nonce) &&
                requesterFingerprint.contentEquals(other.requesterFingerprint) &&
                purpose == other.purpose &&
                expiresAtEpochSeconds == other.expiresAtEpochSeconds
        }

        override fun hashCode(): Int {
            var result = nonce.contentHashCode()
            result = 31 * result + requesterFingerprint.contentHashCode()
            result = 31 * result + purpose.hashCode()
            result = 31 * result + expiresAtEpochSeconds.hashCode()
            return result
        }
    }

    data class Response(val signerFingerprint: ByteArray, val signature: ByteArray) : QrPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Response) return false
            return signerFingerprint.contentEquals(other.signerFingerprint) &&
                signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int =
            31 * signerFingerprint.contentHashCode() + signature.contentHashCode()
    }

    fun encode(): String = when (this) {
        is Pairing -> buildString {
            append(PREFIX_PAIRING)
            append(publicKey.encodeBase32())
            append(label.truncateUtf8(LABEL_MAX_BYTES).encodeBase32())
        }

        is Challenge -> buildString {
            append(PREFIX_CHALLENGE)
            append(nonce.encodeBase32())
            append(requesterFingerprint.encodeBase32())
            append(purpose.code)
            append(expiresAtEpochSeconds.toString(RADIX).uppercase())
        }

        is Response -> buildString {
            append(PREFIX_RESPONSE)
            append(signerFingerprint.encodeBase32())
            append(signature.encodeBase32())
        }
    }

    companion object {
        private const val PREFIX_PAIRING = "KP"
        private const val PREFIX_CHALLENGE = "KC"
        private const val PREFIX_RESPONSE = "KR"
        private const val PREFIX_CHARS = 2
        private const val RADIX = 36

        const val LABEL_MAX_BYTES = 16

        private val keyChars = base32Length(PeerKeyStore.PUBLIC_KEY_BYTES)
        private val nonceChars = base32Length(PairingProtocol.NONCE_BYTES)
        private val fingerprintChars = base32Length(PairedPeer.FINGERPRINT_BYTES)
        private val signatureChars = base32Length(PeerKeyStore.SIGNATURE_BYTES)

        fun decode(raw: String): QrPayload? {
            val text = raw.trim()
            return when {
                text.startsWith(PREFIX_PAIRING) -> decodePairing(text)
                text.startsWith(PREFIX_CHALLENGE) -> decodeChallenge(text)
                text.startsWith(PREFIX_RESPONSE) -> decodeResponse(text)
                else -> null
            }
        }

        private fun decodePairing(text: String): Pairing? {
            val labelStart = PREFIX_CHARS + keyChars
            if (text.length < labelStart) return malformed(PREFIX_PAIRING, text.length)

            val publicKey = text.substring(PREFIX_CHARS, labelStart).decodeBase32()
                ?: return malformed(PREFIX_PAIRING, text.length)
            if (publicKey.size != PeerKeyStore.PUBLIC_KEY_BYTES) return null

            val label = text.substring(labelStart).decodeBase32()
                ?: return malformed(PREFIX_PAIRING, text.length)
            if (label.size > LABEL_MAX_BYTES) return null

            return Pairing(publicKey = publicKey, label = String(label, Charsets.UTF_8))
        }

        private fun decodeChallenge(text: String): Challenge? {
            val fingerprintStart = PREFIX_CHARS + nonceChars
            val purposeAt = fingerprintStart + fingerprintChars
            if (text.length <= purposeAt + 1) return malformed(PREFIX_CHALLENGE, text.length)

            val nonce = text.substring(PREFIX_CHARS, fingerprintStart).decodeBase32()
                ?: return malformed(PREFIX_CHALLENGE, text.length)
            if (nonce.size != PairingProtocol.NONCE_BYTES) return null

            val fingerprint = text.substring(fingerprintStart, purposeAt).decodeBase32()
                ?: return malformed(PREFIX_CHALLENGE, text.length)
            if (fingerprint.size != PairedPeer.FINGERPRINT_BYTES) return null

            val purpose = ChallengePurpose.entries.firstOrNull { it.code == text[purposeAt] }
                ?: return malformed(PREFIX_CHALLENGE, text.length)
            val expiry = text.substring(purposeAt + 1).toLongOrNull(RADIX)
                ?: return malformed(PREFIX_CHALLENGE, text.length)
            if (expiry < 0) return null

            return Challenge(
                nonce = nonce,
                requesterFingerprint = fingerprint,
                purpose = purpose,
                expiresAtEpochSeconds = expiry,
            )
        }

        private fun decodeResponse(text: String): Response? {
            val signatureStart = PREFIX_CHARS + fingerprintChars
            if (text.length != signatureStart + signatureChars) {
                return malformed(PREFIX_RESPONSE, text.length)
            }

            val fingerprint = text.substring(PREFIX_CHARS, signatureStart).decodeBase32()
                ?: return malformed(PREFIX_RESPONSE, text.length)
            if (fingerprint.size != PairedPeer.FINGERPRINT_BYTES) return null

            val signature = text.substring(signatureStart).decodeBase32()
                ?: return malformed(PREFIX_RESPONSE, text.length)
            if (signature.size != PeerKeyStore.SIGNATURE_BYTES) return null

            return Response(signerFingerprint = fingerprint, signature = signature)
        }

        private fun String.truncateUtf8(maxBytes: Int): ByteArray {
            val bytes = toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return bytes
            var end = maxBytes
            while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
            return bytes.copyOf(end)
        }

        private fun <T> malformed(prefix: String, length: Int): T? {
            KillitLog.d(KillitLog.PAIRING) { "Malformed $prefix payload ($length chars)" }
            return null
        }
    }
}
