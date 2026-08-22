package dev.resetlight.app

import dev.resetlight.domain.UiMessage
import dev.resetlight.domain.UiText
import dev.resetlight.features.dtc.DtcClearFailure
import dev.resetlight.features.dtc.DtcClearUiState
import dev.resetlight.features.service.ServiceReminderResetFailure
import dev.resetlight.features.service.ServiceReminderResetResult
import dev.resetlight.features.service.ServiceResetUiState
import dev.resetlight.profiles.DiagnosticTroubleCodeClearProfile
import dev.resetlight.profiles.ServiceReminderOperationProfile

/** Converts protocol outcomes into wording that never encourages a blind retry. */
internal object WriteOutcomePolicy {
    fun serviceResult(result: ServiceReminderResetResult): ServiceResetUiState = when (result) {
        is ServiceReminderResetResult.Committed -> ServiceResetUiState.Committed(
            odometerKm = result.odometerKm,
            distance = result.distance,
            distanceUnit = result.distanceUnit,
            nextServiceDate = result.nextServiceDate,
        )
        is ServiceReminderResetResult.Blocked -> {
            if (result.reason.key == UiMessage.SERVICE_RESET_REASON_DATE_UNCONFIRMED) {
                ServiceResetUiState.NeedsInspection(
                    UiText(UiMessage.SERVICE_RESET_REASON_PARTIALLY_APPLIED),
                )
            } else {
                ServiceResetUiState.Blocked(result.reason)
            }
        }
    }

    fun serviceFailure(
        failure: ServiceReminderResetFailure,
        profile: ServiceReminderOperationProfile,
    ): ServiceResetUiState.NeedsInspection? {
        val request = failure.request.uppercase()
        val isReminderWrite =
            request.startsWith(profile.distanceRequestPrefixKm.uppercase()) ||
                request.startsWith(profile.distanceRequestPrefixMiles.uppercase()) ||
                request.startsWith(profile.dateRequestPrefix.uppercase())
        if (!isReminderWrite) return null
        return ServiceResetUiState.NeedsInspection(
            UiText(UiMessage.SERVICE_RESET_REASON_WRITE_AMBIGUOUS),
        )
    }

    fun dtcFailure(
        failure: DtcClearFailure,
        profile: DiagnosticTroubleCodeClearProfile,
    ): DtcClearUiState.NeedsVerification? = when {
        failure.request.equals(profile.elmRequest, ignoreCase = true) -> DtcClearUiState.NeedsVerification(
            UiText(UiMessage.DTC_CLEAR_REASON_WRITE_AMBIGUOUS),
        )
        failure.request.equals(profile.verificationElmRequest, ignoreCase = true) ->
            DtcClearUiState.NeedsVerification(
                UiText(UiMessage.DTC_CLEAR_REASON_VERIFICATION_LOST),
            )
        else -> null
    }

}
