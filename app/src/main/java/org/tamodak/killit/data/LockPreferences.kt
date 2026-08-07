package org.tamodak.killit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.tamodak.killit.admin.HardeningConfig
import org.tamodak.killit.core.KillitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The DataStore instance, declared as an extension property on [Context] as the DataStore
 * documentation prescribes.
 *
 * The delegate guarantees exactly one instance per file per process. Constructing a second
 * DataStore over the same file throws at runtime, which is why this lives at file scope rather
 * than being created inside [LockPreferences].
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "killit_prefs")

/**
 * Local storage: the hardening toggles, plus a cache and pre-provisioning bootstrap for the
 * passkey.
 *
 * Before Killit is device owner there is nowhere durable to write, so the first passkey lands here
 * and is promoted to [DurableStore] the moment provisioning succeeds.
 *
 * **Everything here is wiped by "Clear data" — that is exactly why [DurableStore] exists.**
 *
 * All functions are main-safe: DataStore performs its own file IO on its own dispatcher.
 */
class LockPreferences(context: Context) {

    private val store = context.applicationContext.dataStore

    /**
     * Forces the backing file to be opened and parsed.
     *
     * The *first* read from a DataStore pays for opening and deserialising the whole file — on a
     * budget device that measured around 150ms, and whichever call happens to be first wears it.
     * Called from `Application.onCreate` on a background coroutine so that cost overlaps with
     * process startup and Compose's first composition, instead of landing on the unlock path.
     *
     * Errors are swallowed on purpose: this is an optimisation, and a failure here simply means
     * the real read pays the cost as it did before.
     */
    suspend fun prewarm() {
        runCatching {
            KillitLog.timed(KillitLog.PREFS, "prewarm DataStore") { store.data.first() }
        }.onFailure { KillitLog.d(KillitLog.PREFS) { "prewarm failed: ${it.javaClass.simpleName}" } }
    }

    /**
     * The hardening toggles, defaulting to fully hardened. A fresh install with no stored values
     * therefore starts locked down rather than open — the safe direction to be wrong in.
     */
    val hardening: Flow<HardeningConfig> = store.data.map { prefs ->
        HardeningConfig(
            blockUninstall = prefs[KEY_HARDEN_UNINSTALL] ?: true,
            blockForceStop = prefs[KEY_HARDEN_FORCE_STOP] ?: true,
            blockSafeBoot = prefs[KEY_HARDEN_SAFE_BOOT] ?: true,
            blockFactoryReset = prefs[KEY_HARDEN_FACTORY_RESET] ?: true,
            blockAppsControl = prefs[KEY_HARDEN_APPS_CONTROL] ?: true,
            blockDateTime = prefs[KEY_HARDEN_DATE_TIME] ?: true,
        )
    }

    suspend fun setHardening(config: HardeningConfig) {
        KillitLog.timed(KillitLog.PREFS, "setHardening") {
            store.edit { prefs ->
                prefs[KEY_HARDEN_UNINSTALL] = config.blockUninstall
                prefs[KEY_HARDEN_FORCE_STOP] = config.blockForceStop
                prefs[KEY_HARDEN_SAFE_BOOT] = config.blockSafeBoot
                prefs[KEY_HARDEN_FACTORY_RESET] = config.blockFactoryReset
                prefs[KEY_HARDEN_APPS_CONTROL] = config.blockAppsControl
                prefs[KEY_HARDEN_DATE_TIME] = config.blockDateTime
            }
        }
        KillitLog.d(KillitLog.PREFS) { "Hardening saved: $config" }
    }

    /**
     * Reads the stored record, or null if any required field is missing or unparseable.
     *
     * A partially written record is treated as no record at all. That is deliberate: a half-record
     * cannot verify anything, so pretending it exists would only produce unwinnable attempts.
     */
    suspend fun readRecord(): CredentialRecord? =
        KillitLog.timed(KillitLog.PREFS, "readRecord") {
            val prefs = store.data.first()

            val type = LockType.fromName(prefs[KEY_LOCK_TYPE])
            if (type == null) {
                KillitLog.d(KillitLog.PREFS) { "readRecord: no lock type stored" }
                return@timed null
            }
            val salt = prefs[KEY_SALT]?.decodeBase64()
            if (salt == null) {
                KillitLog.w(KillitLog.PREFS, "readRecord: lock type is $type but the salt is missing or corrupt")
                return@timed null
            }
            val hash = prefs[KEY_HASH]?.decodeBase64()
            if (hash == null) {
                KillitLog.w(KillitLog.PREFS, "readRecord: lock type is $type but the hash is missing or corrupt")
                return@timed null
            }

            CredentialRecord(
                lockType = type,
                salt = salt,
                hash = hash,
                failedAttempts = prefs[KEY_FAILED_ATTEMPTS] ?: 0,
                lockoutUntilMillis = prefs[KEY_LOCKOUT_UNTIL] ?: 0L,
            ).also {
                KillitLog.d(KillitLog.PREFS) {
                    "readRecord: $type hash=${KillitLog.fingerprint(hash)} failed=${it.failedAttempts}"
                }
            }
        }

    suspend fun writeRecord(record: CredentialRecord) {
        KillitLog.timed(KillitLog.PREFS, "writeRecord") {
            store.edit { prefs ->
                prefs[KEY_LOCK_TYPE] = record.lockType.name
                prefs[KEY_SALT] = record.salt.encodeBase64()
                prefs[KEY_HASH] = record.hash.encodeBase64()
                prefs[KEY_FAILED_ATTEMPTS] = record.failedAttempts
                prefs[KEY_LOCKOUT_UNTIL] = record.lockoutUntilMillis
            }
        }
        KillitLog.d(KillitLog.PREFS) {
            "writeRecord: ${record.lockType} hash=${KillitLog.fingerprint(record.hash)} " +
                "failed=${record.failedAttempts}"
        }
    }

    // ------------------------------------------------------------------ paired peers

    suspend fun readPeers(): List<PairedPeer> =
        PairedPeer.deserialize(store.data.first()[KEY_PEERS])

    suspend fun writePeers(peers: List<PairedPeer>) {
        store.edit { prefs -> prefs[KEY_PEERS] = PairedPeer.serialize(peers) }
        KillitLog.d(KillitLog.PAIRING) { "Local peer list now holds ${peers.size} companions" }
    }

    suspend fun readOwnPublicKey(): ByteArray? = store.data.first()[KEY_OWN_PUBLIC_KEY]?.decodeBase64()

    suspend fun writeOwnPublicKey(publicKey: ByteArray) {
        store.edit { prefs -> prefs[KEY_OWN_PUBLIC_KEY] = publicKey.encodeBase64() }
    }

    suspend fun readGuardianships(): Set<String> =
        deserializeGuardianships(store.data.first()[KEY_GUARDIAN_FOR])

    suspend fun writeGuardianships(fingerprints: Set<String>) {
        store.edit { prefs -> prefs[KEY_GUARDIAN_FOR] = serializeGuardianships(fingerprints) }
    }

    suspend fun readCameraFacing(): CameraFacing =
        CameraFacing.fromName(store.data.first()[KEY_CAMERA_FACING])

    suspend fun writeCameraFacing(facing: CameraFacing) {
        store.edit { prefs -> prefs[KEY_CAMERA_FACING] = facing.name }
    }

    // ------------------------------------------------------------------ release request

    /** The pending release request, or null if none is outstanding. */
    suspend fun readReleaseRequest(): ReleaseRequest? {
        val prefs = store.data.first()
        val requestedAt = prefs[KEY_RELEASE_REQUESTED_AT] ?: return null
        val availableAt = prefs[KEY_RELEASE_AVAILABLE_AT] ?: return null
        return ReleaseRequest(requestedAtMillis = requestedAt, availableAtMillis = availableAt)
    }

    suspend fun writeReleaseRequest(request: ReleaseRequest) {
        store.edit { prefs ->
            prefs[KEY_RELEASE_REQUESTED_AT] = request.requestedAtMillis
            prefs[KEY_RELEASE_AVAILABLE_AT] = request.availableAtMillis
        }
        KillitLog.d(KillitLog.PREFS) { "writeReleaseRequest: available at ${request.availableAtMillis}" }
    }

    suspend fun clearReleaseRequest() {
        store.edit { prefs ->
            prefs.remove(KEY_RELEASE_REQUESTED_AT)
            prefs.remove(KEY_RELEASE_AVAILABLE_AT)
        }
        KillitLog.d(KillitLog.PREFS) { "clearReleaseRequest" }
    }

    /**
     * Drops the local record but leaves the hardening toggles alone.
     *
     * Not currently called by any screen; kept as the counterpart to [writeRecord] for a future
     * "forget this device" flow.
     */
    suspend fun clearRecord() {
        KillitLog.i(KillitLog.PREFS, "Clearing the local credential record")
        store.edit { prefs ->
            prefs.remove(KEY_LOCK_TYPE)
            prefs.remove(KEY_SALT)
            prefs.remove(KEY_HASH)
            prefs.remove(KEY_FAILED_ATTEMPTS)
            prefs.remove(KEY_LOCKOUT_UNTIL)
        }
    }

    private companion object {
        // The salt/hash keys carry a suffix because the hashing scheme changed: a record written
        // by an older build holds a hash this code can never reproduce. Storing the new scheme
        // under new keys makes such a record simply invisible — the user is sent to set a passkey
        // again, rather than being locked out by attempts that can never succeed.
        val KEY_LOCK_TYPE = stringPreferencesKey("lock_type")
        val KEY_SALT = stringPreferencesKey("salt_sha256")
        val KEY_HASH = stringPreferencesKey("hash_sha256")
        val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val KEY_LOCKOUT_UNTIL = longPreferencesKey("lockout_until")

        val KEY_RELEASE_REQUESTED_AT = longPreferencesKey("release_requested_at")
        val KEY_RELEASE_AVAILABLE_AT = longPreferencesKey("release_available_at")

        val KEY_PEERS = stringPreferencesKey("paired_peers")
        val KEY_OWN_PUBLIC_KEY = stringPreferencesKey("own_public_key")

        val KEY_GUARDIAN_FOR = stringPreferencesKey("guardian_for")
        val KEY_CAMERA_FACING = stringPreferencesKey("camera_facing")

        val KEY_HARDEN_UNINSTALL = booleanPreferencesKey("harden_uninstall")
        val KEY_HARDEN_FORCE_STOP = booleanPreferencesKey("harden_force_stop")
        val KEY_HARDEN_SAFE_BOOT = booleanPreferencesKey("harden_safe_boot")
        val KEY_HARDEN_FACTORY_RESET = booleanPreferencesKey("harden_factory_reset")
        val KEY_HARDEN_APPS_CONTROL = booleanPreferencesKey("harden_apps_control")
        val KEY_HARDEN_DATE_TIME = booleanPreferencesKey("harden_date_time")
    }
}
