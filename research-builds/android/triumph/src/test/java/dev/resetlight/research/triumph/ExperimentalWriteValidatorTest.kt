package dev.resetlight.research.triumph

import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.profiles.EcuProfile
import dev.resetlight.features.service.ServiceReminderResetFailure
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ExperimentalWriteValidatorTest {
    private val ecu = TestProfiles.ecu()

    @Test
    fun `matching reads validate service reset then dtc clear in the same session`() = runTest {
        val channel = SuccessfulWriteChannel(ecu)
        val events = RecordingWriteEvents()
        val validator = ExperimentalWriteValidator(ecu, channel, ResearchWriteCommandPolicy(ecu), events)

        val result = validator.validate(
            readSummary = matchingSummary(),
            options = ResearchWriteOptions(
                clearDtcs = true,
                serviceReset = roundTrip(),
            ),
        )

        assertEquals(ResearchWriteOutcome.VALIDATED, result.serviceReset.outcome)
        assertEquals(ResearchRestoreOutcome.RESTORED, result.serviceReset.restoreOutcome)
        assertEquals(ResearchWriteOutcome.VALIDATED, result.dtcClear.outcome)
        assertTrue(channel.commands.indexOfFirst { it.first == "334F" } < channel.commands.indexOfFirst {
            it.first == ecu.diagnosticTroubleCodes.clear.elmRequest
        })
        assertTrue(channel.commands.indexOfFirst { it.first == "334E" } < channel.commands.indexOfFirst {
            it.first == ecu.diagnosticTroubleCodes.clear.elmRequest
        })
        assertTrue(channel.commands.indexOfFirst { it.first.startsWith("5C1B0812") } <
            channel.commands.indexOfFirst { it.first.startsWith("5C1B0811") })
        assertTrue(events.events.any { it.name == "service_reset_validation_finished" })
        assertTrue(events.events.any { it.name == "service_reset_restore_finished" })
        assertTrue(events.events.any { it.name == "dtc_clear_validation_finished" })
        assertEquals(1, channel.commands.count { it.first == ecu.diagnosticTroubleCodes.clear.elmRequest })
    }

    @Test
    fun `miles round trip uses captured service 34 for test and restoration`() = runTest {
        val channel = SuccessfulWriteChannel(ecu)
        val events = RecordingWriteEvents()
        val validator = ExperimentalWriteValidator(
            ecu,
            channel,
            ResearchWriteCommandPolicy(ecu),
            events,
        )

        val result = validator.validate(
            readSummary = matchingSummary(),
            options = ResearchWriteOptions(serviceReset = roundTrip(DistanceUnit.MILES)),
        )

        assertEquals(ResearchWriteOutcome.VALIDATED, result.serviceReset.outcome)
        assertEquals(ResearchRestoreOutcome.RESTORED, result.serviceReset.restoreOutcome)
        assertTrue(channel.commands.any { it.first == "344F" })
        assertTrue(channel.commands.any { it.first == "344E" })
        assertTrue(channel.commands.none { it.first.startsWith("33") })
        assertTrue(
            events.events.any {
                it.name == "service_reset_validation_started" &&
                    it.text?.contains("distance_unit=miles") == true &&
                    it.text.contains("previous_distance=7800") &&
                    it.text.contains("test_distance=7900")
            },
        )
    }

    @Test
    fun `nonmatching reads make requested writes ineligible without sending traffic`() = runTest {
        val channel = SuccessfulWriteChannel(ecu)
        val validator = ExperimentalWriteValidator(
            ecu,
            channel,
            ResearchWriteCommandPolicy(ecu),
            RecordingWriteEvents(),
        )

        val result = validator.validate(
            readSummary = matchingSummary().copy(
                dtcReadConfirmed = false,
                dtcClearCandidate = false,
                serviceReadConfirmed = false,
                serviceResetCandidate = false,
            ),
            options = ResearchWriteOptions(
                clearDtcs = true,
                serviceReset = roundTrip(),
            ),
        )

        assertEquals(ResearchWriteOutcome.INELIGIBLE, result.serviceReset.outcome)
        assertEquals(ResearchWriteOutcome.INELIGIBLE, result.dtcClear.outcome)
        assertTrue(channel.commands.isEmpty())
    }

    @Test
    fun `rejected test values still restore the entered previous values`() = runTest {
        val channel = RejectedTestDateChannel(ecu)
        val validator = ExperimentalWriteValidator(
            ecu,
            channel,
            ResearchWriteCommandPolicy(ecu),
            RecordingWriteEvents(),
        )

        val result = validator.validate(
            readSummary = matchingSummary(),
            options = ResearchWriteOptions(serviceReset = roundTrip()),
        )

        assertEquals(ResearchWriteOutcome.REJECTED, result.serviceReset.outcome)
        assertEquals(ResearchRestoreOutcome.RESTORED, result.serviceReset.restoreOutcome)
        assertTrue(channel.commands.any { it.first == "334E" })
        assertTrue(channel.commands.any { it.first.startsWith("5C1B0811") })
    }

    @Test
    fun `explicit restore rejection is reported and does not skip selected dtc clear`() = runTest {
        val channel = SuccessfulWriteChannel(ecu, rejectRestoreDistance = true)
        val validator = ExperimentalWriteValidator(
            ecu,
            channel,
            ResearchWriteCommandPolicy(ecu),
            RecordingWriteEvents(),
        )

        val result = validator.validate(
            readSummary = matchingSummary(),
            options = ResearchWriteOptions(clearDtcs = true, serviceReset = roundTrip()),
        )

        assertEquals(ResearchWriteOutcome.REJECTED, result.serviceReset.outcome)
        assertEquals(ResearchRestoreOutcome.REJECTED, result.serviceReset.restoreOutcome)
        assertEquals(ResearchWriteOutcome.VALIDATED, result.dtcClear.outcome)
        assertTrue(channel.commands.any { it.first == ecu.diagnosticTroubleCodes.clear.elmRequest })
    }

    @Test
    fun `ambiguous test write does not automatically send a restore write`() = runTest {
        val channel = FailingTestWriteChannel(ecu)
        val events = RecordingWriteEvents()
        val validator = ExperimentalWriteValidator(
            ecu,
            channel,
            ResearchWriteCommandPolicy(ecu),
            events,
        )

        assertFailsWith<ServiceReminderResetFailure> {
            validator.validate(
                readSummary = matchingSummary(),
                options = ResearchWriteOptions(serviceReset = roundTrip()),
            )
        }

        assertTrue(channel.commands.none { it.first == "334E" })
        assertTrue(
            events.events.any {
                it.name == "service_reset_restore_finished" && it.outcome == "unknown"
            },
        )
    }

    private fun roundTrip(unit: DistanceUnit = DistanceUnit.KILOMETERS) = ResearchServiceRoundTripRequest(
        distanceUnit = unit,
        previousDistance = 7_800,
        previousNextServiceDate = LocalDate.of(2027, 8, 17),
        testDistance = 7_900,
        testNextServiceDate = LocalDate.of(2027, 8, 18),
    )

    private fun matchingSummary() = ResearchScanSummary(
        adapterMetadataResponses = 3,
        identifierAttempts = 6,
        identifierResponses = 6,
        dtcReadConfirmed = true,
        dtcCount = 1,
        dtcDetailRecords = 1,
        extendedSessionUsed = false,
        instrumentStatusAscii = "043",
        odometerKm = 44_756,
        serviceReadConfirmed = true,
        dtcClearCandidate = true,
        serviceResetCandidate = true,
    )
}

private class SuccessfulWriteChannel(
    private val ecu: EcuProfile,
    private val rejectRestoreDistance: Boolean = false,
) : DiagnosticWriteChannel {
    val commands = mutableListOf<Pair<String, WriteIntent>>()

    override suspend fun execute(request: String, intent: WriteIntent): String {
        commands += request to intent
        return when {
            request == "ATWS" -> "ELM327 v2.2"
            request.startsWith("AT") -> "OK"
            request == ecu.instrumentReadOnlyCapture.initializeElmRequest -> "704 DE303433FFFFFFFF"
            request == ecu.instrumentReadOnlyCapture.odometerElmRequest -> "704 8D0100AED4000000"
            request == "334E" && rejectRestoreDistance -> "704 B3FFFFFFFFFFFFFF"
            request.startsWith("33") -> "704 B3${request.drop(2)}000000000000"
            request.startsWith("34") -> "704 B4${request.drop(2)}000000000000"
            request.startsWith("5C") -> "704 DC${request.drop(2)}"
            request == ecu.engineSecurityAccess.extendedSessionElmRequest -> "18DAF1D5 02 5003 AAAAAAAAAA"
            request == ecu.engineSecurityAccess.seedRequestElmRequest -> "18DAF1D5 04 6701188B AAAAAA"
            request.startsWith(ecu.engineSecurityAccess.keyRequestElmPrefix) -> "18DAF1D5 02 6702 AAAAAAAAAA"
            request == ecu.diagnosticTroubleCodes.clear.elmRequest -> """
                18DAF1D5 03 7F1478 AAAAAAAA
                18DAF1D5 01 54 AAAAAAAAAAAA
            """.trimIndent()
            request == ecu.diagnosticTroubleCodes.clear.verificationElmRequest ->
                "18DAF1D5 06 59010C000000 AA"
            else -> error("Unexpected write-validation command: $request")
        }
    }
}

private class RecordingWriteEvents : ResearchEventRecorder {
    val events = mutableListOf<ResearchEvent>()
    override fun record(event: ResearchEvent) {
        events += event
    }
}

private class RejectedTestDateChannel(private val ecu: EcuProfile) : DiagnosticWriteChannel {
    val commands = mutableListOf<Pair<String, WriteIntent>>()

    override suspend fun execute(request: String, intent: WriteIntent): String {
        commands += request to intent
        return when {
            request == "ATWS" -> "ELM327 v2.2"
            request.startsWith("AT") -> "OK"
            request == ecu.instrumentReadOnlyCapture.initializeElmRequest -> "704 DE303433FFFFFFFF"
            request == ecu.instrumentReadOnlyCapture.odometerElmRequest -> "704 8D0100AED4000000"
            request == "334F" -> "704 B34F000000000000"
            request.startsWith("5C1B0812") -> "704 DCFFFFFFFFFFFFFF"
            request == "334E" -> "704 B34E000000000000"
            request.startsWith("5C1B0811") -> "704 DC${request.drop(2)}"
            else -> error("Unexpected restore-validation command: $request")
        }
    }
}

private class FailingTestWriteChannel(private val ecu: EcuProfile) : DiagnosticWriteChannel {
    val commands = mutableListOf<Pair<String, WriteIntent>>()

    override suspend fun execute(request: String, intent: WriteIntent): String {
        commands += request to intent
        return when {
            request == "ATWS" -> "ELM327 v2.2"
            request.startsWith("AT") -> "OK"
            request == ecu.instrumentReadOnlyCapture.initializeElmRequest -> "704 DE303433FFFFFFFF"
            request == ecu.instrumentReadOnlyCapture.odometerElmRequest -> "704 8D0100AED4000000"
            request == "334F" -> throw IOException("response lost")
            else -> error("Unexpected ambiguous-write command: $request")
        }
    }
}
