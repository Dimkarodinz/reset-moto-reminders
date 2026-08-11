package dev.resetlight.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseConsentStoreTest {
    @Test
    fun `consent is required until the current notice is accepted`() {
        assertFalse(ReleaseConsentPolicy.isAccepted(acceptedVersion = 0))
        assertTrue(ReleaseConsentPolicy.isAccepted(acceptedVersion = ReleaseConsentPolicy.CURRENT_VERSION))
    }

    @Test
    fun `accepting the notice persists across store instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "release-consent-${UUID.randomUUID()}"
        val first = ReleaseConsentStore(context, preferencesName)

        assertFalse(first.isAccepted())
        first.accept()

        assertTrue(ReleaseConsentStore(context, preferencesName).isAccepted())
    }

    @Test
    fun `an older accepted notice does not bypass a newer notice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferencesName = "release-consent-${UUID.randomUUID()}"
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putInt(ReleaseConsentStore.ACCEPTED_VERSION_KEY, ReleaseConsentPolicy.CURRENT_VERSION - 1)
            .commit()

        assertFalse(ReleaseConsentStore(context, preferencesName).isAccepted())
    }
}
