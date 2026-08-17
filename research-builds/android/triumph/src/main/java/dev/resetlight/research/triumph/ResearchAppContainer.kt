package dev.resetlight.research.triumph

import android.content.Context
import dev.resetlight.adapter.elm.AdapterIdentityMismatch
import dev.resetlight.adapter.elm.AdapterInitializer
import dev.resetlight.adapter.elm.CommandFailure
import dev.resetlight.adapter.elm.CommandIntent
import dev.resetlight.adapter.elm.ElmCommandSession
import dev.resetlight.adapter.elm.InitializationStage
import dev.resetlight.logging.EventJournal
import dev.resetlight.logging.FileJournalSink
import dev.resetlight.domain.ServiceIntervalConstraints
import dev.resetlight.profiles.AdapterProfile
import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.profiles.EcuProfile
import dev.resetlight.profiles.EcuProfileLoader
import dev.resetlight.diagnostics.DiagnosticWriteChannel
import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.transport.ByteTransport
import dev.resetlight.transport.bluetooth.AndroidBluetoothFacade
import dev.resetlight.transport.bluetooth.BluetoothFacade
import dev.resetlight.transport.bluetooth.BondedDevice
import dev.resetlight.transport.bluetooth.BondedDeviceSelector
import dev.resetlight.transport.bluetooth.DevicePairingRequiredException
import dev.resetlight.transport.bluetooth.RfcommByteTransport
import java.io.File
import java.net.SocketTimeoutException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ResearchAppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val adapterProfile: AdapterProfile = applicationContext.assets
        .open("profiles/vlinker-mc-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    private val ecuProfile: EcuProfile = applicationContext.assets
        .open("profiles/tiger-900-gt-pro-2021.ecumap.yaml")
        .use(EcuProfileLoader()::load)
    val serviceIntervalConstraints = ServiceIntervalConstraints(
        stepKm = ecuProfile.serviceReminder.distanceRawUnitKm,
        minKm = ecuProfile.serviceReminder.distanceMinimumRaw * ecuProfile.serviceReminder.distanceRawUnitKm,
        maxKm = ecuProfile.serviceReminder.distanceMaximumRaw * ecuProfile.serviceReminder.distanceRawUnitKm,
    )
    private val bluetooth: BluetoothFacade = AndroidBluetoothFacade(applicationContext)
    private val mutableDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val devices: StateFlow<List<BondedDevice>> = mutableDevices.asStateFlow()

    val sessions = ResearchSessionController(
        scope,
        AndroidResearchSessionExecutor(
            context = applicationContext,
            scope = scope,
            adapterProfile = adapterProfile,
            ecuProfile = ecuProfile,
            bluetooth = bluetooth,
        ),
    )

    fun refreshBondedDevices() {
        mutableDevices.value = BondedDeviceSelector.candidates(
            bluetooth.bondedDevices(),
            adapterProfile.identity.bluetoothName.value,
        )
    }
}

private class AndroidResearchSessionExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val adapterProfile: AdapterProfile,
    private val ecuProfile: EcuProfile,
    private val bluetooth: BluetoothFacade,
    private val transportFactory: (String) -> ByteTransport = { address ->
        RfcommByteTransport(bluetooth, address, adapterProfile.transport.sppServiceUuid)
    },
) : ResearchSessionExecutor {
    override suspend fun run(
        request: ResearchSessionRequest,
        privateAdapterAddress: String,
        onStage: (ResearchSessionStage) -> Unit,
    ): ResearchRunResult {
        val vehicle = request.vehicle
        val report = File(
            context.filesDir,
            "research-reports/triumph-session-${Instant.now().toEpochMilli()}.jsonl",
        )
        val journal = EventJournal(scope, FileJournalSink(report))
        var transport: ByteTransport? = null
        try {
            journal.recordCritical(
                layer = "research",
                name = "research_session_started",
                text = "manufacturer=Triumph model=${vehicle.model} model_year=${vehicle.modelYear} " +
                    "adapter_profile_sha256=${adapterProfile.sourceSha256} ecu_profile_sha256=${ecuProfile.sourceSha256} " +
                    "dtc_clear_requested=${request.writeOptions.clearDtcs} " +
                    "service_reset_requested=${request.writeOptions.serviceReset != null}",
            )

            onStage(ResearchSessionStage.CONNECTING)
            transport = transportFactory(privateAdapterAddress)
            transport.connect()
            val session = ElmCommandSession(transport, commandTimeoutMillis = COMMAND_TIMEOUT_MILLIS) { traffic ->
                journal.record(
                    layer = "elm",
                    name = traffic.direction.name.lowercase(),
                    outcome = if (traffic.complete) "prompt_complete" else "partial",
                    text = traffic.normalizedText,
                    rawHex = traffic.raw.joinToString("") { "%02X".format(it) },
                )
            }

            val identity = AdapterInitializer(session).initialize(adapterProfile) { stage ->
                onStage(
                    when (stage) {
                        InitializationStage.IDENTIFYING -> ResearchSessionStage.IDENTIFYING_ADAPTER
                        InitializationStage.INITIALIZING -> ResearchSessionStage.INITIALIZING_ADAPTER
                    },
                )
            }
            journal.record(
                layer = "adapter",
                name = "adapter_ready",
                outcome = "matched",
                text = "elm=${identity.elm} stn=${identity.stn} profile=${adapterProfile.id}",
            )

            val eventRecorder = ResearchEventRecorder { event ->
                journal.record(
                    layer = "research",
                    name = event.name,
                    outcome = event.outcome,
                    text = event.text,
                )
            }
            val readSummary = TriumphResearchScanner(
                ecuProfile = ecuProfile,
                channel = ResearchCommandChannel { command -> session.execute(command).normalizedText },
                policy = ResearchCommandPolicy(adapterProfile, ecuProfile),
                events = eventRecorder,
                onStage = { stage -> onStage(stage.toSessionStage()) },
            ).scan()

            val writeSummary = if (request.writeOptions.requested) {
                val guardedPolicy = ResearchWriteCommandPolicy(ecuProfile)
                val writeChannel = DiagnosticWriteChannel { command, intent ->
                    val sessionIntent = when (intent) {
                        WriteIntent.READ -> CommandIntent.READ
                        WriteIntent.WRITE -> CommandIntent.WRITE
                    }
                    session.execute(command, sessionIntent).normalizedText
                }
                ExperimentalWriteValidator(
                    ecu = ecuProfile,
                    channel = writeChannel,
                    policy = guardedPolicy,
                    events = eventRecorder,
                    onOperation = { operation ->
                        onStage(
                            when (operation) {
                                ResearchWriteOperation.SERVICE_RESET -> ResearchSessionStage.VALIDATING_SERVICE_RESET
                                ResearchWriteOperation.DTC_CLEAR -> ResearchSessionStage.VALIDATING_DTC_CLEAR
                            },
                        )
                    },
                ).validate(readSummary, request.writeOptions)
            } else {
                ResearchWriteValidationSummary()
            }
            val summary = readSummary.copy(writeValidation = writeSummary)

            onStage(ResearchSessionStage.DISCONNECTING)
            transport.close()
            transport = null
            journal.recordCritical(
                layer = "research",
                name = "research_session_finished",
                outcome = "complete",
                text = "dtc_read=${summary.dtcReadConfirmed} service_read=${summary.serviceReadConfirmed} " +
                    "dtc_clear_candidate=${summary.dtcClearCandidate} " +
                    "service_reset_candidate=${summary.serviceResetCandidate} " +
                    "dtc_clear_validation=${summary.writeValidation.dtcClear.outcome.name.lowercase()} " +
                    "service_reset_validation=${summary.writeValidation.serviceReset.outcome.name.lowercase()} " +
                    "service_restore=${summary.writeValidation.serviceReset.restoreOutcome.name.lowercase()}",
            )
            return ResearchRunResult(report, summary)
        } catch (cancelled: CancellationException) {
            recordTerminalSafely(journal, "cancelled")
            throw ResearchExecutionCancelled(report, cancelled)
        } catch (failure: Throwable) {
            recordTerminalSafely(journal, failure::class.simpleName ?: "failure")
            throw ResearchExecutionFailure(failure.userMessage(), report, failure)
        } finally {
            withContext(NonCancellable) {
                try {
                    onStage(ResearchSessionStage.DISCONNECTING)
                    transport?.close()
                } finally {
                    journal.shutdown()
                }
            }
        }
    }

    private suspend fun recordTerminalSafely(journal: EventJournal, outcome: String) {
        withContext(NonCancellable) {
            try {
                journal.recordCritical("research", "research_session_finished", outcome = outcome)
            } catch (_: Throwable) {
                // Preserve the original transport/cancellation outcome. The
                // writer has already flushed every previously queued event.
            }
        }
    }

    private fun ResearchScanStage.toSessionStage(): ResearchSessionStage = when (this) {
        ResearchScanStage.ADAPTER_METADATA -> ResearchSessionStage.SCANNING_ADAPTER
        ResearchScanStage.ENGINE_IDENTIFIERS -> ResearchSessionStage.SCANNING_ENGINE
        ResearchScanStage.DIAGNOSTIC_TROUBLE_CODES -> ResearchSessionStage.SCANNING_DTCS
        ResearchScanStage.INSTRUMENT_CLUSTER -> ResearchSessionStage.SCANNING_INSTRUMENT
    }

    private fun Throwable.userMessage(): String {
        val commandFailure = generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<CommandFailure>()
            .firstOrNull()
        return when {
            this is DevicePairingRequiredException ->
                "The selected adapter is no longer paired. Pair it in Bluetooth settings."
            this is SocketTimeoutException ->
                "The adapter connection timed out. Ensure it is powered by the motorcycle."
            this is AdapterIdentityMismatch ->
                "The connected device does not match the supported vLinker adapter profile."
            commandFailure is CommandFailure.AmbiguousWrite ->
                "A write may have reached the motorcycle, but its response was lost. " +
                    "Restoration is unconfirmed; inspect the motorcycle and report before retrying."
            commandFailure is CommandFailure.Timeout ->
                "A diagnostic request timed out. The partial report was saved."
            commandFailure is CommandFailure.Disconnected ->
                "The adapter disconnected. Restoration may be incomplete; inspect the motorcycle and partial report."
            commandFailure is CommandFailure.Io ->
                "The adapter connection failed. Restoration may be incomplete; inspect the motorcycle and partial report."
            this is SecurityException -> "Bluetooth permission is required."
            else -> "The scan stopped safely. The partial report was saved."
        }
    }

    private companion object {
        const val COMMAND_TIMEOUT_MILLIS = 5_000L
    }
}
