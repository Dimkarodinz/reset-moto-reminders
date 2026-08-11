package dev.resetlight.app

import dev.resetlight.adapter.elm.ElmCodec
import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState
import dev.resetlight.logging.EventJournal
import dev.resetlight.logging.JournalSink
import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.profiles.DtcMapLoader
import dev.resetlight.profiles.EcuProfileLoader
import dev.resetlight.features.dtc.DtcReadState
import dev.resetlight.features.research.ReadOnlyCaptureState
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdapterSessionOwnerTest {
    @Test
    fun `captured replay reaches adapter ready without vehicle commands`() = runTest {
        val profile = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        val replay = ReplayByteTransport(
            listOf(
                exchange("ATWS", "ATWS\r\rELM327 v2.2\r>"),
                exchange("ATE0", "ATE0\rOK\r>"),
                exchange("ATL0", "OK\r>"),
                exchange("ATS0", "OK\r>"),
                exchange("STI", "STN1151 v4.3.2\r>"),
                exchange("ATH1", "OK\r>"),
            ),
        )
        val sink = MemorySink()
        val owner = AdapterSessionOwner(profile, FakeBluetooth(), EventJournal(backgroundScope, sink, FixedClock()), this) { replay }

        owner.connect("synthetic-address")
        advanceUntilIdle()

        assertTrue(owner.state.value is ConnectionState.AdapterReady)
        assertEquals(profile.id, (owner.state.value as ConnectionState.AdapterReady).mapId)
        replay.assertConsumed()
    }

    @Test
    fun `connecting to an unbonded adapter fails as pairing required`() = runTest {
        val profile = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        // No transportFactory override: the real RfcommByteTransport runs against a
        // facade with no bonded devices, so it must fail with PAIRING_REQUIRED rather
        // than a generic IO error. This pins the classify() arm ordering, since
        // DevicePairingRequiredException is itself an IOException.
        val owner = AdapterSessionOwner(profile, FakeBluetooth(), EventJournal(backgroundScope, MemorySink(), FixedClock()), this)

        owner.connect("synthetic-address")
        advanceUntilIdle()

        val failed = owner.state.value as ConnectionState.Failed
        assertEquals(ConnectionFailure.PAIRING_REQUIRED, failed.reason)
    }

    @Test
    fun `bonded list excludes unrelated names and duplicates`() = runTest {
        val profile = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        val bluetooth = FakeBluetooth(
            listOf(
                BondedDevice("B", "vLinker MC-Android"),
                BondedDevice("A", "other"),
                BondedDevice("B", "vLinker MC-Android"),
            ),
        )
        val owner = AdapterSessionOwner(profile, bluetooth, EventJournal(backgroundScope, MemorySink(), FixedClock()), this)
        owner.refreshBondedDevices()
        assertEquals(listOf("B"), owner.devices.value.map { it.address })
    }

    @Test
    fun `one read-only capture runs after adapter readiness and records zero DTCs`() = runTest {
        val adapterProfile = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        val ecuProfile = EcuProfileLoader().load(
            File("build/generated/profileAssets/profiles/tiger-900-gt-pro-2021.ecumap.yaml").readBytes(),
        )
        val adapterExchanges = listOf(
            exchange("ATWS", "ATWS\r\rELM327 v2.2\r>"),
            exchange("ATE0", "ATE0\rOK\r>"),
            exchange("ATL0", "OK\r>"),
            exchange("ATS0", "OK\r>"),
            exchange("STI", "STN1151 v4.3.2\r>"),
            exchange("ATH1", "OK\r>"),
        )
        val configurationExchanges = ecuProfile.engineReadOnlyCapture.configurationCommands.map { command ->
            exchange(command, if (command == "ATWS") "ELM327 v2.2\r>" else "OK\r>")
        }
        val identifierExchanges = ecuProfile.engineReadOnlyCapture.identifierReads.map { identifier ->
            exchange(identifier.elmRequest, "NO DATA\r>")
        }
        val replay = ReplayByteTransport(
            adapterExchanges + configurationExchanges + identifierExchanges +
                exchange(ecuProfile.engineReadOnlyCapture.dtcCountElmRequest, "59010C000000\r>"),
        )
        val owner = AdapterSessionOwner(
            adapterProfile,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            engineReadOnlyCaptureProfile = ecuProfile.engineReadOnlyCapture,
        ) { replay }

        owner.connect("synthetic-address")
        advanceUntilIdle()
        owner.captureReadOnlyEngineData()
        advanceUntilIdle()

        val capture = owner.readOnlyCaptureState.value as ReadOnlyCaptureState.Complete
        assertEquals(0, capture.dtcCount)
        assertEquals(false, capture.extendedSessionUsed)
        replay.assertConsumed()

        owner.captureReadOnlyEngineData()
        advanceUntilIdle()
        assertTrue(owner.readOnlyCaptureState.value is ReadOnlyCaptureState.Complete)
    }

    @Test
    fun `dtc read reports zero confirmed codes in the default session`() = runTest {
        val owner = readyOwnerWithDtcRead(
            dtcExchanges = listOf(exchange("03190108", "59010C000000\r>")),
        )

        owner.readDiagnosticTroubleCodes()
        advanceUntilIdle()

        val result = owner.dtcReadState.value as DtcReadState.Complete
        assertEquals(0, result.reportedCount)
        assertTrue(result.dtcs.isEmpty())
    }

    @Test
    fun `dtc read decodes a confirmed code and resolves its description`() = runTest {
        val owner = readyOwnerWithDtcRead(
            dtcExchanges = listOf(
                exchange("03190108", "59010C000001\r>"),
                exchange("03190208", "59020C15770008\r>"),
            ),
        )

        owner.readDiagnosticTroubleCodes()
        advanceUntilIdle()

        val result = owner.dtcReadState.value as DtcReadState.Complete
        assertEquals(1, result.reportedCount)
        assertEquals("P1577-00", result.dtcs.single().displayCode)
    }

    private fun TestScope.readyOwnerWithDtcRead(dtcExchanges: List<ReplayExchange>): AdapterSessionOwner {
        val adapterProfile = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        val ecuProfile = EcuProfileLoader().load(
            File("build/generated/profileAssets/profiles/tiger-900-gt-pro-2021.ecumap.yaml").readBytes(),
        )
        val descriptions = DtcMapLoader().load(
            File("build/generated/profileAssets/profiles/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml").readBytes(),
        )
        val adapterExchanges = listOf(
            exchange("ATWS", "ATWS\r\rELM327 v2.2\r>"),
            exchange("ATE0", "ATE0\rOK\r>"),
            exchange("ATL0", "OK\r>"),
            exchange("ATS0", "OK\r>"),
            exchange("STI", "STN1151 v4.3.2\r>"),
            exchange("ATH1", "OK\r>"),
        )
        val replay = ReplayByteTransport(adapterExchanges + dtcExchanges)
        val owner = AdapterSessionOwner(
            adapterProfile,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            dtcReadProfile = ecuProfile.diagnosticTroubleCodes.read,
            dtcDescriptions = descriptions,
        ) { replay }
        owner.connect("synthetic-address")
        advanceUntilIdle()
        return owner
    }

    private fun exchange(command: String, response: String) = ReplayExchange(
        ElmCodec.encode(command),
        listOf(ReplayInbound.Bytes(response.encodeToByteArray())),
    )

    private class FakeBluetooth(private val devices: List<BondedDevice> = emptyList()) : BluetoothFacade {
        override fun bondedDevices(): Collection<BondedDevice> = devices
        override fun cancelDiscovery() = Unit
        override fun createRfcommSocket(address: String, serviceUuid: UUID): RfcommSocketConnection = error("unused")
    }

    private class MemorySink : JournalSink {
        val lines = mutableListOf<String>()
        override fun append(line: String) { lines += line }
        override fun flush() = Unit
        override fun close() = Unit
    }

    private class FixedClock : dev.resetlight.logging.JournalClock {
        override fun wallTime(): Instant = Instant.parse("2026-08-09T12:00:00Z")
        override fun elapsedMillis(): Long = 1
    }
}
