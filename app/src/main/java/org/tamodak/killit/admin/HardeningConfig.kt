package org.tamodak.killit.admin

/**
 * The anti-tamper policies Killit applies as device owner.
 *
 * Every flag maps 1:1 to a call in [DevicePolicyController.applyHardening]. Together they close
 * the routes a user could otherwise take to escape a block without the passkey; each is exposed as
 * a toggle because they all cost something in convenience, and how far to go is the owner's call.
 *
 * All default to `true`: a fresh install starts locked down, which is the safe direction to be
 * wrong in.
 */
data class HardeningConfig(
    /** Stops Killit being uninstalled to escape the block. */
    val blockUninstall: Boolean = true,

    /**
     * Greys out Force stop in Settings. Requires Android 11 — below that the platform has no
     * equivalent API and the toggle is shown disabled.
     */
    val blockForceStop: Boolean = true,

    /** Safe mode would otherwise disable third-party apps on reboot, Killit included. */
    val blockSafeBoot: Boolean = true,

    /**
     * Blocks factory reset, and with it adding a new user — a second user is another route to a
     * de facto reset.
     */
    val blockFactoryReset: Boolean = true,

    /**
     * Blocks managing apps from Settings.
     *
     * Blocking force-stop does **not** block Settings -> Apps -> Killit -> Storage -> Clear data,
     * which would wipe the local passkey copy while the OS keeps the apps blocked. This
     * restriction is the only thing that closes that route. It is broad — it also stops the user
     * managing any *other* app from Settings — which is why it is a toggle rather than always on.
     */
    val blockAppsControl: Boolean = true,

    /**
     * Blocks changing the system date and time.
     *
     * The delay on "remove admin" is measured against the wall clock, so winding the clock forward
     * three days would skip the wait entirely. This restriction is what makes that delay real
     * rather than decorative.
     */
    val blockDateTime: Boolean = true,
) {
    companion object {
        val DEFAULT = HardeningConfig()
    }
}
