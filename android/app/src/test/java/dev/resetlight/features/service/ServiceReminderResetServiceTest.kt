package dev.resetlight.features.service

import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.profiles.EcuProfileLoader
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceReminderResetServiceTest {
    private val ecu = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
    private val service = ServiceReminderResetService(
        ecu.instrumentReadOnlyCapture,
        ecu.serviceReminder,
        ClusterFingerprintGate(ecu),
        ecu.motorcycleId,
    )

    @Test
    fun `replays the observed reset and confirms the committed date echo`() = runTest {
        // The 2026-08-07 capture: interval 10000 km, next-service date 2027-08-07.
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "DE303433FFFFFFFF",
                "0D01" to "8D0100AE76000000",
                "3364" to "B364000000000000",
                "5C1B0807016E0000" to "DC1B0807016E0000",
            ),
        )

        val result = service.reset(
            channel = channel,
            distanceKm = 10_000,
            nextServiceDate = LocalDate.of(2027, 8, 7),
        )

        result as ServiceReminderResetResult.Committed
        assertEquals(44662, result.odometerKm)
        // Exactly the observed write bytes reached the cluster, in order.
        assertEquals(listOf("5E01", "0D01", "3364", "5C1B0807016E0000"), channel.dataRequests)
    }

    @Test
    fun `blocks without committing when the date echo does not match the request`() = runTest {
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "DE303433FFFFFFFF",
                "0D01" to "8D0100AE76000000",
                "3364" to "B364000000000000",
                "5C1B0807016E0000" to "DC1B0808016E0000", // wrong day echoed back
            ),
        )

        val result = service.reset(channel, 10_000, LocalDate.of(2027, 8, 7))

        assertTrue(result is ServiceReminderResetResult.Blocked)
    }

    @Test
    fun `blocks when the distance write is rejected and never sends the date`() = runTest {
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "DE303433FFFFFFFF",
                "0D01" to "8D0100AE76000000",
                "3364" to "7F3331",
            ),
        )

        val result = service.reset(channel, 10_000, LocalDate.of(2027, 8, 7))

        assertTrue(result is ServiceReminderResetResult.Blocked)
        assertTrue(channel.dataRequests.none { it.startsWith("5C") })
    }

    @Test
    fun `blocks before any write when the live cluster status is unexpected`() = runTest {
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "DE393939FFFFFFFF", // status "999", not the validated "043"
                "0D01" to "8D0100AE76000000",
            ),
        )

        val result = service.reset(channel, 10_000, LocalDate.of(2027, 8, 7))

        assertTrue(result is ServiceReminderResetResult.Blocked)
        assertTrue(channel.dataRequests.none { it.startsWith("33") || it.startsWith("5C") })
    }

    @Test
    fun `surfaces a transport failure as a typed failure`() = runTest {
        val channel = DiagnosticWriteChannel { _, _ -> throw IOException("dropped") }

        assertThrows(ServiceReminderResetFailure::class.java) {
            kotlinx.coroutines.runBlocking { service.reset(channel, 10_000, LocalDate.of(2027, 8, 7)) }
        }
    }

    private fun configResponses(): Map<String, String> =
        ecu.instrumentReadOnlyCapture.configurationCommands
            .associateWith { if (it == "ATWS") "ELM327 v2.2" else "OK" }

    private class ScriptedChannel(private val responses: Map<String, String>) : DiagnosticWriteChannel {
        val sent = mutableListOf<String>()
        val dataRequests = mutableListOf<String>()

        override suspend fun execute(request: String, intent: WriteIntent): String {
            sent += request
            if (!request.startsWith("AT")) dataRequests += request
            return responses[request] ?: error("Unexpected request $request")
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
