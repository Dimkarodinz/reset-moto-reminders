package dev.resetlight.research.triumph

import java.io.File

data class ResearchScreenPresentation(
    val startEnabled: Boolean,
    val running: Boolean,
    val canShare: Boolean,
    val reportFile: File?,
    val statusTitle: String,
    val statusBody: String,
)

class ResearchScreenPresenter {
    fun present(
        input: VehicleInputValidation,
        adapterSelected: Boolean,
        session: ResearchSessionState,
        writeOptionsReady: Boolean = true,
    ): ResearchScreenPresentation {
        val running = session is ResearchSessionState.Running
        val report = when (session) {
            is ResearchSessionState.Complete -> session.reportFile
            is ResearchSessionState.Failed -> session.reportFile
            is ResearchSessionState.Cancelled -> session.reportFile
            else -> null
        }
        val (title, body) = session.statusText()
        return ResearchScreenPresentation(
            startEnabled = input is VehicleInputValidation.Valid && adapterSelected && writeOptionsReady && !running,
            running = running,
            canShare = report != null,
            reportFile = report,
            statusTitle = title,
            statusBody = body,
        )
    }

    private fun ResearchSessionState.statusText(): Pair<String, String> = when (this) {
        ResearchSessionState.Idle -> "Ready" to "Enter the motorcycle and select a paired adapter."
        is ResearchSessionState.Running -> "Scan running" to stage.readableName()
        is ResearchSessionState.Complete -> "Scan complete" to
            "DTC read: ${summary.dtcReadConfirmed.yesNo()}; service reads: ${summary.serviceReadConfirmed.yesNo()}."
        is ResearchSessionState.Failed -> "Scan stopped" to message
        is ResearchSessionState.Cancelled -> "Scan cancelled" to "The adapter was disconnected safely."
    }

    private fun ResearchSessionStage.readableName(): String = when (this) {
        ResearchSessionStage.CONNECTING -> "Connecting to the adapter…"
        ResearchSessionStage.IDENTIFYING_ADAPTER -> "Identifying the adapter…"
        ResearchSessionStage.INITIALIZING_ADAPTER -> "Initializing the adapter…"
        ResearchSessionStage.SCANNING_ADAPTER -> "Reading adapter and protocol information…"
        ResearchSessionStage.SCANNING_ENGINE -> "Reading engine ECU identifiers…"
        ResearchSessionStage.SCANNING_DTCS -> "Reading diagnostic trouble-code evidence…"
        ResearchSessionStage.SCANNING_INSTRUMENT -> "Reading TFT instrument evidence…"
        ResearchSessionStage.VALIDATING_SERVICE_RESET -> "Validating the selected service reminder reset…"
        ResearchSessionStage.VALIDATING_DTC_CLEAR -> "Validating the selected DTC clear…"
        ResearchSessionStage.DISCONNECTING -> "Closing the diagnostic connection…"
    }

    private fun Boolean.yesNo(): String = if (this) "confirmed" else "not confirmed"
}
