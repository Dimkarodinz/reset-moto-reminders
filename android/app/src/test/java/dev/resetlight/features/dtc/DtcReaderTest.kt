package dev.resetlight.features.dtc

import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.profiles.DtcMapLoader
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DtcReaderTest {
    private val ecuProfile = EcuProfileLoader().load(
        generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"),
    )
    private val descriptions = DtcMapLoader().load(
        generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
    )

    @Test
    fun `zero count skips the detail request`() = runTest {
        val channel = ScriptedReadChannel(listOf("59010C000000"))
        val result = DtcReader(ecuProfile.diagnosticTroubleCodes.read, descriptions, channel).read()

        assertEquals(0, result.reportedCount)
        assertEquals(emptyList<Any>(), result.dtcs)
        assertEquals(listOf("03190108"), channel.requests)
    }

    @Test
    fun `nonzero count reads details once and resolves descriptions`() = runTest {
        val channel = ScriptedReadChannel(
            listOf("59010C000002", "59020C1577000800300008"),
        )
        val result = DtcReader(ecuProfile.diagnosticTroubleCodes.read, descriptions, channel).read()

        assertEquals(2, result.reportedCount)
        assertEquals(listOf("P1577-00", "P0030-00"), result.dtcs.map { it.displayCode })
        assertEquals(
            "Brake switch 1 and brake switch 2 signals do not match",
            result.dtcs[0].message.message,
        )
        assertEquals(
            "oxygen sensor heater: shorted to ground; circuit open.",
            result.dtcs[1].message.message,
        )
        assertEquals(listOf("03190108", "03190208"), channel.requests)
    }

    @Test
    fun `count mismatch is a typed failure`() = runTest {
        val channel = ScriptedReadChannel(
            listOf("59010C000002", "59020C15770008"),
        )

        val error = assertThrows(DtcReadFailure.CountMismatch::class.java) {
            kotlinx.coroutines.runBlocking {
                DtcReader(ecuProfile.diagnosticTroubleCodes.read, descriptions, channel).read()
            }
        }

        assertEquals(2, error.reportedCount)
        assertEquals(1, error.decodedCount)
    }

    @Test
    fun `transport failure is not retried`() = runTest {
        val channel = ScriptedReadChannel(listOf(IOException("disconnected")))

        val error = assertThrows(DtcReadFailure.Transport::class.java) {
            kotlinx.coroutines.runBlocking {
                DtcReader(ecuProfile.diagnosticTroubleCodes.read, descriptions, channel).read()
            }
        }

        assertEquals("03190108", error.request)
        assertEquals(listOf("03190108"), channel.requests)
    }

    private class ScriptedReadChannel(responses: List<Any>) : DiagnosticReadChannel {
        private val remaining = ArrayDeque(responses)
        val requests = mutableListOf<String>()

        override suspend fun execute(request: String): String {
            requests += request
            return when (val response = remaining.removeFirst()) {
                is String -> response
                is Throwable -> throw response
                else -> error("Unsupported scripted response")
            }
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
