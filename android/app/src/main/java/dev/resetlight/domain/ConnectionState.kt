package dev.resetlight.domain

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object SelectingOrPairing : ConnectionState
    data object Connecting : ConnectionState
    data object Identifying : ConnectionState
    data object Initializing : ConnectionState
    data class AdapterReady(val elmIdentity: String, val stnIdentity: String, val mapId: String) : ConnectionState
    data object Disconnecting : ConnectionState
    data class Failed(val reason: ConnectionFailure) : ConnectionState
}

enum class ConnectionFailure {
    PERMISSION_DENIED,
    PAIRING_REQUIRED,
    TIMEOUT,
    IDENTITY_MISMATCH,
    REMOTE_CLOSE,
    IO,
}

object ConnectionStateMachine {
    fun transition(from: ConnectionState, to: ConnectionState): ConnectionState {
        val allowed = when (from) {
            ConnectionState.Disconnected -> to is ConnectionState.SelectingOrPairing || to is ConnectionState.Connecting
            ConnectionState.SelectingOrPairing -> to is ConnectionState.Connecting || to is ConnectionState.Disconnected
            ConnectionState.Connecting -> to is ConnectionState.Identifying || terminal(to)
            ConnectionState.Identifying -> to is ConnectionState.Initializing || terminal(to)
            ConnectionState.Initializing -> to is ConnectionState.AdapterReady || terminal(to)
            is ConnectionState.AdapterReady -> to is ConnectionState.Disconnecting || to is ConnectionState.Failed
            ConnectionState.Disconnecting -> to is ConnectionState.Disconnected
            is ConnectionState.Failed -> to is ConnectionState.Disconnecting || to is ConnectionState.Disconnected
        }
        check(allowed) { "Illegal connection transition: $from -> $to" }
        return to
    }

    private fun terminal(to: ConnectionState): Boolean =
        to is ConnectionState.Failed || to is ConnectionState.Disconnecting
}
