package org.tamodak.killit.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.tamodak.killit.BuildConfig
import org.tamodak.killit.ServiceLocator
import org.tamodak.killit.admin.DevicePolicyController
import org.tamodak.killit.admin.HardeningConfig
import org.tamodak.killit.admin.provisioning.ShizukuProvisioner
import org.tamodak.killit.admin.provisioning.ShizukuStatus
import org.tamodak.killit.core.KillitLog
import org.tamodak.killit.data.AppEntry
import org.tamodak.killit.data.AppInventory
import org.tamodak.killit.data.CameraFacing
import org.tamodak.killit.data.LockRepository
import org.tamodak.killit.data.LockType
import org.tamodak.killit.data.PairedPeer
import org.tamodak.killit.data.ReleaseRequest
import org.tamodak.killit.data.VerifyResult
import org.tamodak.killit.pairing.ChallengePurpose
import org.tamodak.killit.pairing.ChallengeSession
import org.tamodak.killit.pairing.PairingManager
import org.tamodak.killit.pairing.QrPayload
import org.tamodak.killit.pairing.ScanOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which screen the single-activity UI is showing. */
sealed interface Screen {
    /** Bootstrapping: reading the stores and the current suspension state. */
    data object Loading : Screen

    /** No passkey yet — choose a method and set one. */
    data object Setup : Screen

    /** A passkey exists; prove it. */
    data object Gate : Screen

    data object Home : Screen
    data object Apps : Screen
    data object ChangePasskey : Screen

    /** Manage companion devices: show this device's code, scan another's, review the list. */
    data object Pairing : Screen

    /** Signing a challenge for another device. Reachable without unlocking this one. */
    data object Approve : Screen

    /** Killit's data was cleared: apps are blocked but the passkey can no longer be verified. */
    data object Tampered : Screen
}

/** What the gate shows after a rejected attempt. */
sealed interface GateFeedback {
    data class Wrong(val remainingAttempts: Int) : GateFeedback
    data class LockedOut(val remainingMillis: Long) : GateFeedback
}

/** Transient confirmations. Modelled rather than pre-rendered so the text stays in strings.xml. */
sealed interface UiMessage {
    data object Saved : UiMessage
    data object PasskeySet : UiMessage

    /** Text produced by the platform, shown as-is. */
    data class Error(val text: String) : UiMessage
}

/** Result of a Shizuku provisioning attempt. */
sealed interface ShizukuOutcome {
    data object NotRunning : ShizukuOutcome
    data object TooOld : ShizukuOutcome
    data object PermissionDenied : ShizukuOutcome

    /** Raw stdout/stderr from `dpm set-device-owner`, shown verbatim. */
    data class CommandOutput(val text: String) : ShizukuOutcome
}

/**
 * The entire UI state, in one immutable snapshot.
 *
 * @param blocked what the OS reports as suspended — the source of truth for checkbox state.
 * @param selection pending checkbox state, applied on Save. Diverges from [blocked] only while
 *   the user has unsaved changes.
 */
data class KillitUiState(
    val screen: Screen = Screen.Loading,
    val isDeviceOwner: Boolean = false,
    val lockType: LockType? = null,
    val hardening: HardeningConfig = HardeningConfig.DEFAULT,
    val apps: List<AppEntry> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val selection: Set<String> = emptySet(),
    val appsLoading: Boolean = false,
    val busy: Boolean = false,
    val gateFeedback: GateFeedback? = null,
    val saveFailures: List<String> = emptyList(),
    val shizukuOutcome: ShizukuOutcome? = null,
    val message: UiMessage? = null,
    /** Outstanding request to give up device owner, or null if none has been made. */
    val releaseRequest: ReleaseRequest? = null,

    // ---- companion pairing ----
    /** Companions that must all approve before this device opens. */
    val peers: List<PairedPeer> = emptyList(),
    /** This device's own pairing identity, or null if it has none yet. */
    val ownPublicKey: ByteArray? = null,
    /** The approval round currently being collected, if any. */
    val challenge: ChallengeSession? = null,
    /** A signature produced for *another* device, waiting to be shown as a QR. */
    val approvalResponse: String? = null,
    /** Why the last scan was rejected. Cleared on the next successful one. */
    val scanOutcome: ScanOutcome? = null,
    /** Companion whose removal is being confirmed. */
    val peerPendingRemoval: PairedPeer? = null,
    val cameraFacing: CameraFacing = CameraFacing.DEFAULT,
    /** Base64 fingerprints of devices that cannot open without this phone's key. */
    val guardianFor: Set<String> = emptySet(),
) {
    /**
     * True once this device requires companion approval.
     *
     * This one flag disables the passkey, hides "change passkey" and blocks the removal countdown
     * — see the checks in [KillitViewModel]. Pairing is meant to be binding, and every one of those
     * routes would otherwise be a way back out of it.
     */
    val isPaired: Boolean get() = peers.isNotEmpty()

    /**
     * True when another device's only way in is this phone's private key.
     *
     * Distinct from [isPaired], and the two land on different phones: pairing is one-directional,
     * so the device that *has* companions is the one that cannot open alone, while the device that
     * *is* a companion is the one holding a key nobody can replace. Uninstalling Killit here, or
     * clearing its data, destroys that key and leaves every device below unopenable for good.
     */
    val isGuardian: Boolean get() = guardianFor.isNotEmpty()
    /** Symmetric difference: boxes newly ticked plus boxes newly cleared. */
    val pendingChanges: Int
        get() = ((selection - blocked) + (blocked - selection)).size

    val hasPendingChanges: Boolean get() = pendingChanges > 0

    /** Compact, log-friendly summary. Deliberately excludes the app list, which is huge. */
    fun describe(): String =
        "screen=$screen owner=$isDeviceOwner lock=$lockType apps=${apps.size} " +
            "blocked=${blocked.size} selection=${selection.size} busy=$busy loading=$appsLoading"
}

/**
 * Owns the whole UI state and every action the screens can take.
 *
 * ### Threading
 *
 * Everything runs on `viewModelScope`, i.e. the main dispatcher, and there is deliberately no
 * `withContext` here: the repository and the policy controller are main-safe and own the
 * dispatchers for their own binder, keystore and disk work. If a slow operation ever does surface
 * on the main thread, the fix belongs in that class, not in a wrapper here.
 *
 * ### Authentication
 *
 * [authenticated] is a plain field rather than part of [KillitUiState], so it cannot leak into a
 * saved state bundle. It dies with the ViewModel, which means process death re-locks — the
 * conservative direction.
 */
class KillitViewModel(
    private val repository: LockRepository,
    private val dpc: DevicePolicyController,
    private val inventory: AppInventory,
    private val shizuku: ShizukuProvisioner,
    private val pairing: PairingManager,
) : ViewModel() {

    private val _state = MutableStateFlow(KillitUiState())
    val state: StateFlow<KillitUiState> = _state.asStateFlow()

    private var authenticated = false

    init {
        KillitLog.d(KillitLog.VM) { "ViewModel created; bootstrapping" }
        viewModelScope.launch { bootstrap() }
    }

    // ---------------------------------------------------------------- startup

    /**
     * Decides which screen the app opens on, and gets there as fast as it can.
     *
     * ### Why this is split
     *
     * Choosing the screen needs exactly one thing: whether a passkey record exists. Everything
     * else the app eventually wants — the hardening toggles, the installed package list, which
     * packages the OS currently has suspended — belongs to screens the user has not reached yet.
     * Loading them here first meant the gate waited on ~400ms of package scanning it never used.
     *
     * So the record is read, the screen is shown, and the rest is filled in behind it. The app
     * list arrives while the user is still typing.
     *
     * ### The one case that cannot be rushed
     *
     * `record == null && isDeviceOwner` is ambiguous: it is either a fresh install, or a Killit
     * whose passkey was wiped while the OS kept its apps suspended. Telling those apart requires
     * knowing whether anything is actually blocked, so that path — and only that path — waits.
     *
     * It has to wait. Showing setup first and correcting to [Screen.Tampered] a few hundred
     * milliseconds later would open a window in which whoever cleared the data could set a new
     * passkey, which is exactly the attack the tampered screen exists to stop.
     */
    private suspend fun bootstrap() = KillitLog.timed(KillitLog.VM, "bootstrap (to first screen)") {
        // Fast path. A cached record is proof a passkey exists, and it costs one DataStore read —
        // no device policy service, whose first call in a process is the most expensive thing at
        // startup. Everything else, reconciliation with durable storage included, runs behind the
        // gate the user is already looking at.
        // Both reads hit the same already-open DataStore file, so the companion list costs
        // essentially nothing on top of the record — and the gate cannot be chosen without it,
        // since a paired device must never be shown a passkey prompt.
        val cached = repository.readCachedRecord()
        val cachedPeers = repository.readCachedPeers()
        val ownKey = repository.readCachedOwnPublicKey()

        if (cached != null || cachedPeers.isNotEmpty()) {
            KillitLog.i(
                KillitLog.VM,
                "Bootstrap: cached ${cached?.lockType} record, ${cachedPeers.size} companions -> Gate",
            )
            _state.update {
                it.copy(
                    lockType = cached?.lockType,
                    peers = cachedPeers,
                    ownPublicKey = ownKey,
                    screen = Screen.Gate,
                    appsLoading = true,
                )
            }
            startBackgroundLoad(reconcile = true)
            return@timed
        }

        // Nothing cached: either a fresh install, or one whose data was cleared. Only the durable
        // copy and the blocked set can tell those apart, so this path pays for both.
        KillitLog.d(KillitLog.VM) { "No cached record; consulting durable storage" }
        val record = repository.reconcile()
        val isOwner = dpc.isDeviceOwner()
        _state.update {
            it.copy(isDeviceOwner = isOwner, lockType = record?.lockType, appsLoading = true)
        }

        if (record != null || !isOwner) {
            // A record here came back from durable storage — the restore after a "Clear data".
            val screen = if (record != null) Screen.Gate else Screen.Setup
            KillitLog.i(KillitLog.VM, "Bootstrap: owner=$isOwner lock=${record?.lockType} -> $screen")
            _state.update { it.copy(screen = screen) }
            startBackgroundLoad(reconcile = false)
            return@timed
        }

        // Device owner with no passkey anywhere. Ambiguous, and the only path that has to wait.
        KillitLog.i(KillitLog.VM, "No record while device owner; checking the blocklist for tampering")
        viewModelScope.launch { loadHardening() }
        loadApps()
        val blocked = _state.value.blocked
        val screen = if (blocked.isNotEmpty()) Screen.Tampered else Screen.Setup
        KillitLog.i(KillitLog.VM, "Bootstrap: blocked=${blocked.size} -> $screen")
        _state.update { it.copy(screen = screen) }
    }

    /**
     * Fills in everything the first screen did not need.
     *
     * @param reconcile whether the two credential stores still have to be brought into step. True
     *   on the fast path, which deliberately skipped that to avoid a device policy service call.
     */
    private fun startBackgroundLoad(reconcile: Boolean) {
        viewModelScope.launch { loadHardening() }
        viewModelScope.launch { loadApps() }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    cameraFacing = repository.readCameraFacing(),
                    guardianFor = repository.readCachedGuardianships(),
                )
            }
        }
        viewModelScope.launch {
            val isOwner = dpc.isDeviceOwner()
            _state.update { it.copy(isDeviceOwner = isOwner) }
            // Promotes a pre-provisioning passkey to durable storage, and refreshes the local
            // cache from durable if the two ever drifted. The companion list gets the same
            // treatment: after a "Clear data" this is what brings the pairing back, rather than
            // leaving the device quietly unpaired and open to its passkey again.
            if (reconcile) {
                repository.reconcile()
                val peers = repository.reconcilePeers()
                val ownKey = repository.reconcileOwnPublicKey()
                val guardianFor = repository.reconcileGuardianships()
                _state.update {
                    it.copy(peers = peers, ownPublicKey = ownKey, guardianFor = guardianFor)
                }
            }
        }
    }

    /**
     * Hardening and any pending release request. Both belong to the Home screen, which the user
     * cannot reach without authenticating first, so neither gates the gate.
     */
    private suspend fun loadHardening() {
        val hardening = KillitLog.timed(KillitLog.VM, "read hardening") { repository.hardening.first() }
        _state.update { it.copy(hardening = hardening) }

        val request = repository.releaseRequest()
        if (request != null) {
            KillitLog.i(KillitLog.VM, "Release pending, available at ${request.availableAtMillis}")
        }
        _state.update { it.copy(releaseRequest = request) }
    }

    private suspend fun loadApps() {
        val apps = inventory.load()
        val blocked = dpc.blockedPackages(apps.map { it.packageName })
        _state.update { it.copy(
            apps = apps,
            blocked = blocked,
            // Start with no pending changes: the checkboxes mirror reality.
            selection = blocked,
            appsLoading = false,
        ) }
        KillitLog.d(KillitLog.VM) { "loadApps: ${apps.size} apps, ${blocked.size} blocked" }
    }

    /** Re-checks device owner status after the user has provisioned outside the app (adb, QR). */
    fun refreshOwnerStatus() {
        viewModelScope.launch {
            KillitLog.i(KillitLog.VM, "Refreshing device owner status")
            val isOwner = dpc.isDeviceOwner()
            _state.update { it.copy(isDeviceOwner = isOwner, appsLoading = true) }
            if (isOwner) {
                // A passkey set before provisioning now gains durable, clear-data-proof backing.
                repository.promoteToDurable()
                dpc.applyHardening(_state.value.hardening)
            }
            loadApps()
        }
    }

    // ---------------------------------------------------------------- auth

    /**
     * Checks a submitted credential.
     *
     * Guarded by [KillitUiState.busy] so a double tap cannot spend two lockout attempts on one
     * entry — which matters because key derivation takes long enough for a second tap to land.
     */
    fun submitGate(credential: String) {
        if (_state.value.isPaired) {
            // The passkey is not merely hidden while paired — it stops being accepted. Enforcing
            // it only in the UI would leave the old credential working for anything that reached
            // this function another way.
            KillitLog.w(KillitLog.VM, "Passkey refused: this device requires companion approval")
            return
        }
        if (_state.value.busy) {
            KillitLog.d(KillitLog.VM) { "submitGate ignored: already verifying" }
            return
        }
        viewModelScope.launch {
            KillitLog.i(KillitLog.VM, "Gate submission received (${KillitLog.describeSecret(credential)})")
            _state.update { it.copy(busy = true, gateFeedback = null) }

            val feedback = when (val result = repository.verify(credential)) {
                VerifyResult.Success -> {
                    authenticated = true
                    _state.update { it.copy(screen = Screen.Home) }
                    null
                }

                is VerifyResult.Wrong -> GateFeedback.Wrong(result.remainingAttempts)
                is VerifyResult.LockedOut -> GateFeedback.LockedOut(result.remainingMillis)

                VerifyResult.NoCredential -> {
                    // Nothing to check against; send the user to set one rather than loop.
                    KillitLog.w(KillitLog.VM, "Gate had no record to verify against; going to Setup")
                    _state.update { it.copy(screen = Screen.Setup) }
                    null
                }
            }

            _state.update { it.copy(busy = false, gateFeedback = feedback) }
            KillitLog.d(KillitLog.VM) { "Gate finished: ${_state.value.describe()}" }
        }
    }

    fun setCredential(lockType: LockType, credential: String) {
        if (_state.value.isPaired) {
            // Setting a new passkey while paired would create a second way in that the companions
            // know nothing about.
            KillitLog.w(KillitLog.VM, "Refusing to set a passkey: this device requires companion approval")
            return
        }
        viewModelScope.launch {
            KillitLog.i(KillitLog.VM, "Setting passkey ($lockType)")
            _state.update { it.copy(busy = true) }
            repository.setCredential(lockType, credential)
            // Setting a passkey authenticates the session: the user just proved it twice.
            authenticated = true
            _state.update { it.copy(
                busy = false,
                lockType = lockType,
                screen = Screen.Home,
                message = UiMessage.PasskeySet,
            ) }
        }
    }

    // ---------------------------------------------------------------- app blocking

    /** Local checkbox change only; nothing reaches the OS until [save]. */
    fun toggleSelection(packageName: String, checked: Boolean) {
        val current = _state.value.selection
        _state.update { it.copy(
            selection = if (checked) current + packageName else current - packageName
        ) }
        KillitLog.v(KillitLog.VM) {
            "toggle $packageName -> $checked (${_state.value.pendingChanges} pending)"
        }
    }

    fun discardSelection() {
        KillitLog.d(KillitLog.VM) { "Discarding ${_state.value.pendingChanges} pending changes" }
        _state.update { it.copy(selection = _state.value.blocked) }
    }

    /**
     * Pushes the pending selection to the OS, then re-reads what actually landed.
     *
     * The re-read is the important part: Android silently refuses to suspend some packages, so
     * assuming the write succeeded would leave checkboxes ticked for apps that are still usable.
     */
    fun save() {
        val snapshot = _state.value
        if (snapshot.busy) {
            KillitLog.d(KillitLog.VM) { "save ignored: already busy" }
            return
        }
        viewModelScope.launch {
            KillitLog.i(KillitLog.VM, "Saving ${snapshot.pendingChanges} blocklist changes")
            _state.update { snapshot.copy(busy = true, saveFailures = emptyList()) }

            val result = dpc.applyBlocklist(desired = snapshot.selection, current = snapshot.blocked)

            // Re-read from the OS rather than assuming the write landed: any package Android
            // refused is still unblocked, and its checkbox has to go back.
            val blocked = dpc.blockedPackages(snapshot.apps.map { it.packageName })

            val failures = result.failedToBlock + result.failedToUnblock
            if (failures.isNotEmpty()) {
                KillitLog.w(KillitLog.VM, "Save completed with ${failures.size} rejected packages: $failures")
            } else {
                KillitLog.i(KillitLog.VM, "Save complete; ${blocked.size} packages now blocked")
            }

            _state.update { it.copy(
                busy = false,
                blocked = blocked,
                selection = blocked,
                saveFailures = failures,
                message = result.error?.let(UiMessage::Error) ?: UiMessage.Saved,
            ) }
        }
    }

    fun dismissSaveFailures() {
        _state.update { it.copy(saveFailures = emptyList()) }
    }

    // ---------------------------------------------------------------- hardening

    fun setHardening(config: HardeningConfig) {
        viewModelScope.launch {
            // Refusing the release button alone would be decoration: uninstalling Killit, or
            // clearing its data, destroys the Keystore entry just as completely. While other
            // devices depend on this phone's key, the two restrictions that close those routes
            // are held on.
            val effective = if (_state.value.isGuardian) {
                config.copy(blockUninstall = true, blockAppsControl = true)
            } else {
                config
            }

            KillitLog.i(KillitLog.VM, "Hardening changed: $effective")
            repository.setHardening(effective)
            dpc.applyHardening(effective)
            _state.update { it.copy(hardening = effective) }
        }
    }

    // ---------------------------------------------------------------- provisioning

    /** Not currently used by any screen; kept as the read-only counterpart to [provisionViaShizuku]. */
    suspend fun shizukuStatus(): ShizukuStatus = shizuku.status()

    /**
     * Walks the Shizuku provisioning flow and reports whatever came back.
     *
     * [KillitUiState.busy] stays true across the permission prompt on purpose — that prompt is
     * another app's activity, so Killit goes to the background and [lockOnBackground] would
     * otherwise demand the passkey again mid-provisioning.
     */
    fun provisionViaShizuku() {
        if (_state.value.busy) {
            KillitLog.d(KillitLog.VM) { "provisionViaShizuku ignored: already busy" }
            return
        }
        viewModelScope.launch {
            KillitLog.i(KillitLog.VM, "Starting Shizuku provisioning")
            _state.update { it.copy(busy = true, shizukuOutcome = null) }

            val outcome = when (shizuku.status()) {
                ShizukuStatus.NOT_RUNNING -> ShizukuOutcome.NotRunning
                ShizukuStatus.TOO_OLD -> ShizukuOutcome.TooOld
                ShizukuStatus.READY ->
                    if (!shizuku.requestPermission()) {
                        ShizukuOutcome.PermissionDenied
                    } else {
                        ShizukuOutcome.CommandOutput(shizuku.setDeviceOwner())
                    }
            }

            KillitLog.i(KillitLog.VM, "Shizuku provisioning outcome: ${outcome::class.java.simpleName}")
            _state.update { it.copy(busy = false, shizukuOutcome = outcome) }
            // The command may have succeeded regardless of what its text says, so re-check rather
            // than parsing the output.
            refreshOwnerStatus()
        }
    }

    fun dismissShizukuOutcome() {
        _state.update { it.copy(shizukuOutcome = null) }
    }

    // ---------------------------------------------------------------- companion pairing

    /**
     * Opens the pairing screen, creating this device's identity if it does not have one yet.
     *
     * The key is created here rather than at startup so an unpaired user never pays for a Keystore
     * operation they may never need.
     */
    fun openPairing() {
        viewModelScope.launch {
            // Granting the camera to ourselves keeps the system permission dialog out of the flow.
            // That dialog is another app's window, so it would background Killit, fire ON_STOP and
            // re-lock the session the user just authenticated into.
            dpc.grantSelfPermission(android.Manifest.permission.CAMERA)

            val ownKey = pairing.ensureIdentity()
            if (ownKey == null) {
                KillitLog.e(KillitLog.VM, "Could not establish a pairing identity")
                _state.update { it.copy(scanOutcome = ScanOutcome.NoPairingKey) }
                return@launch
            }
            _state.update { it.copy(ownPublicKey = ownKey, screen = Screen.Pairing, scanOutcome = null) }
        }
    }

    /** Handles a QR scanned on the pairing screen: another device offering itself as a companion. */
    fun onPairingCodeScanned(raw: String) {
        val ownKey = _state.value.ownPublicKey ?: return
        viewModelScope.launch {
            val outcome = pairing.acceptPairing(raw, ownKey)
            val peers = if (outcome == ScanOutcome.Accepted) repository.reconcilePeers() else _state.value.peers
            _state.update { it.copy(scanOutcome = outcome, peers = peers) }
        }
    }

    /**
     * Handles a QR scanned while acting as somebody else's companion.
     *
     * Available from the locked gate without authenticating. Signing proves possession of this
     * device's private key and opens nothing here; requiring an unlock first would deadlock two
     * mutually paired phones, since neither could open in order to help the other.
     */
    fun onApprovalRequestScanned(raw: String) {
        viewModelScope.launch {
            val (outcome, response) = pairing.approveChallenge(raw)
            val guardianFor = if (outcome == ScanOutcome.Accepted) {
                repository.readCachedGuardianships()
            } else {
                _state.value.guardianFor
            }
            _state.update {
                it.copy(
                    scanOutcome = outcome,
                    approvalResponse = response?.encode(),
                    guardianFor = guardianFor,
                )
            }
        }
    }

    /**
     * Opens the approval flow, creating this device's identity if it does not have one yet.
     *
     * Reachable from the locked gate as well as from Home. A device that is not itself paired is
     * never deadlocked, but it still has to be able to sign for the devices that paired with it,
     * and making its owner pass a passkey first buys nothing: signing opens nothing here.
     */
    fun openApproval() {
        viewModelScope.launch {
            dpc.grantSelfPermission(android.Manifest.permission.CAMERA)

            val ownKey = pairing.ensureIdentity()
            if (ownKey == null) {
                KillitLog.e(KillitLog.VM, "Could not establish a pairing identity")
                _state.update { it.copy(scanOutcome = ScanOutcome.NoPairingKey) }
                return@launch
            }
            _state.update {
                it.copy(
                    ownPublicKey = ownKey,
                    screen = Screen.Approve,
                    approvalResponse = null,
                    scanOutcome = null,
                )
            }
        }
    }

    /**
     * Leaves the approval flow.
     *
     * [goHome] cannot be used: this screen is reachable from the locked gate, and an unauthenticated
     * session has to land back there rather than on Home.
     */
    fun closeApproval() {
        _state.update {
            it.copy(
                approvalResponse = null,
                scanOutcome = null,
                screen = if (authenticated) Screen.Home else Screen.Gate,
            )
        }
    }

    fun dismissScanOutcome() {
        _state.update { it.copy(scanOutcome = null) }
    }

    fun setCameraFacing(facing: CameraFacing) {
        if (_state.value.cameraFacing == facing) return
        KillitLog.i(KillitLog.VM, "Scanner camera -> $facing")
        _state.update { it.copy(cameraFacing = facing) }
        viewModelScope.launch { repository.setCameraFacing(facing) }
    }

    /** Starts (or restarts) a round of collecting companion approvals. */
    fun startChallenge(purpose: ChallengePurpose) {
        viewModelScope.launch {
            val ownKey = _state.value.ownPublicKey ?: pairing.ensureIdentity()
            if (ownKey == null) {
                _state.update { it.copy(scanOutcome = ScanOutcome.NoPairingKey) }
                return@launch
            }
            _state.update {
                it.copy(
                    ownPublicKey = ownKey,
                    challenge = ChallengeSession.start(purpose, ownKey),
                    scanOutcome = null,
                )
            }
        }
    }

    /**
     * Handles a companion's response to an open challenge.
     *
     * Every paired companion has to approve, so this accumulates rather than deciding on the first
     * signature. A rejected scan leaves the approvals already collected untouched — a mis-scan
     * should not cost the user a second trip to the first companion.
     */
    fun onApprovalResponseScanned(raw: String) {
        val current = _state.value
        val session = current.challenge ?: return
        viewModelScope.launch {
            val (outcome, updated) = pairing.applyResponse(raw, session, current.peers)
            _state.update { it.copy(scanOutcome = outcome, challenge = updated) }

            if (outcome != ScanOutcome.Accepted) return@launch

            when (updated.purpose) {
                // Opening the device needs *every* companion, which is the whole point of the
                // arrangement — one person cannot decide alone.
                ChallengePurpose.UNLOCK -> {
                    if (!updated.isSatisfiedBy(current.peers)) return@launch
                    KillitLog.i(KillitLog.VM, "Every companion approved; unlocking")
                    authenticated = true
                    _state.update { it.copy(challenge = null, screen = Screen.Home) }
                }

                // Removing one companion needs only that companion's consent. Demanding the whole
                // group would mean a single unreachable phone freezes the list permanently, with
                // no way to remove even the peers that *are* present.
                ChallengePurpose.UNPAIR -> {
                    val peer = current.peerPendingRemoval
                    if (peer == null) {
                        KillitLog.w(KillitLog.VM, "Unpair approved but no companion was selected")
                        return@launch
                    }
                    if (!updated.hasApproval(peer)) {
                        KillitLog.i(
                            KillitLog.VM,
                            "A companion signed, but not the one being removed ('${peer.label}')",
                        )
                        return@launch
                    }
                    repository.removePeer(peer.publicKey)
                    val peers = repository.reconcilePeers()
                    KillitLog.i(KillitLog.VM, "Companion '${peer.label}' removed with its own approval")
                    _state.update {
                        it.copy(challenge = null, peerPendingRemoval = null, peers = peers)
                    }
                }
            }
        }
    }

    /**
     * Begins removing a companion, which that companion has to approve.
     *
     * Unpairing freely would undo everything: remove the companions, fall back to the passkey,
     * and the removal countdown becomes reachable again. Requiring the departing companion's
     * signature keeps the commitment as binding as making it was.
     */
    fun startPeerRemoval(peer: PairedPeer) {
        _state.update { it.copy(peerPendingRemoval = peer) }
        startChallenge(ChallengePurpose.UNPAIR)
    }

    fun cancelPeerRemoval() {
        _state.update { it.copy(peerPendingRemoval = null, challenge = null, scanOutcome = null) }
    }

    fun cancelChallenge() {
        _state.update { it.copy(challenge = null, scanOutcome = null) }
    }

    // ---------------------------------------------------------------- delayed release

    /**
     * Starts the waiting period before device owner can be given up.
     *
     * Nothing is released here — this only writes the deadline. The release itself needs a second
     * confirmation once the wait has elapsed.
     */
    fun requestRelease() {
        if (_state.value.isGuardian) {
            // Blocked on the phone whose key others depend on, not on the phone that has
            // companions. Releasing here ends with Killit uninstallable and its Keystore entry
            // gone, and every device that pairs to this one is then unopenable for good — a
            // factory reset from Recovery is all that is left for them. The phone that merely
            // *has* companions holds nothing anyone else needs, so it is not stopped: it still
            // cannot reach this screen without its companions letting it in.
            KillitLog.w(KillitLog.VM, "Release refused: other devices depend on this phone's key")
            return
        }
        viewModelScope.launch {
            val request = repository.requestRelease(BuildConfig.RELEASE_DELAY_MILLIS)
            _state.update { it.copy(releaseRequest = request) }
        }
    }

    fun cancelRelease() {
        viewModelScope.launch {
            repository.cancelRelease()
            _state.update { it.copy(releaseRequest = null) }
        }
    }

    /**
     * Carries out the release, but only if the wait has actually elapsed.
     *
     * The check is repeated here against freshly read storage rather than trusting
     * [KillitUiState.releaseRequest], which the UI may have been holding for a while. This is the
     * last gate before something that cannot be undone without a factory reset.
     */
    fun releaseDeviceOwner() {
        if (_state.value.busy) return
        viewModelScope.launch {
            if (_state.value.isGuardian) {
                KillitLog.w(KillitLog.VM, "Release refused: other devices depend on this phone's key")
                return@launch
            }
            if (!repository.isReleaseAllowed()) {
                KillitLog.w(KillitLog.VM, "Release refused: the waiting period has not elapsed")
                // Re-sync so the UI shows the real remaining time rather than an enabled button.
                _state.update { it.copy(releaseRequest = repository.releaseRequest()) }
                return@launch
            }

            KillitLog.i(KillitLog.VM, "Waiting period elapsed; releasing device owner")
            _state.update { it.copy(busy = true) }

            // Cleared *before* the release, not after. Clearing the durable copy needs device
            // owner, so doing it afterwards would silently leave the request behind in system
            // storage — and a device provisioned again later would find an already-elapsed
            // deadline waiting for it, skipping the wait entirely.
            //
            // The cost of this ordering is that a failed release makes the user start the wait
            // over. That is the right direction to fail in.
            repository.cancelRelease()

            val packages = _state.value.apps.map { it.packageName }
            dpc.releaseDeviceOwner(packages)
            _state.update {
                it.copy(busy = false, isDeviceOwner = dpc.isDeviceOwner(), releaseRequest = null)
            }
            loadApps()
        }
    }

    // ---------------------------------------------------------------- navigation

    fun navigateTo(screen: Screen) {
        KillitLog.d(KillitLog.VM) { "navigate ${_state.value.screen} -> $screen" }
        _state.update { it.copy(screen = screen, gateFeedback = null) }
    }

    /** Guarded: without the check, a back press from an unauthenticated screen would reach Home. */
    fun goHome() {
        if (!authenticated) {
            KillitLog.w(KillitLog.VM, "goHome refused: session is not authenticated")
            return
        }
        _state.update { it.copy(screen = Screen.Home) }
    }

    /**
     * Re-locks when Killit leaves the foreground.
     *
     * Without this, an authenticated session would sit on the home screen indefinitely — hand the
     * phone over and everything can be unblocked.
     *
     * Skipped while busy: the Shizuku permission prompt is another app's activity, and stopping
     * mid-provisioning to demand the passkey again would just be obstructive.
     */
    fun lockOnBackground() {
        val current = _state.value
        if (!authenticated) {
            KillitLog.v(KillitLog.VM) { "lockOnBackground: already locked" }
            return
        }
        if (current.busy) {
            KillitLog.d(KillitLog.VM) { "lockOnBackground skipped: busy (probably a permission prompt)" }
            return
        }

        KillitLog.i(KillitLog.VM, "App backgrounded; re-locking")
        authenticated = false
        _state.update {
            it.copy(
                screen = Screen.Gate,
                gateFeedback = null,
                // Drop unsaved checkbox changes rather than carrying them past a lock.
                selection = it.blocked,
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    suspend fun iconFor(packageName: String) = inventory.icon(packageName)

    override fun onCleared() {
        super.onCleared()
        KillitLog.d(KillitLog.VM) { "ViewModel cleared" }
    }

    companion object {
        /**
         * Reads the graph from [ServiceLocator]. [ShizukuProvisioner] is built per-ViewModel
         * rather than being a singleton because it holds no state between provisioning attempts.
         */
        val Factory = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                KillitViewModel(
                    repository = ServiceLocator.lockRepository,
                    dpc = ServiceLocator.devicePolicyController,
                    inventory = ServiceLocator.appInventory,
                    shizuku = ShizukuProvisioner(application),
                    pairing = ServiceLocator.pairingManager,
                )
            }
        }
    }
}
