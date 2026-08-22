package dev.resetlight.features.service

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DiagnosticParseException
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.InstrumentResponseDecoder
import dev.resetlight.diagnostics.ServiceReminderCommandBuilder
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.diagnostics.elmConfigurationAccepted
import dev.resetlight.diagnostics.hexOnly
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.profiles.InstrumentReadOnlyCaptureProfile
import dev.resetlight.profiles.ServiceReminderOperationProfile
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

sealed interface ServiceReminderResetResult {
    data class Committed(
        val odometerKm: Int,
        val distance: Int,
        val distanceUnit: DistanceUnit,
        val nextServiceDate: LocalDate,
    ) : ServiceReminderResetResult

    data class Blocked(val reason: UiText) : ServiceReminderResetResult
}

class ServiceReminderResetFailure(
    val request: String,
    cause: Throwable,
) : Exception("Service reminder reset failed while sending $request", cause)

/**
 * Replays the observed service-reminder reset against the instrument cluster:
 * configure the 11-bit route, read status (`5E01`) and odometer (`0D01`), then
 * send the unit-specific distance (`33xx` km or `34xx` miles) and date (`5Cxx…`)
 * writes. Each write's positive
 * response is `request-service | 0x80`, and the date commit echoes its payload,
 * so success is confirmed by matching that echo to the request we sent — the
 * reset's own readback.
 *
 * Instantiated only after a [ServiceWriteGate] authorizes the connected cluster
 * and the user explicitly confirms the current and requested values. A rejected or mismatched write returns
 * [ServiceReminderResetResult.Blocked] without proceeding.
 */
class ServiceReminderResetService(
    private val instrumentProfile: InstrumentReadOnlyCaptureProfile,
    private val serviceProfile: ServiceReminderOperationProfile,
    private val gate: ServiceWriteGate,
    private val motorcycleId: String,
    private val extractor: CanResponseExtractor? = null,
) {
    private val decoder = InstrumentResponseDecoder()
    private val commandBuilder = ServiceReminderCommandBuilder(serviceProfile)

    suspend fun reset(
        channel: DiagnosticWriteChannel,
        distanceKm: Int,
        nextServiceDate: LocalDate,
    ): ServiceReminderResetResult = reset(
        channel,
        distanceKm,
        DistanceUnit.KILOMETERS,
        nextServiceDate,
    )

    suspend fun reset(
        channel: DiagnosticWriteChannel,
        distance: Int,
        distanceUnit: DistanceUnit,
        nextServiceDate: LocalDate,
    ): ServiceReminderResetResult {
        // Build (and validate) the write commands before any traffic. A value the
        // observed one-byte encodings cannot represent blocks the reset with a
        // clear message instead of throwing — the 2026-08-12 trip lost the whole
        // adapter session to exactly this.
        val commands = try {
            commandBuilder.build(distance, distanceUnit, nextServiceDate)
        } catch (invalid: IllegalArgumentException) {
            return ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_INVALID_INPUT),
            )
        }

        instrumentProfile.configurationCommands.forEach { command ->
            val response = execute(channel, command, WriteIntent.READ)
            if (!elmConfigurationAccepted(command, response)) {
                return ServiceReminderResetResult.Blocked(
                    UiText(UiMessage.INSTRUMENT_REASON_TRANSPORT_REJECTED, command),
                )
            }
        }

        val statusResponse = execute(channel, instrumentProfile.initializeElmRequest, WriteIntent.READ)
        val odometerResponse = execute(channel, instrumentProfile.odometerElmRequest, WriteIntent.READ)
        val (status, odometer) = try {
            decoder.decodeInitialize(payload(statusResponse)) to decoder.decodeOdometer(payload(odometerResponse))
        } catch (parse: DiagnosticParseException) {
            return ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_UNRECOGNIZED_STATUS),
            )
        }

        // Fail closed: only write once the live cluster matches the validated
        // fingerprint. No write byte has been sent up to this point.
        val decision = gate.evaluate(motorcycleId, status.statusAscii)
        if (!decision.authorized) {
            return ServiceReminderResetResult.Blocked(decision.reason)
        }

        val distanceResponse = execute(channel, commands.distanceRequest, WriteIntent.WRITE)
        if (!isEchoPositive(commands.distanceRequest, distanceResponse)) {
            return ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_DISTANCE_REJECTED),
            )
        }

        val dateResponse = execute(channel, commands.dateRequest, WriteIntent.WRITE)
        if (!isEchoPositive(commands.dateRequest, dateResponse)) {
            return ServiceReminderResetResult.Blocked(
                UiText(UiMessage.SERVICE_RESET_REASON_DATE_UNCONFIRMED),
            )
        }

        return ServiceReminderResetResult.Committed(
            odometerKm = odometer.odometerKm,
            distance = distance,
            distanceUnit = distanceUnit,
            nextServiceDate = nextServiceDate,
        )
    }

    /**
     * A positive instrument response echoes the request payload with the service
     * byte raised by 0x80 (e.g. `5C…` → `DC…`, `33…` → `B3…`). The distance
     * response is zero-padded to eight bytes, so the response payload must *start
     * with* the request payload rather than equal it exactly.
     */
    private fun isEchoPositive(request: String, response: String): Boolean {
        val requestHex = request.hexOnly()
        val responseHex = try {
            payload(response).hexOnly()
        } catch (parse: DiagnosticParseException) {
            return false
        }
        if (requestHex.length < 2 || responseHex.length < requestHex.length) return false
        val requestService = requestHex.substring(0, 2).toInt(16)
        val responseService = responseHex.substring(0, 2).toInt(16)
        if (responseService != (requestService or 0x80)) return false
        return responseHex.substring(2).startsWith(requestHex.substring(2))
    }

    private fun payload(response: String): String =
        extractor?.extract(response) ?: response

    private suspend fun execute(
        channel: DiagnosticWriteChannel,
        request: String,
        intent: WriteIntent,
    ): String = try {
        channel.execute(request, intent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        throw ServiceReminderResetFailure(request, failure)
    }
}
