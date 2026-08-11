package dev.resetlight.features.connection

import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
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

        assertEquals(UiText(UiMessage.STATUS_DISCONNECTED_TITLE), screen.statusTitle)
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
        assertEquals(UiText(UiMessage.STATUS_DISCONNECTED_DETAIL_NONE), screen.statusDetail)
    }

    @Test
    fun `connection phases expose precise progress labels and no competing actions`() {
        val cases = listOf(
            ConnectionState.Connecting to UiMessage.STATUS_CONNECTING_TITLE,
            ConnectionState.Identifying to UiMessage.STATUS_IDENTIFYING_TITLE,
            ConnectionState.Initializing to UiMessage.STATUS_INITIALIZING_TITLE,
            ConnectionState.Disconnecting to UiMessage.STATUS_DISCONNECTING_TITLE,
        )

        cases.forEach { (state, title) ->
            val screen = presenter.present(state, selectedAdapterName = "vLinker")
            assertEquals(UiText(title), screen.statusTitle)
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

        assertEquals(UiText(UiMessage.STATUS_READY_TITLE), screen.statusTitle)
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

        assertEquals(UiText(UiMessage.FAILURE_PAIRING_TITLE), screen.statusTitle)
        assertEquals(UiText(UiMessage.FAILURE_PAIRING_DETAIL), screen.statusDetail)
        assertEquals(FailureAction.OPEN_BLUETOOTH_SETTINGS, screen.failureAction)
        assertEquals(UiText(UiMessage.FAILURE_PAIRING_ACTION), screen.failureActionLabel)
    }

    @Test
    fun `permission failure gives permission action`() {
        val screen = presenter.present(
            ConnectionState.Failed(ConnectionFailure.PERMISSION_DENIED),
            selectedAdapterName = "vLinker",
        )

        assertEquals(UiText(UiMessage.FAILURE_PERMISSION_TITLE), screen.statusTitle)
        assertEquals(FailureAction.REQUEST_PERMISSION, screen.failureAction)
        assertEquals(UiText(UiMessage.FAILURE_PERMISSION_ACTION), screen.failureActionLabel)
    }

    @Test
    fun `timeout identity mismatch and remote close remain distinct and actionable`() {
        val cases = listOf(
            FailureExpectation(
                ConnectionFailure.TIMEOUT,
                UiMessage.FAILURE_TIMEOUT_TITLE,
                UiMessage.FAILURE_TIMEOUT_DETAIL,
                FailureAction.RETRY_CONNECTION,
                UiMessage.FAILURE_TIMEOUT_ACTION,
            ),
            FailureExpectation(
                ConnectionFailure.IDENTITY_MISMATCH,
                UiMessage.FAILURE_IDENTITY_TITLE,
                UiMessage.FAILURE_IDENTITY_DETAIL,
                FailureAction.SELECT_ADAPTER,
                UiMessage.FAILURE_IDENTITY_ACTION,
            ),
            FailureExpectation(
                ConnectionFailure.REMOTE_CLOSE,
                UiMessage.FAILURE_REMOTE_CLOSE_TITLE,
                UiMessage.FAILURE_REMOTE_CLOSE_DETAIL,
                FailureAction.RETRY_CONNECTION,
                UiMessage.FAILURE_REMOTE_CLOSE_ACTION,
            ),
        )

        cases.forEach { expected ->
            val screen = presenter.present(
                ConnectionState.Failed(expected.failure),
                selectedAdapterName = "vLinker",
            )
            assertEquals(UiText(expected.title), screen.statusTitle)
            assertEquals(UiText(expected.detail), screen.statusDetail)
            assertEquals(expected.action, screen.failureAction)
            assertEquals(UiText(expected.actionLabel), screen.failureActionLabel)
        }
    }

    @Test
    fun `io failure does not claim a more specific cause`() {
        val screen = presenter.present(
            ConnectionState.Failed(ConnectionFailure.IO),
            selectedAdapterName = "vLinker",
        )

        assertEquals(UiText(UiMessage.FAILURE_IO_TITLE), screen.statusTitle)
        assertEquals(FailureAction.RETRY_CONNECTION, screen.failureAction)
    }

    @Test
    fun `service reminder reset remains unavailable without a validated write profile`() {
        val screen = presenter.present(
            ConnectionState.AdapterReady("ELM327", "STN1151", "vlinker-mc-android"),
            selectedAdapterName = "vLinker",
        )

        assertEquals(UiText(UiMessage.SERVICE_CARD_TITLE), screen.serviceCard.title)
        assertEquals(UiText(UiMessage.SERVICE_CARD_UNAVAILABLE_DETAIL), screen.serviceCard.detail)
        assertFalse(screen.serviceCard.enabled)
    }

    private data class FailureExpectation(
        val failure: ConnectionFailure,
        val title: UiMessage,
        val detail: UiMessage,
        val action: FailureAction,
        val actionLabel: UiMessage,
    )
}
