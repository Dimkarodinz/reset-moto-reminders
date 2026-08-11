package dev.resetlight.diagnostics

import dev.resetlight.profiles.DtcDescriptionLookup
import dev.resetlight.profiles.DtcMessage
import java.util.Locale

data class DtcCountResult(
    val statusAvailabilityMask: Int,
    val formatIdentifier: Int,
    val matchingCount: Int,
)

data class DecodedDtc(
    val rawCode: String,
    val displayCode: String,
    val statusByte: Int,
    val confirmed: Boolean,
    val message: DtcMessage,
)

class DtcResponseDecoder(private val descriptions: DtcDescriptionLookup) {
    fun decodeCount(payloadHex: String): DtcCountResult {
        val bytes = payloadHex.diagnosticHexBytes()
        if (bytes.size != 6 || bytes[0].u() != 0x59 || bytes[1].u() != 0x01) {
            throw DiagnosticParseException("Expected a six-byte UDS report-number-of-DTC response")
        }
        return DtcCountResult(
            statusAvailabilityMask = bytes[2].u(),
            formatIdentifier = bytes[3].u(),
            matchingCount = bytes[4].u() shl 8 or bytes[5].u(),
        )
    }

    fun decodeDetails(payloadHex: String): List<DecodedDtc> {
        val bytes = payloadHex.diagnosticHexBytes()
        if (bytes.size < 3 || bytes[0].u() != 0x59 || bytes[1].u() != 0x02) {
            throw DiagnosticParseException("Expected a UDS report-DTC-by-status-mask response")
        }
        val recordBytes = bytes.size - 3
        if (recordBytes % 4 != 0) {
            throw DiagnosticParseException("DTC response contains a truncated four-byte record")
        }
        val count = recordBytes / 4
        if (count > MAX_DTC_RECORDS) {
            throw DiagnosticParseException("DTC response exceeds the $MAX_DTC_RECORDS-record limit")
        }
        return (0 until count).map { index ->
            val offset = 3 + index * 4
            val rawValue = bytes[offset].u() shl 16 or
                (bytes[offset + 1].u() shl 8) or bytes[offset + 2].u()
            val rawCode = String.format(Locale.ROOT, "0x%06X", rawValue)
            val displayCode = displayCode(rawValue)
            val status = bytes[offset + 3].u()
            DecodedDtc(
                rawCode = rawCode,
                displayCode = displayCode,
                statusByte = status,
                confirmed = status and CONFIRMED_DTC_STATUS != 0,
                message = descriptions.descriptionFor(displayCode),
            )
        }
    }

    private fun displayCode(value: Int): String {
        val first = value shr 16
        val second = value shr 8 and 0xFF
        val third = value and 0xFF
        return String.format(
            Locale.ROOT,
            "%c%X%X%X%X-%02X",
            "PCBU"[first shr 6 and 0x03],
            first shr 4 and 0x03,
            first and 0x0F,
            second shr 4,
            second and 0x0F,
            third,
        )
    }

    private companion object {
        const val CONFIRMED_DTC_STATUS = 0x08
        const val MAX_DTC_RECORDS = 64
    }
}
