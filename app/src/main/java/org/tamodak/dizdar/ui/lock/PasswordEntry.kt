package org.tamodak.dizdar.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.core.DizdarLog
import org.tamodak.dizdar.ui.components.DizdarField
import org.tamodak.dizdar.ui.components.DizdarPrimaryButton
import org.tamodak.dizdar.ui.components.DizdarTextButton
import org.tamodak.dizdar.ui.theme.DizdarIcons

const val MIN_PASSWORD_LENGTH = 4

/**
 * Free-text passkey entry.
 *
 * Masked by default with a show/hide toggle, because a passkey typed on a phone keyboard is easy
 * to fat-finger and there is no "wrong, try again" that costs nothing here — every failed attempt
 * counts towards a lockout.
 *
 * This is the only input that opens the soft keyboard, which is why the screens hosting it apply
 * IME insets (`safeDrawingPadding`, which includes the keyboard).
 *
 * @param enabled false while a check is in flight.
 * @param onSubmit called with the entered text. The field is cleared first, so nothing stays on
 *   screen through key derivation.
 * @param modifier applied to the entry.
 */
@Composable
fun PasswordEntry(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    /** Clears the field before submitting, so nothing lingers on screen during verification. */
    fun submit() {
        val entered = value
        value = ""
        DizdarLog.d(DizdarLog.UI) { "Password submitted (${entered.length} chars)" }
        onSubmit(entered)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DizdarField(
            value = value,
            onValueChange = { value = it },
            placeholder = stringResource(R.string.setup_method_password),
            leadingIcon = DizdarIcons.Key,
            enabled = enabled,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (value.length >= MIN_PASSWORD_LENGTH) submit() }
            ),
            trailing = {
                DizdarTextButton(
                    text = stringResource(if (visible) R.string.hide else R.string.show),
                    onClick = { visible = !visible },
                )
            },
        )

        DizdarPrimaryButton(
            text = stringResource(R.string.ok),
            onClick = ::submit,
            enabled = enabled && value.length >= MIN_PASSWORD_LENGTH,
        )
    }
}
