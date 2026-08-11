package dev.resetlight.features.service

import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterFingerprintGateTest {
    private val ecu = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
    private val gate = ClusterFingerprintGate(ecu)

    @Test
    fun `authorizes writes for the exact motorcycle and observed instrument fingerprint`() {
        val decision = gate.evaluate(
            motorcycleId = ecu.motorcycleId,
            instrumentStatusAscii = "043",
        )
        assertTrue(decision.authorized)
    }

    @Test
    fun `refuses a different motorcycle profile`() {
        val decision = gate.evaluate(
            motorcycleId = "some-other-bike",
            instrumentStatusAscii = "043",
        )
        assertFalse(decision.authorized)
    }

    @Test
    fun `refuses an unexpected instrument status`() {
        val decision = gate.evaluate(
            motorcycleId = ecu.motorcycleId,
            instrumentStatusAscii = "999",
        )
        assertFalse(decision.authorized)
        assertTrue(decision.reason.contains("instrument", ignoreCase = true))
    }

    @Test
    fun `exposes the observed transport fingerprint it checked`() {
        assertEquals("0x701", gate.fingerprint.requestCanId)
        assertEquals("0x704", gate.fingerprint.responseCanId)
        assertEquals("ATTP6", gate.fingerprint.elmProtocolCommand)
        assertEquals("043", gate.fingerprint.expectedStatusAscii)
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
