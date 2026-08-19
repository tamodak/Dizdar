package org.tamodak.dizdar.ui.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.data.CameraFacing
import org.tamodak.dizdar.ui.DizdarUiState
import org.tamodak.dizdar.ui.components.DizdarButton
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.components.DizdarPrimaryButton
import org.tamodak.dizdar.ui.components.DizdarScreen

/**
 * Acting as somebody else's companion: read their challenge, hand back a signature.
 *
 * Reachable whether or not this phone has companions of its own. Pairing is one-directional — A
 * adding B changes nothing on B — so a phone that is not itself QR-locked still has to be able to
 * approve, and it can do so from its own locked gate.
 *
 * @param state supplies the signature to show back and the last scan outcome.
 * @param onApprovalRequestScanned called with the text of a scanned challenge.
 * @param onCameraFacingChange called when the user switches cameras.
 * @param onDone leaves the screen, returning to Home or the gate depending on whether this session
 *   is authenticated.
 * @param modifier applied to the screen.
 */
@Composable
fun ApprovalScreen(
    state: DizdarUiState,
    onApprovalRequestScanned: (String) -> Unit,
    onCameraFacingChange: (CameraFacing) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onDone() }

    val response = state.approvalResponse

    DizdarScreen(
        title = stringResource(R.string.qr_choose_approve),
        modifier = modifier,
        onBack = onDone,
    ) {
        Column(
            modifier = Modifier.padding(
                start = DizdarGutter,
                end = DizdarGutter,
                top = 24.dp,
                bottom = 40.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (response == null) {
                FlowStepHeader(
                    step = 1,
                    of = 2,
                    title = stringResource(R.string.qr_approve_scan_title),
                    instruction = stringResource(R.string.qr_approve_intro),
                )
                ScanFeedback(state.scanOutcome)
                ScannerPanel(
                    onScanned = onApprovalRequestScanned,
                    cameraFacing = state.cameraFacing,
                    onCameraFacingChange = onCameraFacingChange,
                )
                DizdarButton(text = stringResource(R.string.cancel), onClick = onDone)
            } else {
                FlowStepHeader(
                    step = 2,
                    of = 2,
                    title = stringResource(R.string.qr_approve_show_title),
                    instruction = stringResource(R.string.qr_approve_ready),
                )
                BigQrPanel(
                    payload = response,
                    contentDescription = stringResource(R.string.qr_approve_code_description),
                )
                DizdarPrimaryButton(text = stringResource(R.string.done), onClick = onDone)
            }
        }
    }
}
