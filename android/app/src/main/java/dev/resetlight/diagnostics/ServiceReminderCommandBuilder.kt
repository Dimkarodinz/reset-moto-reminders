package dev.resetlight.diagnostics

import dev.resetlight.profiles.ServiceReminderOperationProfile
import java.time.LocalDate
import java.util.Locale

data class ServiceReminderCommands(
    val distanceRequest: String,
    val dateRequest: String,
)

class ServiceReminderCommandBuilder(
    private val profile: ServiceReminderOperationProfile,
) {
    fun build(distanceKm: Int, nextServiceDate: LocalDate): ServiceReminderCommands {
        require(distanceKm % profile.distanceRawUnitKm == 0) {
            "Distance must use ${profile.distanceRawUnitKm} km increments"
        }
        val rawDistance = distanceKm / profile.distanceRawUnitKm
        require(rawDistance in profile.distanceMinimumRaw..profile.distanceMaximumRaw) {
            "Distance is outside the supported one-byte range"
        }
        val rawYear = nextServiceDate.year - profile.yearBase
        require(rawYear in 0..0xFF) { "Date year is outside the supported one-byte range" }

        return ServiceReminderCommands(
            distanceRequest = profile.distanceRequestPrefix + rawDistance.hexByte(),
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
