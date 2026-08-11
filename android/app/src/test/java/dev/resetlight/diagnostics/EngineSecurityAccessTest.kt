package dev.resetlight.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineSecurityAccessTest {
    private val derivation = EngineSeedKeyDerivation(multiplier = 0x4B48)

    @Test
    fun `reproduces every retained engine seed-key pair`() {
        val pairs = mapOf(
            "188B" to "A018",
            "871C" to "33E0",
            "FBBD" to "2C28",
            "2A7B" to "FB98",
            "7108" to "2240",
            "89DE" to "D070",
        )
        pairs.forEach { (seed, key) ->
            assertEquals(key, derivation.keyForSeedHex(seed))
        }
    }

    @Test
    fun `extracts the seed from a 6701 seed response and derives the 2702 request`() {
        // 6701 is the positive response to 2701; the two payload bytes are the seed.
        assertEquals("042702A018", derivation.keyRequestFor("6701188B", keyRequestPrefix = "042702"))
    }

    @Test
    fun `rejects a response that is not a security seed`() {
        assertThrows(DiagnosticParseException::class.java) {
            derivation.keyRequestFor("7F2733", keyRequestPrefix = "042702")
        }
    }
}
