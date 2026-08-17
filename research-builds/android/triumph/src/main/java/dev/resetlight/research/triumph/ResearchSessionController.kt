package dev.resetlight.research.triumph

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ResearchSessionStage {
    CONNECTING,
    IDENTIFYING_ADAPTER,
    INITIALIZING_ADAPTER,
    SCANNING_ADAPTER,
    SCANNING_ENGINE,
    SCANNING_DTCS,
    SCANNING_INSTRUMENT,
    VALIDATING_SERVICE_RESET,
    VALIDATING_DTC_CLEAR,
    DISCONNECTING,
}

sealed interface ResearchSessionState {
    data object Idle : ResearchSessionState
    data class Running(
        val vehicle: ResearchVehicle,
        val stage: ResearchSessionStage,
    ) : ResearchSessionState
    data class Complete(
        val vehicle: ResearchVehicle,
        val reportFile: File,
        val summary: ResearchScanSummary,
    ) : ResearchSessionState
    data class Failed(
        val vehicle: ResearchVehicle,
        val message: String,
        val reportFile: File?,
    ) : ResearchSessionState
    data class Cancelled(
        val vehicle: ResearchVehicle,
        val reportFile: File? = null,
    ) : ResearchSessionState
}

data class ResearchRunResult(
    val reportFile: File,
    val summary: ResearchScanSummary,
)

class ResearchExecutionFailure(
    override val message: String,
    val reportFile: File?,
    cause: Throwable? = null,
) : Exception(message, cause)

class ResearchExecutionCancelled(
    val reportFile: File,
    cause: CancellationException? = null,
) : CancellationException("Research scan cancelled") {
    init {
        if (cause != null) initCause(cause)
    }
}

fun interface ResearchSessionExecutor {
    suspend fun run(
        request: ResearchSessionRequest,
        privateAdapterAddress: String,
        onStage: (ResearchSessionStage) -> Unit,
    ): ResearchRunResult
}

class ResearchSessionController(
    private val scope: CoroutineScope,
    private val executor: ResearchSessionExecutor,
) {
    private val mutableState = MutableStateFlow<ResearchSessionState>(ResearchSessionState.Idle)
    val state: StateFlow<ResearchSessionState> = mutableState.asStateFlow()
    private var activeJob: Job? = null

    fun start(vehicle: ResearchVehicle, privateAdapterAddress: String): Boolean =
        start(ResearchSessionRequest(vehicle), privateAdapterAddress)

    fun start(request: ResearchSessionRequest, privateAdapterAddress: String): Boolean {
        if (activeJob?.isActive == true) return false
        val vehicle = request.vehicle
        mutableState.value = ResearchSessionState.Running(vehicle, ResearchSessionStage.CONNECTING)
        activeJob = scope.launch {
            try {
                val result = executor.run(request, privateAdapterAddress) { stage ->
                    mutableState.value = ResearchSessionState.Running(vehicle, stage)
                }
                mutableState.value = ResearchSessionState.Complete(
                    vehicle = vehicle,
                    reportFile = result.reportFile,
                    summary = result.summary,
                )
            } catch (cancelled: ResearchExecutionCancelled) {
                mutableState.value = ResearchSessionState.Cancelled(vehicle, cancelled.reportFile)
            } catch (cancelled: CancellationException) {
                mutableState.value = ResearchSessionState.Cancelled(vehicle)
            } catch (failure: ResearchExecutionFailure) {
                mutableState.value = ResearchSessionState.Failed(
                    vehicle = vehicle,
                    message = failure.message,
                    reportFile = failure.reportFile,
                )
            } catch (failure: Throwable) {
                mutableState.value = ResearchSessionState.Failed(
                    vehicle = vehicle,
                    message = failure.message ?: "Research scan failed",
                    reportFile = null,
                )
            }
        }
        return true
    }

    fun cancel() {
        activeJob?.cancel()
    }
}
