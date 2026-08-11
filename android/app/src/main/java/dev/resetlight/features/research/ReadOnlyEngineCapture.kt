package dev.resetlight.features.research

import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.profiles.EngineReadOnlyCaptureProfile
import kotlinx.coroutines.CancellationException

sealed interface ReadOnlyCaptureState {
    data object Idle : ReadOnlyCaptureState
    data object Running : ReadOnlyCaptureState
    data class Complete(
        val dtcCount: Int,
        val extendedSessionUsed: Boolean,
        val responseCount: Int,
    ) : ReadOnlyCaptureState
    data class Blocked(val reason: UiText) : ReadOnlyCaptureState
    data class Failed(val reason: UiText) : ReadOnlyCaptureState
}

data class ResearchCaptureResponse(
    val name: String,
    val request: String,
    val response: String,
)

sealed class ReadOnlyEngineCaptureResult {
    abstract val responses: List<ResearchCaptureResponse>
    abstract val extendedSessionUsed: Boolean
    abstract val dtcCount: Int?
    abstract val dtcDetailResponse: String?

    data class Complete(
        override val responses: List<ResearchCaptureResponse>,
        override val extendedSessionUsed: Boolean,
        override val dtcCount: Int,
        override val dtcDetailResponse: String?,
    ) : ReadOnlyEngineCaptureResult()

    data class Blocked(
        val reason: UiText,
        override val responses: List<ResearchCaptureResponse>,
        override val extendedSessionUsed: Boolean,
    ) : ReadOnlyEngineCaptureResult() {
        override val dtcCount: Int? = null
        override val dtcDetailResponse: String? = null
    }
}

class ReadOnlyEngineCaptureFailure(
    val request: String,
    cause: Throwable,
) : Exception("Read-only ECU capture failed while sending $request", cause)

class ReadOnlyEngineCapture(
    private val profile: EngineReadOnlyCaptureProfile,
    private val channel: DiagnosticReadChannel,
) {
    suspend fun capture(): ReadOnlyEngineCaptureResult {
        val responses = mutableListOf<ResearchCaptureResponse>()

        profile.configurationCommands.forEach { command ->
            val response = execute("configure_engine_transport", command, responses)
            if (!configurationAccepted(command, response)) {
                return ReadOnlyEngineCaptureResult.Blocked(
                    reason = UiText(UiMessage.CAPTURE_REASON_ENGINE_TRANSPORT_REJECTED, command),
                    responses = responses,
                    extendedSessionUsed = false,
                )
            }
        }

        profile.identifierReads.forEach { identifier ->
            execute(identifier.name, identifier.elmRequest, responses)
        }

        val defaultCountResponse = execute(
            "count_confirmed_dtcs_default_session",
            profile.dtcCountElmRequest,
            responses,
        )
        parseDtcCount(defaultCountResponse)?.let { count ->
            return complete(count, extendedSessionUsed = false, responses)
        }

        val sessionResponse = execute(
            "enter_observed_extended_session",
            profile.extendedSessionElmRequest,
            responses,
        )
        if (!normalizedHex(sessionResponse).contains("5003")) {
            return ReadOnlyEngineCaptureResult.Blocked(
                reason = UiText(UiMessage.CAPTURE_REASON_EXTENDED_SESSION_REJECTED),
                responses = responses,
                extendedSessionUsed = true,
            )
        }

        val extendedCountResponse = execute(
            "count_confirmed_dtcs_extended_session",
            profile.dtcCountElmRequest,
            responses,
        )
        val extendedCount = parseDtcCount(extendedCountResponse)
            ?: return ReadOnlyEngineCaptureResult.Blocked(
                reason = UiText(UiMessage.CAPTURE_REASON_DTC_COUNT_UNAVAILABLE),
                responses = responses,
                extendedSessionUsed = true,
            )
        return complete(extendedCount, extendedSessionUsed = true, responses)
    }

    private suspend fun complete(
        count: Int,
        extendedSessionUsed: Boolean,
        responses: MutableList<ResearchCaptureResponse>,
    ): ReadOnlyEngineCaptureResult.Complete {
        val detail = if (count > 0) {
            execute("read_confirmed_dtcs", profile.dtcDetailElmRequest, responses)
        } else {
            null
        }
        return ReadOnlyEngineCaptureResult.Complete(
            responses = responses,
            extendedSessionUsed = extendedSessionUsed,
            dtcCount = count,
            dtcDetailResponse = detail,
        )
    }

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
        throw ReadOnlyEngineCaptureFailure(request, failure)
    }

    private fun configurationAccepted(command: String, response: String): Boolean {
        val normalized = response.uppercase()
        return if (command == "ATWS") {
            normalized.contains("ELM327")
        } else {
            normalized.lines().any { it.trim() == "OK" }
        }
    }

    private fun parseDtcCount(response: String): Int? {
        val hex = normalizedHex(response)
        val marker = hex.indexOf("5901")
        if (marker < 0 || hex.length < marker + 12) return null
        return hex.substring(marker + 8, marker + 12).toIntOrNull(16)
    }

    private fun normalizedHex(value: String): String = buildString(value.length) {
        value.forEach { character ->
            if (character in '0'..'9' || character.uppercaseChar() in 'A'..'F') {
                append(character.uppercaseChar())
            }
        }
    }
}
