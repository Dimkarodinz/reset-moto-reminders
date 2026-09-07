package dev.resetlight.features.service

import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.profiles.InstrumentFamilyProfile
import dev.resetlight.profiles.InstrumentFamilyProfileLoader
import dev.resetlight.profiles.ServiceReminderStrategy
import dev.resetlight.profiles.securityKeyFor
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedServiceReminderResetServiceTest {
    @Test
    fun `AES security derivation encrypts the full seed and returns the first four bytes`() {
        val derived = InstrumentSecurityKeyDerivation().derive(
            seedHex = "00112233445566778899AABBCCDDEEFF",
            aesKeyHex = "000102030405060708090A0B0C0D0E0F",
        )

        assertEquals("69C4E0D8", derived)
    }

    @Test
    fun `security variant follows known timing suffix and otherwise uses the production fallback`() {
        val security = checkNotNull(updated().combinedWrite)

        assertEquals("timing_3201f4", security.securityKeyFor("5003003201F4").id)
        assertEquals("timing_3200cb", security.securityKeyFor("5003003200CB").id)
        assertEquals("default", security.securityKeyFor("5003006401F4").id)
        assertThrows(IllegalArgumentException::class.java) { security.securityKeyFor("7F1022") }
    }

    @Test
    fun `updated payload preserves live fields and writes absolute next-service odometer`() {
        val profile = checkNotNull(updated().combinedWrite)
        val payload = CombinedServiceReminderPayloadBuilder(profile).build(
            strategy = updated().strategy,
            intervalKm = 10_000,
            nextServiceDate = LocalDate.of(2027, 8, 7),
            a500Payload = "62A50000AE76",
            a000Payload = "62A0000102030405060708090A0B0C",
        )

        assertEquals(44_662, payload.odometerKm)
        assertEquals(54_662, payload.nextServiceOdometerKm)
        assertEquals(ServiceReminderStrategy.UPDATED_COMBINED, payload.resolvedStrategy)
        assertEquals(
            listOf("100F2EA00000AE76", "2100D5860102031B", "2208070000000000"),
            payload.frames,
        )
    }

    @Test
    fun `hybrid payload uses the distinct 25 km encoding`() {
        val family = hybrid()
        val profile = checkNotNull(family.combinedWrite)
        val payload = CombinedServiceReminderPayloadBuilder(profile).build(
            strategy = family.strategy,
            intervalKm = 10_000,
            nextServiceDate = LocalDate.of(2027, 8, 7),
            a500Payload = "62A50000AE76",
            a000Payload = "62A0000102030405",
        )

        assertEquals(
            listOf("10082EA000088A1B", "2108070000000000"),
            payload.frames,
        )
        assertEquals(ServiceReminderStrategy.HYBRID_COMBINED, payload.resolvedStrategy)
    }

    @Test
    fun `adaptive payload selects updated encoding from twelve byte A000 data`() {
        val family = adaptive()
        val payload = CombinedServiceReminderPayloadBuilder(checkNotNull(family.combinedWrite)).build(
            strategy = family.strategy,
            intervalKm = 10_000,
            nextServiceDate = LocalDate.of(2027, 8, 7),
            a500Payload = "62A50000AE76",
            a000Payload = "62A0000102030405060708090A0B0C",
        )

        assertEquals(ServiceReminderStrategy.UPDATED_COMBINED, payload.resolvedStrategy)
        assertEquals(
            listOf("100F2EA00000AE76", "2100D5860102031B", "2208070000000000"),
            payload.frames,
        )
    }

    @Test
    fun `adaptive payload selects hybrid encoding from five byte A000 data`() {
        val family = adaptive()
        val payload = CombinedServiceReminderPayloadBuilder(checkNotNull(family.combinedWrite)).build(
            strategy = family.strategy,
            intervalKm = 10_000,
            nextServiceDate = LocalDate.of(2027, 8, 7),
            a500Payload = "62A50000AE76",
            a000Payload = "62A0000102030405",
        )

        assertEquals(ServiceReminderStrategy.HYBRID_COMBINED, payload.resolvedStrategy)
        assertEquals(listOf("10082EA000088A1B", "2108070000000000"), payload.frames)
    }

    @Test
    fun `adaptive reset rejects an unknown A000 shape before any write`() = runTest {
        val family = adaptive()
        val profile = checkNotNull(family.combinedWrite)
        val channel = QueueChannel(
            configResponses(family) + mapOf(
                profile.sessionRequest to listOf("5003003201F4"),
                profile.seedRequest to listOf("670100112233445566778899AABBCCDDEEFF"),
                "062702E91266B2" to listOf("6702"),
                "0322A500" to listOf("62A50000AE76"),
                "0322A000" to listOf("62A000010203040506"),
                "0322A010" to listOf("62A0100102"),
            ),
        )

        val result = CombinedServiceReminderResetService(family).reset(
            channel,
            10_000,
            DistanceUnit.KILOMETERS,
            LocalDate.of(2027, 8, 7),
        )

        assertTrue(result is ServiceReminderResetResult.Blocked)
        assertTrue(channel.writes.isEmpty())
    }

    @Test
    fun `adaptive hybrid reset uses live selection through post-write verification`() = runTest {
        val family = adaptive()
        val profile = checkNotNull(family.combinedWrite)
        val channel = QueueChannel(
            configResponses(family) + mapOf(
                profile.sessionRequest to listOf("5003003201F4"),
                profile.seedRequest to listOf("670100112233445566778899AABBCCDDEEFF"),
                "062702E91266B2" to listOf("6702"),
                "0322A500" to listOf("62A50000AE76"),
                "0322A000" to listOf(
                    "62A0000102030405",
                    "62A000088A1B0807",
                ),
                "0322A010" to listOf("62A0100102", "62A0100102"),
                "10082EA000088A1B" to listOf("3000000000000000"),
                "2108070000000000" to listOf("6EA000"),
            ),
        )

        val result = CombinedServiceReminderResetService(family).reset(
            channel,
            10_001,
            DistanceUnit.KILOMETERS,
            LocalDate.of(2027, 8, 7),
        )

        assertTrue(result is ServiceReminderResetResult.Committed)
        assertEquals(listOf("10082EA000088A1B", "2108070000000000"), channel.writes)
    }

    @Test
    fun `updated reset performs one security attempt and confirms the combined write`() = runTest {
        val family = updated()
        val profile = checkNotNull(family.combinedWrite)
        val channel = QueueChannel(
            configResponses(family) + mapOf(
                profile.sessionRequest to listOf("5003003201F4"),
                profile.seedRequest to listOf("670100112233445566778899AABBCCDDEEFF"),
                "062702E91266B2" to listOf("6702"),
                "0322A500" to listOf("62A50000AE76"),
                "0322A000" to listOf(
                    "62A0000102030405060708090A0B0C",
                    "62A00000AE7600D5860708091B0807",
                ),
                "0322A010" to listOf("62A0100102", "62A0100102"),
                "100F2EA00000AE76" to listOf("3000000000000000"),
                "2100D5860102031B" to listOf(""),
                "2208070000000000" to listOf("6EA000"),
            ),
        )

        val result = CombinedServiceReminderResetService(family).reset(
            channel,
            10_000,
            DistanceUnit.KILOMETERS,
            LocalDate.of(2027, 8, 7),
        )

        result as ServiceReminderResetResult.Committed
        assertEquals(44_662, result.odometerKm)
        assertEquals(10_000, result.distance)
        assertEquals(3, channel.writes.size)
        assertEquals("100F2EA00000AE76", channel.writes.first())
    }

    @Test
    fun `updated reset accepts live 29 bit ISO-TP response framing`() = runTest {
        val family = updated()
        val profile = checkNotNull(family.combinedWrite)
        val header = "18DAF1C1"
        val channel = QueueChannel(
            configResponses(family) + mapOf(
                profile.sessionRequest to listOf("${header}065003003201F4AA"),
                profile.seedRequest to listOf(
                    "${header}1012670100112233\n${header}21445566778899AA\n${header}22BBCCDDEEFFAAAA",
                ),
                "062702E91266B2" to listOf("${header}026702AAAAAAAAAA"),
                "0322A500" to listOf("${header}0662A50000AE76AA"),
                "0322A000" to listOf(
                    "${header}100F62A000010203\n${header}210405060708090A\n${header}220B0CAAAAAAAAAA",
                    "${header}100F62A00000AE76\n${header}2100D5860708091B\n${header}220807AAAAAAAAAA",
                ),
                "0322A010" to listOf(
                    "${header}0562A0100102AAAA",
                    "${header}0562A0100102AAAA",
                ),
                "100F2EA00000AE76" to listOf("${header}300000AAAAAAAAAA"),
                "2100D5860102031B" to listOf(""),
                "2208070000000000" to listOf("${header}036EA000AAAAAAAA"),
            ),
        )

        val result = CombinedServiceReminderResetService(
            family,
            extractor = CanResponseExtractor("0x18DAF1C1", isoTp = true),
        ).reset(channel, 10_000, DistanceUnit.KILOMETERS, LocalDate.of(2027, 8, 7))

        assertTrue(result is ServiceReminderResetResult.Committed)
    }

    @Test
    fun `malformed precursor blocks before write`() = runTest {
        val family = updated()
        val profile = checkNotNull(family.combinedWrite)
        val channel = QueueChannel(
            configResponses(family) + mapOf(
                profile.sessionRequest to listOf("5003003201F4"),
                profile.seedRequest to listOf("670100112233445566778899AABBCCDDEEFF"),
                "062702E91266B2" to listOf("6702"),
                "0322A500" to listOf("62A50000AE76"),
                "0322A000" to listOf("62A00001"),
                "0322A010" to listOf("62A0100102"),
            ),
        )

        val result = CombinedServiceReminderResetService(family).reset(
            channel,
            10_000,
            DistanceUnit.KILOMETERS,
            LocalDate.of(2027, 8, 7),
        )

        assertTrue(result is ServiceReminderResetResult.Blocked)
        assertTrue(channel.writes.isEmpty())
    }

    @Test
    fun `transport failure after first frame is ambiguous and never retried`() = runTest {
        val family = updated()
        val profile = checkNotNull(family.combinedWrite)
        val channel = object : DiagnosticWriteChannel {
            private val reads = QueueChannel(
                configResponses(family) + mapOf(
                    profile.sessionRequest to listOf("5003003201F4"),
                    profile.seedRequest to listOf("670100112233445566778899AABBCCDDEEFF"),
                    "062702E91266B2" to listOf("6702"),
                    "0322A500" to listOf("62A50000AE76"),
                    "0322A000" to listOf("62A0000102030405060708090A0B0C"),
                    "0322A010" to listOf("62A0100102"),
                    "100F2EA00000AE76" to listOf("3000000000000000"),
                ),
            )
            val attempts = mutableListOf<String>()

            override suspend fun execute(request: String, intent: WriteIntent): String {
                if (intent == WriteIntent.WRITE) attempts += request
                if (request == "2100D5860102031B") error("link dropped")
                return reads.execute(request, intent)
            }
        }

        val failure = assertThrows(ServiceReminderResetFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                CombinedServiceReminderResetService(family).reset(
                    channel,
                    10_000,
                    DistanceUnit.KILOMETERS,
                    LocalDate.of(2027, 8, 7),
                )
            }
        }

        assertTrue(failure.writeStarted)
        assertEquals(listOf("100F2EA00000AE76", "2100D5860102031B"), channel.attempts)
    }

    @Test
    fun `lost post-write readback is still reported as an ambiguous write`() = runTest {
        val family = updated()
        val profile = checkNotNull(family.combinedWrite)
        val base = QueueChannel(
            configResponses(family) + mapOf(
                profile.sessionRequest to listOf("5003003201F4"),
                profile.seedRequest to listOf("670100112233445566778899AABBCCDDEEFF"),
                "062702E91266B2" to listOf("6702"),
                "0322A500" to listOf("62A50000AE76"),
                "0322A000" to listOf("62A0000102030405060708090A0B0C"),
                "0322A010" to listOf("62A0100102"),
                "100F2EA00000AE76" to listOf("3000000000000000"),
                "2100D5860102031B" to listOf(""),
                "2208070000000000" to listOf("6EA000"),
            ),
        )
        var a000Reads = 0
        val channel = DiagnosticWriteChannel { request, intent ->
            if (request == "0322A000" && ++a000Reads == 2) error("readback lost")
            base.execute(request, intent)
        }

        val failure = assertThrows(ServiceReminderResetFailure::class.java) {
            kotlinx.coroutines.runBlocking {
                CombinedServiceReminderResetService(family).reset(
                    channel,
                    10_000,
                    DistanceUnit.KILOMETERS,
                    LocalDate.of(2027, 8, 7),
                )
            }
        }

        assertTrue(failure.writeStarted)
        assertEquals("0322A000", failure.request)
    }

    private fun updated() = family("triumph-updated-tft.instrumentfamily.yaml")
    private fun hybrid() = family("triumph-hybrid-display.instrumentfamily.yaml")
    private fun adaptive() = family("triumph-adaptive-combined.instrumentfamily.yaml")

    private fun family(name: String): InstrumentFamilyProfile = InstrumentFamilyProfileLoader().load(
        File("build/generated/profileAssets/profiles/$name").readBytes(),
    )

    private fun configResponses(family: InstrumentFamilyProfile): Map<String, List<String>> =
        family.module.transport.observedElmAdapterConfiguration.associateWith {
            listOf(if (it == "ATWS") "ELM327 v2.2" else "OK")
        }

    private class QueueChannel(responses: Map<String, List<String>>) : DiagnosticWriteChannel {
        private val remaining = responses.mapValues { (_, values) -> ArrayDeque(values) }
        val writes = mutableListOf<String>()

        override suspend fun execute(request: String, intent: WriteIntent): String {
            if (intent == WriteIntent.WRITE) writes += request
            return remaining[request]?.removeFirstOrNull() ?: error("Unexpected request $request")
        }
    }
}
