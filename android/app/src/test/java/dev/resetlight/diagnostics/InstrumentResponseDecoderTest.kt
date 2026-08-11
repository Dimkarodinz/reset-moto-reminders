package dev.resetlight.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InstrumentResponseDecoderTest {
    private val decoder = InstrumentResponseDecoder()

    @Test
    fun `decodes the observed odometer response to kilometres`() {
        // 0D01 -> 8D0100AE76000000, observed on 2026-08-10 with a real odometer of 44662 km.
        val result = decoder.decodeOdometer("8D0100AE76000000")

        assertEquals(44662, result.odometerKm)
        assertEquals("0xAE76", result.odometerRaw)
    }

    @Test
    fun `decodes the observed initialize status payload`() {
        // 5E01 -> DE303433FFFFFFFF, the "043" ASCII status seen in both captures.
        val result = decoder.decodeInitialize("DE303433FFFFFFFF")

        assertEquals("043", result.statusAscii)
    }

    @Test
    fun `rejects a response that is not the odometer positive response`() {
        assertThrows(DiagnosticParseException::class.java) {
            decoder.decodeOdometer("7F0D11")
        }
    }

    @Test
    fun `rejects a truncated odometer response`() {
        assertThrows(DiagnosticParseException::class.java) {
            decoder.decodeOdometer("8D01")
        }
    }
}
