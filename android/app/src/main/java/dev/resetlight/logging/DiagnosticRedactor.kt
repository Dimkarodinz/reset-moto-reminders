package dev.resetlight.logging

import java.nio.charset.StandardCharsets

object DiagnosticRedactor {
    private val mac = Regex("(?i)\\b(?:[0-9A-F]{2}:){5}[0-9A-F]{2}\\b")
    private val vin = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b")
    private val labeledSerial = Regex("(?i)(serial\\s*[=:]\\s*)[A-Z0-9-]{5,}")
    private val plainSerial = Regex("\\b[0-9]{10,}\\b")

    fun redactText(value: String): String = value
        .replace(mac, "[REDACTED_MAC]")
        .replace(vin, "[REDACTED_VIN]")
        .replace(labeledSerial) { "${it.groupValues[1]}[REDACTED_SERIAL]" }
        .replace(plainSerial, "[REDACTED_SERIAL]")

    fun redactJournalText(value: String): String {
        val personalDataRedacted = redactText(value)
        val compact = personalDataRedacted.filterNot(Char::isWhitespace).uppercase()
        return if (SECURITY_MARKERS.any(compact::contains)) {
            "[REDACTED_SECURITY_ACCESS]"
        } else {
            personalDataRedacted
        }
    }

    fun redactDiagnosticHex(value: String): String {
        val compact = value.replace(" ", "").uppercase()
        val ascii = compact.decodeHexAsciiOrNull()
        val searchable = listOfNotNull(compact, ascii?.uppercase())
        if (searchable.any { text -> SECURITY_MARKERS.any(text::contains) }) {
            return "[REDACTED_SECURITY_ACCESS]"
        }
        if (searchable.any { text -> IDENTITY_MARKERS.any(text::contains) }) {
            return "[REDACTED_IDENTIFIER]"
        }
        if (ascii != null && redactText(ascii) != ascii) {
            return "[REDACTED_PERSONAL_DATA]"
        }
        return value
    }

    private fun String.decodeHexAsciiOrNull(): String? {
        if (isEmpty() || length % 2 != 0 || any { it !in "0123456789ABCDEF" }) return null
        val bytes = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return String(bytes, StandardCharsets.ISO_8859_1)
    }

    private val SECURITY_MARKERS = listOf("2701", "2702", "6701", "6702")
    private val IDENTITY_MARKERS = listOf("F190", "F18C")
}
