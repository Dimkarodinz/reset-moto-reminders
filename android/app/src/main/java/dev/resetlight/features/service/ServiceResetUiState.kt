package dev.resetlight.features.service

import dev.resetlight.domain.UiText
import java.time.LocalDate

sealed interface ServiceResetUiState {
    data object Idle : ServiceResetUiState
    data object Running : ServiceResetUiState
    data class Committed(
        val odometerKm: Int,
        val distanceKm: Int,
        val nextServiceDate: LocalDate,
    ) : ServiceResetUiState
    data class Blocked(val reason: UiText) : ServiceResetUiState
    data class Failed(val reason: UiText) : ServiceResetUiState
}
