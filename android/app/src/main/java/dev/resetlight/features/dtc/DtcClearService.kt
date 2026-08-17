package dev.resetlight.features.dtc

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DiagnosticParseException
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.DtcResponseDecoder
import dev.resetlight.diagnostics.EngineSeedKeyDerivation
import dev.resetlight.diagnostics.elmConfigurationAccepted
import dev.resetlight.diagnostics.hexOnly
import dev.resetlight.diagnostics.UdsResponse
import dev.resetlight.diagnostics.UdsResponseParser
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.profiles.DiagnosticTroubleCodeClearProfile
import dev.resetlight.profiles.DtcDescriptionLookup
import dev.resetlight.profiles.EngineSecurityAccessProfile
import kotlinx.coroutines.CancellationException

sealed interface DtcClearResult {
    data class Cleared(val remainingCount: Int) : DtcClearResult
    data class Blocked(val reason: UiText) : DtcClearResult
}

class DtcClearFailure(
    val request: String,
    cause: Throwable,
) : Exception("DTC clear failed while sending $request", cause)

/**
 * Clears confirmed DTCs on the engine ECU. The only observed clear ran inside
 * the extended session after SecurityAccess, so this replays exactly that
 * sequence: `1003` → `2701` seed → derived `2702` key → `14FFFFFF` → verify a
 * zero count. Callers apply their build-specific eligibility gate and explicit
 * confirmation before instantiating this service. A refused session or key
 * returns [DtcClearResult.Blocked] without sending the clear.
 *
 * The maximum number of `response-pending` (`0x78`) negatives tolerated while
 * awaiting the final clear response is bounded so a stuck ECU cannot spin.
 */
class DtcClearService(
    private val clearProfile: DiagnosticTroubleCodeClearProfile,
    private val securityProfile: EngineSecurityAccessProfile,
    private val derivation: EngineSeedKeyDerivation,
    private val channel: DiagnosticWriteChannel,
    private val configurationCommands: List<String> = emptyList(),
    private val extractor: CanResponseExtractor? = null,
) {
    suspend fun clear(): DtcClearResult {
        // Re-apply the engine route first: the adapter keeps whatever route the
        // previous operation configured (e.g. the instrument's 11-bit route).
        configurationCommands.forEach { command ->
            val response = execute(command, WriteIntent.READ)
            if (!elmConfigurationAccepted(command, response)) {
                return DtcClearResult.Blocked(
                    UiText(UiMessage.CAPTURE_REASON_ENGINE_TRANSPORT_REJECTED, command),
                )
            }
        }

        val sessionResponse = execute(securityProfile.extendedSessionElmRequest, WriteIntent.READ)
        if (!sessionResponse.startsWithHex(securityProfile.extendedSessionPositivePrefix)) {
            return DtcClearResult.Blocked(UiText(UiMessage.DTC_CLEAR_REASON_SESSION_REFUSED))
        }

        val seedResponse = execute(securityProfile.seedRequestElmRequest, WriteIntent.READ)
        val keyRequest = try {
            derivation.keyRequestFor(payload(seedResponse).hexOnly(), securityProfile.keyRequestElmPrefix)
        } catch (parse: DiagnosticParseException) {
            return DtcClearResult.Blocked(UiText(UiMessage.DTC_CLEAR_REASON_NO_SEED))
        }
        val keyResponse = execute(keyRequest, WriteIntent.WRITE)
        if (!keyResponse.isPositive(SECURITY_ACCESS_POSITIVE)) {
            return DtcClearResult.Blocked(UiText(UiMessage.DTC_CLEAR_REASON_SECURITY_REJECTED))
        }

        val clearResponse = awaitFinalClearResponse()
        if (!clearResponse.isPositive(CLEAR_POSITIVE)) {
            return DtcClearResult.Blocked(UiText(UiMessage.DTC_CLEAR_REASON_REJECTED))
        }

        val verification = execute(clearProfile.verificationElmRequest, WriteIntent.READ)
        val remaining = remainingCount(verification)
            ?: return DtcClearResult.Blocked(UiText(UiMessage.DTC_CLEAR_REASON_COUNT_UNCONFIRMED))
        return DtcClearResult.Cleared(remaining)
    }


    private suspend fun awaitFinalClearResponse(): String {
        var response = execute(clearProfile.elmRequest, WriteIntent.WRITE)
        var pendingWaits = 0
        while (isResponsePending(response) && pendingWaits < MAX_PENDING_WAITS) {
            pendingWaits++
            response = execute(clearProfile.elmRequest, WriteIntent.WRITE)
        }
        return response
    }

    /**
     * The payload of a live framed response, or the input unchanged when no
     * extractor is wired (bare map/test payloads).
     */
    private fun payload(response: String): String =
        extractor?.extract(response) ?: response

    private fun remainingCount(verification: String): Int? =
        try {
            DtcResponseDecoder(EMPTY_DESCRIPTIONS).decodeCount(payload(verification).hexOnly()).matchingCount
        } catch (parse: DiagnosticParseException) {
            null
        }

    private fun isResponsePending(response: String): Boolean = try {
        val parsed = UdsResponseParser.parse(payload(response).hexOnly())
        parsed is UdsResponse.Negative && parsed.pending
    } catch (parse: DiagnosticParseException) {
        false
    }

    private fun String.isPositive(service: Int): Boolean = try {
        val parsed = UdsResponseParser.parse(payload(this).hexOnly())
        parsed is UdsResponse.Positive && parsed.service == service
    } catch (parse: DiagnosticParseException) {
        false
    }

    private fun String.startsWithHex(prefix: String): Boolean = try {
        payload(this).hexOnly().startsWith(prefix.uppercase())
    } catch (parse: DiagnosticParseException) {
        false
    }

    private suspend fun execute(request: String, intent: WriteIntent): String = try {
        channel.execute(request, intent)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        throw DtcClearFailure(request, failure)
    }

    private companion object {
        const val SECURITY_ACCESS_POSITIVE = 0x67
        const val CLEAR_POSITIVE = 0x54
        const val MAX_PENDING_WAITS = 10

        val EMPTY_DESCRIPTIONS = DtcDescriptionLookup { code ->
            dev.resetlight.profiles.DtcMessage(code, dev.resetlight.profiles.DtcMessageStatus.UNKNOWN)
        }
    }
}
