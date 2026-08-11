package dev.resetlight.diagnostics

import java.util.Locale

data class InstrumentInitializeResult(
    val statusAscii: String,
)

data class InstrumentOdometerResult(
    val odometerRaw: String,
    val odometerKm: Int,
)

/**
 * Decodes the two observed instrument-cluster read responses of the Tiger 900
 * TFT instrument. The cluster uses a proprietary non-UDS protocol whose positive
 * responses are `request | 0x80`; these decoders only interpret the exact byte
 * layouts retained from real captures and never construct a request.
 */
class InstrumentResponseDecoder {
    fun decodeInitialize(payloadHex: String): InstrumentInitializeResult {
        val bytes = payloadHex.diagnosticHexBytes()
        if (bytes.isEmpty() || bytes[0].u() != INITIALIZE_POSITIVE_RESPONSE) {
            throw DiagnosticParseException("Expected the 0x5E positive-response (0xDE) instrument status")
        }
        // Bytes after the service byte are ASCII status digits padded with 0xFF.
        val statusAscii = bytes.drop(1)
            .takeWhile { it.u() != PADDING_BYTE }
            .map { it.u().toChar() }
            .joinToString("")
        return InstrumentInitializeResult(statusAscii = statusAscii)
    }

    fun decodeOdometer(payloadHex: String): InstrumentOdometerResult {
        val bytes = payloadHex.diagnosticHexBytes()
        if (bytes.size < 5 || bytes[0].u() != ODOMETER_POSITIVE_RESPONSE) {
            throw DiagnosticParseException("Expected the 0x0D positive-response (0x8D) odometer report")
        }
        // Observed layout 8D 01 00 AE 76 00 00 00: the odometer is the big-endian
        // 16-bit value at offset 3 (0xAE76 == 44662 km in the retained capture).
        val raw = bytes[3].u() shl 8 or bytes[4].u()
        return InstrumentOdometerResult(
            odometerRaw = String.format(Locale.ROOT, "0x%04X", raw),
            odometerKm = raw,
        )
    }

    private companion object {
        const val INITIALIZE_POSITIVE_RESPONSE = 0xDE
        const val ODOMETER_POSITIVE_RESPONSE = 0x8D
        const val PADDING_BYTE = 0xFF
    }
}
