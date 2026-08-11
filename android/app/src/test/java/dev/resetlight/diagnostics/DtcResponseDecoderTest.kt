package dev.resetlight.diagnostics

import dev.resetlight.profiles.DtcDictionary
import dev.resetlight.profiles.DtcMessage
import dev.resetlight.profiles.DtcMessageStatus
import dev.resetlight.profiles.DtcReferenceMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcResponseDecoderTest {
    private val dictionary = DtcDictionary(
        schemaVersion = 3,
        id = "test-en",
        locale = "en",
        manufacturer = "Triumph",
        moduleKey = "engine_ecu",
        motorcycleProfileIds = setOf("test-profile"),
        ecuFamily = "test-family",
        genericFallbackMessages = mapOf(
            'P' to "Powertrain diagnostic trouble code {code}. No validated manufacturer description is available.",
            'C' to "Chassis diagnostic trouble code {code}. No validated manufacturer description is available.",
            'B' to "Body diagnostic trouble code {code}. No validated manufacturer description is available.",
            'U' to "Network diagnostic trouble code {code}. No validated manufacturer description is available.",
        ),
        unknownMessage = "Unrecognized diagnostic trouble code format.",
        entries = mapOf(
            "P1577-00" to DtcMessage(
                message = "Brake-switch 2 and brake-switch 1 signals do not agree.",
                status = DtcMessageStatus.THIRD_PARTY_CORROBORATED,
            ),
        ),
        referenceCatalog = DtcReferenceMetadata(
            sourceName = "test-reference",
            sourceVersion = "1",
        ),
        referenceEntries = emptyMap(),
        sourceSha256 = "test-hash",
    )
    private val decoder = DtcResponseDecoder(dictionary)

    @Test
    fun `decodes captured zero and one DTC count responses`() {
        assertEquals(0, decoder.decodeCount("59010C000000").matchingCount)

        val count = decoder.decodeCount("59010C000001")
        assertEquals(1, count.matchingCount)
        assertEquals(0x0C, count.statusAvailabilityMask)
        assertEquals(0x00, count.formatIdentifier)
    }

    @Test
    fun `decodes captured confirmed DTC and dictionary message`() {
        val dtc = decoder.decodeDetails("59020C15770008").single()

        assertEquals("0x157700", dtc.rawCode)
        assertEquals("P1577-00", dtc.displayCode)
        assertEquals(0x08, dtc.statusByte)
        assertTrue(dtc.confirmed)
        assertEquals(
            "Brake-switch 2 and brake-switch 1 signals do not agree.",
            dtc.message.message,
        )
    }

    @Test
    fun `decodes multiple DTCs and gives unmapped codes a generic subsystem description`() {
        val dtcs = decoder.decodeDetails("59020C1577000801234501")

        assertEquals(listOf("P1577-00", "P0123-45"), dtcs.map { it.displayCode })
        assertEquals(
            "Powertrain diagnostic trouble code P0123-45. " +
                "No validated manufacturer description is available.",
            dtcs[1].message.message,
        )
        assertEquals(DtcMessageStatus.GENERIC_CLASSIFICATION, dtcs[1].message.status)
    }

    @Test
    fun `rejects truncated and wrong-subfunction responses`() {
        assertThrows(DiagnosticParseException::class.java) {
            decoder.decodeCount("59010C0000")
        }
        assertThrows(DiagnosticParseException::class.java) {
            decoder.decodeDetails("59010C15770008")
        }
        assertThrows(DiagnosticParseException::class.java) {
            decoder.decodeDetails("59020C157700")
        }
    }
}
