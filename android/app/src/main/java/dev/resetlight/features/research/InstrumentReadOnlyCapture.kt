package dev.resetlight.features.research

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DiagnosticParseException
import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.diagnostics.InstrumentResponseDecoder
import dev.resetlight.diagnostics.elmConfigurationAccepted
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.profiles.InstrumentReadOnlyCaptureProfile
import kotlinx.coroutines.CancellationException

sealed interface InstrumentReadState {
    data object Idle : InstrumentReadState
    data object Running : InstrumentReadState
    data class Complete(
        val statusAscii: String,
        val odometerKm: Int,
        val odometerRaw: String,
    ) : InstrumentReadState
    data class Blocked(val reason: UiText) : InstrumentReadState
    data class Failed(val reason: UiText) : InstrumentReadState
}

sealed class InstrumentReadOnlyCaptureResult {
    data class Complete(
        val statusAscii: String,
        val odometerKm: Int,
        val odometerRaw: String,
        val responses: List<ResearchCaptureResponse>,
    ) : InstrumentReadOnlyCaptureResult()

    data class Blocked(
        val reason: UiText,
        val responses: List<ResearchCaptureResponse>,
    ) : InstrumentReadOnlyCaptureResult()
}

class InstrumentReadOnlyCaptureFailure(
    val request: String,
    cause: Throwable,
) : Exception("Instrument read-only capture failed while sending $request", cause)

/**
 * Sends only the two observed instrument-cluster reads (`5E01`, `0D01`) after
 * configuring the cluster's 11-bit route. This is first contact with the
 * instrument module; `0D01` semantics are still unconfirmed, so this capture is
 * research-build only and never issues a `33`/`5C` write.
 */
class InstrumentReadOnlyCapture(
    private val profile: InstrumentReadOnlyCaptureProfile,
    private val channel: DiagnosticReadChannel,
    private val extractor: CanResponseExtractor? = null,
) {
    private val decoder = InstrumentResponseDecoder()

    suspend fun capture(): InstrumentReadOnlyCaptureResult {
        val responses = mutableListOf<ResearchCaptureResponse>()

        profile.configurationCommands.forEach { command ->
            val response = execute("configure_instrument_transport", command, responses)
            if (!elmConfigurationAccepted(command, response)) {
                return InstrumentReadOnlyCaptureResult.Blocked(
                    reason = UiText(UiMessage.INSTRUMENT_REASON_TRANSPORT_REJECTED, command),
                    responses = responses,
                )
            }
        }

        val initializeResponse = execute("initialize_or_read_status", profile.initializeElmRequest, responses)
        val odometerResponse = execute("read_odometer", profile.odometerElmRequest, responses)

        return try {
            val status = decoder.decodeInitialize(payload(initializeResponse))
            val odometer = decoder.decodeOdometer(payload(odometerResponse))
            InstrumentReadOnlyCaptureResult.Complete(
                statusAscii = status.statusAscii,
                odometerKm = odometer.odometerKm,
                odometerRaw = odometer.odometerRaw,
                responses = responses,
            )
        } catch (parse: DiagnosticParseException) {
            InstrumentReadOnlyCaptureResult.Blocked(
                reason = UiText(UiMessage.INSTRUMENT_REASON_UNRECOGNIZED_RESPONSE),
                responses = responses,
            )
        }
    }

    private fun payload(response: String): String =
        extractor?.extract(response) ?: response

    private suspend fun execute(
        name: String,
        request: String,
        responses: MutableList<ResearchCaptureResponse>,
    ): String = try {
        channel.execute(request).also { response ->
            responses += ResearchCaptureResponse(name, request, response)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        throw InstrumentReadOnlyCaptureFailure(request, failure)
    }
}
