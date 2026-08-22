package dev.resetlight.research.triumph

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.EngineSeedKeyDerivation
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.features.dtc.DtcClearResult
import dev.resetlight.features.dtc.DtcClearService
import dev.resetlight.features.service.GateDecision
import dev.resetlight.features.service.ServiceReminderResetResult
import dev.resetlight.features.service.ServiceReminderResetFailure
import dev.resetlight.features.service.ServiceReminderResetService
import dev.resetlight.features.service.ServiceWriteGate
import dev.resetlight.profiles.EcuProfile

/**
 * Runs only explicitly selected operations, after the read-only scan has saved
 * its evidence. A candidate mismatch blocks the corresponding operation before
 * any write command is sent.
 */
class ExperimentalWriteValidator(
    private val ecu: EcuProfile,
    private val channel: DiagnosticWriteChannel,
    private val policy: ResearchWriteCommandPolicy,
    private val events: ResearchEventRecorder,
    private val onOperation: (ResearchWriteOperation) -> Unit = {},
) {
    private fun guardedChannel(operation: String) = DiagnosticWriteChannel { request, intent ->
        policy.requireAllowed(request, intent)
        events.record(
            ResearchEvent(
                name = "write_validation_command_started",
                text = "operation=$operation intent=${intent.name.lowercase()}",
            ),
        )
        channel.execute(request, intent)
    }

    suspend fun validate(
        readSummary: ResearchScanSummary,
        options: ResearchWriteOptions,
    ): ResearchWriteValidationSummary {
        val service = validateServiceReset(readSummary, options.serviceReset)
        val dtc = validateDtcClear(readSummary, options.clearDtcs)
        return ResearchWriteValidationSummary(serviceReset = service, dtcClear = dtc)
    }

    private suspend fun validateServiceReset(
        readSummary: ResearchScanSummary,
        request: ResearchServiceRoundTripRequest?,
    ): ResearchOperationValidation {
        if (request == null) return ResearchOperationValidation()
        if (!readSummary.serviceResetCandidate || readSummary.instrumentStatusAscii == null) {
            return ResearchOperationValidation(
                ResearchWriteOutcome.INELIGIBLE,
                "Known TFT status and odometer reads did not both match",
            ).also { recordFinished("service_reset", it) }
        }

        onOperation(ResearchWriteOperation.SERVICE_RESET)
        events.record(
            ResearchEvent(
                name = "service_reset_validation_started",
                text = "distance_unit=${request.distanceUnit.name.lowercase()} " +
                    "previous_distance=${request.previousDistance} " +
                    "previous_next_service_date=${request.previousNextServiceDate} " +
                    "test_distance=${request.testDistance} " +
                    "test_next_service_date=${request.testNextServiceDate}",
            ),
        )
        val expectedStatus = readSummary.instrumentStatusAscii
        val gate = ServiceWriteGate { _, actualStatus ->
            if (actualStatus == expectedStatus) {
                GateDecision(true, UiText(UiMessage.GATE_REASON_AUTHORIZED))
            } else {
                GateDecision(false, UiText(UiMessage.GATE_REASON_STATUS_MISMATCH, actualStatus, expectedStatus))
            }
        }
        val testResult = try {
            serviceResetService(gate).reset(
                guardedChannel("service_reset_test"),
                request.testDistance,
                request.distanceUnit,
                request.testNextServiceDate,
            )
        } catch (failure: ServiceReminderResetFailure) {
            events.record(
                ResearchEvent(
                    name = "service_reset_restore_finished",
                    outcome = ResearchRestoreOutcome.UNKNOWN.name.lowercase(),
                    text = "Test write result became ambiguous or transport failed; automatic restore was not attempted",
                ),
            )
            throw failure
        }
        val testValidation = when (testResult) {
            is ServiceReminderResetResult.Committed -> ResearchOperationValidation(
                ResearchWriteOutcome.VALIDATED,
                "Temporary +100 ${request.distanceUnit.name.lowercase()}/+1 day values were accepted",
            )
            is ServiceReminderResetResult.Blocked -> ResearchOperationValidation(
                ResearchWriteOutcome.REJECTED,
                testResult.reason.toString(),
            )
        }
        events.record(
            ResearchEvent(
                name = "service_reset_test_finished",
                outcome = testValidation.outcome.name.lowercase(),
                text = testValidation.detail,
            ),
        )

        events.record(
            ResearchEvent(
                name = "service_reset_restore_started",
                text = "distance_unit=${request.distanceUnit.name.lowercase()} " +
                    "distance=${request.previousDistance} " +
                    "next_service_date=${request.previousNextServiceDate}",
            ),
        )
        val restoreResult = try {
            serviceResetService(gate).reset(
                guardedChannel("service_reset_restore"),
                request.previousDistance,
                request.distanceUnit,
                request.previousNextServiceDate,
            )
        } catch (failure: ServiceReminderResetFailure) {
            events.record(
                ResearchEvent(
                    name = "service_reset_restore_finished",
                    outcome = ResearchRestoreOutcome.UNKNOWN.name.lowercase(),
                    text = "Restore result is ambiguous; inspect the motorcycle before retrying",
                ),
            )
            throw failure
        }
        val restoreOutcome = when (restoreResult) {
            is ServiceReminderResetResult.Committed -> ResearchRestoreOutcome.RESTORED
            is ServiceReminderResetResult.Blocked -> ResearchRestoreOutcome.REJECTED
        }
        val restoreDetail = when (restoreResult) {
            is ServiceReminderResetResult.Committed -> "Entered previous values were restored and echoes confirmed"
            is ServiceReminderResetResult.Blocked -> restoreResult.reason.toString()
        }
        events.record(
            ResearchEvent(
                name = "service_reset_restore_finished",
                outcome = restoreOutcome.name.lowercase(),
                text = restoreDetail,
            ),
        )

        val final = testValidation.copy(
            outcome = if (restoreOutcome == ResearchRestoreOutcome.RESTORED) {
                testValidation.outcome
            } else {
                ResearchWriteOutcome.REJECTED
            },
            detail = "test=${testValidation.detail}; restore=$restoreDetail",
            restoreOutcome = restoreOutcome,
        )
        return final.also { recordFinished("service_reset", it) }
    }

    private fun serviceResetService(gate: ServiceWriteGate) = ServiceReminderResetService(
        instrumentProfile = ecu.instrumentReadOnlyCapture,
        serviceProfile = ecu.serviceReminder,
        gate = gate,
        motorcycleId = "research-candidate",
        extractor = CanResponseExtractor(ecu.instrumentCluster.transport.responseCanId, isoTp = false),
    )

    private suspend fun validateDtcClear(
        readSummary: ResearchScanSummary,
        requested: Boolean,
    ): ResearchOperationValidation {
        if (!requested) return ResearchOperationValidation()
        if (!readSummary.dtcClearCandidate) {
            return ResearchOperationValidation(
                ResearchWriteOutcome.INELIGIBLE,
                "DTC count response was not confirmed",
            ).also { recordFinished("dtc_clear", it) }
        }

        onOperation(ResearchWriteOperation.DTC_CLEAR)
        events.record(ResearchEvent(name = "dtc_clear_validation_started"))
        val result = DtcClearService(
            clearProfile = ecu.diagnosticTroubleCodes.clear,
            securityProfile = ecu.engineSecurityAccess,
            derivation = EngineSeedKeyDerivation(ecu.engineSecurityAccess.seedKeyMultiplier),
            channel = guardedChannel("dtc_clear"),
            configurationCommands = ecu.engineReadOnlyCapture.configurationCommands,
            extractor = CanResponseExtractor(ecu.engineEcu.transport.responseCanId, isoTp = true),
        ).clear()

        return when (result) {
            is DtcClearResult.Cleared -> ResearchOperationValidation(
                if (result.remainingCount == 0) ResearchWriteOutcome.VALIDATED else ResearchWriteOutcome.REJECTED,
                "Verified remaining DTC count: ${result.remainingCount}",
            )
            is DtcClearResult.Blocked -> ResearchOperationValidation(
                ResearchWriteOutcome.REJECTED,
                result.reason.toString(),
            )
        }.also { recordFinished("dtc_clear", it) }
    }

    private fun recordFinished(operation: String, result: ResearchOperationValidation) {
        events.record(
            ResearchEvent(
                name = "${operation}_validation_finished",
                outcome = result.outcome.name.lowercase(),
                text = result.detail,
            ),
        )
    }
}

enum class ResearchWriteOperation {
    SERVICE_RESET,
    DTC_CLEAR,
}
