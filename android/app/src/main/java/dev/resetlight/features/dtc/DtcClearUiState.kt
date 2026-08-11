package dev.resetlight.features.dtc

sealed interface DtcClearUiState {
    data object Idle : DtcClearUiState
    data object Running : DtcClearUiState
    data class Cleared(val remainingCount: Int) : DtcClearUiState
    data class Blocked(val reason: String) : DtcClearUiState
    data class Failed(val reason: String) : DtcClearUiState
}
