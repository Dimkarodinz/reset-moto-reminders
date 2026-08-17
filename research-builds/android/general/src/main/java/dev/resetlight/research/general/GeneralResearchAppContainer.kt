package dev.resetlight.research.general

import android.content.Context
import dev.resetlight.adapter.elm.AdapterIdentityMismatch
import dev.resetlight.adapter.elm.AdapterInitializer
import dev.resetlight.adapter.elm.CommandFailure
import dev.resetlight.adapter.elm.ElmCommandSession
import dev.resetlight.adapter.elm.InitializationStage
import dev.resetlight.logging.EventJournal
import dev.resetlight.logging.FileJournalSink
import dev.resetlight.profiles.AdapterProfile
import dev.resetlight.profiles.AdapterProfileLoader
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

class GeneralResearchAppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val adapterProfile: AdapterProfile = applicationContext.assets
        .open("profiles/vlinker-mc-android.adaptermap.yaml")
        .use(AdapterProfileLoader()::load)
    val readPlan: GeneralReadPlan = applicationContext.assets
        .open("profiles/standard-obd-read.researchprofile.yaml")
        .use { GeneralReadPlanLoader().load(it.readBytes()) }
    private val bluetooth: BluetoothFacade = AndroidBluetoothFacade(applicationContext)
    private val mutableDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val devices: StateFlow<List<BondedDevice>> = mutableDevices.asStateFlow()

    val sessions = GeneralResearchSessionController(
        scope,
        AndroidGeneralResearchSessionExecutor(
            context = applicationContext,
            scope = scope,
            adapterProfile = adapterProfile,
            readPlan = readPlan,
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

private class AndroidGeneralResearchSessionExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val adapterProfile: AdapterProfile,
    private val readPlan: GeneralReadPlan,
    private val bluetooth: BluetoothFacade,
    private val transportFactory: (String) -> ByteTransport = { address ->
        RfcommByteTransport(bluetooth, address, adapterProfile.transport.sppServiceUuid)
    },
) : GeneralResearchSessionExecutor {
    override suspend fun run(
        vehicle: GeneralVehicle,
        privateAdapterAddress: String,
        onStage: (GeneralSessionStage) -> Unit,
    ): GeneralResearchRunResult {
        val report = File(
            context.filesDir,
            "research-reports/general-session-${Instant.now().toEpochMilli()}.jsonl",
        )
        val journal = EventJournal(scope, FileJournalSink(report))
        var transport: ByteTransport? = null
        try {
            journal.recordCritical(
                layer = "research",
                name = "general_session_started",
                text = GeneralSessionMetadata.describe(vehicle, adapterProfile.sourceSha256, readPlan),
            )

            onStage(GeneralSessionStage.CONNECTING)
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
                        InitializationStage.IDENTIFYING -> GeneralSessionStage.IDENTIFYING_ADAPTER
                        InitializationStage.INITIALIZING -> GeneralSessionStage.INITIALIZING_ADAPTER
                    },
                )
            }
            journal.record(
                layer = "adapter",
                name = "adapter_ready",
                outcome = "matched",
                text = "elm=${identity.elm} stn=${identity.stn} profile=${adapterProfile.id}",
            )

            val summary = GeneralResearchScanner(
                plan = readPlan,
                channel = GeneralResearchChannel { command -> session.execute(command).normalizedText },
                policy = GeneralResearchCommandPolicy(readPlan),
                events = GeneralResearchEventRecorder { event ->
                    journal.record("research", event.name, event.outcome, event.text)
                },
                onPhase = { phase -> onStage(phase.toSessionStage()) },
            ).scan()

            onStage(GeneralSessionStage.DISCONNECTING)
            transport.close()
            transport = null
            journal.recordCritical(
                layer = "research",
                name = "general_session_finished",
                outcome = "complete",
                text = "attempted=${summary.attempted} responded=${summary.responded} " +
                    summary.phaseResponses.entries.joinToString(" ") { "${it.key}=${it.value}" },
            )
            return GeneralResearchRunResult(report, summary)
        } catch (cancelled: CancellationException) {
            recordTerminalSafely(journal, "cancelled")
            throw GeneralResearchExecutionCancelled(report, cancelled)
        } catch (failure: Throwable) {
            recordTerminalSafely(journal, failure::class.simpleName ?: "failure")
            throw GeneralResearchExecutionFailure(failure.userMessage(), report, failure)
        } finally {
            withContext(NonCancellable) {
                try {
                    onStage(GeneralSessionStage.DISCONNECTING)
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
                journal.recordCritical("research", "general_session_finished", outcome = outcome)
            } catch (_: Throwable) {
                // Preserve the transport or cancellation failure and the events already flushed.
            }
        }
    }

    private fun String.toSessionStage(): GeneralSessionStage = when (this) {
        "adapter_metadata" -> GeneralSessionStage.READING_ADAPTER
        "protocol_setup" -> GeneralSessionStage.CONFIGURING_PROTOCOL
        "supported_pids" -> GeneralSessionStage.READING_SUPPORTED_PIDS
        "selected_protocol" -> GeneralSessionStage.READING_SELECTED_PROTOCOL
        "diagnostic_trouble_codes" -> GeneralSessionStage.READING_DTCS
        "module_information" -> GeneralSessionStage.READING_MODULE_INFORMATION
        else -> error("Unmapped research phase: $this")
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
            commandFailure is CommandFailure.Timeout ->
                "A diagnostic request timed out. The partial report was saved."
            commandFailure is CommandFailure.Disconnected ->
                "The adapter disconnected. The partial report was saved."
            commandFailure is CommandFailure.Io ->
                "The adapter connection failed. The partial report was saved."
            this is SecurityException -> "Bluetooth permission is required."
            else -> "The scan stopped safely. The partial report was saved."
        }
    }

    private companion object {
        const val COMMAND_TIMEOUT_MILLIS = 5_000L
    }
}
