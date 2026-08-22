package dev.resetlight.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The live framings below are the retained evidence from the 2026-08-12 trip
 * journal (`logs/2026-08-12/session-1786454940306.jsonl`): with `ATH1` and
 * `ATCAF0` the adapter prefixes every frame with the response CAN ID, the
 * engine adds an ISO-TP PCI byte and `AA` padding, and the instrument returns
 * its raw eight data bytes.
 */
class CanResponseExtractorTest {
    private val engine = CanResponseExtractor("0x18DAF1D5", isoTp = true)
    private val instrument = CanResponseExtractor("0x704", isoTp = false)

    @Test
    fun `extracts the engine DTC count payload from the live framed response`() {
        assertEquals(
            "59010C000000",
            engine.extract("18DAF1D50659010C000000AA"),
        )
    }

    @Test
    fun `extracts pending and final UDS responses returned before one ELM prompt`() {
        assertEquals(
            listOf("7F1478", "54"),
            engine.extractAll(
                """
                18DAF1D5037F1478AAAAAAAA
                18DAF1D50154AAAAAAAAAAAA
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `extracts an engine identifier payload from the live framed response`() {
        assertEquals(
            "62F1A0000000",
            engine.extract("18DAF1D50662F1A0000000AA"),
        )
    }

    @Test
    fun `extracts the live instrument status frame including its FF padding`() {
        // The 11-bit CAN ID is three hex digits, so the raw string is odd-length;
        // this exact response crashed the pre-fix decoder on hardware.
        assertEquals(
            "DE303433FFFFFFFF",
            instrument.extract("704DE303433FFFFFFFF"),
        )
    }

    @Test
    fun `extracts the live instrument odometer frame`() {
        assertEquals(
            "8D0100AE9C000000",
            instrument.extract("7048D0100AE9C000000"),
        )
    }

    @Test
    fun `passes a bare map-format payload through unchanged`() {
        assertEquals("59010C000000", engine.extract("59010C000000"))
        assertEquals("DE303433FFFFFFFF", instrument.extract("DE303433FFFFFFFF"))
    }

    @Test
    fun `reassembles an ISO-TP multi-frame engine response`() {
        // Synthetic standard ISO 15765-2 framing: first frame declares 0x14 (20)
        // bytes, consecutive frames carry seven bytes each. No multi-frame
        // response has been captured on hardware yet.
        val payload = engine.extract(
            """
            18DAF1D51014590206AABBCC
            18DAF1D521DDEEFF11223344
            18DAF1D5225566778899AABB
            """.trimIndent(),
        )
        assertEquals("590206AABBCCDDEEFF112233445566778899AABB", payload)
    }

    @Test
    fun `NO DATA raises the typed no-response error instead of a hex failure`() {
        assertThrows(DiagnosticNoResponseException::class.java) {
            engine.extract("NO DATA")
        }
        assertThrows(DiagnosticNoResponseException::class.java) {
            instrument.extract("NO DATA")
        }
    }

    @Test
    fun `CAN ERROR raises the typed no-response error`() {
        assertThrows(DiagnosticNoResponseException::class.java) {
            engine.extract("CAN ERROR")
        }
    }

    @Test
    fun `a truncated ISO-TP single frame is rejected`() {
        assertThrows(DiagnosticParseException::class.java) {
            engine.extract("18DAF1D5065901")
        }
    }

    @Test
    fun `a truncated multi-frame response is rejected`() {
        assertThrows(DiagnosticParseException::class.java) {
            engine.extract("18DAF1D51014590206AABBCC")
        }
    }

    @Test
    fun `an empty response is rejected`() {
        assertThrows(DiagnosticParseException::class.java) {
            engine.extract("")
        }
    }
}
