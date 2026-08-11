package dev.resetlight.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.resetlight.R
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText

/** The Android string resource backing each [UiMessage]. */
@StringRes
fun UiMessage.resourceId(): Int = when (this) {
    UiMessage.STATUS_DISCONNECTED_TITLE -> R.string.status_disconnected_title
    UiMessage.STATUS_DISCONNECTED_DETAIL_SELECTED -> R.string.status_disconnected_detail_selected
    UiMessage.STATUS_DISCONNECTED_DETAIL_NONE -> R.string.status_disconnected_detail_none
    UiMessage.STATUS_SELECTING_TITLE -> R.string.status_selecting_title
    UiMessage.STATUS_SELECTING_DETAIL -> R.string.status_selecting_detail
    UiMessage.STATUS_CONNECTING_TITLE -> R.string.status_connecting_title
    UiMessage.STATUS_CONNECTING_DETAIL -> R.string.status_connecting_detail
    UiMessage.STATUS_IDENTIFYING_TITLE -> R.string.status_identifying_title
    UiMessage.STATUS_IDENTIFYING_DETAIL -> R.string.status_identifying_detail
    UiMessage.STATUS_INITIALIZING_TITLE -> R.string.status_initializing_title
    UiMessage.STATUS_INITIALIZING_DETAIL -> R.string.status_initializing_detail
    UiMessage.STATUS_READY_TITLE -> R.string.status_ready_title
    UiMessage.STATUS_READY_DETAIL -> R.string.status_ready_detail
    UiMessage.STATUS_DISCONNECTING_TITLE -> R.string.status_disconnecting_title
    UiMessage.STATUS_DISCONNECTING_DETAIL -> R.string.status_disconnecting_detail

    UiMessage.FAILURE_PERMISSION_TITLE -> R.string.failure_permission_title
    UiMessage.FAILURE_PERMISSION_DETAIL -> R.string.failure_permission_detail
    UiMessage.FAILURE_PERMISSION_ACTION -> R.string.failure_permission_action
    UiMessage.FAILURE_PAIRING_TITLE -> R.string.failure_pairing_title
    UiMessage.FAILURE_PAIRING_DETAIL -> R.string.failure_pairing_detail
    UiMessage.FAILURE_PAIRING_ACTION -> R.string.failure_pairing_action
    UiMessage.FAILURE_TIMEOUT_TITLE -> R.string.failure_timeout_title
    UiMessage.FAILURE_TIMEOUT_DETAIL -> R.string.failure_timeout_detail
    UiMessage.FAILURE_TIMEOUT_ACTION -> R.string.failure_timeout_action
    UiMessage.FAILURE_IDENTITY_TITLE -> R.string.failure_identity_title
    UiMessage.FAILURE_IDENTITY_DETAIL -> R.string.failure_identity_detail
    UiMessage.FAILURE_IDENTITY_ACTION -> R.string.failure_identity_action
    UiMessage.FAILURE_REMOTE_CLOSE_TITLE -> R.string.failure_remote_close_title
    UiMessage.FAILURE_REMOTE_CLOSE_DETAIL -> R.string.failure_remote_close_detail
    UiMessage.FAILURE_REMOTE_CLOSE_ACTION -> R.string.failure_remote_close_action
    UiMessage.FAILURE_IO_TITLE -> R.string.failure_io_title
    UiMessage.FAILURE_IO_DETAIL -> R.string.failure_io_detail
    UiMessage.FAILURE_IO_ACTION -> R.string.failure_io_action

    UiMessage.SERVICE_CARD_TITLE -> R.string.service_card_title
    UiMessage.SERVICE_CARD_UNAVAILABLE_DETAIL -> R.string.service_card_unavailable_detail

    UiMessage.CAPTURE_FAILED_ERROR -> R.string.capture_failed_error
    UiMessage.CAPTURE_REASON_ENGINE_TRANSPORT_REJECTED -> R.string.capture_reason_engine_transport_rejected
    UiMessage.CAPTURE_REASON_EXTENDED_SESSION_REJECTED -> R.string.capture_reason_extended_session_rejected
    UiMessage.CAPTURE_REASON_DTC_COUNT_UNAVAILABLE -> R.string.capture_reason_dtc_count_unavailable

    UiMessage.DTC_READ_FAILED_ERROR -> R.string.dtc_read_failed_error
    UiMessage.DTC_READ_COUNT_MISMATCH -> R.string.dtc_read_count_mismatch

    UiMessage.INSTRUMENT_READ_FAILED_ERROR -> R.string.instrument_read_failed_error
    UiMessage.INSTRUMENT_REASON_TRANSPORT_REJECTED -> R.string.instrument_reason_transport_rejected
    UiMessage.INSTRUMENT_REASON_UNRECOGNIZED_RESPONSE -> R.string.instrument_reason_unrecognized_response

    UiMessage.DTC_CLEAR_FAILED_ERROR -> R.string.dtc_clear_failed_error
    UiMessage.DTC_CLEAR_REASON_SESSION_REFUSED -> R.string.dtc_clear_reason_session_refused
    UiMessage.DTC_CLEAR_REASON_NO_SEED -> R.string.dtc_clear_reason_no_seed
    UiMessage.DTC_CLEAR_REASON_SECURITY_REJECTED -> R.string.dtc_clear_reason_security_rejected
    UiMessage.DTC_CLEAR_REASON_REJECTED -> R.string.dtc_clear_reason_rejected
    UiMessage.DTC_CLEAR_REASON_COUNT_UNCONFIRMED -> R.string.dtc_clear_reason_count_unconfirmed

    UiMessage.SERVICE_RESET_FAILED_ERROR -> R.string.service_reset_failed_error
    UiMessage.SERVICE_RESET_REASON_UNRECOGNIZED_STATUS -> R.string.service_reset_reason_unrecognized_status
    UiMessage.SERVICE_RESET_REASON_DISTANCE_REJECTED -> R.string.service_reset_reason_distance_rejected
    UiMessage.SERVICE_RESET_REASON_DATE_UNCONFIRMED -> R.string.service_reset_reason_date_unconfirmed

    UiMessage.GATE_REASON_PROFILE_MISMATCH -> R.string.gate_reason_profile_mismatch
    UiMessage.GATE_REASON_STATUS_MISMATCH -> R.string.gate_reason_status_mismatch
    UiMessage.GATE_REASON_AUTHORIZED -> R.string.gate_reason_authorized
}

/** Resolves this message to a localized string using the phone locale. */
@Composable
fun UiText.resolved(): String =
    if (args.isEmpty()) {
        stringResource(key.resourceId())
    } else {
        stringResource(key.resourceId(), *args.toTypedArray())
    }

/** The short unit suffix shown next to a distance value in the display unit. */
@Composable
fun DistanceUnit.label(): String = stringResource(
    when (this) {
        DistanceUnit.KILOMETERS -> R.string.distance_unit_km
        DistanceUnit.MILES -> R.string.distance_unit_miles
    },
)
