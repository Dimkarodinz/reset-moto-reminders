package dev.resetlight.domain

import kotlin.math.roundToInt

/**
 * A distance unit. The ECU has a *wire* unit (what it sends and accepts over
 * the adapter) and the dashboard has a *display* unit (what the rider reads).
 * They are usually the same, but a miles-market bike can store km on the wire
 * while showing miles, so the two are modeled independently per ECU profile.
 */
enum class DistanceUnit {
    KILOMETERS,
    MILES,
    ;

    companion object {
        private const val KM_PER_MILE = 1.609344

        fun fromProfileValue(value: String): DistanceUnit = when (value) {
            "km" -> KILOMETERS
            "miles" -> MILES
            else -> throw IllegalArgumentException("Unsupported distance unit $value")
        }

        /** Converts [value] from [from] to [to], rounding to the nearest whole unit. */
        fun convert(value: Int, from: DistanceUnit, to: DistanceUnit): Int = when {
            from == to -> value
            from == KILOMETERS && to == MILES -> (value / KM_PER_MILE).roundToInt()
            else -> (value * KM_PER_MILE).roundToInt()
        }
    }
}

/**
 * The distance units for one motorcycle: the ECU's [wire] unit and the dash's
 * [display] unit. When they match, values pass through unchanged; when they
 * differ (e.g. a miles dash over a km wire), the app converts at the edge so
 * the rider always sees the dash's unit while the ECU still gets wire values.
 */
data class MotorcycleDistanceUnits(
    val wire: DistanceUnit,
    val display: DistanceUnit,
) {
    /** A wire value (as the ECU reports it) rendered in the display unit. */
    fun wireToDisplay(wireValue: Int): Int = DistanceUnit.convert(wireValue, wire, display)

    /** A display value (as the rider entered it) converted to the wire unit. */
    fun displayToWire(displayValue: Int): Int = DistanceUnit.convert(displayValue, display, wire)
}
