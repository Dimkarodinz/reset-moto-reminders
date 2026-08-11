package dev.resetlight.features.research

import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyEngineCaptureTest {
    private val profile = EcuProfileLoader()
        .load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))

    @Test
    fun `captures non-sensitive identifiers and zero DTC count without changing session`() = runTest {
        val channel = RecordingChannel { request, _ ->
            when (request) {
                "ATWS" -> "ELM327 v2.2"
                profile.engineReadOnlyCapture.dtcCountElmRequest -> "59010C000000"
                else -> "OK"
            }
        }

        val result = ReadOnlyEngineCapture(profile.engineReadOnlyCapture, channel).capture()

        assertEquals(0, result.dtcCount)
        assertFalse(result.extendedSessionUsed)
        assertNull(result.dtcDetailResponse)
        assertTrue(channel.requests.containsAll(profile.engineReadOnlyCapture.configurationCommands))
        assertTrue(channel.requests.containsAll(profile.engineReadOnlyCapture.identifierReads.map { it.elmRequest }))
        assertFalse(channel.requests.contains("0322F18C"))
        assertFalse(channel.requests.contains("0322F190"))
        assertNoWriteOrSecurityCommands(channel.requests)
    }

    @Test
    fun `uses one observed extended-session fallback then captures DTC details`() = runTest {
        val channel = RecordingChannel { request, occurrence ->
            when {
                request == "ATWS" -> "ELM327 v2.2"
                request == profile.engineReadOnlyCapture.dtcCountElmRequest && occurrence == 1 -> "7F197E"
                request == profile.engineReadOnlyCapture.extendedSessionElmRequest -> "5003"
                request == profile.engineReadOnlyCapture.dtcCountElmRequest -> "59010C000001"
                request == profile.engineReadOnlyCapture.dtcDetailElmRequest -> "59020C15770008"
                else -> "OK"
            }
        }

        val result = ReadOnlyEngineCapture(profile.engineReadOnlyCapture, channel).capture()

        assertTrue(result.extendedSessionUsed)
        assertEquals(1, result.dtcCount)
        assertEquals("59020C15770008", result.dtcDetailResponse)
        assertEquals(2, channel.requests.count { it == profile.engineReadOnlyCapture.dtcCountElmRequest })
        assertEquals(1, channel.requests.count { it == profile.engineReadOnlyCapture.extendedSessionElmRequest })
        assertEquals(1, channel.requests.count { it == profile.engineReadOnlyCapture.dtcDetailElmRequest })
        assertNoWriteOrSecurityCommands(channel.requests)
    }

    @Test
    fun `stops when extended session is rejected`() = runTest {
        val channel = RecordingChannel { request, _ ->
            when (request) {
                "ATWS" -> "ELM327 v2.2"
                profile.engineReadOnlyCapture.dtcCountElmRequest -> "7F197E"
                profile.engineReadOnlyCapture.extendedSessionElmRequest -> "7F1012"
                else -> "OK"
            }
        }

        val result = ReadOnlyEngineCapture(profile.engineReadOnlyCapture, channel).capture()

        assertTrue(result is ReadOnlyEngineCaptureResult.Blocked)
        assertEquals(1, channel.requests.count { it == profile.engineReadOnlyCapture.dtcCountElmRequest })
        assertFalse(channel.requests.contains(profile.engineReadOnlyCapture.dtcDetailElmRequest))
        assertNoWriteOrSecurityCommands(channel.requests)
    }

    @Test
    fun `transport failure stops without retry`() {
        val channel = RecordingChannel { request, _ ->
            if (request == profile.engineReadOnlyCapture.dtcCountElmRequest) throw IOException("link lost")
            if (request == "ATWS") "ELM327 v2.2" else "OK"
        }

        assertThrows(ReadOnlyEngineCaptureFailure::class.java) {
            runTest { ReadOnlyEngineCapture(profile.engineReadOnlyCapture, channel).capture() }
        }
        assertEquals(1, channel.requests.count { it == profile.engineReadOnlyCapture.dtcCountElmRequest })
    }

    private fun assertNoWriteOrSecurityCommands(requests: List<String>) {
        assertFalse(requests.any { it.contains("2701") || it.contains("2702") })
        assertFalse(requests.any { it == "0414FFFFFF" })
        assertFalse(requests.any { it.startsWith("33") || it.startsWith("5C") })
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }

    private class RecordingChannel(
        private val response: (request: String, occurrence: Int) -> String,
    ) : DiagnosticReadChannel {
        val requests = mutableListOf<String>()

        override suspend fun execute(request: String): String {
            requests += request
            return response(request, requests.count { it == request })
        }
    }
}
