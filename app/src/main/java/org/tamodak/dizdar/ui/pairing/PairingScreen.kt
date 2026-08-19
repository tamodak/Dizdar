package org.tamodak.dizdar.ui.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.core.DizdarLog
import org.tamodak.dizdar.data.CameraFacing
import org.tamodak.dizdar.data.PairedPeer
import org.tamodak.dizdar.pairing.ChallengeSession
import org.tamodak.dizdar.pairing.QrPayload
import org.tamodak.dizdar.pairing.ScanOutcome
import org.tamodak.dizdar.ui.DizdarUiState
import org.tamodak.dizdar.ui.components.DizdarBody
import org.tamodak.dizdar.ui.components.DizdarButton
import org.tamodak.dizdar.ui.components.DizdarDangerButton
import org.tamodak.dizdar.ui.components.DizdarDialog
import org.tamodak.dizdar.ui.components.DizdarError
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.components.DizdarPanel
import org.tamodak.dizdar.ui.components.DizdarPrimaryButton
import org.tamodak.dizdar.ui.components.DizdarRow
import org.tamodak.dizdar.ui.components.DizdarRule
import org.tamodak.dizdar.ui.components.DizdarScreen
import org.tamodak.dizdar.ui.components.DizdarSectionTitle
import org.tamodak.dizdar.ui.components.DizdarTextButton
import org.tamodak.dizdar.ui.formatDuration
import org.tamodak.dizdar.ui.theme.DizdarBlue
import org.tamodak.dizdar.ui.theme.DizdarForeground
import org.tamodak.dizdar.ui.theme.DizdarGreen
import org.tamodak.dizdar.ui.theme.DizdarIcons
import org.tamodak.dizdar.ui.theme.DizdarRed
import org.tamodak.dizdar.ui.theme.DizdarRowDivider
import org.tamodak.dizdar.ui.theme.DizdarTextFaint
import kotlinx.coroutines.delay

/**
 * Where the user is in the pairing flow.
 *
 * `Hub` is the list and the two routes out of it. `Warning` through `Added` is the locking side —
 * acknowledge the consequences, get ready, scan the other phone's code, confirm. `ShowCode` is the
 * other side, where this phone offers itself as a companion and stores nothing.
 */
private enum class PairStep { Hub, Warning, Ready, Scan, Added, ShowCode }

/**
 * Where the user is in removing a companion: show the challenge, then scan that companion's
 * signature back.
 */
private enum class RemovalStep { ShowCode, Scan }

/**
 * Manage companion devices.
 *
 * Pairing is the most consequential thing this app can do — it disables the passkey, blocks the
 * removal countdown, and can only be undone with a companion present — so the first pairing is
 * gated behind an explicit acknowledgement of exactly that.
 *
 * @param state supplies the peer list, this device's own code, and the last scan outcome.
 * @param onPairingCodeScanned called with the text of a scanned pairing offer.
 * @param onStartPeerRemoval begins removing a companion, which it then has to approve.
 * @param onCancelPeerRemoval abandons a removal in progress.
 * @param onRemovalResponseScanned called with the text of a scanned removal approval.
 * @param onDismissScanOutcome clears the explanation of why the last scan was rejected.
 * @param onCameraFacingChange called when the user switches cameras.
 * @param onBack leaves the screen.
 * @param modifier applied to the screen.
 */
@Composable
fun PairingScreen(
    state: DizdarUiState,
    onPairingCodeScanned: (String) -> Unit,
    onStartPeerRemoval: (PairedPeer) -> Unit,
    onCancelPeerRemoval: () -> Unit,
    onRemovalResponseScanned: (String) -> Unit,
    onDismissScanOutcome: () -> Unit,
    onCameraFacingChange: (CameraFacing) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(PairStep.Hub) }
    var confirmRemoval by remember { mutableStateOf<PairedPeer?>(null) }
    var peersBeforeScan by rememberSaveable { mutableIntStateOf(0) }
    val ownKey = state.ownPublicKey

    fun goTo(next: PairStep) {
        DizdarLog.d(DizdarLog.UI) { "Pairing step $step -> $next" }
        onDismissScanOutcome()
        step = next
    }

    BackHandler { if (step == PairStep.Hub) onBack() else goTo(PairStep.Hub) }

    DizdarScreen(
        title = stringResource(R.string.pair_title),
        modifier = modifier,
        onBack = { if (step == PairStep.Hub) onBack() else goTo(PairStep.Hub) },
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
            // The unpair round takes over the screen: the departing companion has to sign, and
            // doing that alongside the pairing flow would leave two rounds competing for the same
            // camera.
            val removalSession = state.challenge
            val removalPeer = state.peerPendingRemoval
            if (removalPeer != null) {
                // The peer is chosen before the round exists, so there is a frame or two with no
                // session to show. Falling through to the hub here would flash the wrong screen.
                if (removalSession == null) {
                    CircularProgressIndicator(color = DizdarGreen)
                    DizdarButton(
                        text = stringResource(R.string.cancel),
                        onClick = onCancelPeerRemoval,
                    )
                    return@Column
                }
                RemovalFlow(
                    peer = removalPeer,
                    session = removalSession,
                    scanOutcome = state.scanOutcome,
                    cameraFacing = state.cameraFacing,
                    onCameraFacingChange = onCameraFacingChange,
                    onResponseScanned = onRemovalResponseScanned,
                    onDismissScanOutcome = onDismissScanOutcome,
                    onCancel = onCancelPeerRemoval,
                )
                return@Column
            }

            when (step) {
                PairStep.Hub -> {
                    ScanFeedback(state.scanOutcome)

                    if (state.peers.isNotEmpty()) {
                        PeerList(state = state, onRemove = { confirmRemoval = it })
                    }

                    DizdarBody(
                        text = stringResource(R.string.pair_hub_subtitle),
                        textAlign = TextAlign.Center,
                    )

                    if (ownKey == null) {
                        DizdarError(stringResource(R.string.scan_no_key))
                    } else {
                        DizdarRow(
                            title = stringResource(R.string.pair_role_lock_title),
                            subtitle = stringResource(R.string.pair_role_lock_desc),
                            icon = DizdarIcons.Lock,
                            iconTint = DizdarRed,
                            borderColor = DizdarRed,
                            onClick = {
                                goTo(if (state.peers.isEmpty()) PairStep.Warning else PairStep.Ready)
                            },
                        )
                        DizdarRow(
                            title = stringResource(R.string.pair_role_companion_title),
                            subtitle = stringResource(R.string.pair_role_companion_desc),
                            icon = DizdarIcons.QrCode,
                            iconTint = DizdarBlue,
                            onClick = { goTo(PairStep.ShowCode) },
                        )
                    }

                    DizdarButton(text = stringResource(R.string.back), onClick = onBack)
                }

                PairStep.Warning -> FirstPairingWarning(
                    onAcknowledge = { goTo(PairStep.Ready) },
                    onBack = { goTo(PairStep.Hub) },
                )

                PairStep.Ready -> {
                    FlowStepHeader(
                        step = 1,
                        of = 2,
                        title = stringResource(R.string.pair_add_ready_title),
                        instruction = stringResource(R.string.pair_add_ready_body),
                    )
                    LockDirectionBanner(locksThisPhone = true)
                    DizdarPrimaryButton(
                        text = stringResource(R.string.pair_add_ready_action),
                        onClick = {
                            peersBeforeScan = state.peers.size
                            goTo(PairStep.Scan)
                        },
                    )
                    DizdarButton(
                        text = stringResource(R.string.cancel),
                        onClick = { goTo(PairStep.Hub) },
                    )
                }

                PairStep.Scan -> {
                    LaunchedEffect(state.peers.size) {
                        if (state.peers.size > peersBeforeScan) step = PairStep.Added
                    }
                    FlowStepHeader(
                        step = 2,
                        of = 2,
                        title = stringResource(R.string.pair_add_scan_title),
                        instruction = stringResource(R.string.pair_add_scan_body),
                    )
                    LockDirectionBanner(locksThisPhone = true)
                    ScanFeedback(state.scanOutcome)
                    ScannerPanel(
                        onScanned = onPairingCodeScanned,
                        cameraFacing = state.cameraFacing,
                        onCameraFacingChange = onCameraFacingChange,
                    )
                    DizdarButton(
                        text = stringResource(R.string.back),
                        onClick = { goTo(PairStep.Ready) },
                    )
                }

                PairStep.Added -> {
                    Text(
                        text = stringResource(
                            R.string.pair_add_done_title,
                            state.peers.lastOrNull()?.label.orEmpty(),
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = DizdarGreen,
                        textAlign = TextAlign.Center,
                    )
                    DizdarBody(
                        text = stringResource(R.string.pair_add_done_body),
                        textAlign = TextAlign.Center,
                    )
                    DizdarPrimaryButton(
                        text = stringResource(R.string.pair_add_another),
                        onClick = { goTo(PairStep.Ready) },
                    )
                    DizdarButton(
                        text = stringResource(R.string.done),
                        onClick = { goTo(PairStep.Hub) },
                    )
                }

                PairStep.ShowCode -> {
                    if (ownKey == null) {
                        DizdarError(stringResource(R.string.scan_no_key))
                    } else {
                        val payload = remember(ownKey) {
                            QrPayload.Pairing(publicKey = ownKey, label = deviceLabel()).encode()
                        }
                        Text(
                            text = stringResource(R.string.pair_show_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = DizdarForeground,
                        )
                        DizdarBody(
                            text = stringResource(R.string.pair_show_body),
                            textAlign = TextAlign.Center,
                        )
                        LockDirectionBanner(locksThisPhone = false)
                        BigQrPanel(
                            payload = payload,
                            contentDescription = stringResource(R.string.pair_code_description),
                        )
                    }
                    DizdarButton(
                        text = stringResource(R.string.done),
                        onClick = { goTo(PairStep.Hub) },
                    )
                }
            }
        }
    }

    confirmRemoval?.let { peer ->
        DizdarDialog(
            title = stringResource(R.string.pair_remove_title, peer.label),
            body = stringResource(R.string.pair_remove_body, peer.label),
            accent = DizdarRed,
            onDismiss = { confirmRemoval = null },
            confirmText = stringResource(R.string.pair_remove_start),
            onConfirm = {
                confirmRemoval = null
                onStartPeerRemoval(peer)
            },
            dismissText = stringResource(R.string.cancel),
        )
    }
}

/**
 * The one-time acknowledgement before a device gains its first companion.
 *
 * Written plainly and without softening, because everything it describes is true and none of it is
 * reversible without the other phone in the room.
 */
@Composable
private fun FirstPairingWarning(onAcknowledge: () -> Unit, onBack: () -> Unit) {
    DizdarPanel(borderColor = DizdarRed, spacing = 16.dp) {
        DizdarSectionTitle(stringResource(R.string.pair_warning_title))
        DizdarBody(
            text = stringResource(R.string.pair_warning_body),
            color = DizdarForeground,
        )
        DizdarDangerButton(
            text = stringResource(R.string.pair_warning_accept),
            onClick = onAcknowledge,
        )
        DizdarButton(text = stringResource(R.string.cancel), onClick = onBack)
    }
}

/**
 * The companions currently paired, each with a way to start removing it.
 *
 * @param state supplies the peer list.
 * @param onRemove begins removing that companion, which it then has to approve.
 */
@Composable
private fun PeerList(state: DizdarUiState, onRemove: (PairedPeer) -> Unit) {
    DizdarPanel(spacing = 12.dp) {
        DizdarSectionTitle(stringResource(R.string.pair_list_title, state.peers.size))
        DizdarBody(
            text = stringResource(R.string.pair_list_all_required),
            color = DizdarTextFaint,
        )
        state.peers.forEach { peer ->
            DizdarRule(color = DizdarRowDivider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = peer.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DizdarForeground,
                    modifier = Modifier.weight(1f),
                )
                DizdarTextButton(
                    text = stringResource(R.string.pair_remove),
                    onClick = { onRemove(peer) },
                    color = DizdarRed,
                )
            }
        }
    }
}

/** The challenge/response round that removing a companion requires. */
@Composable
private fun ColumnScope.RemovalFlow(
    peer: PairedPeer,
    session: ChallengeSession,
    scanOutcome: ScanOutcome?,
    cameraFacing: CameraFacing,
    onCameraFacingChange: (CameraFacing) -> Unit,
    onResponseScanned: (String) -> Unit,
    onDismissScanOutcome: () -> Unit,
    onCancel: () -> Unit,
) {
    var step by rememberSaveable(peer.label) { mutableStateOf(RemovalStep.ShowCode) }

    var now by remember(session.expiresAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.expiresAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= session.expiresAtMillis) break
            delay(1_000)
        }
    }

    if (session.isExpired(now)) {
        DizdarError(stringResource(R.string.qr_session_expired))
        DizdarButton(text = stringResource(R.string.cancel), onClick = onCancel)
        return
    }

    val remaining = formatDuration(session.remainingMillis(now))

    when (step) {
        RemovalStep.ShowCode -> {
            val payload = remember(session) { session.toQrPayload().encode() }
            FlowStepHeader(
                step = 1,
                of = 2,
                title = stringResource(R.string.pair_remove_show_title, peer.label),
                instruction = stringResource(R.string.pair_remove_show_body, peer.label, remaining),
            )
            BigQrPanel(
                payload = payload,
                contentDescription = stringResource(R.string.pair_remove_code_description),
            )
            DizdarPrimaryButton(
                text = stringResource(R.string.pair_scanned_next),
                onClick = {
                    onDismissScanOutcome()
                    step = RemovalStep.Scan
                },
            )
        }

        RemovalStep.Scan -> {
            FlowStepHeader(
                step = 2,
                of = 2,
                title = stringResource(R.string.pair_remove_scan_title, peer.label),
                instruction = stringResource(R.string.pair_remove_scan_body, remaining),
            )
            ScanFeedback(scanOutcome)
            ScannerPanel(
                onScanned = onResponseScanned,
                cameraFacing = cameraFacing,
                onCameraFacingChange = onCameraFacingChange,
            )
            DizdarButton(
                text = stringResource(R.string.pair_back_to_code),
                onClick = {
                    onDismissScanOutcome()
                    step = RemovalStep.ShowCode
                },
            )
        }
    }

    DizdarTextButton(text = stringResource(R.string.cancel), onClick = onCancel)
}

/**
 * What this device calls itself in a pairing code, so the other user can tell two companions
 * apart. Presentational only — nothing is decided by it.
 */
private fun deviceLabel(): String =
    listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Dizdar" }
