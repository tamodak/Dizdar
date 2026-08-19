package org.tamodak.dizdar

import android.app.Application
import android.os.Build
import org.tamodak.dizdar.core.DizdarLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.ShizukuProvider

/**
 * Application entry point.
 *
 * Does two things and nothing else: builds the dependency graph, and tells the Shizuku provider
 * how this app is laid out. Everything expensive is deferred to the ViewModel, because
 * `onCreate` runs on the main thread before the first frame — work added here is time the launcher
 * icon spends looking unresponsive.
 */
class DizdarApp : Application() {

    /**
     * Lives as long as the process. Only used for warm-up work that must not be tied to a screen,
     * so there is nothing to cancel — [SupervisorJob] keeps one failure from taking the scope down.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Records the session banner, builds the dependency graph, and kicks off warm-up.
     *
     * Runs on the main thread before the first frame, so the only work performed inline is
     * construction; the preference file is opened on [appScope] instead.
     */
    override fun onCreate() {
        super.onCreate()

        // First line of every session. The device and build identity here is what makes a log
        // dump from someone else's phone actionable — most Dizdar problems are device-specific
        // (which packages the OEM refuses to suspend, how slow key derivation is, whether
        // StrongBox exists), so knowing the hardware is half the diagnosis.
        DizdarLog.i(
            DizdarLog.APP,
            "Dizdar starting: ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME} " +
                "(${BuildConfig.BUILD_TYPE}) on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "verbose logging=${DizdarLog.verbose}",
        )

        DizdarLog.timed(DizdarLog.APP, "ServiceLocator.init") {
            ServiceLocator.init(this)
        }

        // Open the preferences file now, in parallel with the rest of process startup and
        // Compose's first composition. Whichever read happens first pays for opening and parsing
        // it; doing that here means the unlock path finds it already warm.
        appScope.launch { ServiceLocator.lockRepository.prewarm() }

        // Needed for Shizuku pre-v11; harmless on newer versions. False because Dizdar runs in a
        // single process — the privileged user service is Shizuku's own process, not Dizdar's.
        ShizukuProvider.enableMultiProcessSupport(false)
        DizdarLog.d(DizdarLog.APP) { "Shizuku multi-process support disabled (single-process app)" }
    }
}
