package org.tamodak.killit.pairing

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

internal fun base32Length(byteCount: Int): Int = (byteCount * 8 + 4) / 5

internal fun ByteArray.encodeBase32(): String {
    if (isEmpty()) return ""

    val out = StringBuilder(base32Length(size))
    var buffer = 0
    var bits = 0

    for (byte in this) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            bits -= 5
            out.append(ALPHABET[(buffer ushr bits) and 0x1F])
        }
        buffer = buffer and ((1 shl bits) - 1)
    }

    if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
    return out.toString()
}

internal fun String.decodeBase32(): ByteArray? {
    if (isEmpty()) return ByteArray(0)

    val out = ByteArray(length * 5 / 8)
    var index = 0
    var buffer = 0
    var bits = 0

    for (char in this) {
        val value = ALPHABET.indexOf(char)
        if (value < 0) return null
        buffer = (buffer shl 5) or value
        bits += 5
        if (bits >= 8) {
            bits -= 8
            out[index++] = ((buffer ushr bits) and 0xFF).toByte()
            buffer = buffer and ((1 shl bits) - 1)
        }
    }

    if (bits >= 5) return null
    if (buffer != 0) return null
    if (index != out.size) return null
    return out
}
