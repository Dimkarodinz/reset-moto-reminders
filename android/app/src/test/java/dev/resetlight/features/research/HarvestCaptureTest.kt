package dev.resetlight.features.research

import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestCaptureTest {
    private val ecu = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))

    @Test
    fun `gathers engine identifiers, dtcs and instrument reads in one pass`() = runTest {
        val channel = MapChannel(
            engineConfig = ecu.engineReadOnlyCapture.configurationCommands,
            instrumentConfig = ecu.instrumentReadOnlyCapture.configurationCommands,
            requestResponses = buildMap {
                ecu.engineReadOnlyCapture.identifierReads.forEach { put(it.elmRequest, "62F1A0000000") }
                put(ecu.engineReadOnlyCapture.dtcCountElmRequest, "59010C000000")
                put(ecu.instrumentReadOnlyCapture.initializeElmRequest, "DE303433FFFFFFFF")
                put(ecu.instrumentReadOnlyCapture.odometerElmRequest, "8D0100AE76000000")
            },
        )

        val result = HarvestCapture(
            ecu.engineReadOnlyCapture,
            ecu.instrumentReadOnlyCapture,
            channel,
        ).run()

        val engine = result.engine as ReadOnlyEngineCaptureResult.Complete
        assertEquals(0, engine.dtcCount)
        val instrument = result.instrument as InstrumentReadOnlyCaptureResult.Complete
        assertEquals(44662, instrument.odometerKm)
        assertEquals("043", instrument.statusAscii)
        // A single connection produced both halves; no write ever went out.
        assertTrue(channel.sent.none { it.startsWith("33") || it.startsWith("5C") || it.startsWith("0414") })
    }

    @Test
    fun `still returns the engine half when the instrument route is rejected`() = runTest {
        val channel = MapChannel(
            engineConfig = ecu.engineReadOnlyCapture.configurationCommands,
            instrumentConfig = emptyList(), // instrument config commands all answer "?"
            requestResponses = buildMap {
                ecu.engineReadOnlyCapture.identifierReads.forEach { put(it.elmRequest, "62F1A0000000") }
                put(ecu.engineReadOnlyCapture.dtcCountElmRequest, "59010C000000")
            },
        )

        val result = HarvestCapture(
            ecu.engineReadOnlyCapture,
            ecu.instrumentReadOnlyCapture,
            channel,
        ).run()

        assertTrue(result.engine is ReadOnlyEngineCaptureResult.Complete)
        assertTrue(result.instrument is InstrumentReadOnlyCaptureResult.Blocked)
    }

    private class MapChannel(
        engineConfig: List<String>,
        instrumentConfig: List<String>,
        private val requestResponses: Map<String, String>,
    ) : DiagnosticReadChannel {
        val sent = mutableListOf<String>()
        private val configOk = (engineConfig + instrumentConfig).toSet()

        override suspend fun execute(request: String): String {
            sent += request
            if (request == "ATWS") return "ELM327 v2.2"
            if (request.startsWith("AT")) return if (request in configOk) "OK" else "?"
            return requestResponses[request] ?: error("Unexpected request $request")
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
