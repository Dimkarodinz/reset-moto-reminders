package dev.resetlight.features.service

import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.domain.DistanceUnit
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
    fun `an unsupported interval blocks before any byte is sent`() = runTest {
        // Trip 2026-08-12: the builder's require() threw pre-I/O and tore the
        // whole adapter session down. Invalid input must block gracefully.
        val channel = ScriptedChannel(emptyMap())

        val notHundreds = service.reset(channel, 10_050, LocalDate.of(2027, 8, 7))
        val tooLarge = service.reset(channel, 30_000, LocalDate.of(2027, 8, 7))
        val yearOutOfRange = service.reset(channel, 10_000, LocalDate.of(3000, 8, 7))

        for (result in listOf(notHundreds, tooLarge, yearOutOfRange)) {
            result as ServiceReminderResetResult.Blocked
            assertEquals(
                dev.resetlight.domain.UiMessage.SERVICE_RESET_REASON_INVALID_INPUT,
                result.reason.key,
            )
        }
        assertEquals(emptyList<String>(), channel.sent)
    }

    @Test
    fun `replays the reset against live framed instrument responses`() = runTest {
        // The same handshake with the 704-prefixed framing the 2026-08-12 trip
        // showed the adapter actually returns (ATH1 on).
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "704DE303433FFFFFFFF",
                "0D01" to "7048D0100AE9C000000",
                "3364" to "704B364000000000000",
                "5C1B0807016E0000" to "704DC1B0807016E0000",
            ),
        )

        val result = ServiceReminderResetService(
            ecu.instrumentReadOnlyCapture,
            ecu.serviceReminder,
            ClusterFingerprintGate(ecu),
            ecu.motorcycleId,
            extractor = dev.resetlight.diagnostics.CanResponseExtractor("0x704", isoTp = false),
        ).reset(channel, 10_000, LocalDate.of(2027, 8, 7))

        result as ServiceReminderResetResult.Committed
        assertEquals(44700, result.odometerKm)
        assertEquals(listOf("5E01", "0D01", "3364", "5C1B0807016E0000"), channel.dataRequests)
    }

    @Test
    fun `replays the 2026-08-13 km-mode reset that committed on hardware`() = runTest {
        // Journal session-1786622057643, dashboard in km: odometer 0xAED4 (44756),
        // interval 7800 km (334E), next service 2027-08-13. 704-framed responses.
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "704DE303433FFFFFFFF",
                "0D01" to "7048D0100AED4000000",
                "334E" to "704B34E000000000000",
                "5C1B080D016E0000" to "704DC1B080D016E0000",
            ),
        )

        val result = framedService().reset(channel, 7_800, LocalDate.of(2027, 8, 13))

        result as ServiceReminderResetResult.Committed
        assertEquals(44756, result.odometerKm)
        assertEquals(listOf("5E01", "0D01", "334E", "5C1B080D016E0000"), channel.dataRequests)
    }

    @Test
    fun `replays the captured 2026-08-22 miles reset`() = runTest {
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "704DE303433FFFFFFFF",
                "0D01" to "7048D0100AED4000000",
                "343C" to "704B43C000000000000",
                "5C1B0816016E0000" to "704DC1B0816016E0000",
            ),
        )

        val result = framedService().reset(
            channel,
            6_000,
            DistanceUnit.MILES,
            LocalDate.of(2027, 8, 22),
        )

        result as ServiceReminderResetResult.Committed
        assertEquals(DistanceUnit.MILES, result.distanceUnit)
        assertEquals(6_000, result.distance)
        assertEquals(listOf("5E01", "0D01", "343C", "5C1B0816016E0000"), channel.dataRequests)
    }

    @Test
    fun `blocks the 2026-08-13 miles-mode reset the cluster rejected and never sends the date`() = runTest {
        // Same bike/odometer/interval as above, dashboard switched to miles. Reads
        // are byte-identical to km mode, but the identical 334E write is rejected:
        // B3 (positive service byte) followed by all-FF instead of the 4E echo.
        val channel = ScriptedChannel(
            configResponses() + mapOf(
                "5E01" to "704DE303433FFFFFFFF",
                "0D01" to "7048D0100AED4000000",
                "334E" to "704B3FFFFFFFFFFFFFF",
            ),
        )

        val result = framedService().reset(channel, 7_800, LocalDate.of(2027, 8, 13))

        result as ServiceReminderResetResult.Blocked
        assertEquals(
            dev.resetlight.domain.UiMessage.SERVICE_RESET_REASON_DISTANCE_REJECTED,
            result.reason.key,
        )
        assertTrue(channel.dataRequests.none { it.startsWith("5C") })
    }

    @Test
    fun `surfaces a transport failure as a typed failure`() = runTest {
        val channel = DiagnosticWriteChannel { _, _ -> throw IOException("dropped") }

        assertThrows(ServiceReminderResetFailure::class.java) {
            kotlinx.coroutines.runBlocking { service.reset(channel, 10_000, LocalDate.of(2027, 8, 7)) }
        }
    }

    private fun framedService(): ServiceReminderResetService =
        ServiceReminderResetService(
            ecu.instrumentReadOnlyCapture,
            ecu.serviceReminder,
            ClusterFingerprintGate(ecu),
            ecu.motorcycleId,
            extractor = dev.resetlight.diagnostics.CanResponseExtractor("0x704", isoTp = false),
        )

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
