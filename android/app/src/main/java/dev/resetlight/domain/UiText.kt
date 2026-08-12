package dev.resetlight.domain

/**
 * A user-visible message produced by the Android-free domain and presenter
 * layers and resolved to a localized string at the Compose edge. [key] names
 * the message; [args] are substituted positionally into the resolved template
 * (`%1$s`, `%2$s`, …). Keeping this Android-free lets services and the
 * presenter stay unit-testable on the JVM while every string is still driven by
 * the phone locale through Android string resources.
 */
data class UiText(val key: UiMessage, val args: List<String> = emptyList()) {
    constructor(key: UiMessage, vararg args: String) : this(key, args.toList())

    /** Compact form for journals: the stable key name plus any arguments. */
    override fun toString(): String =
        if (args.isEmpty()) key.name else "${key.name}(${args.joinToString(", ")})"
}

/**
 * Stable identifiers for every message that crosses the domain/presenter → UI
 * boundary. Each maps one-to-one to an Android string resource at the Compose
 * edge; static UI-only text (button labels, card titles) is referenced directly
 * from resources and does not appear here.
 */
enum class UiMessage {
    // Connection status (presenter)
    STATUS_DISCONNECTED_TITLE,
    STATUS_DISCONNECTED_DETAIL_SELECTED,
    STATUS_DISCONNECTED_DETAIL_NONE,
    STATUS_SELECTING_TITLE,
    STATUS_SELECTING_DETAIL,
    STATUS_CONNECTING_TITLE,
    STATUS_CONNECTING_DETAIL,
    STATUS_IDENTIFYING_TITLE,
    STATUS_IDENTIFYING_DETAIL,
    STATUS_INITIALIZING_TITLE,
    STATUS_INITIALIZING_DETAIL,
    STATUS_READY_TITLE,
    STATUS_READY_DETAIL,
    STATUS_DISCONNECTING_TITLE,
    STATUS_DISCONNECTING_DETAIL,

    // Connection failures (presenter)
    FAILURE_PERMISSION_TITLE,
    FAILURE_PERMISSION_DETAIL,
    FAILURE_PERMISSION_ACTION,
    FAILURE_PAIRING_TITLE,
    FAILURE_PAIRING_DETAIL,
    FAILURE_PAIRING_ACTION,
    FAILURE_TIMEOUT_TITLE,
    FAILURE_TIMEOUT_DETAIL,
    FAILURE_TIMEOUT_ACTION,
    FAILURE_IDENTITY_TITLE,
    FAILURE_IDENTITY_DETAIL,
    FAILURE_IDENTITY_ACTION,
    FAILURE_REMOTE_CLOSE_TITLE,
    FAILURE_REMOTE_CLOSE_DETAIL,
    FAILURE_REMOTE_CLOSE_ACTION,
    FAILURE_IO_TITLE,
    FAILURE_IO_DETAIL,
    FAILURE_IO_ACTION,

    // Unavailable service card (presenter)
    SERVICE_CARD_TITLE,
    SERVICE_CARD_UNAVAILABLE_DETAIL,

    // Read-only engine capture
    CAPTURE_FAILED_ERROR,
    CAPTURE_REASON_ENGINE_TRANSPORT_REJECTED,
    CAPTURE_REASON_EXTENDED_SESSION_REJECTED,
    CAPTURE_REASON_DTC_COUNT_UNAVAILABLE,

    // DTC read
    DTC_READ_FAILED_ERROR,
    DTC_READ_COUNT_MISMATCH,

    // Shared: the module did not answer on the configured route
    ECU_NO_RESPONSE,

    // Instrument read
    INSTRUMENT_READ_FAILED_ERROR,
    INSTRUMENT_REASON_TRANSPORT_REJECTED,
    INSTRUMENT_REASON_UNRECOGNIZED_RESPONSE,

    // DTC clear
    DTC_CLEAR_FAILED_ERROR,
    DTC_CLEAR_REASON_SESSION_REFUSED,
    DTC_CLEAR_REASON_NO_SEED,
    DTC_CLEAR_REASON_SECURITY_REJECTED,
    DTC_CLEAR_REASON_REJECTED,
    DTC_CLEAR_REASON_COUNT_UNCONFIRMED,

    // Service reminder reset
    SERVICE_RESET_FAILED_ERROR,
    SERVICE_RESET_REASON_INVALID_INPUT,
    SERVICE_RESET_REASON_UNRECOGNIZED_STATUS,
    SERVICE_RESET_REASON_DISTANCE_REJECTED,
    SERVICE_RESET_REASON_DATE_UNCONFIRMED,

    // Cluster fingerprint gate
    GATE_REASON_PROFILE_MISMATCH,
    GATE_REASON_STATUS_MISMATCH,
    GATE_REASON_AUTHORIZED,
}
