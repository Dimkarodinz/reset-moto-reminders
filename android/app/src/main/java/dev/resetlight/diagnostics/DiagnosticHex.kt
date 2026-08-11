package dev.resetlight.diagnostics

class DiagnosticParseException(message: String) : IllegalArgumentException(message)

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
