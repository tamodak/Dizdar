package org.tamodak.dizdar.admin

/**
 * The anti-tamper policies Dizdar applies as device owner.
 *
 * Every flag maps 1:1 to a call in [DevicePolicyController.applyHardening]. Together they close
 * the routes a user could otherwise take to escape a block without the passkey; each is exposed as
 * a toggle because they all cost something in convenience, and how far to go is the owner's call.
 *
 * All default to `true`: a fresh install starts locked down, which is the safe direction to be
 * wrong in.
 *
 * @param blockUninstall stops Dizdar being uninstalled to escape the block.
 * @param blockForceStop greys out Force stop in Settings. Requires Android 11 — below that the
 *   platform has no equivalent API and the toggle is shown disabled.
 * @param blockSafeBoot blocks safe mode, which would otherwise disable third-party apps on reboot,
 *   Dizdar included.
 * @param blockFactoryReset blocks factory reset, and with it adding a new user — a second user is
 *   another route to a de facto reset.
 * @param blockAppsControl blocks managing apps from Settings. Blocking force-stop does **not**
 *   block Settings -> Apps -> Dizdar -> Storage -> Clear data, which would wipe the local passkey
 *   copy while the OS keeps the apps blocked. This restriction is the only thing that closes that
 *   route. It is broad — it also stops the user managing any *other* app from Settings — which is
 *   why it is a toggle rather than always on.
 * @param blockDateTime blocks changing the system date and time. The delay on "remove admin" is
 *   measured against the wall clock, so winding the clock forward three days would skip the wait
 *   entirely. This restriction is what makes that delay real rather than decorative.
 */
data class HardeningConfig(
    val blockUninstall: Boolean = true,
    val blockForceStop: Boolean = true,
    val blockSafeBoot: Boolean = true,
    val blockFactoryReset: Boolean = true,
    val blockAppsControl: Boolean = true,
    val blockDateTime: Boolean = true,
) {
    companion object {
        /** Fully hardened, the state a fresh install starts in. */
        val DEFAULT = HardeningConfig()
    }
}
