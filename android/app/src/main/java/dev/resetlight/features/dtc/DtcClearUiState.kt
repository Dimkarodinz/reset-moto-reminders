package dev.resetlight.features.dtc

import dev.resetlight.domain.UiText

sealed interface DtcClearUiState {
    data object Idle : DtcClearUiState
    data object Running : DtcClearUiState
    data class Cleared(val remainingCount: Int) : DtcClearUiState
    data class Blocked(val reason: UiText) : DtcClearUiState
    data class Failed(val reason: UiText) : DtcClearUiState
}
