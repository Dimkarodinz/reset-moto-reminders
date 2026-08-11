package dev.resetlight.features.connection

import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionScreenPresenterTest {
    private val presenter = ConnectionScreenPresenter()

    @Test
    fun `disconnected offers adapter selection and connection`() {
        val screen = presenter.present(ConnectionState.Disconnected, selectedAdapterName = "vLinker MC-Android")

        assertEquals("Disconnected", screen.statusTitle)
        assertTrue(screen.showPairOrSelect)
        assertTrue(screen.showConnect)
        assertTrue(screen.connectEnabled)
        assertFalse(screen.showDisconnect)
        assertEquals("vLinker MC-Android", screen.selectedAdapterName)
    }

    @Test
    fun `connect stays disabled until an adapter is selected`() {
        val screen = presenter.present(ConnectionState.Disconnected, selectedAdapterName = null)

        assertTrue(screen.showConnect)
        assertFalse(screen.connectEnabled)
        assertEquals("Pair or select an adapter before connecting.", screen.statusDetail)
    }

    @Test
    fun `connection phases expose precise progress labels and no competing actions`() {
        val cases = listOf(
            ConnectionState.Connecting to "Connecting",
            ConnectionState.Identifying to "Identifying adapter",
            ConnectionState.Initializing to "Initializing adapter",
            ConnectionState.Disconnecting to "Disconnecting",
        )

        cases.forEach { (state, title) ->
            val screen = presenter.present(state, selectedAdapterName = "vLinker")
            assertEquals(title, screen.statusTitle)
            assertTrue(screen.inProgress)
            assertFalse(screen.showConnect)
            assertFalse(screen.showDisconnect)
            assertNull(screen.failureAction)
        }
    }

    @Test
    fun `ready exposes identities selected map and disconnect only`() {
        val screen = presenter.present(
            ConnectionState.AdapterReady(
                elmIdentity = "ELM327 v2.2",
                stnIdentity = "STN1151 v4.3.2",
                mapId = "vlinker-mc-android",
            ),
            selectedAdapterName = "vLinker MC-Android",
            researchCaptureEnabled = true,
        )

        assertEquals("Adapter ready", screen.statusTitle)
        assertEquals("ELM327 v2.2", screen.elmIdentity)
        assertEquals("STN1151 v4.3.2", screen.stnIdentity)
        assertEquals("vlinker-mc-android", screen.mapId)
        assertTrue(screen.showDisconnect)
        assertTrue(screen.showReadOnlyCapture)
        assertTrue(screen.showDtcRead)
        assertTrue(screen.showServiceInfoRead)
        assertFalse(screen.showConnect)
        assertFalse(screen.showPairOrSelect)
    }

    @Test
    fun `release presentation never exposes research capture`() {
        val screen = presenter.present(
            ConnectionState.AdapterReady("ELM327", "STN1151", "vlinker-mc-android"),
            selectedAdapterName = "vLinker",
            researchCaptureEnabled = false,
        )

        assertFalse(screen.showReadOnlyCapture)
        assertFalse(screen.showServiceInfoRead)
    }

    @Test
    fun `dtc read is a mainline feature once the adapter is ready`() {
        val screen = presenter.present(
            ConnectionState.AdapterReady("ELM327", "STN1151", "vlinker-mc-android"),
            selectedAdapterName = "vLinker",
            researchCaptureEnabled = false,
        )

        assertTrue("DTC read must not be gated behind the research build", screen.showDtcRead)
    }

    @Test
    fun `pairing failure gives settings action and confirmed pin`() {
        val screen = presenter.present(
            ConnectionState.Failed(ConnectionFailure.PAIRING_REQUIRED),
            selectedAdapterName = null,
        )

        assertEquals("Pairing required", screen.statusTitle)
        assertEquals(
            "Pair vLinker MC-Android in Android Bluetooth settings using PIN 1234, then return and select it.",
            screen.statusDetail,
        )
        assertEquals(FailureAction.OPEN_BLUETOOTH_SETTINGS, screen.failureAction)
        assertEquals("Open Bluetooth settings", screen.failureActionLabel)
    }

    @Test
    fun `permission failure gives permission action`() {
        val screen = presenter.present(
            ConnectionState.Failed(ConnectionFailure.PERMISSION_DENIED),
            selectedAdapterName = "vLinker",
        )

        assertEquals("Bluetooth permission needed", screen.statusTitle)
        assertEquals(FailureAction.REQUEST_PERMISSION, screen.failureAction)
        assertEquals("Grant permission", screen.failureActionLabel)
    }

    @Test
    fun `timeout identity mismatch and remote close remain distinct and actionable`() {
        val cases = listOf(
            FailureExpectation(
                ConnectionFailure.TIMEOUT,
                "Connection timed out",
                "The adapter did not respond in time. Check that it is powered and nearby, then try again.",
                FailureAction.RETRY_CONNECTION,
                "Try again",
            ),
            FailureExpectation(
                ConnectionFailure.IDENTITY_MISMATCH,
                "Unsupported adapter identity",
                "The connected device does not match the selected adapter map. Disconnect and select the correct adapter.",
                FailureAction.SELECT_ADAPTER,
                "Select another adapter",
            ),
            FailureExpectation(
                ConnectionFailure.REMOTE_CLOSE,
                "Adapter disconnected",
                "The adapter closed the connection. Check power and Bluetooth range before reconnecting.",
                FailureAction.RETRY_CONNECTION,
                "Reconnect",
            ),
        )

        cases.forEach { expected ->
            val screen = presenter.present(
                ConnectionState.Failed(expected.failure),
                selectedAdapterName = "vLinker",
            )
            assertEquals(expected.title, screen.statusTitle)
            assertEquals(expected.detail, screen.statusDetail)
            assertEquals(expected.action, screen.failureAction)
            assertEquals(expected.actionLabel, screen.failureActionLabel)
        }
    }

    @Test
    fun `io failure does not claim a more specific cause`() {
        val screen = presenter.present(
            ConnectionState.Failed(ConnectionFailure.IO),
            selectedAdapterName = "vLinker",
        )

        assertEquals("Connection failed", screen.statusTitle)
        assertEquals(FailureAction.RETRY_CONNECTION, screen.failureAction)
    }

    @Test
    fun `service reminder reset remains unavailable without a validated write profile`() {
        val screen = presenter.present(
            ConnectionState.AdapterReady("ELM327", "STN1151", "vlinker-mc-android"),
            selectedAdapterName = "vLinker",
        )

        assertEquals("Service reminder reset", screen.serviceCard.title)
        assertEquals("Unavailable until the motorcycle profile is validated.", screen.serviceCard.detail)
        assertFalse(screen.serviceCard.enabled)
    }

    private data class FailureExpectation(
        val failure: ConnectionFailure,
        val title: String,
        val detail: String,
        val action: FailureAction,
        val actionLabel: String,
    )
}
