package org.tamodak.dizdar.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.core.DizdarLog
import org.tamodak.dizdar.data.LockType
import org.tamodak.dizdar.ui.components.DizdarBody
import org.tamodak.dizdar.ui.components.DizdarError
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.components.DizdarRow
import org.tamodak.dizdar.ui.components.DizdarScreen
import org.tamodak.dizdar.ui.components.DizdarTextButton
import org.tamodak.dizdar.ui.lock.CredentialInput
import org.tamodak.dizdar.ui.lock.isLongEnough
import org.tamodak.dizdar.ui.theme.DizdarBlue
import org.tamodak.dizdar.ui.theme.DizdarIcons

/**
 * Choose a method, enter the passkey, confirm it.
 *
 * The enter/confirm pairing is local UI state — **nothing is persisted until both entries match**,
 * so a mistyped passkey can never lock the user out of their own device. On a mismatch the first
 * entry is discarded entirely and the flow restarts, rather than asking for the confirmation
 * again: re-confirming against a first entry the user may have fat-fingered would just persist the
 * mistake.
 *
 * Doubles as the change-passkey screen; [onCancel] is non-null only in that mode.
 *
 * @param enabled false while a save is in flight.
 * @param onConfirmed called once both entries match, with the method and the passkey.
 * @param modifier applied to the screen.
 * @param onCancel leaves without setting anything. Null in first-time setup, where there is no
 *   passkey to go back to.
 */
@Composable
fun CredentialSetupScreen(
    enabled: Boolean,
    onConfirmed: (LockType, String) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    var selectedType by remember { mutableStateOf<LockType?>(null) }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }

    val type = selectedType

    fun reset() {
        selectedType = null
        firstEntry = null
        error = null
    }

    DizdarScreen(
        title = when {
            type != null -> stringResource(type.labelRes())
            onCancel == null -> stringResource(R.string.setup_title)
            else -> stringResource(R.string.setup_change_title)
        },
        modifier = modifier,
        onBack = if (type != null) ::reset else onCancel,
    ) {
        Column(
            modifier = Modifier.padding(
                start = DizdarGutter,
                end = DizdarGutter,
                top = 24.dp,
                bottom = 40.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (type == null) {
                DizdarBody(
                    text = stringResource(R.string.setup_subtitle),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                DizdarRow(
                    title = stringResource(R.string.setup_method_pin),
                    subtitle = stringResource(R.string.setup_method_pin_desc),
                    icon = DizdarIcons.Keypad,
                    iconTint = DizdarBlue,
                    onClick = { selectedType = LockType.PIN },
                )
                DizdarRow(
                    title = stringResource(R.string.setup_method_password),
                    subtitle = stringResource(R.string.setup_method_password_desc),
                    icon = DizdarIcons.Key,
                    iconTint = DizdarBlue,
                    onClick = { selectedType = LockType.PASSWORD },
                )
                DizdarRow(
                    title = stringResource(R.string.setup_method_pattern),
                    subtitle = stringResource(R.string.setup_method_pattern_desc),
                    icon = DizdarIcons.Pattern,
                    iconTint = DizdarBlue,
                    onClick = { selectedType = LockType.PATTERN },
                )

                if (onCancel != null) {
                    DizdarTextButton(text = stringResource(R.string.cancel), onClick = onCancel)
                }
            } else {
                val methodName = stringResource(type.labelRes())
                DizdarBody(
                    text = if (firstEntry == null) {
                        stringResource(R.string.setup_enter, methodName)
                    } else {
                        stringResource(R.string.setup_confirm, methodName)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let { DizdarError(stringResource(it)) }

                CredentialInput(
                    lockType = type,
                    enabled = enabled,
                    onSubmit = { value ->
                        val pending = firstEntry
                        when {
                            // Setup reports a short entry, unlike the gate, which ignores it silently.
                            !type.isLongEnough(value) -> {
                                DizdarLog.d(DizdarLog.UI) { "Setup rejected a too-short $type entry" }
                                error = type.tooShortRes()
                            }

                            pending == null -> {
                                DizdarLog.d(DizdarLog.UI) { "Setup: first entry accepted, awaiting confirmation" }
                                firstEntry = value
                                error = null
                            }

                            pending == value -> {
                                DizdarLog.d(DizdarLog.UI) { "Setup: entries match, persisting" }
                                error = null
                                onConfirmed(type, value)
                            }

                            else -> {
                                DizdarLog.d(DizdarLog.UI) { "Setup: entries did not match, restarting" }
                                firstEntry = null
                                error = R.string.setup_mismatch
                            }
                        }
                    },
                    modifier = Modifier.widthIn(max = 340.dp),
                )

                DizdarTextButton(text = stringResource(R.string.back), onClick = ::reset)
            }
        }
    }
}

/**
 * Names a lock method for the picker.
 *
 * @return the string resource for this method's label.
 */
private fun LockType.labelRes(): Int = when (this) {
    LockType.PIN -> R.string.setup_method_pin
    LockType.PASSWORD -> R.string.setup_method_password
    LockType.PATTERN -> R.string.setup_method_pattern
}

/**
 * Explains the minimum length for a lock method.
 *
 * Per-method rather than one generic sentence, because the units differ — digits, characters, dots
 * — and "too short" alone leaves the user guessing which.
 *
 * @return the string resource for this method's minimum-length message.
 */
private fun LockType.tooShortRes(): Int = when (this) {
    LockType.PIN -> R.string.setup_too_short_pin
    LockType.PASSWORD -> R.string.setup_too_short_password
    LockType.PATTERN -> R.string.setup_too_short_pattern
}
