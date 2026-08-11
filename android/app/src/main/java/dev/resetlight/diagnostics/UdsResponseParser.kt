package dev.resetlight.diagnostics

sealed interface UdsResponse {
    data class Positive(val service: Int, val payload: ByteArray) : UdsResponse
    data class Negative(
        val requestService: Int,
        val responseCode: Int,
        val pending: Boolean,
    ) : UdsResponse
}

object UdsResponseParser {
    fun parse(payloadHex: String): UdsResponse {
        val bytes = payloadHex.diagnosticHexBytes()
        if ((bytes[0].toInt() and 0xFF) != NEGATIVE_RESPONSE_SERVICE) {
            return UdsResponse.Positive(bytes[0].toInt() and 0xFF, bytes.copyOfRange(1, bytes.size))
        }
        if (bytes.size != 3) {
            throw DiagnosticParseException("UDS negative response must contain service and response code")
        }
        val responseCode = bytes[2].toInt() and 0xFF
        return UdsResponse.Negative(
            requestService = bytes[1].toInt() and 0xFF,
            responseCode = responseCode,
            pending = responseCode == RESPONSE_PENDING,
        )
    }

    private const val NEGATIVE_RESPONSE_SERVICE = 0x7F
    private const val RESPONSE_PENDING = 0x78
}
