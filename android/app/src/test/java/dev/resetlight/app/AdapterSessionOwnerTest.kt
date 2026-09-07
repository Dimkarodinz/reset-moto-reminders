package dev.resetlight.app

import dev.resetlight.adapter.elm.ElmCodec
import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.logging.EventJournal
import dev.resetlight.logging.JournalSink
import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.profiles.DtcMapLoader
import dev.resetlight.profiles.EcuProfileLoader
import dev.resetlight.profiles.EngineFamilyProfileLoader
import dev.resetlight.profiles.InstrumentFamilyProfileLoader
import dev.resetlight.profiles.MotorcycleProfileCatalogLoader
import dev.resetlight.features.dtc.DtcReadState
import dev.resetlight.features.dtc.DtcClearUiState
import dev.resetlight.features.research.InstrumentReadState
import dev.resetlight.features.research.ReadOnlyCaptureState
import dev.resetlight.features.service.ServiceResetUiState
import dev.resetlight.transport.ByteTransport
import dev.resetlight.transport.ReplayByteTransport
import dev.resetlight.transport.ReplayExchange
import dev.resetlight.transport.ReplayInbound
import dev.resetlight.transport.bluetooth.BluetoothFacade
import dev.resetlight.transport.bluetooth.BleAdapterFacade
import dev.resetlight.transport.bluetooth.BleDiscoveryProfile
import dev.resetlight.transport.bluetooth.BleScanResult
import dev.resetlight.transport.bluetooth.BondedDevice
import dev.resetlight.transport.bluetooth.RfcommSocketConnection
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdapterSessionOwnerTest {
    @Test
    fun `updated and hybrid profiles expose experimental service reset`() = runTest {
        val catalog = motorcycleCatalog()
        val owner = AdapterSessionOwner(
            adapterProfile("vlinker-mc-android.adaptermap.yaml"),
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            motorcycleCatalog = catalog,
            writesEnabled = true,
        )

        assertTrue(owner.selectMotorcycle("triumph-tiger-900-gen2"))
        assertTrue(owner.serviceResetAvailable)
        assertEquals(1, owner.serviceIntervalConstraints?.stepKm)

        assertTrue(owner.selectMotorcycle("triumph-tiger-sport-660"))
        assertTrue(owner.serviceResetAvailable)
        assertEquals(25, owner.serviceIntervalConstraints?.stepKm)

        assertTrue(owner.selectMotorcycle("triumph-street-triple-765-modern"))
        assertTrue(owner.serviceResetAvailable)
        assertEquals(1, owner.serviceIntervalConstraints?.stepKm)
    }

    @Test
    fun `motorcycle profile changes only while disconnected`() = runTest {
        val adapter = adapterProfile("vlinker-mc-android.adaptermap.yaml")
        val catalog = motorcycleCatalog()
        val replay = ReplayByteTransport(
            listOf(
                exchange("ATWS", "ELM327 v2.2\r>"),
                exchange("ATE0", "OK\r>"),
                exchange("ATL0", "OK\r>"),
                exchange("ATS0", "OK\r>"),
                exchange("STI", "STN1151 v4.3.2\r>"),
                exchange("ATH1", "OK\r>"),
            ),
        )
        val owner = AdapterSessionOwner(
            adapter,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            motorcycleCatalog = catalog,
        ) { _, _ -> replay }

        assertEquals(catalog.defaultProfileId, owner.selectedMotorcycle.value?.id)
        assertTrue(owner.selectMotorcycle("triumph-street-triple-765-modern"))
        assertEquals("triumph-street-triple-765-modern", owner.selectedMotorcycle.value?.id)

        owner.connect("synthetic-address")
        advanceUntilIdle()

        assertTrue(owner.state.value is ConnectionState.AdapterReady)
        assertEquals(false, owner.selectMotorcycle(catalog.defaultProfileId))
        assertEquals("triumph-street-triple-765-modern", owner.selectedMotorcycle.value?.id)
    }

    @Test
    fun `experimental modern profile uses direct DTC clear without inherited security`() = runTest {
        val adapter = adapterProfile("vlinker-mc-android.adaptermap.yaml")
        val catalog = motorcycleCatalog()
        val modern = catalog.profiles.single { it.id == "triumph-street-triple-765-modern" }
        val adapterExchanges = listOf(
            exchange("ATWS", "ELM327 v2.2\r>"),
            exchange("ATE0", "OK\r>"),
            exchange("ATL0", "OK\r>"),
            exchange("ATS0", "OK\r>"),
            exchange("STI", "STN1151 v4.3.2\r>"),
            exchange("ATH1", "OK\r>"),
        )
        val configuration = modern.engineFamily.readOnlyCapture.configurationCommands
        val replay = ReplayByteTransport(
            adapterExchanges +
                configuration.map { command ->
                    exchange(command, if (command == "ATWS") "ELM327 v2.2\r>" else "OK\r>")
                } +
                exchange(modern.engineFamily.identityRequest, "18DAF1D50862F18C0102030405\r>") +
                exchange(modern.engineFamily.dtcClear.elmRequest, "18DAF1D50154AAAAAAAAAAAA\r>") +
                exchange(
                    modern.engineFamily.dtcClear.verificationElmRequest,
                    "18DAF1D50659010C000000AA\r>",
                ),
        )
        val dictionary = DtcMapLoader().load(
            File("build/generated/profileAssets/profiles/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml").readBytes(),
        )
        val owner = AdapterSessionOwner(
            adapter,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            motorcycleCatalog = catalog,
            dtcDescriptions = dictionary,
            writesEnabled = true,
        ) { _, _ -> replay }

        assertTrue(owner.selectMotorcycle(modern.id))
        owner.connect("synthetic-address")
        advanceUntilIdle()
        owner.clearDiagnosticTroubleCodes()
        advanceUntilIdle()

        assertEquals(DtcClearUiState.Cleared(0), owner.dtcClearState.value)
        replay.assertConsumed()
    }

    @Test
    fun `experimental first generation Tiger reuses original TFT service family`() = runTest {
        val adapter = adapterProfile("vlinker-mc-android.adaptermap.yaml")
        val catalog = motorcycleCatalog()
        val motorcycle = catalog.profiles.single { it.id == "triumph-tiger-900-gen1-other" }
        val instrument = motorcycle.instrumentFamily ?: error("instrument family missing")
        val service = instrument.originalSplit ?: error("service profile missing")
        val adapterExchanges = listOf(
            exchange("ATWS", "ELM327 v2.2\r>"),
            exchange("ATE0", "OK\r>"),
            exchange("ATL0", "OK\r>"),
            exchange("ATS0", "OK\r>"),
            exchange("STI", "STN1151 v4.3.2\r>"),
            exchange("ATH1", "OK\r>"),
        )
        val replay = ReplayByteTransport(
            adapterExchanges +
                instrument.readOnlyCapture!!.configurationCommands.map { command ->
                    exchange(command, if (command == "ATWS") "ELM327 v2.2\r>" else "OK\r>")
                } +
                exchange(service.initializeRequest, "704DE303433FFFFFFFF\r>") +
                exchange(service.odometerRequest, "7048D0100AE76000000\r>") +
                exchange("3364", "704B364000000000000\r>") +
                exchange("5C1B0807016E0000", "704DC1B0807016E0000\r>"),
        )
        val owner = AdapterSessionOwner(
            adapter,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            motorcycleCatalog = catalog,
            writesEnabled = true,
        ) { _, _ -> replay }

        assertTrue(owner.selectMotorcycle(motorcycle.id))
        assertTrue(owner.serviceResetAvailable)
        owner.connect("synthetic-address")
        advanceUntilIdle()
        owner.resetServiceReminder(10_000, DistanceUnit.KILOMETERS, LocalDate.of(2027, 8, 7))
        advanceUntilIdle()

        val committed = owner.serviceResetState.value as ServiceResetUiState.Committed
        assertEquals(44_662, committed.odometerKm)
        assertEquals(10_000, committed.distance)
        replay.assertConsumed()
    }

    @Test
    fun `classic registry keeps every product profile separate`() = runTest {
        val vlinker = adapterProfile("vlinker-mc-android.adaptermap.yaml")
        val mx = adapterProfile("obdlink-mx-android.adaptermap.yaml")
        val lx = adapterProfile("obdlink-lx-android.adaptermap.yaml")
        val mxPlus = adapterProfile("obdlink-mx-plus-android.adaptermap.yaml")
        val bluetooth = FakeBluetooth(
            listOf(
                BondedDevice("V", "vLinker MC-Android"),
                BondedDevice("L", "OBDLink LX"),
                BondedDevice("M", "OBDLink MX"),
                BondedDevice("P", "OBDLink MX+"),
                BondedDevice("X", "OBDLink CX"),
            ),
        )
        val owner = AdapterSessionOwner(
            vlinker,
            bluetooth,
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            additionalProfiles = listOf(mx, lx, mxPlus),
        )

        owner.refreshBondedDevices()

        assertEquals(
            mapOf(
                "L" to "obdlink-lx-android",
                "M" to "obdlink-mx-android",
                "P" to "obdlink-mx-plus-android",
                "V" to "vlinker-mc-android",
            ),
            owner.devices.value.associate { it.address to it.profileId },
        )
        assertEquals(
            mapOf("L" to true, "M" to true, "P" to true, "V" to false),
            owner.devices.value.associate { it.address to it.experimental },
        )
    }

    @Test
    fun `original MX reaches ready through its own identity profile`() = runTest {
        val vlinker = adapterProfile("vlinker-mc-android.adaptermap.yaml")
        val mx = adapterProfile("obdlink-mx-android.adaptermap.yaml")
        val replay = ReplayByteTransport(
            listOf(
                exchange("STDI", "OBDLink MX BT r1.0\r>"),
                exchange("ATE0", "OK\r>"),
                exchange("ATL0", "OK\r>"),
                exchange("ATS0", "OK\r>"),
                exchange("STI", "STN1151 v5.6.19\r>"),
                exchange("ATH1", "OK\r>"),
            ),
        )
        var openedProfileId: String? = null
        var openedServiceUuid: UUID? = null
        val owner = AdapterSessionOwner(
            vlinker,
            FakeBluetooth(listOf(BondedDevice("M", "OBDLink MX"))),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            additionalProfiles = listOf(mx),
            transportFactory = { _, selectedProfile ->
                openedProfileId = selectedProfile.id
                openedServiceUuid = selectedProfile.transport.sppServiceUuid
                replay
            },
        )

        owner.refreshBondedDevices()
        owner.connect("M")
        advanceUntilIdle()

        val ready = owner.state.value as ConnectionState.AdapterReady
        assertEquals("obdlink-mx-android", ready.mapId)
        assertEquals("obdlink-mx-android", openedProfileId)
        assertEquals(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"), openedServiceUuid)
        replay.assertConsumed()
    }

    @Test
    fun `documented CX profile reaches ready through the shared ELM session`() = runTest {
        val vlinker = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        val cx = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/obdlink-cx.adaptermap.yaml").readBytes(),
        )
        val replay = ReplayByteTransport(
            listOf(
                exchange("ATI", "OBDLink CX\r>"),
                exchange("ATE0", "OK\r>"),
                exchange("ATL0", "OK\r>"),
                exchange("ATS0", "OK\r>"),
                exchange("STI", "STN2230 v5.6.19\r>"),
                exchange("ATH1", "OK\r>"),
            ),
        )
        val ble = FakeBleAdapter(replay)
        val owner = AdapterSessionOwner(
            vlinker,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            additionalProfiles = listOf(cx),
            bleAdapter = ble,
        )

        owner.refreshBondedDevices()
        advanceUntilIdle()
        val device = owner.devices.value.single()
        owner.connect(device.address)
        advanceUntilIdle()

        val ready = owner.state.value as ConnectionState.AdapterReady
        assertEquals("obdlink-cx", ready.mapId)
        assertEquals("synthetic-cx", ble.connectedAddress)
        replay.assertConsumed()
    }

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
        val owner = AdapterSessionOwner(profile, FakeBluetooth(), EventJournal(backgroundScope, sink, FixedClock()), this) { _, _ -> replay }

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
        ) { _, _ -> replay }

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

    @Test
    fun `dtc read NO DATA fails the operation but keeps the adapter session alive`() = runTest {
        // Trip 2026-08-12: NO DATA on the DTC read raised a parse exception that
        // tore the whole vLink session down. The adapter link is healthy in that
        // situation, so only the operation may fail.
        val owner = readyOwnerWithDtcRead(
            dtcExchanges = listOf(exchange("03190108", "NO DATA\r>")),
            engineResponseCanId = "0x18DAF1D5",
        )

        owner.readDiagnosticTroubleCodes()
        advanceUntilIdle()

        val failed = owner.dtcReadState.value as DtcReadState.Failed
        assertEquals(dev.resetlight.domain.UiMessage.ECU_NO_RESPONSE, failed.reason.key)
        assertTrue(owner.state.value is ConnectionState.AdapterReady)
    }

    @Test
    fun `dtc read transport failure still tears the session down`() = runTest {
        val owner = readyOwnerWithDtcRead(
            dtcExchanges = listOf(
                ReplayExchange(
                    ElmCodec.encode("03190108"),
                    listOf(ReplayInbound.Failure(java.io.IOException("dropped"))),
                ),
            ),
            engineResponseCanId = "0x18DAF1D5",
        )

        owner.readDiagnosticTroubleCodes()
        advanceUntilIdle()

        assertTrue(owner.dtcReadState.value is DtcReadState.Failed)
        assertTrue(owner.state.value is ConnectionState.Failed)
    }

    @Test
    fun `one whole operation blocks another operation and disconnect until it finishes`() = runTest {
        val adapterProfile = AdapterProfileLoader().load(
            File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes(),
        )
        val ecuProfile = EcuProfileLoader().load(
            File("build/generated/profileAssets/profiles/tiger-900-gt-pro-2021.ecumap.yaml").readBytes(),
        )
        val descriptions = DtcMapLoader().load(
            File("build/generated/profileAssets/profiles/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml").readBytes(),
        )
        val replay = ReplayByteTransport(
            adapterReadyExchanges() + exchange(
                ecuProfile.diagnosticTroubleCodes.read.countElmRequest,
                "59010C000000\r>",
            ),
        )
        val blocking = BlockingResponseTransport(
            replay,
            ElmCodec.encode(ecuProfile.diagnosticTroubleCodes.read.countElmRequest),
        )
        val owner = AdapterSessionOwner(
            adapterProfile,
            FakeBluetooth(),
            EventJournal(backgroundScope, MemorySink(), FixedClock()),
            this,
            instrumentReadOnlyCaptureProfile = ecuProfile.instrumentReadOnlyCapture,
            dtcReadProfile = ecuProfile.diagnosticTroubleCodes.read,
            dtcDescriptions = descriptions,
        ) { _, _ -> blocking }
        owner.connect("synthetic-address")
        advanceUntilIdle()

        owner.readDiagnosticTroubleCodes()
        blocking.commandStarted.await()
        runCurrent()
        assertTrue(owner.operationInProgress.value)

        owner.readInstrumentServiceInfo()
        owner.disconnect()
        runCurrent()

        assertTrue(owner.instrumentReadState.value is InstrumentReadState.Idle)
        assertTrue(owner.state.value is ConnectionState.AdapterReady)

        blocking.releaseResponse.complete(Unit)
        advanceUntilIdle()

        assertTrue(owner.dtcReadState.value is DtcReadState.Complete)
        assertTrue(owner.instrumentReadState.value is InstrumentReadState.Idle)
        assertEquals(false, owner.operationInProgress.value)
        replay.assertConsumed()
    }

    private fun TestScope.readyOwnerWithDtcRead(
        dtcExchanges: List<ReplayExchange>,
        engineResponseCanId: String? = null,
    ): AdapterSessionOwner {
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
            engineResponseCanId = engineResponseCanId,
        ) { _, _ -> replay }
        owner.connect("synthetic-address")
        advanceUntilIdle()
        return owner
    }

    private fun exchange(command: String, response: String) = ReplayExchange(
        ElmCodec.encode(command),
        listOf(ReplayInbound.Bytes(response.encodeToByteArray())),
    )

    private fun adapterProfile(name: String) = AdapterProfileLoader().load(
        File("build/generated/profileAssets/profiles/$name").readBytes(),
    )

    private fun motorcycleCatalog() = run {
        val root = File("build/generated/profileAssets/profiles")
        val engine = EngineFamilyProfileLoader().load(File(root, "triumph-modern-can.enginefamily.yaml").readBytes())
        val instruments = listOf(
            "triumph-original-tft.instrumentfamily.yaml",
            "triumph-updated-tft.instrumentfamily.yaml",
            "triumph-hybrid-display.instrumentfamily.yaml",
            "triumph-adaptive-combined.instrumentfamily.yaml",
        ).associate { name ->
            InstrumentFamilyProfileLoader().load(File(root, name).readBytes()).let { it.id to it }
        }
        MotorcycleProfileCatalogLoader().load(
            File(root, "triumph.motorcycleprofiles.yaml").readBytes(),
            mapOf(engine.id to engine),
            instruments,
        )
    }

    private fun adapterReadyExchanges(): List<ReplayExchange> = listOf(
        exchange("ATWS", "ATWS\r\rELM327 v2.2\r>"),
        exchange("ATE0", "ATE0\rOK\r>"),
        exchange("ATL0", "OK\r>"),
        exchange("ATS0", "OK\r>"),
        exchange("STI", "STN1151 v4.3.2\r>"),
        exchange("ATH1", "OK\r>"),
    )

    private class BlockingResponseTransport(
        private val delegate: ByteTransport,
        private val blockedCommand: ByteArray,
    ) : ByteTransport {
        val commandStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        private var blockNextRead = false

        override suspend fun connect() = delegate.connect()

        override suspend fun write(bytes: ByteArray) {
            delegate.write(bytes)
            if (bytes.contentEquals(blockedCommand)) {
                blockNextRead = true
                commandStarted.complete(Unit)
            }
        }

        override suspend fun read(): ByteArray? {
            if (blockNextRead) {
                releaseResponse.await()
                blockNextRead = false
            }
            return delegate.read()
        }

        override suspend fun close() = delegate.close()
    }

    private class FakeBluetooth(private val devices: List<BondedDevice> = emptyList()) : BluetoothFacade {
        override fun bondedDevices(): Collection<BondedDevice> = devices
        override fun cancelDiscovery() = Unit
        override fun createRfcommSocket(address: String, serviceUuid: UUID): RfcommSocketConnection = error("unused")
    }

    private class FakeBleAdapter(private val transport: ByteTransport) : BleAdapterFacade {
        var connectedAddress: String? = null

        override suspend fun scan(profiles: Collection<BleDiscoveryProfile>): List<BleScanResult> = listOf(
            BleScanResult("synthetic-cx", "OBDLink CX", profiles.single().profileId),
        )

        override fun createGattTransport(
            address: String,
            profile: dev.resetlight.profiles.AdapterProfile,
        ): ByteTransport {
            connectedAddress = address
            return transport
        }
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
