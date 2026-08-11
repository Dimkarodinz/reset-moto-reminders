package dev.resetlight.features.dtc

import dev.resetlight.diagnostics.DecodedDtc
import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.diagnostics.DtcResponseDecoder
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
}

class DtcReader(
    private val profile: DiagnosticTroubleCodeReadProfile,
    descriptions: DtcDescriptionLookup,
    private val channel: DiagnosticReadChannel,
) {
    private val decoder = DtcResponseDecoder(descriptions)

    suspend fun read(): DtcReadResult {
        val count = decoder.decodeCount(executeOnce(profile.countElmRequest))
        if (count.matchingCount == 0) return DtcReadResult(0, emptyList())

        val dtcs = decoder.decodeDetails(executeOnce(profile.detailElmRequest))
        if (dtcs.size != count.matchingCount) {
            throw DtcReadFailure.CountMismatch(count.matchingCount, dtcs.size)
        }
        return DtcReadResult(count.matchingCount, dtcs)
    }

    private suspend fun executeOnce(request: String): String = try {
        channel.execute(request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        throw DtcReadFailure.Transport(request, failure)
    }
}
