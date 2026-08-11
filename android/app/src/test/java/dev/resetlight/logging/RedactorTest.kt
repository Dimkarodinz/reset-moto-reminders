package dev.resetlight.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    @Test
    fun `redacts bluetooth address vin and serial fields`() {
        val input = "mac=AA:BB:CC:DD:EE:FF vin=1HGCM82633A004352 serial=1234567890"
        val result = DiagnosticRedactor.redactText(input)
        assertFalse(result.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(result.contains("1HGCM82633A004352"))
        assertFalse(result.contains("1234567890"))
        assertTrue(result.contains("[REDACTED"))
    }

    @Test
    fun `security access payload never survives pre-persistence redaction`() {
        assertTrue(DiagnosticRedactor.redactDiagnosticHex("022701A1B2").contains("REDACTED"))
        assertTrue(DiagnosticRedactor.redactDiagnosticHex("042702C3D4").contains("REDACTED"))
        assertTrue(DiagnosticRedactor.redactDiagnosticHex("046701188B").contains("REDACTED"))
        assertTrue(DiagnosticRedactor.redactDiagnosticHex("026702").contains("REDACTED"))
        assertTrue(DiagnosticRedactor.redactJournalText("18DAF1D5 04 6701 188B").contains("REDACTED"))
        assertTrue(DiagnosticRedactor.redactJournalText("command=042702A018").contains("REDACTED"))
    }

    @Test
    fun `ascii encoded raw traffic cannot leak identity or security material`() {
        val sensitiveRawTraffic = listOf(
            "0322F190\r",
            "0322F18C\r",
            "042702A018\r",
            "62F1901HGCM82633A004352\r>",
            "mac=AA:BB:CC:DD:EE:FF\r>",
        )

        sensitiveRawTraffic.forEach { traffic ->
            val rawHex = traffic.encodeToByteArray().joinToString("") { "%02X".format(it) }
            assertTrue(traffic, DiagnosticRedactor.redactDiagnosticHex(rawHex).contains("REDACTED"))
        }
    }
}
