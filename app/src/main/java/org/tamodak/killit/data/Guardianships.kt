package org.tamodak.killit.data

/**
 * The devices that depend on **this** phone's private key to open.
 *
 * Recorded as Base64 fingerprints only, because that is all a challenge carries — this phone never
 * learns a label for a device it is a companion of. Losing this key is unrecoverable for every
 * device listed here, which is why the list, not the peer list, is what has to block giving up
 * device owner.
 */
internal fun serializeGuardianships(fingerprints: Set<String>): String =
    fingerprints.joinToString(separator = "\n")

internal fun deserializeGuardianships(raw: String?): Set<String> =
    raw?.lineSequence()?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
