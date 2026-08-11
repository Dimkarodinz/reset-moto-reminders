package dev.resetlight.diagnostics

import dev.resetlight.profiles.ServiceReminderOperationProfile
import dev.resetlight.domain.DistanceUnit
import java.time.LocalDate
import java.util.Locale

data class ServiceReminderCommands(
    val distanceRequest: String,
    val dateRequest: String,
)

class ServiceReminderCommandBuilder(
    private val profile: ServiceReminderOperationProfile,
) {
    fun build(distance: Int, nextServiceDate: LocalDate): ServiceReminderCommands =
        build(distance, DistanceUnit.KILOMETERS, nextServiceDate)

    fun build(
        distance: Int,
        unit: DistanceUnit,
        nextServiceDate: LocalDate,
    ): ServiceReminderCommands {
        require(distance % profile.distanceRawUnit == 0) {
            "Distance must use ${profile.distanceRawUnit}-unit increments"
        }
        val rawDistance = distance / profile.distanceRawUnit
        require(rawDistance in profile.distanceMinimumRaw..profile.distanceMaximumRaw) {
            "Distance is outside the supported one-byte range"
        }
        val rawYear = nextServiceDate.year - profile.yearBase
        require(rawYear in 0..0xFF) { "Date year is outside the supported one-byte range" }

        return ServiceReminderCommands(
            distanceRequest = when (unit) {
                DistanceUnit.KILOMETERS -> profile.distanceRequestPrefixKm
                DistanceUnit.MILES -> profile.distanceRequestPrefixMiles
            } + rawDistance.hexByte(),
            dateRequest = buildString {
                append(profile.dateRequestPrefix)
                append(rawYear.hexByte())
                append(nextServiceDate.monthValue.hexByte())
                append(nextServiceDate.dayOfMonth.hexByte())
                append(profile.dateFixedSuffix)
            },
        )
    }
}

private fun Int.hexByte(): String = String.format(Locale.ROOT, "%02X", this)
