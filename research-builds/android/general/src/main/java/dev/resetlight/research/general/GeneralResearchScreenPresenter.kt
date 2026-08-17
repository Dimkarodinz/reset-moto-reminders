package dev.resetlight.research.general

import java.io.File

data class GeneralResearchScreenPresentation(
    val startEnabled: Boolean,
    val running: Boolean,
    val canShare: Boolean,
    val reportFile: File?,
    val statusTitle: String,
    val statusBody: String,
)

class GeneralResearchScreenPresenter {
    fun present(
        input: GeneralVehicleValidation,
        adapterSelected: Boolean,
        session: GeneralSessionState,
    ): GeneralResearchScreenPresentation {
        val running = session is GeneralSessionState.Running
        val report = when (session) {
            is GeneralSessionState.Complete -> session.reportFile
            is GeneralSessionState.Failed -> session.reportFile
            is GeneralSessionState.Cancelled -> session.reportFile
            else -> null
        }
        val (title, body) = session.statusText()
        return GeneralResearchScreenPresentation(
            startEnabled = input is GeneralVehicleValidation.Valid && adapterSelected && !running,
            running = running,
            canShare = report != null,
            reportFile = report,
            statusTitle = title,
            statusBody = body,
        )
    }

    private fun GeneralSessionState.statusText(): Pair<String, String> = when (this) {
        GeneralSessionState.Idle -> "Ready" to "Enter the motorcycle and select a paired adapter."
        is GeneralSessionState.Running -> "Scan running" to stage.readableName()
        is GeneralSessionState.Complete -> "Scan complete" to
            "Received useful responses to ${summary.responded} of ${summary.attempted} bounded requests."
        is GeneralSessionState.Failed -> "Scan stopped" to message
        is GeneralSessionState.Cancelled -> "Scan cancelled" to "The partial report was preserved."
    }

    private fun GeneralSessionStage.readableName(): String = when (this) {
        GeneralSessionStage.CONNECTING -> "Connecting to the adapter…"
        GeneralSessionStage.IDENTIFYING_ADAPTER -> "Identifying the adapter…"
        GeneralSessionStage.INITIALIZING_ADAPTER -> "Initializing the adapter…"
        GeneralSessionStage.READING_ADAPTER -> "Reading adapter and protocol information…"
        GeneralSessionStage.CONFIGURING_PROTOCOL -> "Selecting the automatic diagnostic protocol…"
        GeneralSessionStage.READING_SUPPORTED_PIDS -> "Reading standard supported-PID pages…"
        GeneralSessionStage.READING_SELECTED_PROTOCOL -> "Recording the selected diagnostic protocol…"
        GeneralSessionStage.READING_DTCS -> "Reading stored, pending, and permanent DTCs…"
        GeneralSessionStage.READING_MODULE_INFORMATION -> "Reading non-identifying module information…"
        GeneralSessionStage.DISCONNECTING -> "Closing the diagnostic connection…"
    }
}
