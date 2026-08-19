package org.tamodak.dizdar.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.core.DizdarLog
import org.tamodak.dizdar.data.LockType
import org.tamodak.dizdar.ui.GateFeedback
import org.tamodak.dizdar.ui.components.DizdarBody
import org.tamodak.dizdar.ui.components.DizdarButton
import org.tamodak.dizdar.ui.components.DizdarError
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.formatDuration
import org.tamodak.dizdar.ui.lock.CredentialInput
import org.tamodak.dizdar.ui.lock.isLongEnough
import org.tamodak.dizdar.ui.theme.DizdarBackground
import org.tamodak.dizdar.ui.theme.DizdarForeground
import org.tamodak.dizdar.ui.theme.DizdarGreen
import org.tamodak.dizdar.ui.theme.DizdarIcons

/**
 * Dizdar's own lock.
 *
 * Dizdar is the only thing that can unblock a suspended app, so this gate is what actually protects
 * them — the strength of the whole product is the strength of this screen plus the hardening that
 * stops it being bypassed.
 *
 * @param enabled false while a verification is in flight. Note that verification is genuinely slow
 *   (see `CredentialStore.derive`), so on a low-end device the inputs can stay disabled for
 *   seconds after a submission with no other indication that anything is happening.
 */
@Composable
fun AuthGateScreen(
    lockType: LockType,
    enabled: Boolean,
    feedback: GateFeedback?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onApproveAnother: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DizdarBackground)
            // No Scaffold here, so the insets have to be applied by hand. safeDrawing covers the
            // system bars, the cutout and the IME, which the password variant of this screen opens.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DizdarGutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = DizdarIcons.Shield,
            contentDescription = null,
            tint = DizdarGreen,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.gate_title),
            style = MaterialTheme.typography.headlineSmall,
            color = DizdarForeground,
            textAlign = TextAlign.Center,
        )
        DizdarBody(
            text = stringResource(R.string.gate_subtitle),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        when (feedback) {
            is GateFeedback.Wrong -> DizdarError(
                text = if (feedback.remainingAttempts > 0) {
                    stringResource(R.string.gate_wrong, feedback.remainingAttempts)
                } else {
                    stringResource(R.string.gate_wrong_generic)
                }
            )

            is GateFeedback.LockedOut -> DizdarError(
                text = stringResource(
                    R.string.gate_locked_out,
                    formatDuration(feedback.remainingMillis),
                )
            )

            null -> Unit
        }

        CredentialInput(
            lockType = lockType,
            enabled = enabled && feedback !is GateFeedback.LockedOut,
            // A stray two-dot swipe is a slip, not a guess — don't spend a lockout attempt on it.
            // Dropped silently, which is why an under-length pattern appears to do nothing at all.
            onSubmit = { value ->
                if (lockType.isLongEnough(value)) {
                    onSubmit(value)
                } else {
                    DizdarLog.d(DizdarLog.UI) {
                        "Gate ignored a too-short $lockType entry (${DizdarLog.describeSecret(value)})"
                    }
                }
            },
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(top = 8.dp),
        )

        // Approving a companion's challenge opens nothing here, so it does not sit behind this
        // gate — and the phone asking for approval may be the only one that is locked.
        if (onApproveAnother != null) {
            DizdarButton(
                text = stringResource(R.string.qr_choose_approve),
                onClick = onApproveAnother,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }
}
