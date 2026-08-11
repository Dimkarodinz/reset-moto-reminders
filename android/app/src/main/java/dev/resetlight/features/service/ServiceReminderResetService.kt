package dev.resetlight.features.service

import dev.resetlight.diagnostics.DiagnosticParseException
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.InstrumentResponseDecoder
import dev.resetlight.diagnostics.ServiceReminderCommandBuilder
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.diagnostics.hexOnly
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.profiles.InstrumentReadOnlyCaptureProfile
import dev.resetlight.profiles.ServiceReminderOperationProfile
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

sealed interface ServiceReminderResetResult {
    data class Committed(
        val odometerKm: Int,
        val distanceKm: Int,
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
 * send the distance (`33xx`) and date (`5Cxx…`) writes. Each write's positive
 * response is `request-service | 0x80`, and the date commit echoes its payload,
 * so success is confirmed by matching that echo to the request we sent — the
 * reset's own readback.
 *
 * Instantiated only from the research build after [ClusterFingerprintGate]
 * authorizes the connected cluster and the user explicitly confirms the current
 * and requested values. A rejected or mismatched write returns
 * [ServiceReminderResetResult.Blocked] without proceeding.
 */
class ServiceReminderResetService(
    private val instrumentProfile: InstrumentReadOnlyCaptureProfile,
    private val serviceProfile: ServiceReminderOperationProfile,
    private val gate: ClusterFingerprintGate,
    private val motorcycleId: String,
) {
    private val decoder = InstrumentResponseDecoder()
    private val commandBuilder = ServiceReminderCommandBuilder(serviceProfile)

    suspend fun reset(
        channel: DiagnosticWriteChannel,
        distanceKm: Int,
        nextServiceDate: LocalDate,
    ): ServiceReminderResetResult {
        val commands = commandBuilder.build(distanceKm, nextServiceDate)

        instrumentProfile.configurationCommands.forEach { command ->
            val response = execute(channel, command, WriteIntent.READ)
            if (!configurationAccepted(command, response)) {
                return ServiceReminderResetResult.Blocked(
                    UiText(UiMessage.INSTRUMENT_REASON_TRANSPORT_REJECTED, command),
                )
            }
        }

        val statusResponse = execute(channel, instrumentProfile.initializeElmRequest, WriteIntent.READ)
        val odometerResponse = execute(channel, instrumentProfile.odometerElmRequest, WriteIntent.READ)
        val (status, odometer) = try {
            decoder.decodeInitialize(statusResponse) to decoder.decodeOdometer(odometerResponse)
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
            distanceKm = distanceKm,
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
        val responseHex = response.hexOnly()
        if (requestHex.length < 2 || responseHex.length < requestHex.length) return false
        val requestService = requestHex.substring(0, 2).toInt(16)
        val responseService = responseHex.substring(0, 2).toInt(16)
        if (responseService != (requestService or 0x80)) return false
        return responseHex.substring(2).startsWith(requestHex.substring(2))
    }

    private fun configurationAccepted(command: String, response: String): Boolean {
        val normalized = response.uppercase()
        return if (command == "ATWS") {
            normalized.contains("ELM327")
        } else {
            normalized.lines().any { it.trim() == "OK" }
        }
    }

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
