package org.tamodak.dizdar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Fixed, dark, and not derived from the wallpaper.
 *
 * Dynamic colour is deliberately absent. Dizdar says "protected" in green and "exposed" in red,
 * and a wallpaper-derived scheme is free to map both onto neighbouring pastels — on a device whose
 * whole job is to tell the user whether their apps are actually locked, that is not a cosmetic
 * risk. There is no light scheme for the same reason: one palette means one set of contrast
 * figures to have checked, rather than two and a habit of only ever looking at one of them.
 *
 * Most of the UI is built from the components in `ui/components`, which take their colours from
 * `Color.kt` directly. This scheme exists for the Material widgets that survive —
 * `CircularProgressIndicator`, `BasicTextField`'s selection handles, ripples — so that they do not
 * arrive in Material's default purple.
 */
private val DizdarColorScheme = darkColorScheme(
    primary = DizdarGreen,
    onPrimary = DizdarBackground,
    primaryContainer = DizdarGreenTint,
    onPrimaryContainer = DizdarGreen,
    secondary = DizdarBlue,
    onSecondary = DizdarBackground,
    tertiary = DizdarBlue,
    onTertiary = DizdarBackground,
    background = DizdarBackground,
    onBackground = DizdarForeground,
    surface = DizdarBackground,
    onSurface = DizdarForeground,
    surfaceVariant = DizdarSurface,
    onSurfaceVariant = DizdarTextMuted,
    surfaceContainer = DizdarSurface,
    surfaceContainerHigh = DizdarSurface,
    surfaceContainerHighest = DizdarSurface,
    error = DizdarRed,
    onError = DizdarBackground,
    errorContainer = DizdarRedTint,
    onErrorContainer = DizdarRed,
    outline = DizdarBorder,
    outlineVariant = DizdarBorderMuted,
    scrim = DizdarBackground,
)

/**
 * Every corner is square.
 *
 * [RoundedCornerShape] at zero rather than `RectangleShape`, which is not a `CornerBasedShape` and
 * so cannot be handed to [Shapes] at all. The effect is identical.
 */
private val Square = RoundedCornerShape(0.dp)

/** Every Material size slot mapped to [Square], so nothing rounds a corner behind our back. */
private val DizdarShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square,
)

/**
 * Wraps content in Dizdar's palette, type scale and shapes.
 *
 * Fixed dark. There is no light variant and no dynamic colour: the design depends on a near-black
 * background for its bordered, unfilled surfaces to read at all.
 *
 * @param content the UI to theme.
 */
@Composable
fun DizdarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DizdarColorScheme,
        typography = Typography,
        shapes = DizdarShapes,
        content = content,
    )
}
