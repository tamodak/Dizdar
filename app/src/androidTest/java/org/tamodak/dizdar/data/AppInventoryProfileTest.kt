package org.tamodak.killit.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Attributes the cost of building the app list to the individual PackageManager calls behind it.
 *
 * `AppInventory.load` makes two calls per installed package, and which of them dominates decides
 * what a fix would even look like — so this measures rather than guesses.
 */
@RunWith(AndroidJUnit4::class)
class AppInventoryProfileTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pm: PackageManager = context.packageManager

    @Test
    fun profileInstalledAppScan() {
        @Suppress("DEPRECATION")
        val installed = measured("getInstalledApplications") { pm.getInstalledApplications(0) }

        val labels = measured("getApplicationLabel x${installed.size}") {
            installed.map { pm.getApplicationLabel(it).toString() }
        }

        val launchable = measured("getLaunchIntentForPackage x${installed.size}") {
            installed.count { pm.getLaunchIntentForPackage(it.packageName) != null }
        }

        Log.i(TAG, "Scanned ${installed.size} packages, $launchable launchable, ${labels.size} labels")
    }

    private inline fun <T> measured(label: String, block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        val result = block()
        Log.i(TAG, "$label took ${SystemClock.elapsedRealtime() - startedAt}ms")
        return result
    }

    private companion object {
        const val TAG = "KillitProfile"
    }
}
