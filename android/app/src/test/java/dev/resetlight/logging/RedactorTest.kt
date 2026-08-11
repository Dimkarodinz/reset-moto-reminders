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
    }
}
