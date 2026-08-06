package org.tamodak.killit.pairing

import org.tamodak.killit.core.KillitLog
import org.tamodak.killit.data.PairedPeer
import org.tamodak.killit.data.encodeBase64

/**
 * One round of collecting companion approvals.
 *
 * Every paired companion has to sign before the device opens, so a session is not a single
 * question and answer — it is a nonce held open while the phone is carried between people. The
 * same nonce serves the whole round: each companion's signature is distinguished by the signer
 * fingerprint bound into the payload, not by a fresh challenge each time.
 *
 * ### Lifetime
 *
 * [DEFAULT_LIFETIME_MILLIS] is ten minutes rather than the thirty to sixty seconds a single
 * challenge would need. A round means waking the other phone, opening Killit on it, reaching the
 * approval screen, scanning, and carrying the answer back — per companion. A session that expires
 * mid-round is worse than no timer: the user re-scans the first companion, times out again, and
 * never learns why.
 *
 * The session lives in the ViewModel, so it survives rotation and dies with the process. Dying is
 * correct — a half-collected set of approvals should not outlive the app.
 */
data class ChallengeSession(
    val purpose: ChallengePurpose,
    val nonce: ByteArray,
    /** The device asking to be opened; its fingerprint goes into the QR and into what is signed. */
    val requesterPublicKey: ByteArray,
    val expiresAtMillis: Long,
    /** Base64 fingerprints of the companions that have already signed, in scan order. */
    val approvals: Set<String> = emptySet(),
) {
    val requesterFingerprint: ByteArray get() = PairedPeer.fingerprintOf(requesterPublicKey)

    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    /** True once every companion in [peers] has signed. */
    fun isSatisfiedBy(peers: List<PairedPeer>): Boolean =
        peers.isNotEmpty() && peers.all { it.fingerprint.encodeBase64() in approvals }

    fun hasApproval(peer: PairedPeer): Boolean = peer.fingerprint.encodeBase64() in approvals

    fun withApproval(peer: PairedPeer): ChallengeSession =
        copy(approvals = approvals + peer.fingerprint.encodeBase64())

    /** The QR a companion scans. */
    fun toQrPayload(): QrPayload.Challenge = QrPayload.Challenge(
        nonce = nonce,
        requesterFingerprint = requesterFingerprint,
        purpose = purpose,
        expiresAtEpochSeconds = expiresAtMillis / 1000,
    )

    // ByteArray fields again: without this a session would never compare equal to itself and any
    // state comparison in Compose would treat every recomposition as a change.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChallengeSession) return false
        return purpose == other.purpose &&
            nonce.contentEquals(other.nonce) &&
            requesterPublicKey.contentEquals(other.requesterPublicKey) &&
            expiresAtMillis == other.expiresAtMillis &&
            approvals == other.approvals
    }

    override fun hashCode(): Int {
        var result = purpose.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + requesterPublicKey.contentHashCode()
        result = 31 * result + expiresAtMillis.hashCode()
        result = 31 * result + approvals.hashCode()
        return result
    }

    companion object {
        /** Long enough to carry the phone to a second person and back. */
        const val DEFAULT_LIFETIME_MILLIS = 600_000L

        fun start(
            purpose: ChallengePurpose,
            requesterPublicKey: ByteArray,
            nowMillis: Long = System.currentTimeMillis(),
            lifetimeMillis: Long = DEFAULT_LIFETIME_MILLIS,
        ): ChallengeSession {
            KillitLog.i(KillitLog.PAIRING, "Starting a $purpose challenge, valid for ${lifetimeMillis}ms")
            return ChallengeSession(
                purpose = purpose,
                nonce = PairingProtocol.newNonce(),
                requesterPublicKey = requesterPublicKey,
                expiresAtMillis = nowMillis + lifetimeMillis,
            )
        }
    }
}
