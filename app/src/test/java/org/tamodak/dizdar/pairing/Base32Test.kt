package org.tamodak.killit.pairing

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The encoding every QR payload is built from.
 *
 * Hand-written, so it is worth testing at every length rather than at one: the bit-packing loop
 * carries a partial byte between characters, and the lengths where that leftover is not a whole
 * byte are exactly where an off-by-one hides.
 */
class Base32Test {

    @Test
    fun roundTripsAtEveryLength() {
        val random = SecureRandom()

        for (size in 0..64) {
            val bytes = ByteArray(size).also { random.nextBytes(it) }
            val encoded = bytes.encodeBase32()

            assertEquals("Encoded length must be predictable", base32Length(size), encoded.length)
            assertTrue(
                "Base32 has to stay inside the QR alphanumeric set: $encoded",
                encoded.all { it in 'A'..'Z' || it in '2'..'7' },
            )
            assertTrue("Round trip failed at $size bytes", bytes.contentEquals(encoded.decodeBase32()))
        }
    }

    @Test
    fun theFieldWidthsTheWireFormatDependsOn() {
        assertEquals(53, base32Length(PeerKeyStore.PUBLIC_KEY_BYTES))
        assertEquals(103, base32Length(PeerKeyStore.SIGNATURE_BYTES))
        assertEquals(26, base32Length(PairingProtocol.NONCE_BYTES))
    }

    @Test
    fun rejectsAnythingItDidNotProduce() {
        assertNull("Lowercase is not in the alphabet", "aaaa".decodeBase32())
        assertNull("Padding is not accepted", "AAAA====".decodeBase32())
        assertNull("0, 1 and 8 are not in the alphabet", "AAA0".decodeBase32())

        // Lengths that cannot come from a whole number of bytes: one, three or six characters
        // leave five or more bits over, which is a character that encoded nothing.
        listOf("A", "AAA", "AAAAAA").forEach {
            assertNull("${it.length} characters is not a valid length", it.decodeBase32())
        }

        // Canonical decoding: the unused low bits of the last character must be zero.
        assertNull("Non-zero padding bits", "AB".decodeBase32())
    }
}
