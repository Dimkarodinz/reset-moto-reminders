package dev.resetlight.diagnostics

open class DiagnosticParseException(message: String) : IllegalArgumentException(message)

/**
 * The adapter answered with `NO DATA` (or an ELM error string) instead of a CAN
 * frame — the module did not respond on the configured route. A subtype of
 * [DiagnosticParseException] so existing blocked/failed handling applies, while
 * callers can still surface the more precise "no response" message.
 */
class DiagnosticNoResponseException(raw: String) :
    DiagnosticParseException("The module did not respond: $raw")

internal fun String.diagnosticHexBytes(): ByteArray {
    if (isEmpty() || length % 2 != 0 || any { it !in "0123456789abcdefABCDEF" }) {
        throw DiagnosticParseException("Diagnostic payload must be non-empty even-length hexadecimal")
    }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/** Keeps only hex digits, uppercased — strips adapter framing such as spaces and CR. */
internal fun String.hexOnly(): String = buildString(length) {
    this@hexOnly.forEach { character ->
        val upper = character.uppercaseChar()
        if (character in '0'..'9' || upper in 'A'..'F') append(upper)
    }
}

internal fun Byte.u(): Int = toInt() and 0xFF
