package dev.resetlight.features.connection

import dev.resetlight.domain.ConnectionFailure
import dev.resetlight.domain.ConnectionState
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText

data class ConnectionScreenState(
    val statusTitle: UiText,
    val statusDetail: UiText,
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
    val showUnavailableServiceCard: Boolean = false,
    val elmIdentity: String? = null,
    val stnIdentity: String? = null,
    val mapId: String? = null,
    val failureAction: FailureAction? = null,
    val failureActionLabel: UiText? = null,
    val serviceCard: UnavailableFeatureCardState = UnavailableFeatureCardState(
        UiText(UiMessage.SERVICE_CARD_TITLE),
    ),
)

data class UnavailableFeatureCardState(
    val title: UiText,
    val detail: UiText = UiText(UiMessage.SERVICE_CARD_UNAVAILABLE_DETAIL),
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
            statusTitle = UiText(UiMessage.STATUS_SELECTING_TITLE),
            statusDetail = UiText(UiMessage.STATUS_SELECTING_DETAIL),
            selectedAdapterName = selectedAdapterName,
            showPairOrSelect = true,
        )
        ConnectionState.Connecting -> progress(
            title = UiText(UiMessage.STATUS_CONNECTING_TITLE),
            detail = UiText(UiMessage.STATUS_CONNECTING_DETAIL),
            selectedAdapterName = selectedAdapterName,
        )
        ConnectionState.Identifying -> progress(
            title = UiText(UiMessage.STATUS_IDENTIFYING_TITLE),
            detail = UiText(UiMessage.STATUS_IDENTIFYING_DETAIL),
            selectedAdapterName = selectedAdapterName,
        )
        ConnectionState.Initializing -> progress(
            title = UiText(UiMessage.STATUS_INITIALIZING_TITLE),
            detail = UiText(UiMessage.STATUS_INITIALIZING_DETAIL),
            selectedAdapterName = selectedAdapterName,
        )
        is ConnectionState.AdapterReady -> ConnectionScreenState(
            statusTitle = UiText(UiMessage.STATUS_READY_TITLE),
            statusDetail = UiText(UiMessage.STATUS_READY_DETAIL),
            selectedAdapterName = selectedAdapterName,
            showDisconnect = true,
            showReadOnlyCapture = researchCaptureEnabled,
            showDtcRead = true,
            // This bounded read proves the motorcycle itself responded, not
            // only the Bluetooth adapter, so it is a normal product feature.
            showServiceInfoRead = true,
            showDtcClear = writeOperationsEnabled,
            showServiceReset = writeOperationsEnabled,
            showUnavailableServiceCard = !writeOperationsEnabled,
            elmIdentity = state.elmIdentity,
            stnIdentity = state.stnIdentity,
            mapId = state.mapId,
        )
        ConnectionState.Disconnecting -> progress(
            title = UiText(UiMessage.STATUS_DISCONNECTING_TITLE),
            detail = UiText(UiMessage.STATUS_DISCONNECTING_DETAIL),
            selectedAdapterName = selectedAdapterName,
        )
        is ConnectionState.Failed -> failure(state.reason, selectedAdapterName)
    }

    private fun disconnected(selectedAdapterName: String?): ConnectionScreenState = ConnectionScreenState(
        statusTitle = UiText(UiMessage.STATUS_DISCONNECTED_TITLE),
        statusDetail = selectedAdapterName?.let {
            UiText(UiMessage.STATUS_DISCONNECTED_DETAIL_SELECTED, it)
        } ?: UiText(UiMessage.STATUS_DISCONNECTED_DETAIL_NONE),
        selectedAdapterName = selectedAdapterName,
        showPairOrSelect = true,
        showConnect = true,
        connectEnabled = selectedAdapterName != null,
    )

    private fun progress(
        title: UiText,
        detail: UiText,
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
                title = UiMessage.FAILURE_PERMISSION_TITLE,
                detail = UiMessage.FAILURE_PERMISSION_DETAIL,
                action = FailureAction.REQUEST_PERMISSION,
                actionLabel = UiMessage.FAILURE_PERMISSION_ACTION,
            )
            ConnectionFailure.PAIRING_REQUIRED -> FailureMessage(
                title = UiMessage.FAILURE_PAIRING_TITLE,
                detail = UiMessage.FAILURE_PAIRING_DETAIL,
                action = FailureAction.OPEN_BLUETOOTH_SETTINGS,
                actionLabel = UiMessage.FAILURE_PAIRING_ACTION,
            )
            ConnectionFailure.TIMEOUT -> FailureMessage(
                title = UiMessage.FAILURE_TIMEOUT_TITLE,
                detail = UiMessage.FAILURE_TIMEOUT_DETAIL,
                action = FailureAction.RETRY_CONNECTION,
                actionLabel = UiMessage.FAILURE_TIMEOUT_ACTION,
            )
            ConnectionFailure.IDENTITY_MISMATCH -> FailureMessage(
                title = UiMessage.FAILURE_IDENTITY_TITLE,
                detail = UiMessage.FAILURE_IDENTITY_DETAIL,
                action = FailureAction.SELECT_ADAPTER,
                actionLabel = UiMessage.FAILURE_IDENTITY_ACTION,
            )
            ConnectionFailure.REMOTE_CLOSE -> FailureMessage(
                title = UiMessage.FAILURE_REMOTE_CLOSE_TITLE,
                detail = UiMessage.FAILURE_REMOTE_CLOSE_DETAIL,
                action = FailureAction.RETRY_CONNECTION,
                actionLabel = UiMessage.FAILURE_REMOTE_CLOSE_ACTION,
            )
            ConnectionFailure.IO -> FailureMessage(
                title = UiMessage.FAILURE_IO_TITLE,
                detail = UiMessage.FAILURE_IO_DETAIL,
                action = FailureAction.RETRY_CONNECTION,
                actionLabel = UiMessage.FAILURE_IO_ACTION,
            )
        }
        return ConnectionScreenState(
            statusTitle = UiText(message.title),
            statusDetail = UiText(message.detail),
            selectedAdapterName = selectedAdapterName,
            failureAction = message.action,
            failureActionLabel = UiText(message.actionLabel),
        )
    }

    private data class FailureMessage(
        val title: UiMessage,
        val detail: UiMessage,
        val action: FailureAction,
        val actionLabel: UiMessage,
    )
}
