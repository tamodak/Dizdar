package org.tamodak.killit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.tamodak.killit.ui.theme.KillitBackground
import org.tamodak.killit.ui.theme.KillitBlue
import org.tamodak.killit.ui.theme.KillitBorderDim
import org.tamodak.killit.ui.theme.KillitBorderMuted
import org.tamodak.killit.ui.theme.KillitBorderStrong
import org.tamodak.killit.ui.theme.KillitDivider
import org.tamodak.killit.ui.theme.KillitForeground
import org.tamodak.killit.ui.theme.KillitGreen
import org.tamodak.killit.ui.theme.KillitIcons
import org.tamodak.killit.ui.theme.KillitRed
import org.tamodak.killit.ui.theme.KillitSurface
import org.tamodak.killit.ui.theme.KillitTextFaint
import org.tamodak.killit.ui.theme.KillitTextMuted
import org.tamodak.killit.ui.theme.KillitTextStrong

/*
 * Killit's own widget set.
 *
 * Material 3 is still the theme underneath — it supplies the type scale, the colour slots and the
 * ripples — but almost none of its components are used. `Card`, `Button`, `Checkbox`, `Switch`,
 * `Scaffold`, `AlertDialog` and `TabRow` all carry tonal elevation, rounded corners and filled
 * containers, and this design has none of those. Restyling each of them per call site would have
 * meant repeating the same overrides in a dozen screens and still losing to the ones Material does
 * not expose. The components here are a single place where the rules live instead.
 *
 * ### The rules
 *
 * Shape comes from a border, never a fill. Corners are square. A surface is distinguished by the
 * weight and colour of its outline, not by being lighter than what is behind it — which is why the
 * background is near-black everywhere and stays that way.
 *
 * Colour is meaning, so it is rationed. Most things are [KillitBorderDim] with
 * [KillitForeground] text; green, red and blue appear only where they say something (see
 * `Color.kt`).
 *
 * Disabled state is 50% alpha on both border and content rather than a separate palette, so
 * nothing needs a disabled colour of its own.
 *
 * ### Insets
 *
 * [KillitScreen] applies `safeDrawingPadding` once, which covers the status bar, the navigation
 * bar, the cutout and the IME. Screens that use it must not apply insets again. The three that
 * draw their own chrome — the gate, the QR gate and the approval flow — apply it themselves.
 */

/** Horizontal margin for screen content. Every screen uses this and nothing else. */
val KillitGutter = 20.dp

/** Rules, list separators, and the frame around a small icon. */
private val BorderThin = 2.dp

/** Anything interactive or load-bearing. Thin enough borders stop reading as deliberate. */
private val BorderThick = 3.dp

private const val DisabledAlpha = 0.5f

private fun Color.dimmed(enabled: Boolean) = if (enabled) this else copy(alpha = DisabledAlpha)

/**
 * Header, scrolling body, optional bottom bar — the frame every full screen uses.
 *
 * Applies `safeDrawingPadding` for the whole screen, so content must not apply insets again.
 *
 * @param onBack draws the bordered back button when non-null. Purely presentational: it does not
 *   install a [androidx.activity.compose.BackHandler], so a screen that needs the system back
 *   gesture too has to add one and point both at the same lambda.
 * @param scrollable false when the content scrolls something of its own — an app list, say. The
 *   body is a `ColumnScope` either way, so a non-scrolling screen can hand `Modifier.weight(1f)`
 *   to whatever should take the remaining height.
 */
@Composable
fun KillitScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KillitBackground)
            .safeDrawingPadding(),
    ) {
        KillitHeader(title = title, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            content = content,
        )

        bottomBar?.invoke()
    }
}

@Composable
fun KillitHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KillitGutter, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(BorderThin, KillitBorderStrong, RectangleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KillitIcons.ArrowLeft,
                    contentDescription = null,
                    tint = KillitForeground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = KillitForeground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    KillitRule()
}

@Composable
fun KillitRule(
    modifier: Modifier = Modifier,
    color: Color = KillitDivider,
    thickness: Dp = BorderThin,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun KillitPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = KillitBorderDim,
    background: Color = Color.Transparent,
    padding: Dp = 20.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .border(BorderThick, borderColor, RectangleShape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun KillitStatusPanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderThick, accent, RectangleShape)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(52.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = KillitTextMuted,
            textAlign = TextAlign.Center,
        )
        extra()
    }
}

/**
 * A bordered, tappable row: icon, title, optional subtitle, chevron.
 *
 * The workhorse of the app — settings entries, provisioning routes, lock methods and the pairing
 * roles are all this. The chevron is drawn only when the row actually navigates, so a row that
 * merely acts in place does not promise a screen that never arrives.
 *
 * @param accent colours the title and the chevron; defaults to ordinary foreground.
 * @param borderColor set alongside [accent] to make a row read as consequential — the red pairing
 *   row, for instance. Left dim by default.
 */
@Composable
fun KillitRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = KillitForeground,
    accent: Color = KillitForeground,
    borderColor: Color = KillitBorderDim,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .border(BorderThick, borderColor.dimmed(enabled), RectangleShape)
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint.dimmed(enabled),
                modifier = Modifier.size(32.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accent.dimmed(enabled),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KillitTextFaint.dimmed(enabled),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showChevron && onClick != null) {
            Icon(
                imageVector = KillitIcons.ChevronRight,
                contentDescription = null,
                tint = accent.dimmed(enabled),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * A square, bordered button.
 *
 * [accent] and [borderColor] are separate on purpose. A neutral button wants white text in a dim
 * border, which a single colour cannot express; passing one value for both is what makes a button
 * shout, and only the primary and danger variants should. Get this wrong and every secondary
 * action on screen competes with the one that matters.
 */
@Composable
fun KillitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = KillitForeground,
    borderColor: Color = KillitBorderMuted,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .border(BorderThick, borderColor.dimmed(enabled), RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accent.dimmed(enabled),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun KillitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    KillitButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        accent = KillitGreen,
        borderColor = KillitGreen,
        background = if (enabled) KillitGreen.copy(alpha = 0.12f) else Color.Transparent,
        enabled = enabled,
    )
}

@Composable
fun KillitDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    KillitButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        accent = KillitRed,
        borderColor = KillitRed,
        enabled = enabled,
    )
}

@Composable
fun KillitTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = KillitTextMuted,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color.dimmed(enabled),
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

/**
 * A square checkbox: hollow when unchecked, filled green with a tick when checked.
 *
 * @param onCheckedChange null when an enclosing row already handles the tap — passing it here as
 *   well would put a second, smaller touch target inside the first and make the row feel like it
 *   only responds near the box.
 */
@Composable
fun KillitCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val accent = (if (checked) KillitGreen else KillitBorderMuted).dimmed(enabled)
    Box(
        modifier = modifier
            .size(size)
            .background(if (checked) accent else Color.Transparent)
            .border(BorderThick, accent, RectangleShape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = KillitIcons.Check,
                contentDescription = null,
                tint = KillitBackground,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

@Composable
fun KillitStep(number: Int, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .border(BorderThick, KillitBlue, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = KillitBlue,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = KillitForeground,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
fun KillitSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = KillitTextStrong,
        modifier = modifier,
    )
}

@Composable
fun KillitBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = KillitTextMuted,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
fun KillitError(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = KillitRed,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun KillitField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderThick, KillitBorderDim, RectangleShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = KillitTextFaint,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KillitTextFaint,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = KillitForeground),
                cursorBrush = SolidColor(KillitGreen),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * A modal built on the raw [Dialog] rather than `AlertDialog`, which cannot be squared off or
 * stripped of its container fill.
 *
 * @param accent borders the dialog and its confirm button — red for anything destructive, which is
 *   most of what Killit asks about. The dismiss button is always neutral, so the two never look
 *   equally weighted.
 * @param content extra body content between the message and the buttons; used for the list of
 *   packages Android refused to suspend, and for verbatim shell output.
 */
@Composable
fun KillitDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = KillitBorderMuted,
    body: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(KillitSurface)
                .border(BorderThick, accent, RectangleShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = KillitForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KillitTextStrong,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            content()
            if (confirmText != null && onConfirm != null) {
                KillitButton(
                    text = confirmText,
                    onClick = onConfirm,
                    accent = accent,
                    borderColor = accent,
                )
            }
            if (dismissText != null) {
                KillitButton(text = dismissText, onClick = onDismiss)
            }
        }
    }
}

@Composable
fun KillitIconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = KillitTextFaint,
    borderColor: Color = KillitBorderMuted,
    size: Dp = 42.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(BorderThin, borderColor, RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
fun KillitTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) KillitGreen.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        BorderThick,
                        if (active) KillitGreen else KillitBorderDim,
                        RectangleShape,
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) KillitGreen else KillitTextMuted,
                )
            }
        }
    }
}

/**
 * A labelled setting with a checkbox, used where Material would use a `Switch`.
 *
 * The whole row is the touch target and the checkbox is inert, which is both a larger target and
 * the reason this reads as one control rather than a label sitting next to a separate one.
 *
 * Note for accessibility work: the row is `clickable` rather than `toggleable`, so TalkBack
 * announces it as a button and does not report checked state. Worth fixing; noted so it is not
 * mistaken for an oversight.
 */
@Composable
fun KillitToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = KillitForeground.dimmed(enabled),
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KillitTextFaint.dimmed(enabled),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        KillitCheckbox(checked = checked, enabled = enabled, size = 30.dp)
    }
}
