package dev.resetlight.features.dtc

import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.EngineSeedKeyDerivation
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcClearServiceTest {
    private val ecu = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
    private val clearProfile = ecu.diagnosticTroubleCodes.clear
    private val securityProfile = ecu.engineSecurityAccess

    @Test
    fun `authenticates then clears and verifies zero remaining codes`() = runTest {
        val script = ScriptedChannel(
            mapOf(
                securityProfile.extendedSessionElmRequest to "5003",
                securityProfile.seedRequestElmRequest to "6701188B",
                "042702A018" to "6702",
                clearProfile.elmRequest to "54",
                clearProfile.verificationElmRequest to "59010C000000",
            ),
        )

        val result = DtcClearService(clearProfile, securityProfile, EngineSeedKeyDerivation(0x4B48), script).clear()

        assertEquals(DtcClearResult.Cleared(remainingCount = 0), result)
        // The derived key request must be exactly what the observed handshake expects.
        assertTrue(script.sent.contains("042702A018"))
        assertEquals(clearProfile.elmRequest, script.sent[3])
    }

    @Test
    fun `accepts pending then positive from one clear request without resending it`() = runTest {
        val framedExtractor = dev.resetlight.diagnostics.CanResponseExtractor("0x18DAF1D5", isoTp = true)
        val script = ScriptedChannel(
            mapOf(
                securityProfile.extendedSessionElmRequest to "5003",
                securityProfile.seedRequestElmRequest to "6701188B",
                "042702A018" to "6702",
                clearProfile.elmRequest to """
                    18DAF1D5037F1478AAAAAAAA
                    18DAF1D50154AAAAAAAAAAAA
                """.trimIndent(),
                clearProfile.verificationElmRequest to "59010C000000",
            ),
        )

        val result = DtcClearService(
            clearProfile,
            securityProfile,
            EngineSeedKeyDerivation(0x4B48),
            script,
            extractor = framedExtractor,
        ).clear()

        assertEquals(DtcClearResult.Cleared(remainingCount = 0), result)
        assertEquals(1, script.sent.count { it == clearProfile.elmRequest })
    }

    @Test
    fun `pending without a final response blocks and never resends the clear`() = runTest {
        val framedExtractor = dev.resetlight.diagnostics.CanResponseExtractor("0x18DAF1D5", isoTp = true)
        val script = ScriptedChannel(
            mapOf(
                securityProfile.extendedSessionElmRequest to "5003",
                securityProfile.seedRequestElmRequest to "6701188B",
                "042702A018" to "6702",
                clearProfile.elmRequest to "18DAF1D5037F1478AAAAAAAA",
            ),
        )

        val result = DtcClearService(
            clearProfile,
            securityProfile,
            EngineSeedKeyDerivation(0x4B48),
            script,
            extractor = framedExtractor,
        ).clear()

        assertTrue(result is DtcClearResult.Blocked)
        assertEquals(1, script.sent.count { it == clearProfile.elmRequest })
        assertTrue(script.sent.none { it == clearProfile.verificationElmRequest })
    }

    @Test
    fun `reports blocked when the ECU rejects security access`() = runTest {
        val script = ScriptedChannel(
            mapOf(
                securityProfile.extendedSessionElmRequest to "5003",
                securityProfile.seedRequestElmRequest to "6701188B",
                "042702A018" to "7F2735",
            ),
        )

        val result = DtcClearService(clearProfile, securityProfile, EngineSeedKeyDerivation(0x4B48), script).clear()

        assertTrue(result is DtcClearResult.Blocked)
        // No clear request may be sent once security access is refused.
        assertTrue(script.sent.none { it == clearProfile.elmRequest })
    }

    @Test
    fun `reports blocked when the extended session is refused`() = runTest {
        val script = ScriptedChannel(
            mapOf(securityProfile.extendedSessionElmRequest to "7F1012"),
        )

        val result = DtcClearService(clearProfile, securityProfile, EngineSeedKeyDerivation(0x4B48), script).clear()

        assertTrue(result is DtcClearResult.Blocked)
        assertTrue(script.sent.none { it == securityProfile.seedRequestElmRequest })
    }

    @Test
    fun `configures the engine route then clears against live framed responses`() = runTest {
        // The live framing the 2026-08-12 trip showed (ATH1 + ATCAF0): CAN ID
        // 18DAF1D5, ISO-TP length byte, AA padding. The verification frame is
        // the trip's real DTC-count response byte-for-byte.
        val configuration = ecu.engineReadOnlyCapture.configurationCommands
        val script = ScriptedChannel(
            configuration.associateWith { if (it == "ATWS") "ELM327 v2.2" else "OK" } + mapOf(
                securityProfile.extendedSessionElmRequest to "18DAF1D5065003003201F4AA",
                securityProfile.seedRequestElmRequest to "18DAF1D5046701188BAAAAAA",
                "042702A018" to "18DAF1D5026702AAAAAAAAAA",
                clearProfile.elmRequest to "18DAF1D50154AAAAAAAAAAAA",
                clearProfile.verificationElmRequest to "18DAF1D50659010C000000AA",
            ),
        )

        val result = DtcClearService(
            clearProfile,
            securityProfile,
            EngineSeedKeyDerivation(0x4B48),
            script,
            configurationCommands = configuration,
            extractor = dev.resetlight.diagnostics.CanResponseExtractor("0x18DAF1D5", isoTp = true),
        ).clear()

        assertEquals(DtcClearResult.Cleared(remainingCount = 0), result)
        // The engine route is applied before any diagnostic request.
        assertEquals(configuration, script.sent.take(configuration.size))
    }

    @Test
    fun `surfaces a transport failure as a typed failure`() = runTest {
        val channel = DiagnosticWriteChannel { _, _ -> throw IOException("dropped") }

        assertThrows(DtcClearFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                DtcClearService(clearProfile, securityProfile, EngineSeedKeyDerivation(0x4B48), channel).clear()
            }
        }
    }

    private class ScriptedChannel(
        private val responses: Map<String, String>,
        private val queued: Map<String, ArrayDeque<String>> = emptyMap(),
    ) : DiagnosticWriteChannel {
        val sent = mutableListOf<String>()
        private val mutableQueued = queued.mapValues { ArrayDeque(it.value) }

        override suspend fun execute(request: String, intent: WriteIntent): String {
            sent += request
            mutableQueued[request]?.let { if (it.isNotEmpty()) return it.removeFirst() }
            return responses[request] ?: error("Unexpected request $request")
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
