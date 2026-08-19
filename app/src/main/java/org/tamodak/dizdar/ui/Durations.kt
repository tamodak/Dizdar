package org.tamodak.dizdar.ui

import java.util.concurrent.TimeUnit

/**
 * Formats a remaining duration for display, e.g. `"2d 4h"`, `"3h 12m"`, `"1m 30s"`, `"45s"`.
 *
 * Shared by the two places that count down: the gate's lockout after too many wrong attempts
 * (seconds to minutes) and the wait before device owner can be given up (up to days). Only the two
 * largest units are shown — at three days out, seconds are noise.
 *
 * Floors at one second so a sub-second remainder never reads as `"0s"`, which would look like the
 * wait is over when it is not.
 *
 * Built in code rather than from string resources, so it is not localisable. Worth closing if
 * Dizdar is ever translated; the unit letters would need to come from `strings.xml`.
 *
 * @param millis the remaining duration.
 * @return the formatted string, with at most two units.
 */
internal fun formatDuration(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(1)

    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
