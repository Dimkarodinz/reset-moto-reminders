package dev.resetlight.research.general

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GeneralSessionStage {
    CONNECTING,
    IDENTIFYING_ADAPTER,
    INITIALIZING_ADAPTER,
    READING_ADAPTER,
    CONFIGURING_PROTOCOL,
    READING_SUPPORTED_PIDS,
    READING_SELECTED_PROTOCOL,
    READING_DTCS,
    READING_MODULE_INFORMATION,
    DISCONNECTING,
}

sealed interface GeneralSessionState {
    data object Idle : GeneralSessionState
    data class Running(val vehicle: GeneralVehicle, val stage: GeneralSessionStage) : GeneralSessionState
    data class Complete(
        val vehicle: GeneralVehicle,
        val reportFile: File,
        val summary: GeneralResearchSummary,
    ) : GeneralSessionState
    data class Failed(val vehicle: GeneralVehicle, val message: String, val reportFile: File?) : GeneralSessionState
    data class Cancelled(val vehicle: GeneralVehicle, val reportFile: File? = null) : GeneralSessionState
}

data class GeneralResearchRunResult(
    val reportFile: File,
    val summary: GeneralResearchSummary,
)

class GeneralResearchExecutionFailure(
    override val message: String,
    val reportFile: File?,
    cause: Throwable? = null,
) : Exception(message, cause)

class GeneralResearchExecutionCancelled(
    val reportFile: File,
    cause: CancellationException? = null,
) : CancellationException("Research scan cancelled") {
    init {
        if (cause != null) initCause(cause)
    }
}

fun interface GeneralResearchSessionExecutor {
    suspend fun run(
        vehicle: GeneralVehicle,
        privateAdapterAddress: String,
        onStage: (GeneralSessionStage) -> Unit,
    ): GeneralResearchRunResult
}

class GeneralResearchSessionController(
    private val scope: CoroutineScope,
    private val executor: GeneralResearchSessionExecutor,
) {
    private val mutableState = MutableStateFlow<GeneralSessionState>(GeneralSessionState.Idle)
    val state: StateFlow<GeneralSessionState> = mutableState.asStateFlow()
    private var activeJob: Job? = null

    fun start(vehicle: GeneralVehicle, privateAdapterAddress: String): Boolean {
        if (activeJob?.isActive == true) return false
        mutableState.value = GeneralSessionState.Running(vehicle, GeneralSessionStage.CONNECTING)
        activeJob = scope.launch {
            try {
                val result = executor.run(vehicle, privateAdapterAddress) { stage ->
                    mutableState.value = GeneralSessionState.Running(vehicle, stage)
                }
                mutableState.value = GeneralSessionState.Complete(vehicle, result.reportFile, result.summary)
            } catch (cancelled: GeneralResearchExecutionCancelled) {
                mutableState.value = GeneralSessionState.Cancelled(vehicle, cancelled.reportFile)
            } catch (cancelled: CancellationException) {
                mutableState.value = GeneralSessionState.Cancelled(vehicle)
            } catch (failure: GeneralResearchExecutionFailure) {
                mutableState.value = GeneralSessionState.Failed(vehicle, failure.message, failure.reportFile)
            } catch (failure: Throwable) {
                mutableState.value = GeneralSessionState.Failed(
                    vehicle,
                    failure.message ?: "Research scan failed",
                    null,
                )
            }
        }
        return true
    }

    fun cancel() {
        activeJob?.cancel()
    }
}
