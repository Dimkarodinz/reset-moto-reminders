package dev.resetlight.app

import dev.resetlight.domain.ConnectionState
import dev.resetlight.features.research.ReadOnlyCaptureState
import dev.resetlight.logging.EventJournal
import dev.resetlight.logging.JournalSink
import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.profiles.EcuProfileLoader
import dev.resetlight.profiles.YamlProfileDocument
import dev.resetlight.transport.ReplayByteTransport
import dev.resetlight.transport.ReplayExchange
import dev.resetlight.transport.ReplayInbound
import dev.resetlight.transport.bluetooth.BluetoothFacade
import dev.resetlight.transport.bluetooth.BondedDevice
import dev.resetlight.transport.bluetooth.RfcommSocketConnection
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays the sanitized byte-exact transcript of the first project-app
 * motorcycle capture (2026-08-10, Tiger 900 GT Pro, ignition on, engine off)
 * through the full session stack. This regression-locks the validated
 * hardware behavior: adapter initialization, engine transport configuration,
 * the six non-sensitive identifier reads and the default-session zero-DTC
 * count read, with no extended session and no detail read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotorcycleCaptureTranscriptTest {
    private data class TranscriptExchange(
        val command: String,
        val outboundHex: String,
        val inboundHex: String,
    )

    private val transcript = loadTranscript("motorcycle-2026-08-10-read-only-capture.yaml")

    @Test
    fun `captured motorcycle transcript replays to a zero-DTC default-session capture`() = runTest {
        val adapterProfile = AdapterProfileLoader().load(generatedProfile("vlinker-mc-android.adaptermap.yaml"))
        val ecuProfile = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
        val replay = ReplayByteTransport(
            transcript.map { exchange ->
                ReplayExchange(
                    exchange.outboundHex.hexToBytes(),
                    listOf(ReplayInbound.Bytes(exchange.inboundHex.hexToBytes())),
                )
            },
        )
        val owner = AdapterSessionOwner(
            adapterProfile,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            engineReadOnlyCaptureProfile = ecuProfile.engineReadOnlyCapture,
        ) { _, _ -> replay }

        owner.connect("synthetic-address")
        advanceUntilIdle()
        assertTrue(owner.state.value is ConnectionState.AdapterReady)

        owner.captureReadOnlyEngineData()
        advanceUntilIdle()

        val capture = owner.readOnlyCaptureState.value as ReadOnlyCaptureState.Complete
        assertEquals(0, capture.dtcCount)
        assertFalse(capture.extendedSessionUsed)
        replay.assertConsumed()
    }

    @Test
    fun `transcript stays within the read-only allowlist and matches the capture profile order`() {
        val ecuProfile = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
        val capture = ecuProfile.engineReadOnlyCapture
        val commands = transcript.map { it.command }

        commands.forEach { command ->
            assertFalse("SecurityAccess must not appear: $command", command.contains("2701") || command.contains("2702"))
            assertFalse("DTC clear must not appear: $command", command == "0414FFFFFF")
            assertFalse("Instrument writes must not appear: $command", command.startsWith("33") || command.startsWith("5C"))
            assertFalse("Sensitive identifier reads must not appear: $command", command == "0322F18C" || command == "0322F190")
        }

        val expectedCaptureSequence =
            capture.configurationCommands +
                capture.identifierReads.map { it.elmRequest } +
                capture.dtcCountElmRequest
        assertEquals(expectedCaptureSequence, commands.drop(ADAPTER_INITIALIZATION_EXCHANGES))
        assertFalse(commands.contains(capture.extendedSessionElmRequest))
        assertFalse(commands.contains(capture.dtcDetailElmRequest))
    }

    @Test
    fun `transcript responses match the map-observed identifier values and zero count`() {
        val responsesByCommand = transcript.associate { exchange ->
            exchange.command to exchange.inboundHex.hexToBytes().decodeToString()
        }
        mapOf(
            "0322F1A0" to "62F1A0000000",
            "0322F1A2" to "62F1A2000006",
            "0322F1AE" to "62F1AE0004",
            "0322F199" to "62F199250509",
            "0322F19B" to "62F19B250509",
            "0322F1A7" to "62F1A7202001",
            "03190108" to "59010C000000",
        ).forEach { (command, expectedPayload) ->
            val response = responsesByCommand.getValue(command)
            assertTrue(
                "$command response must contain $expectedPayload, got $response",
                response.contains(expectedPayload),
            )
            assertTrue(
                "$command response must come from the engine response CAN id",
                response.contains("18DAF1D5"),
            )
        }
    }

    private fun loadTranscript(name: String): List<TranscriptExchange> {
        val file = File("src/test/resources/transcripts/$name")
        check(file.isFile) { "Transcript fixture is missing: ${file.absolutePath}" }
        val document = YamlProfileDocument.parse(file.readBytes()).root
        return document.child("exchanges").list().map { node ->
            TranscriptExchange(
                command = node.child("command").string(),
                outboundHex = node.child("outbound_hex").string(),
                inboundHex = node.child("inbound_hex").string(),
            )
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Odd-length hex string" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private class FakeBluetooth : BluetoothFacade {
        override fun bondedDevices(): Collection<BondedDevice> = emptyList()
        override fun cancelDiscovery() = Unit
        override fun createRfcommSocket(address: String, serviceUuid: UUID): RfcommSocketConnection = error("unused")
    }

    private class MemorySink : JournalSink {
        override fun append(line: String) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }

    private class FixedClock : dev.resetlight.logging.JournalClock {
        override fun wallTime(): Instant = Instant.parse("2026-08-10T19:44:15Z")
        override fun elapsedMillis(): Long = 1
    }

    private companion object {
        const val ADAPTER_INITIALIZATION_EXCHANGES = 6
    }
}
