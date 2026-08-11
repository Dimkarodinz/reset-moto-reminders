package dev.resetlight.features.service

import dev.resetlight.domain.UiText
import dev.resetlight.domain.DistanceUnit
import java.time.LocalDate

sealed interface ServiceResetUiState {
    data object Idle : ServiceResetUiState
    data object Running : ServiceResetUiState
    data class Committed(
        val odometerKm: Int,
        val distance: Int,
        val distanceUnit: DistanceUnit,
        val nextServiceDate: LocalDate,
    ) : ServiceResetUiState
    data class Blocked(val reason: UiText) : ServiceResetUiState
    data class NeedsInspection(val reason: UiText) : ServiceResetUiState
    data class Failed(val reason: UiText) : ServiceResetUiState
}
