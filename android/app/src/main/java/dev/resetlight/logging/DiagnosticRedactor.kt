package dev.resetlight.logging

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

    fun redactDiagnosticHex(value: String): String {
        val compact = value.replace(" ", "").uppercase()
        return if (compact.contains("2701") || compact.contains("2702")) {
            "[REDACTED_SECURITY_ACCESS]"
        } else {
            value
        }
    }
}
