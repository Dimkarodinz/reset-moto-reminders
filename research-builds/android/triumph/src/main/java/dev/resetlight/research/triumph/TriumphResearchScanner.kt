package dev.resetlight.research.triumph

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.DtcResponseDecoder
import dev.resetlight.diagnostics.InstrumentResponseDecoder
import dev.resetlight.diagnostics.elmConfigurationAccepted
import dev.resetlight.profiles.DtcDescriptionLookup
import dev.resetlight.profiles.DtcMessage
import dev.resetlight.profiles.DtcMessageStatus
import dev.resetlight.profiles.EcuProfile

class TriumphResearchScanner(
    private val ecuProfile: EcuProfile,
    private val channel: ResearchCommandChannel,
    private val policy: ResearchCommandPolicy,
    private val events: ResearchEventRecorder,
    private val onStage: (ResearchScanStage) -> Unit = {},
) {
    private val engineExtractor = CanResponseExtractor(
        ecuProfile.engineEcu.transport.responseCanId,
        isoTp = true,
    )
    private val instrumentExtractor = CanResponseExtractor(
        ecuProfile.instrumentCluster.transport.responseCanId,
        isoTp = false,
    )
    private val dtcDecoder = DtcResponseDecoder(
        DtcDescriptionLookup { code -> DtcMessage("Unknown DTC $code", DtcMessageStatus.UNKNOWN) },
    )
    private val instrumentDecoder = InstrumentResponseDecoder()

    suspend fun scan(): ResearchScanSummary {
        onStage(ResearchScanStage.ADAPTER_METADATA)
        val adapterResponses = ADAPTER_METADATA.count { command ->
            execute(command, "adapter_${command.lowercase()}").hasUsableResponse()
        }

        onStage(ResearchScanStage.ENGINE_IDENTIFIERS)
        val engineConfigured = configure(
            ecuProfile.engineReadOnlyCapture.configurationCommands,
            "engine_transport",
        )
        var identifierResponses = 0
        if (engineConfigured) {
            ecuProfile.engineReadOnlyCapture.identifierReads.forEach { identifier ->
                val response = execute(identifier.elmRequest, identifier.name)
                if (response.hasUsableResponse()) identifierResponses++
            }
        } else {
            events.record(ResearchEvent("engine_identifiers_skipped", "transport_unavailable"))
        }

        onStage(ResearchScanStage.DIAGNOSTIC_TROUBLE_CODES)
        var dtcCount: Int? = null
        var detailRecords: Int? = null
        var extendedSessionUsed = false
        if (engineConfigured) {
            dtcCount = readDtcCount("dtc_count_default")
            if (dtcCount == null) {
                val sessionResponse = execute(
                    ecuProfile.engineReadOnlyCapture.extendedSessionElmRequest,
                    "extended_session_once",
                )
                if (positiveExtendedSession(sessionResponse)) {
                    extendedSessionUsed = true
                    dtcCount = readDtcCount("dtc_count_extended")
                }
            }
            if (dtcCount != null && dtcCount > 0) {
                val detailResponse = execute(
                    ecuProfile.diagnosticTroubleCodes.read.detailElmRequest,
                    "dtc_details",
                )
                detailRecords = decodeDtcDetails(detailResponse)
            } else if (dtcCount == 0) {
                detailRecords = 0
            }
        } else {
            events.record(ResearchEvent("dtc_scan_skipped", "engine_transport_unavailable"))
        }

        onStage(ResearchScanStage.INSTRUMENT_CLUSTER)
        val instrumentConfigured = configure(
            ecuProfile.instrumentReadOnlyCapture.configurationCommands,
            "instrument_transport",
        )
        var statusAscii: String? = null
        var odometerKm: Int? = null
        if (instrumentConfigured) {
            val statusResponse = execute(
                ecuProfile.instrumentReadOnlyCapture.initializeElmRequest,
                "instrument_status",
            )
            statusAscii = decodeInstrumentStatus(statusResponse)
            val odometerResponse = execute(
                ecuProfile.instrumentReadOnlyCapture.odometerElmRequest,
                "instrument_odometer",
            )
            odometerKm = decodeOdometer(odometerResponse)
        } else {
            events.record(ResearchEvent("instrument_reads_skipped", "transport_unavailable"))
        }

        val serviceReadConfirmed = statusAscii != null && odometerKm != null
        val summary = ResearchScanSummary(
            adapterMetadataResponses = adapterResponses,
            identifierAttempts = if (engineConfigured) ecuProfile.engineReadOnlyCapture.identifierReads.size else 0,
            identifierResponses = identifierResponses,
            dtcReadConfirmed = dtcCount != null,
            dtcCount = dtcCount,
            dtcDetailRecords = detailRecords,
            extendedSessionUsed = extendedSessionUsed,
            instrumentStatusAscii = statusAscii,
            odometerKm = odometerKm,
            serviceReadConfirmed = serviceReadConfirmed,
            dtcClearCandidate = dtcCount != null,
            serviceResetCandidate = serviceReadConfirmed &&
                statusAscii == ecuProfile.instrumentReadOnlyCapture.expectedStatusAscii,
        )
        events.record(
            ResearchEvent(
                name = "scan_finished",
                outcome = "complete",
                text = summary.toJournalSummary(),
            ),
        )
        return summary
    }

    private suspend fun configure(commands: List<String>, label: String): Boolean {
        commands.forEach { command ->
            val response = execute(command, label)
            if (!elmConfigurationAccepted(command, response)) {
                events.record(ResearchEvent("${label}_rejected", "rejected", "command=$command"))
                return false
            }
        }
        return true
    }

    private suspend fun readDtcCount(name: String): Int? {
        val response = execute(ecuProfile.diagnosticTroubleCodes.read.countElmRequest, name)
        val count = runCatching {
            dtcDecoder.decodeCount(engineExtractor.extract(response)).matchingCount
        }.getOrNull()
        events.record(
            ResearchEvent(
                name = "${name}_decoded",
                outcome = if (count == null) "unrecognized" else "confirmed",
                text = count?.let { "count=$it" },
            ),
        )
        return count
    }

    private fun decodeDtcDetails(response: String): Int? {
        val count = runCatching {
            dtcDecoder.decodeDetails(engineExtractor.extract(response)).size
        }.getOrNull()
        events.record(
            ResearchEvent(
                name = "dtc_details_decoded",
                outcome = if (count == null) "unrecognized" else "confirmed",
                text = count?.let { "records=$it" },
            ),
        )
        return count
    }

    private fun positiveExtendedSession(response: String): Boolean = runCatching {
        engineExtractor.extract(response).startsWith("5003", ignoreCase = true)
    }.getOrDefault(false)

    private fun decodeInstrumentStatus(response: String): String? {
        val result = runCatching {
            instrumentDecoder.decodeInitialize(instrumentExtractor.extract(response)).statusAscii
        }.getOrNull()
        events.record(
            ResearchEvent(
                "instrument_status_decoded",
                if (result == null) "unrecognized" else "confirmed",
                result?.let { "status=$it" },
            ),
        )
        return result
    }

    private fun decodeOdometer(response: String): Int? {
        val result = runCatching {
            instrumentDecoder.decodeOdometer(instrumentExtractor.extract(response)).odometerKm
        }.getOrNull()
        events.record(
            ResearchEvent(
                "instrument_odometer_decoded",
                if (result == null) "unrecognized" else "confirmed",
                result?.let { "odometer_km=$it" },
            ),
        )
        return result
    }

    private suspend fun execute(command: String, name: String): String {
        policy.requireAllowed(command)
        events.record(ResearchEvent("probe_started", text = "name=$name command=$command"))
        return channel.execute(command).also { response ->
            events.record(
                ResearchEvent(
                    name = "probe_finished",
                    outcome = if (response.hasUsableResponse()) "response" else "no_response",
                    text = "name=$name",
                ),
            )
        }
    }

    private fun String.hasUsableResponse(): Boolean {
        val normalized = uppercase()
        return normalized.isNotBlank() && normalized != "?" && NO_RESPONSE_MARKERS.none(normalized::contains)
    }

    private fun ResearchScanSummary.toJournalSummary(): String =
        "adapter_metadata=$adapterMetadataResponses identifiers=$identifierResponses/$identifierAttempts " +
            "dtc_read=$dtcReadConfirmed dtc_count=${dtcCount ?: "unknown"} " +
            "dtc_clear_candidate=$dtcClearCandidate service_read=$serviceReadConfirmed " +
            "service_reset_candidate=$serviceResetCandidate extended_session=$extendedSessionUsed"

    private companion object {
        val ADAPTER_METADATA = listOf("ATRV", "ATDP", "ATDPN")
        val NO_RESPONSE_MARKERS = listOf(
            "NO DATA",
            "CAN ERROR",
            "BUS INIT",
            "STOPPED",
            "UNABLE TO CONNECT",
            "BUFFER FULL",
            "ERROR",
        )
    }
}
