package dev.resetlight.features.dtc

import dev.resetlight.diagnostics.DecodedDtc
import dev.resetlight.domain.UiText

/**
 * User-facing state of the confirmed-DTC read on the engine ECU. The read runs
 * in the default diagnostic session and needs neither an extended session nor
 * SecurityAccess; this was validated against the motorcycle on 2026-08-10.
 */
sealed interface DtcReadState {
    data object Idle : DtcReadState
    data object Running : DtcReadState
    data class Complete(
        val reportedCount: Int,
        val dtcs: List<DecodedDtc>,
    ) : DtcReadState
    data class Failed(val reason: UiText) : DtcReadState
}
