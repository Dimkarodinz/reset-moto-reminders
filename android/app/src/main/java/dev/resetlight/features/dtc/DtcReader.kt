package dev.resetlight.features.dtc

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DecodedDtc
import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.diagnostics.DtcResponseDecoder
import dev.resetlight.diagnostics.elmConfigurationAccepted
import dev.resetlight.profiles.DiagnosticTroubleCodeReadProfile
import dev.resetlight.profiles.DtcDescriptionLookup
import kotlinx.coroutines.CancellationException

data class DtcReadResult(
    val reportedCount: Int,
    val dtcs: List<DecodedDtc>,
)

sealed class DtcReadFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class CountMismatch(
        val reportedCount: Int,
        val decodedCount: Int,
    ) : DtcReadFailure(
        "DTC count response reported $reportedCount but the detail response contained $decodedCount",
    )

    class Transport(
        val request: String,
        cause: Throwable,
    ) : DtcReadFailure("DTC read failed while sending $request", cause)

    class ConfigurationRejected(
        val command: String,
    ) : DtcReadFailure("Adapter rejected engine transport command $command")
}

class DtcReader(
    private val profile: DiagnosticTroubleCodeReadProfile,
    descriptions: DtcDescriptionLookup,
    private val channel: DiagnosticReadChannel,
    private val configurationCommands: List<String> = emptyList(),
    private val extractor: CanResponseExtractor? = null,
) {
    private val decoder = DtcResponseDecoder(descriptions)

    suspend fun read(): DtcReadResult {
        // The adapter keeps whatever route the previous operation configured
        // (e.g. the instrument's 11-bit route), so the engine route must be
        // re-applied before every read — the 2026-08-12 trip showed a DTC read
        // after an instrument read getting NO DATA on the stale route.
        configurationCommands.forEach { command ->
            val response = executeOnce(command)
            if (!elmConfigurationAccepted(command, response)) {
                throw DtcReadFailure.ConfigurationRejected(command)
            }
        }

        val count = decoder.decodeCount(payload(executeOnce(profile.countElmRequest)))
        if (count.matchingCount == 0) return DtcReadResult(0, emptyList())

        val dtcs = decoder.decodeDetails(payload(executeOnce(profile.detailElmRequest)))
        if (dtcs.size != count.matchingCount) {
            throw DtcReadFailure.CountMismatch(count.matchingCount, dtcs.size)
        }
        return DtcReadResult(count.matchingCount, dtcs)
    }

    private fun payload(response: String): String =
        extractor?.extract(response) ?: response

    private suspend fun executeOnce(request: String): String = try {
        channel.execute(request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        throw DtcReadFailure.Transport(request, failure)
    }
}
