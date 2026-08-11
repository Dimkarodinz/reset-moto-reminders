package dev.resetlight.diagnostics

/**
 * Extracts the diagnostic payload from a live ELM response.
 *
 * The ECU maps record payload-only observed responses (`59010C000000`,
 * `DE303433FFFFFFFF`), but with headers on (`ATH1`) and automatic formatting
 * off (`ATCAF0`) the adapter returns raw frames:
 *
 * - engine (29-bit, ISO-TP): `18DAF1D5 06 59010C0000 00 AA` — CAN ID, then an
 *   ISO-TP PCI byte, then the payload, then `AA` padding;
 * - instrument (11-bit, no ISO-TP): `704 DE303433FFFFFFFF` — CAN ID, then the
 *   raw eight data bytes (note the odd-length hex string: an 11-bit ID is
 *   three hex digits).
 *
 * This extractor strips the module's response CAN ID, reassembles ISO-TP
 * single- and multi-frame responses for the engine, and returns the raw data
 * bytes for the instrument. A response that is already a bare payload (the
 * map/test format, or an `ATH0` configuration) passes through unchanged, and
 * `NO DATA`/ELM error strings raise [DiagnosticNoResponseException] instead of
 * a confusing hex-parse failure. The 2026-08-12 trip journal is the retained
 * evidence for both live framings.
 */
class CanResponseExtractor(
    responseCanId: String,
    private val isoTp: Boolean,
) {
    private val header = responseCanId.removePrefix("0x").removePrefix("0X").uppercase()

    init {
        require(header.isNotEmpty() && header.all { it in "0123456789ABCDEF" }) {
            "Response CAN ID must be hexadecimal"
        }
    }

    fun extract(raw: String): String {
        val payloads = extractAll(raw)
        return payloads.singleOrNull()
            ?: throw DiagnosticParseException("Expected one diagnostic response, got ${payloads.size}")
    }

    /**
     * Extracts every complete diagnostic response returned before one ELM
     * prompt. This matters for UDS response-pending: the adapter can return the
     * `7F..78` frame and the final positive frame as one command response.
     */
    fun extractAll(raw: String): List<String> {
        val upper = raw.uppercase()
        if (NO_RESPONSE_MARKERS.any { upper.contains(it) }) {
            throw DiagnosticNoResponseException(raw.trim())
        }
        val frames = raw.lines()
            .map { it.hexOnly() }
            .filter { it.isNotEmpty() }
        if (frames.isEmpty()) {
            throw DiagnosticParseException("Response contains no hexadecimal frame data")
        }
        // Bare payload (map/test format or ATH0): no frame carries our CAN ID.
        if (frames.none { it.startsWith(header) && it.length > header.length }) {
            return frames
        }
        val dataFrames = frames
            .filter { it.startsWith(header) && it.length > header.length }
            .map { it.substring(header.length) }
        return if (isoTp) {
            if (dataFrames.all { it.diagnosticHexBytes()[0].u() shr 4 == SINGLE_FRAME }) {
                dataFrames.map(::decodeSingleFrame)
            } else {
                listOf(reassembleIsoTp(dataFrames))
            }
        } else {
            dataFrames.map { it }
        }
    }

    private fun decodeSingleFrame(frameHex: String): String {
        val frame = frameHex.diagnosticHexBytes()
        val length = frame[0].u() and 0x0F
        if (length == 0 || frame.size < 1 + length) {
            throw DiagnosticParseException("ISO-TP single frame shorter than its declared length")
        }
        return frame.toHex(from = 1, count = length)
    }

    private fun reassembleIsoTp(dataFrames: List<String>): String {
        val first = dataFrames.first().diagnosticHexBytes()
        return when (first[0].u() shr 4) {
            SINGLE_FRAME -> {
                decodeSingleFrame(dataFrames.first())
            }
            FIRST_FRAME -> {
                val total = ((first[0].u() and 0x0F) shl 8) or first[1].u()
                val assembled = StringBuilder(first.toHex(from = 2, count = first.size - 2))
                dataFrames.drop(1).forEach { frameHex ->
                    val frame = frameHex.diagnosticHexBytes()
                    if (frame[0].u() shr 4 != CONSECUTIVE_FRAME) {
                        throw DiagnosticParseException("Expected an ISO-TP consecutive frame")
                    }
                    assembled.append(frame.toHex(from = 1, count = frame.size - 1))
                }
                if (assembled.length < total * 2) {
                    throw DiagnosticParseException(
                        "ISO-TP response truncated: expected $total bytes, got ${assembled.length / 2}",
                    )
                }
                assembled.substring(0, total * 2)
            }
            else -> throw DiagnosticParseException("Unsupported ISO-TP frame type in response")
        }
    }

    private fun ByteArray.toHex(from: Int, count: Int): String = buildString(count * 2) {
        for (index in from until minOf(from + count, this@toHex.size)) {
            append("%02X".format(this@toHex[index]))
        }
    }

    private companion object {
        const val SINGLE_FRAME = 0x0
        const val FIRST_FRAME = 0x1
        const val CONSECUTIVE_FRAME = 0x2

        val NO_RESPONSE_MARKERS = listOf(
            "NO DATA",
            "CAN ERROR",
            "BUS INIT",
            "STOPPED",
            "UNABLE TO CONNECT",
        )
    }
}
