package dev.resetlight.app

import dev.resetlight.adapter.elm.CommandFailure
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.features.dtc.DtcClearFailure
import dev.resetlight.features.dtc.DtcClearUiState
import dev.resetlight.features.service.ServiceReminderResetFailure
import dev.resetlight.features.service.ServiceReminderResetResult
import dev.resetlight.features.service.ServiceResetUiState
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteOutcomePolicyTest {
    private val ecu = EcuProfileLoader().load(
        File("build/generated/profileAssets/profiles/tiger-900-gt-pro-2021.ecumap.yaml").readBytes(),
    )

    @Test
    fun `unconfirmed date is reported as a partial service update`() {
        val state = WriteOutcomePolicy.serviceResult(
            ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_DATE_UNCONFIRMED),
            ),
        )

        state as ServiceResetUiState.NeedsInspection
        assertEquals(UiMessage.SERVICE_RESET_REASON_PARTIALLY_APPLIED, state.reason.key)
    }

    @Test
    fun `an explicit distance rejection remains an ordinary blocked result`() {
        val state = WriteOutcomePolicy.serviceResult(
            ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_DISTANCE_REJECTED),
            ),
        )

        assertTrue(state is ServiceResetUiState.Blocked)
    }

    @Test
    fun `lost service write response requires inspection before retry`() {
        val request = ecu.serviceReminder.distanceRequestPrefixKm + "64"
        val failure = ServiceReminderResetFailure(
            request,
            IOException("write failed before a response was available"),
        )

        val state = WriteOutcomePolicy.serviceFailure(failure, ecu.serviceReminder)

        state as ServiceResetUiState.NeedsInspection
        assertEquals(UiMessage.SERVICE_RESET_REASON_WRITE_AMBIGUOUS, state.reason.key)
    }

    @Test
    fun `pre-write service failure is not mislabeled as an ambiguous write`() {
        val failure = ServiceReminderResetFailure(
            ecu.instrumentReadOnlyCapture.initializeElmRequest,
            CommandFailure.Timeout(
                ecu.instrumentReadOnlyCapture.initializeElmRequest,
                IllegalStateException("timeout"),
            ),
        )

        assertNull(WriteOutcomePolicy.serviceFailure(failure, ecu.serviceReminder))
    }

    @Test
    fun `lost clear response says outcome is unknown`() {
        val request = ecu.diagnosticTroubleCodes.clear.elmRequest
        val failure = DtcClearFailure(
            request,
            IOException("write failed before a response was available"),
        )

        val state = WriteOutcomePolicy.dtcFailure(failure, ecu.diagnosticTroubleCodes.clear)

        state as DtcClearUiState.NeedsVerification
        assertEquals(UiMessage.DTC_CLEAR_REASON_WRITE_AMBIGUOUS, state.reason.key)
    }

    @Test
    fun `lost verification after positive clear says reread is needed`() {
        val request = ecu.diagnosticTroubleCodes.clear.verificationElmRequest
        val failure = DtcClearFailure(
            request,
            CommandFailure.Timeout(request, IllegalStateException("timeout")),
        )

        val state = WriteOutcomePolicy.dtcFailure(failure, ecu.diagnosticTroubleCodes.clear)

        state as DtcClearUiState.NeedsVerification
        assertEquals(UiMessage.DTC_CLEAR_REASON_VERIFICATION_LOST, state.reason.key)
    }
}
