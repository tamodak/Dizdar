package org.tamodak.killit.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.tamodak.killit.admin.DevicePolicyController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one thing worth a test here: a passkey that was set must keep opening the lock, and a wrong
 * one must not.
 *
 * Runs on a device rather than the JVM because [LockPreferences] is backed by DataStore.
 */
@RunWith(AndroidJUnit4::class)
class PasskeyFlowTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val credentials = CredentialStore()
    private val prefs = LockPreferences(context)
    private val repository = LockRepository(
        prefs = prefs,
        durable = DurableStore(DevicePolicyController(context)),
        credentials = credentials,
    )

    @Before
    fun setUp() = runBlocking { prefs.clearRecord() }

    @After
    fun tearDown() = runBlocking { prefs.clearRecord() }

    @Test
    fun setThenVerify(): Unit = runBlocking {
        repository.setCredential(LockType.PATTERN, PASSKEY)

        assertEquals(VerifyResult.Success, repository.verify(PASSKEY))
        assertTrue("A wrong passkey must be rejected", repository.verify("9-9-9-9") is VerifyResult.Wrong)
        // Still works after a wrong guess, and the wrong guess did not corrupt the record.
        assertEquals(VerifyResult.Success, repository.verify(PASSKEY))
    }

    @Test
    fun wrongAttemptsCountDownThenReset(): Unit = runBlocking {
        repository.setCredential(LockType.PIN, "112233")

        val first = repository.verify("000000") as VerifyResult.Wrong
        val second = repository.verify("000000") as VerifyResult.Wrong
        assertEquals(first.remainingAttempts - 1, second.remainingAttempts)

        assertEquals(VerifyResult.Success, repository.verify("112233"))
        assertEquals("Attempts reset after a success", 0, prefs.readRecord()!!.failedAttempts)
    }

    @Test
    fun hashingIsSaltedAndDeterministic() {
        val salt = credentials.newSalt()

        assertTrue(
            "The same input must produce the same hash",
            credentials.matches(
                credentials.hash(PASSKEY, salt),
                credentials.hash(PASSKEY, salt),
            )
        )
        assertFalse(
            "A different salt must produce a different hash",
            credentials.matches(
                credentials.hash(PASSKEY, salt),
                credentials.hash(PASSKEY, credentials.newSalt()),
            )
        )
        assertFalse(
            "A different passkey must produce a different hash",
            credentials.matches(
                credentials.hash(PASSKEY, salt),
                credentials.hash("1-2-3-4", salt),
            )
        )
        assertEquals("Expected a SHA-256 digest", 32, credentials.hash(PASSKEY, salt).size)
    }

    private companion object {
        const val PASSKEY = "0-3-6-7"
    }
}
