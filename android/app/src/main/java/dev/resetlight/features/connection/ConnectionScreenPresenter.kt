package dev.resetlight.features.connection

import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState

data class ConnectionScreenState(
    val statusTitle: String,
    val statusDetail: String,
    val selectedAdapterName: String?,
    val inProgress: Boolean = false,
    val showPairOrSelect: Boolean = false,
    val showConnect: Boolean = false,
    val connectEnabled: Boolean = false,
    val showDisconnect: Boolean = false,
    val showReadOnlyCapture: Boolean = false,
    val showDtcRead: Boolean = false,
    val showServiceInfoRead: Boolean = false,
    val showDtcClear: Boolean = false,
    val showServiceReset: Boolean = false,
    val elmIdentity: String? = null,
    val stnIdentity: String? = null,
    val mapId: String? = null,
    val failureAction: FailureAction? = null,
    val failureActionLabel: String? = null,
    val serviceCard: UnavailableFeatureCardState = UnavailableFeatureCardState("Service reminder reset"),
)

data class UnavailableFeatureCardState(
    val title: String,
    val detail: String = "Unavailable until the motorcycle profile is validated.",
    val enabled: Boolean = false,
)

enum class FailureAction {
    REQUEST_PERMISSION,
    OPEN_BLUETOOTH_SETTINGS,
    RETRY_CONNECTION,
    SELECT_ADAPTER,
}

class ConnectionScreenPresenter {
    fun present(
        state: ConnectionState,
        selectedAdapterName: String?,
        researchCaptureEnabled: Boolean = false,
        writeOperationsEnabled: Boolean = false,
    ): ConnectionScreenState = when (state) {
        ConnectionState.Disconnected -> disconnected(selectedAdapterName)
        ConnectionState.SelectingOrPairing -> ConnectionScreenState(
            statusTitle = "Select or pair adapter",
            statusDetail = "Choose a bonded vLinker adapter, or pair it in Android Bluetooth settings.",
            selectedAdapterName = selectedAdapterName,
            showPairOrSelect = true,
        )
        ConnectionState.Connecting -> progress(
            title = "Connecting",
            detail = "Opening the Bluetooth connection.",
            selectedAdapterName = selectedAdapterName,
        )
        ConnectionState.Identifying -> progress(
            title = "Identifying adapter",
            detail = "Checking that the adapter identity matches the selected map.",
            selectedAdapterName = selectedAdapterName,
        )
        ConnectionState.Initializing -> progress(
            title = "Initializing adapter",
            detail = "Applying the adapter initialization sequence.",
            selectedAdapterName = selectedAdapterName,
        )
        is ConnectionState.AdapterReady -> ConnectionScreenState(
            statusTitle = "Adapter ready",
            statusDetail = "The adapter connection is ready.",
            selectedAdapterName = selectedAdapterName,
            showDisconnect = true,
            showReadOnlyCapture = researchCaptureEnabled,
            showDtcRead = true,
            showServiceInfoRead = researchCaptureEnabled,
            showDtcClear = writeOperationsEnabled,
            showServiceReset = writeOperationsEnabled,
            elmIdentity = state.elmIdentity,
            stnIdentity = state.stnIdentity,
            mapId = state.mapId,
        )
        ConnectionState.Disconnecting -> progress(
            title = "Disconnecting",
            detail = "Closing the adapter connection.",
            selectedAdapterName = selectedAdapterName,
        )
        is ConnectionState.Failed -> failure(state.reason, selectedAdapterName)
    }

    private fun disconnected(selectedAdapterName: String?): ConnectionScreenState = ConnectionScreenState(
        statusTitle = "Disconnected",
        statusDetail = selectedAdapterName?.let { "Selected adapter: $it" }
            ?: "Pair or select an adapter before connecting.",
        selectedAdapterName = selectedAdapterName,
        showPairOrSelect = true,
        showConnect = true,
        connectEnabled = selectedAdapterName != null,
    )

    private fun progress(
        title: String,
        detail: String,
        selectedAdapterName: String?,
    ): ConnectionScreenState = ConnectionScreenState(
        statusTitle = title,
        statusDetail = detail,
        selectedAdapterName = selectedAdapterName,
        inProgress = true,
    )

    private fun failure(
        reason: ConnectionFailure,
        selectedAdapterName: String?,
    ): ConnectionScreenState {
        val message = when (reason) {
            ConnectionFailure.PERMISSION_DENIED -> FailureMessage(
                title = "Bluetooth permission needed",
                detail = "Allow the Bluetooth permission requested by Reset Moto Reminders, then try again.",
                action = FailureAction.REQUEST_PERMISSION,
                actionLabel = "Grant permission",
            )
            ConnectionFailure.PAIRING_REQUIRED -> FailureMessage(
                title = "Pairing required",
                detail = "Pair vLinker MC-Android in Android Bluetooth settings using PIN 1234, then return and select it.",
                action = FailureAction.OPEN_BLUETOOTH_SETTINGS,
                actionLabel = "Open Bluetooth settings",
            )
            ConnectionFailure.TIMEOUT -> FailureMessage(
                title = "Connection timed out",
                detail = "The adapter did not respond in time. Check that it is powered and nearby, then try again.",
                action = FailureAction.RETRY_CONNECTION,
                actionLabel = "Try again",
            )
            ConnectionFailure.IDENTITY_MISMATCH -> FailureMessage(
                title = "Unsupported adapter identity",
                detail = "The connected device does not match the selected adapter map. Disconnect and select the correct adapter.",
                action = FailureAction.SELECT_ADAPTER,
                actionLabel = "Select another adapter",
            )
            ConnectionFailure.REMOTE_CLOSE -> FailureMessage(
                title = "Adapter disconnected",
                detail = "The adapter closed the connection. Check power and Bluetooth range before reconnecting.",
                action = FailureAction.RETRY_CONNECTION,
                actionLabel = "Reconnect",
            )
            ConnectionFailure.IO -> FailureMessage(
                title = "Connection failed",
                detail = "Bluetooth communication failed. Check adapter power and range, then try again.",
                action = FailureAction.RETRY_CONNECTION,
                actionLabel = "Try again",
            )
        }
        return ConnectionScreenState(
            statusTitle = message.title,
            statusDetail = message.detail,
            selectedAdapterName = selectedAdapterName,
            failureAction = message.action,
            failureActionLabel = message.actionLabel,
        )
    }

    private data class FailureMessage(
        val title: String,
        val detail: String,
        val action: FailureAction,
        val actionLabel: String,
    )
}
