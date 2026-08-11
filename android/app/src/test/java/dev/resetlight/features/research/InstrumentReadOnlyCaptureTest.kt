package dev.resetlight.features.research

import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentReadOnlyCaptureTest {
    private val profile = EcuProfileLoader().load(
        generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"),
    ).instrumentReadOnlyCapture

    @Test
    fun `configures the cluster then reads status and odometer without any write`() = runTest {
        val channel = RecordingChannel(
            configResponses = profile.configurationCommands.associateWith { command ->
                if (command == "ATWS") "ELM327 v2.2" else "OK"
            },
            requestResponses = mapOf(
                profile.initializeElmRequest to "DE303433FFFFFFFF",
                profile.odometerElmRequest to "8D0100AE76000000",
            ),
        )

        val result = InstrumentReadOnlyCapture(profile, channel).capture()

        result as InstrumentReadOnlyCaptureResult.Complete
        assertEquals(44662, result.odometerKm)
        assertEquals("043", result.statusAscii)
        // Only the two observed reads reach the cluster; no 33/5C write is ever sent.
        assertEquals(
            listOf(profile.initializeElmRequest, profile.odometerElmRequest),
            channel.dataRequests,
        )
        assertTrue(channel.dataRequests.none { it.startsWith("33") || it.startsWith("5C") })
    }

    @Test
    fun `blocks when the cluster route configuration is rejected`() = runTest {
        val channel = RecordingChannel(
            configResponses = profile.configurationCommands.associateWith { "?" },
            requestResponses = emptyMap(),
        )

        val result = InstrumentReadOnlyCapture(profile, channel).capture()

        assertTrue(result is InstrumentReadOnlyCaptureResult.Blocked)
    }

    @Test
    fun `transport failure surfaces as a typed capture failure`() = runTest {
        val channel = object : DiagnosticReadChannel {
            override suspend fun execute(request: String): String = throw IOException("dropped")
        }

        assertThrows(InstrumentReadOnlyCaptureFailure::class.java) {
            kotlinx.coroutines.runBlocking { InstrumentReadOnlyCapture(profile, channel).capture() }
        }
    }

    private class RecordingChannel(
        private val configResponses: Map<String, String>,
        private val requestResponses: Map<String, String>,
    ) : DiagnosticReadChannel {
        val dataRequests = mutableListOf<String>()

        override suspend fun execute(request: String): String {
            configResponses[request]?.let { return it }
            dataRequests += request
            return requestResponses[request] ?: error("Unexpected request $request")
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
