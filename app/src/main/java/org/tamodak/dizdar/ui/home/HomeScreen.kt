package org.tamodak.dizdar.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tamodak.dizdar.R
import org.tamodak.dizdar.admin.HardeningConfig
import org.tamodak.dizdar.admin.DizdarDeviceAdminReceiver
import org.tamodak.dizdar.core.DizdarLog
import org.tamodak.dizdar.ui.DizdarUiState
import org.tamodak.dizdar.ui.ShizukuOutcome
import org.tamodak.dizdar.ui.components.DizdarBody
import org.tamodak.dizdar.ui.components.DizdarButton
import org.tamodak.dizdar.ui.components.DizdarDangerButton
import org.tamodak.dizdar.ui.components.DizdarDialog
import org.tamodak.dizdar.ui.components.DizdarGutter
import org.tamodak.dizdar.ui.components.DizdarPanel
import org.tamodak.dizdar.ui.components.DizdarPrimaryButton
import org.tamodak.dizdar.ui.components.DizdarRow
import org.tamodak.dizdar.ui.components.DizdarRule
import org.tamodak.dizdar.ui.components.DizdarScreen
import org.tamodak.dizdar.ui.components.DizdarSectionTitle
import org.tamodak.dizdar.ui.components.DizdarStatusPanel
import org.tamodak.dizdar.ui.components.DizdarStep
import org.tamodak.dizdar.ui.components.DizdarTextButton
import org.tamodak.dizdar.ui.components.DizdarToggleRow
import org.tamodak.dizdar.ui.formatDuration
import org.tamodak.dizdar.ui.theme.DizdarBlue
import org.tamodak.dizdar.ui.theme.DizdarBorderDim
import org.tamodak.dizdar.ui.theme.DizdarForeground
import org.tamodak.dizdar.ui.theme.DizdarGreen
import org.tamodak.dizdar.ui.theme.DizdarIcons
import org.tamodak.dizdar.ui.theme.DizdarRed
import org.tamodak.dizdar.ui.theme.DizdarRowDivider
import org.tamodak.dizdar.ui.theme.DizdarSurface
import org.tamodak.dizdar.ui.theme.DizdarTextFaint
import org.tamodak.dizdar.ui.theme.DizdarTextMuted
import org.tamodak.dizdar.ui.theme.DizdarTextStrong
import kotlinx.coroutines.delay

/**
 * Which provisioning route the user opened, or null on the hub.
 *
 * Local UI state rather than a [org.tamodak.dizdar.ui.Screen]: these pages are pure instructions
 * with no state of their own, and routing them through the ViewModel would put three more entries
 * in a screen enum that exists to describe where the *session* is.
 */
private enum class SetupMethod { Adb, Shizuku, Qr }

/**
 * The authenticated landing screen.
 *
 * Shows one of two faces depending on [DizdarUiState.isDeviceOwner]: before provisioning it offers
 * the three ways to become device owner, each opening its own step-by-step page; afterwards it is
 * the tamper protection toggles. The app list is reachable in both states — browsable as a
 * read-only preview beforehand — so the user can see what Dizdar will manage before committing to a
 * factory reset.
 *
 * @param state supplies owner status, the hardening toggles, the release request and the peer list.
 * @param onManageApps opens the app picker.
 * @param onChangePasskey opens the change-passkey flow.
 * @param onHardeningChange called with the full config whenever one toggle moves.
 * @param onProvisionViaShizuku runs the no-computer provisioning path.
 * @param onDismissShizukuOutcome closes the dialog showing what the command reported.
 * @param onRefreshStatus re-checks owner status after provisioning outside the app.
 * @param onRequestRelease starts the wait before device owner can be given up.
 * @param onCancelRelease abandons that wait.
 * @param onReleaseDeviceOwner carries out the release, once the wait has elapsed.
 * @param onOpenPairing opens companion management.
 * @param modifier applied to the screen.
 * @param onApproveAnother switches to approving another device's challenge. Null until this device
 *   has a pairing identity of its own to sign with.
 */
@Composable
fun HomeScreen(
    state: DizdarUiState,
    onManageApps: () -> Unit,
    onChangePasskey: () -> Unit,
    onHardeningChange: (HardeningConfig) -> Unit,
    onProvisionViaShizuku: () -> Unit,
    onDismissShizukuOutcome: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRequestRelease: () -> Unit,
    onCancelRelease: () -> Unit,
    onReleaseDeviceOwner: () -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier,
    onApproveAnother: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val adminComponent = remember(context) {
        DizdarDeviceAdminReceiver.componentName(context).flattenToString()
    }
    var setupMethod by remember { mutableStateOf<SetupMethod?>(null) }
    // Two separate confirmations: one to start the wait, another to actually release once it is
    // over. The second is the irreversible one.
    var confirmRelease by remember { mutableStateOf(false) }
    var confirmReleaseNow by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeviceOwner) {
        if (state.isDeviceOwner) setupMethod = null
    }

    val method = setupMethod
    if (method != null) {
        BackHandler { setupMethod = null }
        SetupMethodScreen(
            method = method,
            adminComponent = adminComponent,
            busy = state.busy,
            modifier = modifier,
            onCopy = { context.copyToClipboard(it) },
            onProvisionViaShizuku = onProvisionViaShizuku,
            onRefreshStatus = onRefreshStatus,
            onBack = { setupMethod = null },
        )
    } else {
        DizdarScreen(title = stringResource(R.string.app_name), modifier = modifier) {
            Column(
                modifier = Modifier.padding(
                    start = DizdarGutter,
                    end = DizdarGutter,
                    top = 24.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                StatusPanel(state = state, onRefresh = onRefreshStatus)

                // Browsable before provisioning too: the list shows a banner and leaves the
                // checkboxes disabled, so the user can see what Dizdar will manage first.
                DizdarRow(
                    title = stringResource(R.string.home_manage_apps),
                    subtitle = if (state.isDeviceOwner) {
                        null
                    } else {
                        stringResource(R.string.home_manage_apps_locked)
                    },
                    icon = DizdarIcons.Apps,
                    iconTint = if (state.isDeviceOwner) DizdarGreen else DizdarForeground,
                    borderColor = if (state.isDeviceOwner) DizdarGreen else DizdarBorderDim,
                    background = if (state.isDeviceOwner) {
                        DizdarGreen.copy(alpha = 0.10f)
                    } else {
                        Color.Transparent
                    },
                    onClick = onManageApps,
                )

                if (state.isDeviceOwner) {
                    HardeningPanel(
                        config = state.hardening,
                        isGuardian = state.isGuardian,
                        onChange = onHardeningChange,
                    )
                } else {
                    SetupSection(onOpen = { setupMethod = it })
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DizdarRule()

                    if (state.isDeviceOwner) {
                        // Gated on being somebody's companion, not on having companions. This
                        // phone's key is the only way the devices below can open; the phone that
                        // merely *has* companions holds nothing anyone else needs.
                        if (state.isGuardian) {
                            // Not merely disabled — explained. A greyed-out button with no reason
                            // is how users conclude the app is broken.
                            DizdarPanel(borderColor = DizdarRed) {
                                DizdarBody(
                                    text = stringResource(
                                        R.string.home_guardian_release_blocked,
                                        state.guardianFor.size,
                                    )
                                )
                            }
                        } else {
                            ReleaseSection(
                                state = state,
                                onRequestRelease = { confirmRelease = true },
                                onCancelRelease = onCancelRelease,
                                onReleaseNow = { confirmReleaseNow = true },
                            )
                        }
                    }

                    // Hidden once paired: companions replace the passkey entirely, so offering to
                    // change one would suggest a way in that no longer exists.
                    if (!state.isPaired) {
                        DizdarRow(
                            title = stringResource(R.string.home_change_passkey),
                            icon = DizdarIcons.Key,
                            onClick = onChangePasskey,
                        )
                    }

                    DizdarRow(
                        title = if (state.isPaired) {
                            stringResource(R.string.pair_list_title, state.peers.size)
                        } else {
                            stringResource(R.string.pair_entry)
                        },
                        icon = DizdarIcons.Devices,
                        onClick = onOpenPairing,
                    )

                    // Shown whether or not this phone has companions of its own: pairing is
                    // one-directional, so being somebody's companion is independent of having one.
                    if (onApproveAnother != null) {
                        DizdarRow(
                            title = stringResource(R.string.qr_choose_approve),
                            icon = DizdarIcons.QrCode,
                            onClick = onApproveAnother,
                        )
                    }
                }
            }
        }
    }

    state.shizukuOutcome?.let { outcome ->
        DizdarDialog(
            title = stringResource(R.string.prov_result_title),
            onDismiss = onDismissShizukuOutcome,
            dismissText = stringResource(R.string.ok),
        ) {
            when (outcome) {
                ShizukuOutcome.NotRunning ->
                    DizdarBody(
                        text = stringResource(R.string.prov_shizuku_not_running),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                ShizukuOutcome.TooOld ->
                    DizdarBody(
                        text = stringResource(R.string.prov_shizuku_old),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                ShizukuOutcome.PermissionDenied ->
                    DizdarBody(
                        text = stringResource(R.string.prov_shizuku_denied),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                // Shell output verbatim: "there are already some accounts on the device" is the
                // usual failure and paraphrasing it would hide the fix.
                is ShizukuOutcome.CommandOutput -> MonospaceBlock(outcome.text)
            }
        }
    }

    if (confirmRelease) {
        DizdarDialog(
            title = stringResource(R.string.home_release_confirm_title),
            body = stringResource(R.string.home_release_confirm_body),
            accent = DizdarRed,
            onDismiss = { confirmRelease = false },
            confirmText = stringResource(R.string.home_release_start),
            onConfirm = {
                DizdarLog.i(DizdarLog.UI, "User started the release waiting period")
                confirmRelease = false
                onRequestRelease()
            },
            dismissText = stringResource(R.string.cancel),
        )
    }

    if (confirmReleaseNow) {
        DizdarDialog(
            title = stringResource(R.string.home_release_now_confirm_title),
            body = stringResource(R.string.home_release_now_confirm_body),
            accent = DizdarRed,
            onDismiss = { confirmReleaseNow = false },
            confirmText = stringResource(R.string.home_release_now),
            onConfirm = {
                DizdarLog.i(DizdarLog.UI, "User confirmed device owner release after the wait")
                confirmReleaseNow = false
                onReleaseDeviceOwner()
            },
            dismissText = stringResource(R.string.cancel),
        )
    }
}

/**
 * The headline panel: whether Dizdar is device owner, and how much it is currently blocking.
 *
 * Green or red throughout, because this one fact decides whether anything else on the screen can
 * take effect at all.
 *
 * @param state supplies owner status and the blocked count.
 * @param onRefresh re-checks owner status, for when the user has provisioned outside the app.
 */
@Composable
private fun StatusPanel(state: DizdarUiState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DizdarStatusPanel(
            icon = DizdarIcons.Shield,
            title = stringResource(
                if (state.isDeviceOwner) R.string.home_status_owner_yes
                else R.string.home_status_owner_no
            ),
            subtitle = stringResource(
                if (state.isDeviceOwner) R.string.home_status_owner_yes_desc
                else R.string.home_status_owner_no_desc
            ),
            accent = if (state.isDeviceOwner) DizdarGreen else DizdarRed,
        ) {
            if (state.isDeviceOwner) {
                DizdarBody(
                    text = stringResource(R.string.home_blocked_count, state.blocked.size),
                    color = DizdarForeground,
                )
            }
        }
        DizdarTextButton(text = stringResource(R.string.prov_refresh), onClick = onRefresh)
    }
}

/**
 * The routes to becoming device owner, shown only while Dizdar is not one.
 *
 * @param onOpen opens the walkthrough for the chosen method.
 */
@Composable
private fun SetupSection(onOpen: (SetupMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DizdarSectionTitle(stringResource(R.string.prov_section_title))

        DizdarRow(
            title = stringResource(R.string.prov_adb_name),
            subtitle = stringResource(R.string.prov_adb_short),
            icon = DizdarIcons.Computer,
            iconTint = DizdarBlue,
            onClick = { onOpen(SetupMethod.Adb) },
        )
        DizdarRow(
            title = stringResource(R.string.prov_shizuku_name),
            subtitle = stringResource(R.string.prov_shizuku_short),
            icon = DizdarIcons.Phone,
            iconTint = DizdarBlue,
            onClick = { onOpen(SetupMethod.Shizuku) },
        )
        DizdarRow(
            title = stringResource(R.string.prov_qr_name),
            subtitle = stringResource(R.string.prov_qr_short),
            icon = DizdarIcons.QrCode,
            iconTint = DizdarBlue,
            onClick = { onOpen(SetupMethod.Qr) },
        )
    }
}

/**
 * One provisioning route, as numbered steps.
 *
 * All three need a device with no accounts and no existing device owner, which on a phone already
 * in use means a factory reset — stated up front on every route rather than discovered halfway
 * through one of them.
 *
 * Each page ends in Refresh status, because none of the three can report their own success: adb
 * and the QR wizard finish outside the app entirely, and even Shizuku only reports what the shell
 * printed. Re-reading the device policy service is the only thing that actually settles it.
 *
 * @param method which route to walk through.
 * @param adminComponent the flattened admin name, which the adb route has the user copy.
 * @param busy false enables the actions; true while provisioning is in flight.
 * @param onCopy puts a command on the clipboard.
 * @param onProvisionViaShizuku runs the Shizuku path. Used only on that page.
 * @param onRefreshStatus re-checks owner status.
 * @param onBack returns to the hub.
 * @param modifier applied to the screen.
 */
@Composable
private fun SetupMethodScreen(
    method: SetupMethod,
    adminComponent: String,
    busy: Boolean,
    onCopy: (String) -> Unit,
    onProvisionViaShizuku: () -> Unit,
    onRefreshStatus: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (method) {
        SetupMethod.Adb -> R.string.prov_adb_name
        SetupMethod.Shizuku -> R.string.prov_shizuku_name
        SetupMethod.Qr -> R.string.prov_qr_name
    }
    val introRes = when (method) {
        SetupMethod.Adb -> R.string.prov_adb_intro
        SetupMethod.Shizuku -> R.string.prov_shizuku_intro
        SetupMethod.Qr -> R.string.prov_qr_intro
    }
    val steps = when (method) {
        SetupMethod.Adb -> listOf(
            R.string.prov_adb_step_1,
            R.string.prov_adb_step_2,
            R.string.prov_adb_step_3,
        )

        SetupMethod.Shizuku -> listOf(
            R.string.prov_shizuku_step_1,
            R.string.prov_shizuku_step_2,
            R.string.prov_shizuku_step_3,
        )

        SetupMethod.Qr -> listOf(
            R.string.prov_qr_step_1,
            R.string.prov_qr_step_2,
            R.string.prov_qr_step_3,
        )
    }

    DizdarScreen(title = stringResource(titleRes), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.padding(
                start = DizdarGutter,
                end = DizdarGutter,
                top = 24.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            DizdarBody(stringResource(introRes))

            DizdarPanel(borderColor = DizdarBorderDim) {
                DizdarBody(stringResource(R.string.prov_precondition), color = DizdarTextFaint)
            }

            steps.forEachIndexed { index, textRes ->
                DizdarStep(number = index + 1, text = stringResource(textRes))
            }

            when (method) {
                SetupMethod.Adb -> {
                    MonospaceBlock("adb shell dpm set-device-owner $adminComponent")
                    DizdarButton(
                        text = stringResource(R.string.copy),
                        onClick = { onCopy("adb shell dpm set-device-owner $adminComponent") },
                    )
                }

                SetupMethod.Shizuku -> DizdarPrimaryButton(
                    text = stringResource(
                        if (busy) R.string.prov_shizuku_working else R.string.prov_shizuku_run
                    ),
                    onClick = onProvisionViaShizuku,
                    enabled = !busy,
                )

                SetupMethod.Qr -> QrIllustration()
            }

            DizdarButton(text = stringResource(R.string.prov_refresh), onClick = onRefreshStatus)
        }
    }
}

/** A stand-in glyph, not a scannable code — the real provisioning QR lives on the website. */
@Composable
private fun QrIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally),
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .border(3.dp, DizdarTextStrong, RectangleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = DizdarIcons.QrCodeSolid,
                contentDescription = null,
                tint = DizdarForeground,
                modifier = Modifier.size(130.dp),
            )
        }
    }
}

/**
 * Verbatim text on a raised surface: adb commands to copy, and shell output to read.
 *
 * Monospaced because both are things the user has to type accurately or compare character by
 * character.
 *
 * @param text the content, shown exactly as given.
 */
@Composable
private fun MonospaceBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DizdarSurface)
            .padding(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = DizdarForeground,
        )
    }
}

/**
 * The three states of giving up device owner: not requested, waiting, ready.
 *
 * The waiting state is the point of the whole feature, so it is shown as a panel with a live
 * countdown rather than a disabled button — the user should be able to see exactly how long is
 * left, and that cancelling is available at any time.
 *
 * @param state supplies the pending request, and whether other devices depend on this phone's key.
 * @param onRequestRelease starts the wait.
 * @param onCancelRelease abandons it.
 * @param onReleaseNow carries out the release, once the wait has elapsed.
 */
@Composable
private fun ReleaseSection(
    state: DizdarUiState,
    onRequestRelease: () -> Unit,
    onCancelRelease: () -> Unit,
    onReleaseNow: () -> Unit,
) {
    val request = state.releaseRequest

    if (request == null) {
        DizdarRow(
            title = stringResource(R.string.home_release),
            icon = DizdarIcons.Lock,
            iconTint = DizdarRed,
            accent = DizdarRed,
            borderColor = DizdarRed,
            enabled = !state.busy,
            showChevron = false,
            onClick = onRequestRelease,
        )
        return
    }

    // Re-reads the clock once a second so the countdown is live. Keyed on the deadline so a
    // cancel-then-request cycle restarts it rather than reusing a stale coroutine.
    var now by remember(request.availableAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(request.availableAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= request.availableAtMillis) break
            delay(1_000)
        }
    }

    val remaining = request.remainingMillis(now)
    val ready = request.isReady(now)

    DizdarPanel(borderColor = if (ready) DizdarRed else DizdarBlue, spacing = 16.dp) {
        DizdarSectionTitle(
            stringResource(
                if (ready) R.string.home_release_ready_title else R.string.home_release_pending_title
            )
        )

        if (ready) {
            DizdarBody(stringResource(R.string.home_release_ready_body))
            DizdarDangerButton(
                text = stringResource(R.string.home_release_now),
                onClick = onReleaseNow,
                enabled = !state.busy,
            )
        } else {
            DizdarBody(
                text = stringResource(
                    R.string.home_release_pending_body,
                    formatDuration(remaining),
                ),
                color = DizdarForeground,
            )
        }

        DizdarButton(
            text = stringResource(R.string.home_release_cancel),
            onClick = onCancelRelease,
            enabled = !state.busy,
        )
    }
}

/**
 * The anti-tamper toggles.
 *
 * @param config the toggles as stored.
 * @param isGuardian true when other devices depend on this phone's key, which locks two of the
 *   toggles on — see `DizdarViewModel.setHardening` for why they cannot be given up.
 * @param onChange called with the full config whenever one toggle moves.
 */
@Composable
private fun HardeningPanel(
    config: HardeningConfig,
    isGuardian: Boolean,
    onChange: (HardeningConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DizdarSectionTitle(stringResource(R.string.home_hardening))

        DizdarPanel(padding = 18.dp, spacing = 0.dp) {
            DizdarToggleRow(
                title = stringResource(R.string.harden_uninstall),
                description = stringResource(R.string.harden_uninstall_desc),
                checked = config.blockUninstall,
                enabled = !isGuardian,
                onCheckedChange = { onChange(config.copy(blockUninstall = it)) },
            )
            DizdarRule(color = DizdarRowDivider)
            DizdarToggleRow(
                title = stringResource(R.string.harden_force_stop),
                description = stringResource(R.string.harden_force_stop_desc),
                checked = config.blockForceStop,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                onCheckedChange = { onChange(config.copy(blockForceStop = it)) },
            )
            DizdarRule(color = DizdarRowDivider)
            DizdarToggleRow(
                title = stringResource(R.string.harden_safe_boot),
                description = stringResource(R.string.harden_safe_boot_desc),
                checked = config.blockSafeBoot,
                onCheckedChange = { onChange(config.copy(blockSafeBoot = it)) },
            )
            DizdarRule(color = DizdarRowDivider)
            DizdarToggleRow(
                title = stringResource(R.string.harden_factory_reset),
                description = stringResource(R.string.harden_factory_reset_desc),
                checked = config.blockFactoryReset,
                onCheckedChange = { onChange(config.copy(blockFactoryReset = it)) },
            )
            DizdarRule(color = DizdarRowDivider)
            DizdarToggleRow(
                title = stringResource(R.string.harden_apps_control),
                description = stringResource(R.string.harden_apps_control_desc),
                checked = config.blockAppsControl,
                enabled = !isGuardian,
                onCheckedChange = { onChange(config.copy(blockAppsControl = it)) },
            )
            DizdarRule(color = DizdarRowDivider)
            DizdarToggleRow(
                title = stringResource(R.string.harden_date_time),
                description = stringResource(R.string.harden_date_time_desc),
                checked = config.blockDateTime,
                onCheckedChange = { onChange(config.copy(blockDateTime = it)) },
            )
        }
    }
}

/** Copies a provisioning command so it can be pasted into a terminal or messaged to a computer. */
private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    DizdarLog.d(DizdarLog.UI) { "Copied to clipboard: $text" }
    clipboard.setPrimaryClip(ClipData.newPlainText("Dizdar", text))
    // Android 13+ shows its own copy confirmation; a second one would be noise.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}
