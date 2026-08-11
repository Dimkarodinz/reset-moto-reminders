package dev.resetlight.app

import dev.resetlight.adapter.elm.AdapterIdentityMismatch
import dev.resetlight.adapter.elm.AdapterInitializer
import dev.resetlight.adapter.elm.CommandFailure
import dev.resetlight.adapter.elm.ElmCommandSession
import dev.resetlight.adapter.elm.InitializationStage
import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState
import dev.resetlight.domain.ConnectionStateMachine
import dev.resetlight.logging.EventJournal
import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.EngineSeedKeyDerivation
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.features.dtc.DtcClearFailure
import dev.resetlight.features.dtc.DtcClearResult
import dev.resetlight.features.dtc.DtcClearService
import dev.resetlight.features.dtc.DtcClearUiState
import dev.resetlight.features.dtc.DtcReadFailure
import dev.resetlight.features.dtc.DtcReadState
import dev.resetlight.features.dtc.DtcReader
import dev.resetlight.features.research.InstrumentReadOnlyCapture
import dev.resetlight.features.research.InstrumentReadOnlyCaptureFailure
import dev.resetlight.features.research.InstrumentReadOnlyCaptureResult
import dev.resetlight.features.research.InstrumentReadState
import dev.resetlight.features.research.ReadOnlyCaptureState
import dev.resetlight.features.research.ReadOnlyEngineCapture
import dev.resetlight.features.research.ReadOnlyEngineCaptureFailure
import dev.resetlight.features.research.ReadOnlyEngineCaptureResult
import dev.resetlight.features.service.ClusterFingerprintGate
import dev.resetlight.features.service.ServiceReminderResetFailure
import dev.resetlight.features.service.ServiceReminderResetResult
import dev.resetlight.features.service.ServiceReminderResetService
import dev.resetlight.features.service.ServiceResetUiState
import dev.resetlight.profiles.DiagnosticTroubleCodeClearProfile
import dev.resetlight.profiles.DiagnosticTroubleCodeReadProfile
import dev.resetlight.profiles.DtcDescriptionLookup
import dev.resetlight.profiles.EngineReadOnlyCaptureProfile
import dev.resetlight.profiles.EngineSecurityAccessProfile
import dev.resetlight.profiles.InstrumentReadOnlyCaptureProfile
import dev.resetlight.profiles.ServiceReminderOperationProfile
import dev.resetlight.profiles.AdapterProfile
import dev.resetlight.adapter.elm.CommandIntent
import dev.resetlight.transport.ByteTransport
import java.time.LocalDate
import dev.resetlight.transport.bluetooth.BluetoothFacade
import dev.resetlight.transport.bluetooth.BondedDevice
import dev.resetlight.transport.bluetooth.BondedDeviceSelector
import dev.resetlight.transport.bluetooth.DevicePairingRequiredException
import dev.resetlight.transport.bluetooth.RfcommByteTransport
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KMutableProperty0
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdapterSessionOwner(
    val profile: AdapterProfile,
    private val bluetooth: BluetoothFacade,
    private val journal: EventJournal,
    private val scope: CoroutineScope,
    private val engineReadOnlyCaptureProfile: EngineReadOnlyCaptureProfile? = null,
    private val instrumentReadOnlyCaptureProfile: InstrumentReadOnlyCaptureProfile? = null,
    private val dtcReadProfile: DiagnosticTroubleCodeReadProfile? = null,
    private val dtcDescriptions: DtcDescriptionLookup? = null,
    private val dtcClearProfile: DiagnosticTroubleCodeClearProfile? = null,
    private val engineSecurityAccessProfile: EngineSecurityAccessProfile? = null,
    private val serviceReminderProfile: ServiceReminderOperationProfile? = null,
    private val clusterFingerprintGate: ClusterFingerprintGate? = null,
    private val motorcycleId: String? = null,
    private val writesEnabled: Boolean = false,
    private val transportFactory: (String) -> ByteTransport = { address ->
        RfcommByteTransport(bluetooth, address, profile.transport.sppServiceUuid)
    },
) {
    init {
        journal.record(
            layer = "profile",
            name = "adapter_profile_loaded",
            text = "id=${profile.id} schema=${profile.schemaVersion} sha256=${profile.sourceSha256}",
        )
    }

    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    private val mutableDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val devices: StateFlow<List<BondedDevice>> = mutableDevices.asStateFlow()

    private val activeTransport = AtomicReference<ByteTransport?>(null)
    private val activeSession = AtomicReference<ElmCommandSession?>(null)
    private var connectionJob: Job? = null
    private var readOnlyCaptureJob: Job? = null
    private var instrumentReadJob: Job? = null
    private var dtcReadJob: Job? = null
    private var dtcClearJob: Job? = null
    private var serviceResetJob: Job? = null

    private val mutableReadOnlyCaptureState = MutableStateFlow<ReadOnlyCaptureState>(ReadOnlyCaptureState.Idle)
    val readOnlyCaptureState: StateFlow<ReadOnlyCaptureState> = mutableReadOnlyCaptureState.asStateFlow()

    private val mutableInstrumentReadState = MutableStateFlow<InstrumentReadState>(InstrumentReadState.Idle)
    val instrumentReadState: StateFlow<InstrumentReadState> = mutableInstrumentReadState.asStateFlow()

    private val mutableDtcReadState = MutableStateFlow<DtcReadState>(DtcReadState.Idle)
    val dtcReadState: StateFlow<DtcReadState> = mutableDtcReadState.asStateFlow()

    private val mutableDtcClearState = MutableStateFlow<DtcClearUiState>(DtcClearUiState.Idle)
    val dtcClearState: StateFlow<DtcClearUiState> = mutableDtcClearState.asStateFlow()

    private val mutableServiceResetState = MutableStateFlow<ServiceResetUiState>(ServiceResetUiState.Idle)
    val serviceResetState: StateFlow<ServiceResetUiState> = mutableServiceResetState.asStateFlow()

    /** True only when the build packaged the write profiles (research build). */
    val writeOperationsAvailable: Boolean =
        writesEnabled &&
            dtcClearProfile != null &&
            engineSecurityAccessProfile != null &&
            serviceReminderProfile != null &&
            clusterFingerprintGate != null &&
            motorcycleId != null

    fun refreshBondedDevices() {
        mutableDevices.value = BondedDeviceSelector.candidates(
            bluetooth.bondedDevices(),
            profile.identity.bluetoothName.value,
        )
    }

    fun connect(address: String) {
        if (connectionJob?.isActive == true) return
        if (state.value is ConnectionState.Failed) transitionSafely(ConnectionState.Disconnected)
        if (state.value !is ConnectionState.Disconnected) return
        mutableReadOnlyCaptureState.value = ReadOnlyCaptureState.Idle
        mutableInstrumentReadState.value = InstrumentReadState.Idle
        mutableDtcReadState.value = DtcReadState.Idle
        mutableDtcClearState.value = DtcClearUiState.Idle
        mutableServiceResetState.value = ServiceResetUiState.Idle
        connectionJob = scope.launch { runConnection(address) }
    }

    fun disconnect() {
        if (state.value is ConnectionState.Disconnected) return
        connectionJob?.cancel()
        readOnlyCaptureJob?.cancel()
        instrumentReadJob?.cancel()
        dtcReadJob?.cancel()
        dtcClearJob?.cancel()
        serviceResetJob?.cancel()
        connectionJob = scope.launch {
            transitionSafely(ConnectionState.Disconnecting)
            activeSession.set(null)
            activeTransport.getAndSet(null)?.close()
            transitionSafely(ConnectionState.Disconnected)
        }
    }

    fun captureReadOnlyEngineData() {
        val captureProfile = engineReadOnlyCaptureProfile ?: return
        val session = activeSession.get() ?: return
        runGatedOperation(
            job = ::readOnlyCaptureJob,
            stateFlow = mutableReadOnlyCaptureState,
            // Run at most once per connection: any non-Idle state blocks a re-run.
            isBusy = { it !is ReadOnlyCaptureState.Idle },
            running = ReadOnlyCaptureState.Running,
            idle = ReadOnlyCaptureState.Idle,
            failed = ReadOnlyCaptureState.Failed(
                "Read-only capture stopped after a connection or adapter error.",
            ),
            startedEvent = "read_only_engine_capture_started",
            failedEvent = "read_only_engine_capture_failed",
        ) {
            val result = ReadOnlyEngineCapture(
                captureProfile,
                DiagnosticReadChannel { request -> session.execute(request).normalizedText },
            ).capture()
            mutableReadOnlyCaptureState.value = when (result) {
                is ReadOnlyEngineCaptureResult.Complete -> ReadOnlyCaptureState.Complete(
                    dtcCount = result.dtcCount,
                    extendedSessionUsed = result.extendedSessionUsed,
                    responseCount = result.responses.size,
                )
                is ReadOnlyEngineCaptureResult.Blocked -> ReadOnlyCaptureState.Blocked(result.reason)
            }
            journal.record(
                layer = "operation",
                name = "read_only_engine_capture_finished",
                text = when (result) {
                    is ReadOnlyEngineCaptureResult.Complete ->
                        "complete dtc_count=${result.dtcCount} extended_session=${result.extendedSessionUsed} responses=${result.responses.size}"
                    is ReadOnlyEngineCaptureResult.Blocked -> "blocked reason=${result.reason}"
                },
            )
        }
    }

    fun readDiagnosticTroubleCodes() {
        val readProfile = dtcReadProfile ?: return
        val descriptions = dtcDescriptions ?: return
        val session = activeSession.get() ?: return
        runGatedOperation(
            job = ::dtcReadJob,
            stateFlow = mutableDtcReadState,
            isBusy = { it is DtcReadState.Running },
            running = DtcReadState.Running,
            idle = DtcReadState.Idle,
            failed = DtcReadState.Failed(
                "Reading trouble codes stopped after a connection or adapter error.",
            ),
            startedEvent = "dtc_read_started",
            failedEvent = "dtc_read_failed",
            recover = { failure ->
                // The ECU reported an inconsistent count; the connection is still
                // usable, so surface the read failure without tearing it down.
                if (failure is DtcReadFailure.CountMismatch) {
                    mutableDtcReadState.value = DtcReadState.Failed(
                        "The ECU reported ${failure.reportedCount} codes but returned ${failure.decodedCount}. Try reading again.",
                    )
                    journal.record("operation", "dtc_read_failed", outcome = "count_mismatch")
                    true
                } else {
                    false
                }
            },
        ) {
            val result = DtcReader(
                readProfile,
                descriptions,
                DiagnosticReadChannel { request -> session.execute(request).normalizedText },
            ).read()
            mutableDtcReadState.value = DtcReadState.Complete(
                reportedCount = result.reportedCount,
                dtcs = result.dtcs,
            )
            journal.record(
                layer = "operation",
                name = "dtc_read_finished",
                text = "count=${result.reportedCount} decoded=${result.dtcs.size}",
            )
        }
    }

    fun readInstrumentServiceInfo() {
        val captureProfile = instrumentReadOnlyCaptureProfile ?: return
        val session = activeSession.get() ?: return
        runGatedOperation(
            job = ::instrumentReadJob,
            stateFlow = mutableInstrumentReadState,
            isBusy = { it is InstrumentReadState.Running },
            running = InstrumentReadState.Running,
            idle = InstrumentReadState.Idle,
            failed = InstrumentReadState.Failed(
                "Reading instrument data stopped after a connection or adapter error.",
            ),
            startedEvent = "instrument_read_started",
            failedEvent = "instrument_read_failed",
        ) {
            val result = InstrumentReadOnlyCapture(
                captureProfile,
                DiagnosticReadChannel { request -> session.execute(request).normalizedText },
            ).capture()
            mutableInstrumentReadState.value = when (result) {
                is InstrumentReadOnlyCaptureResult.Complete -> InstrumentReadState.Complete(
                    statusAscii = result.statusAscii,
                    odometerKm = result.odometerKm,
                    odometerRaw = result.odometerRaw,
                )
                is InstrumentReadOnlyCaptureResult.Blocked -> InstrumentReadState.Blocked(result.reason)
            }
            journal.record(
                layer = "operation",
                name = "instrument_read_finished",
                text = when (result) {
                    is InstrumentReadOnlyCaptureResult.Complete ->
                        "complete odometer_km=${result.odometerKm} responses=${result.responses.size}"
                    is InstrumentReadOnlyCaptureResult.Blocked -> "blocked reason=${result.reason}"
                },
            )
        }
    }

    /**
     * Clears confirmed engine DTCs. Available only in the research build: it runs
     * SecurityAccess and sends a write, so it is gated on [writeOperationsAvailable]
     * and only ever reached after the caller confirms the exact motorcycle.
     */
    fun clearDiagnosticTroubleCodes() {
        val clearProfile = dtcClearProfile ?: return
        val securityProfile = engineSecurityAccessProfile ?: return
        val session = activeSession.get() ?: return
        if (!writeOperationsAvailable) return
        runGatedOperation(
            job = ::dtcClearJob,
            stateFlow = mutableDtcClearState,
            isBusy = { it is DtcClearUiState.Running },
            running = DtcClearUiState.Running,
            idle = DtcClearUiState.Idle,
            failed = DtcClearUiState.Failed(
                "Clearing trouble codes stopped after a connection or adapter error.",
            ),
            startedEvent = "dtc_clear_started",
            failedEvent = "dtc_clear_failed",
        ) {
            val result = DtcClearService(
                clearProfile,
                securityProfile,
                EngineSeedKeyDerivation(securityProfile.seedKeyMultiplier),
                writeChannel(session),
            ).clear()
            mutableDtcClearState.value = when (result) {
                is DtcClearResult.Cleared -> DtcClearUiState.Cleared(result.remainingCount)
                is DtcClearResult.Blocked -> DtcClearUiState.Blocked(result.reason)
            }
            journal.record(
                layer = "operation",
                name = "dtc_clear_finished",
                text = when (result) {
                    is DtcClearResult.Cleared -> "cleared remaining=${result.remainingCount}"
                    is DtcClearResult.Blocked -> "blocked reason=${result.reason}"
                },
            )
        }
    }

    /**
     * Replays the observed service-reminder reset. Available only in the research
     * build; the [ClusterFingerprintGate] inside the service fails closed before
     * any write byte leaves the adapter.
     */
    fun resetServiceReminder(distanceKm: Int, nextServiceDate: LocalDate) {
        val instrumentProfile = instrumentReadOnlyCaptureProfile ?: return
        val serviceProfile = serviceReminderProfile ?: return
        val gate = clusterFingerprintGate ?: return
        val id = motorcycleId ?: return
        val session = activeSession.get() ?: return
        if (!writeOperationsAvailable) return
        runGatedOperation(
            job = ::serviceResetJob,
            stateFlow = mutableServiceResetState,
            isBusy = { it is ServiceResetUiState.Running },
            running = ServiceResetUiState.Running,
            idle = ServiceResetUiState.Idle,
            failed = ServiceResetUiState.Failed(
                "Resetting the service reminder stopped after a connection or adapter error.",
            ),
            startedEvent = "service_reset_started",
            failedEvent = "service_reset_failed",
        ) {
            val result = ServiceReminderResetService(
                instrumentProfile,
                serviceProfile,
                gate,
                id,
            ).reset(writeChannel(session), distanceKm, nextServiceDate)
            mutableServiceResetState.value = when (result) {
                is ServiceReminderResetResult.Committed -> ServiceResetUiState.Committed(
                    odometerKm = result.odometerKm,
                    distanceKm = result.distanceKm,
                    nextServiceDate = result.nextServiceDate,
                )
                is ServiceReminderResetResult.Blocked -> ServiceResetUiState.Blocked(result.reason)
            }
            journal.record(
                layer = "operation",
                name = "service_reset_finished",
                text = when (result) {
                    is ServiceReminderResetResult.Committed ->
                        "committed odometer_km=${result.odometerKm} distance_km=${result.distanceKm}"
                    is ServiceReminderResetResult.Blocked -> "blocked reason=${result.reason}"
                },
            )
        }
    }

    /**
     * Shared launcher for the gated operations. Enforces the common guards
     * (adapter must be ready, the operation must not already be busy or running),
     * moves the operation's state flow to [running], journals [startedEvent], and
     * runs [body] on the session scope. Cancellation returns to [idle] and
     * rethrows; any other error falls through [recover] first, and if that does
     * not consume it, moves to [failed], journals [failedEvent] and tears the
     * session down via [failSession]. [body] owns the terminal success/blocked
     * state and its own "finished" journal entry.
     */
    private fun <T> runGatedOperation(
        job: KMutableProperty0<Job?>,
        stateFlow: MutableStateFlow<T>,
        isBusy: (T) -> Boolean,
        running: T,
        idle: T,
        failed: T,
        startedEvent: String,
        failedEvent: String,
        recover: (suspend (Throwable) -> Boolean)? = null,
        body: suspend () -> Unit,
    ) {
        if (state.value !is ConnectionState.AdapterReady) return
        if (isBusy(stateFlow.value)) return
        if (job.get()?.isActive == true) return

        stateFlow.value = running
        journal.record("operation", startedEvent)
        job.set(
            scope.launch {
                try {
                    body()
                } catch (cancelled: CancellationException) {
                    stateFlow.value = idle
                    throw cancelled
                } catch (failure: Throwable) {
                    if (recover?.invoke(failure) == true) return@launch
                    stateFlow.value = failed
                    journal.record("operation", failedEvent, outcome = failure::class.simpleName)
                    failSession(failure)
                }
            },
        )
    }

    /**
     * Bridges the write-capable operations onto the live command session,
     * mapping the operation-level [WriteIntent] to the session's [CommandIntent]
     * so writes are journaled distinctly from reads.
     */
    private fun writeChannel(session: ElmCommandSession): DiagnosticWriteChannel =
        DiagnosticWriteChannel { request, intent ->
            val commandIntent = when (intent) {
                WriteIntent.READ -> CommandIntent.READ
                WriteIntent.WRITE -> CommandIntent.WRITE
            }
            session.execute(request, commandIntent).normalizedText
        }

    private suspend fun runConnection(address: String) {
        val transport = transportFactory(address)
        activeTransport.set(transport)
        try {
            transition(ConnectionState.Connecting)
            transport.connect()
            val session = ElmCommandSession(transport) { event ->
                journal.record(
                    layer = "elm",
                    name = event.direction.name.lowercase(),
                    text = event.normalizedText,
                    rawHex = event.raw.joinToString("") { "%02X".format(it) },
                    outcome = if (event.complete) "prompt_complete" else "partial",
                )
            }
            val identity = AdapterInitializer(session).initialize(profile) { stage ->
                transition(
                    when (stage) {
                        InitializationStage.IDENTIFYING -> ConnectionState.Identifying
                        InitializationStage.INITIALIZING -> ConnectionState.Initializing
                    },
                )
            }
            activeSession.set(session)
            transition(ConnectionState.AdapterReady(identity.elm, identity.stn, profile.id))
        } catch (cancelled: CancellationException) {
            transport.close()
            throw cancelled
        } catch (failure: Throwable) {
            transport.close()
            activeTransport.compareAndSet(transport, null)
            activeSession.set(null)
            transitionSafely(ConnectionState.Failed(classify(failure)))
            journal.record("bluetooth", "connection_failed", outcome = failure::class.simpleName)
        }
    }

    /** Tears down the live session and moves to Failed after a fatal operation error. */
    private suspend fun failSession(failure: Throwable) {
        activeSession.set(null)
        activeTransport.getAndSet(null)?.close()
        transitionSafely(ConnectionState.Failed(classify(failure)))
    }

    private fun transition(next: ConnectionState) {
        mutableState.value = ConnectionStateMachine.transition(mutableState.value, next)
        journal.record("operation", "connection_state", text = next::class.simpleName)
    }

    private fun transitionSafely(next: ConnectionState) {
        val current = mutableState.value
        mutableState.value = try {
            ConnectionStateMachine.transition(current, next)
        } catch (_: IllegalStateException) {
            next
        }
        journal.record("operation", "connection_state", text = next::class.simpleName)
    }

    private fun classify(failure: Throwable): ConnectionFailure = when (failure) {
        is ReadOnlyEngineCaptureFailure -> classify(failure.cause ?: failure)
        is InstrumentReadOnlyCaptureFailure -> classify(failure.cause ?: failure)
        is DtcReadFailure.Transport -> classify(failure.cause ?: failure)
        is DtcClearFailure -> classify(failure.cause ?: failure)
        is ServiceReminderResetFailure -> classify(failure.cause ?: failure)
        is AdapterIdentityMismatch -> ConnectionFailure.IDENTITY_MISMATCH
        is CommandFailure.Timeout, is SocketTimeoutException -> ConnectionFailure.TIMEOUT
        is CommandFailure.Disconnected -> ConnectionFailure.REMOTE_CLOSE
        is SecurityException -> ConnectionFailure.PERMISSION_DENIED
        // Must precede the generic IOException arm: DevicePairingRequiredException
        // is an IOException, and `when` matches top-to-bottom.
        is DevicePairingRequiredException -> ConnectionFailure.PAIRING_REQUIRED
        is IOException, is CommandFailure.Io -> ConnectionFailure.IO
        else -> ConnectionFailure.IO
    }
}
