package dev.resetlight.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsResponseParserTest {
    @Test
    fun `parses positive negative and response-pending results`() {
        val positive = UdsResponseParser.parse("54") as UdsResponse.Positive
        assertEquals(0x54, positive.service)

        val negative = UdsResponseParser.parse("7F1422") as UdsResponse.Negative
        assertEquals(0x14, negative.requestService)
        assertEquals(0x22, negative.responseCode)
        assertFalse(negative.pending)

        val pending = UdsResponseParser.parse("7F1478") as UdsResponse.Negative
        assertTrue(pending.pending)
    }

    @Test
    fun `rejects an empty or truncated response`() {
        assertThrows(DiagnosticParseException::class.java) { UdsResponseParser.parse("") }
        assertThrows(DiagnosticParseException::class.java) { UdsResponseParser.parse("7F14") }
    }
}
