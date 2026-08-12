package dev.resetlight.domain

import java.time.LocalDate

/**
 * The service-interval values the observed one-byte wire encoding can carry:
 * a multiple of [stepKm] between [minKm] and [maxKm], all in wire kilometres.
 * Derived from the ECU profile (`raw_unit_km` 100, raw range 1..255 for the
 * captured Tiger 900), never hardcoded in the UI.
 */
data class ServiceIntervalConstraints(
    val stepKm: Int,
    val minKm: Int,
    val maxKm: Int,
) {
    /**
     * Validates a raw text field value entered in the dashboard's display
     * unit. Returns null when the value is writable as-is, otherwise the
     * error to show. Fractions, negatives and non-numeric input are FORMAT
     * errors; a whole number the wire encoding cannot represent (not a
     * multiple of [stepKm] once converted, or outside [minKm]..[maxKm]) is a
     * RANGE error.
     */
    fun validate(text: String, units: MotorcycleDistanceUnits): ServiceIntervalError? {
        val trimmed = text.trim()
        val display = trimmed.toIntOrNull()
        if (trimmed.isEmpty() || display == null || display <= 0) {
            return ServiceIntervalError.FORMAT
        }
        val wireKm = units.displayToWire(display)
        if (wireKm % stepKm != 0 || wireKm < minKm || wireKm > maxKm) {
            return ServiceIntervalError.RANGE
        }
        return null
    }

    /** [minKm] rendered in the display unit, for the range error message. */
    fun minDisplay(units: MotorcycleDistanceUnits): Int = units.wireToDisplay(minKm)

    /** [maxKm] rendered in the display unit, for the range error message. */
    fun maxDisplay(units: MotorcycleDistanceUnits): Int = units.wireToDisplay(maxKm)

    /** [stepKm] rendered in the display unit, for the range error message. */
    fun stepDisplay(units: MotorcycleDistanceUnits): Int = units.wireToDisplay(stepKm)
}

enum class ServiceIntervalError { FORMAT, RANGE }

/**
 * Date rules for the next-service date: never in the past, at most
 * [MAX_YEARS_AHEAD] years out, defaulting to one year from today. The date
 * picker enforces the same window; these functions are the testable source of
 * truth and the final gate before the write.
 */
object NextServiceDateRules {
    const val MAX_YEARS_AHEAD = 2L

    fun default(today: LocalDate): LocalDate = today.plusYears(1)

    fun earliest(today: LocalDate): LocalDate = today

    fun latest(today: LocalDate): LocalDate = today.plusYears(MAX_YEARS_AHEAD)

    fun isValid(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(earliest(today)) && !date.isAfter(latest(today))
}
