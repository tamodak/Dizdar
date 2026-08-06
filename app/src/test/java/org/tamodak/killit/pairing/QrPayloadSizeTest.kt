package org.tamodak.killit.pairing

import org.tamodak.killit.data.PairedPeer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How big the codes actually come out.
 *
 * The thing that decides whether a 2017 camera can read a QR off another phone's screen is the
 * size of one module, and that is set by how many modules there are. Every payload here is built
 * at its maximum length and held to a module count, so a field added later shows up as a failing
 * test rather than as a code nobody can scan.
 *
 * Runs on the JVM: [QrPayload.encode] and [Base32] touch no Android APIs, and ZXing's encoder is
 * pure Java.
 */
class QrPayloadSizeTest {

    /** The characters QR alphanumeric mode can hold at 5.5 bits each. Anything else costs 8. */
    private val alphanumeric = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    @Test
    fun pairingCodeStaysSmall() {
        val payload = QrPayload.Pairing(
            publicKey = ByteArray(PeerKeyStore.PUBLIC_KEY_BYTES) { 0xFF.toByte() },
            // Longer than the limit on purpose: encoding truncates, so this is the worst case.
            label = "M".repeat(64),
        )
        assertModules(payload, expected = 33)
    }

    @Test
    fun challengeCodeStaysSmall() {
        val payload = QrPayload.Challenge(
            nonce = ByteArray(PairingProtocol.NONCE_BYTES) { 0xFF.toByte() },
            requesterFingerprint = ByteArray(PairedPeer.FINGERPRINT_BYTES) { 0xFF.toByte() },
            purpose = ChallengePurpose.UNPAIR,
            // Far enough out that the base-36 expiry is at its widest.
            expiresAtEpochSeconds = 99_999_999_999L,
        )
        assertModules(payload, expected = 29)
    }

    @Test
    fun responseCodeStaysSmall() {
        val payload = QrPayload.Response(
            signerFingerprint = ByteArray(PairedPeer.FINGERPRINT_BYTES) { 0xFF.toByte() },
            signature = ByteArray(PeerKeyStore.SIGNATURE_BYTES) { 0xFF.toByte() },
        )
        assertModules(payload, expected = 37)
    }

    @Test
    fun labelIsTruncatedOnACharacterBoundary() {
        // Every character is three UTF-8 bytes, so a naive cut at 16 would split one in half and
        // the other phone would show a replacement character where a device name should be.
        val payload = QrPayload.Pairing(
            publicKey = ByteArray(PeerKeyStore.PUBLIC_KEY_BYTES),
            label = "設定".repeat(20),
        )
        val decoded = QrPayload.decode(payload.encode()) as QrPayload.Pairing

        assertEquals("設定設定設", decoded.label)
        assertTrue(decoded.label.toByteArray(Charsets.UTF_8).size <= QrPayload.LABEL_MAX_BYTES)
    }

    private fun assertModules(payload: QrPayload, expected: Int) {
        val text = payload.encode()

        assertTrue(
            "Payload leaves QR alphanumeric mode, which costs a third of the capacity: $text",
            text.all { it in alphanumeric },
        )

        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 0,
            ),
        )

        assertEquals(
            "${payload.javaClass.simpleName} is ${text.length} chars -> ${matrix.width} modules",
            expected,
            matrix.width,
        )
    }
}
