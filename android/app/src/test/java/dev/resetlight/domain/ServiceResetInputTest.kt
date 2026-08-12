package dev.resetlight.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceResetInputTest {
    private val constraints = ServiceIntervalConstraints(stepKm = 100, minKm = 100, maxKm = 25_500)
    private val kmOnly = MotorcycleDistanceUnits(DistanceUnit.KILOMETERS, DistanceUnit.KILOMETERS)

    @Test
    fun `accepts writable whole-hundred intervals`() {
        assertNull(constraints.validate("10000", kmOnly))
        assertNull(constraints.validate("100", kmOnly))
        assertNull(constraints.validate("25500", kmOnly))
        assertNull(constraints.validate(" 8000 ", kmOnly))
    }

    @Test
    fun `rejects fractions negatives and non-numbers as format errors`() {
        assertEquals(ServiceIntervalError.FORMAT, constraints.validate("100.5", kmOnly))
        assertEquals(ServiceIntervalError.FORMAT, constraints.validate("-100", kmOnly))
        assertEquals(ServiceIntervalError.FORMAT, constraints.validate("0", kmOnly))
        assertEquals(ServiceIntervalError.FORMAT, constraints.validate("", kmOnly))
        assertEquals(ServiceIntervalError.FORMAT, constraints.validate("10 000", kmOnly))
        assertEquals(ServiceIntervalError.FORMAT, constraints.validate("abc", kmOnly))
    }

    @Test
    fun `rejects values the one-byte wire encoding cannot represent`() {
        // The 2026-08-12 trip crashed on exactly this class of input.
        assertEquals(ServiceIntervalError.RANGE, constraints.validate("10050", kmOnly))
        assertEquals(ServiceIntervalError.RANGE, constraints.validate("50", kmOnly))
        assertEquals(ServiceIntervalError.RANGE, constraints.validate("25600", kmOnly))
        assertEquals(ServiceIntervalError.RANGE, constraints.validate("30000", kmOnly))
    }

    @Test
    fun `range bounds render in the display unit`() {
        assertEquals(100, constraints.stepDisplay(kmOnly))
        assertEquals(100, constraints.minDisplay(kmOnly))
        assertEquals(25_500, constraints.maxDisplay(kmOnly))
    }

    @Test
    fun `next service date defaults to one year ahead`() {
        val today = LocalDate.of(2026, 8, 12)
        assertEquals(LocalDate.of(2027, 8, 12), NextServiceDateRules.default(today))
    }

    @Test
    fun `next service date allows today through two years ahead`() {
        val today = LocalDate.of(2026, 8, 12)
        assertTrue(NextServiceDateRules.isValid(today, today))
        assertTrue(NextServiceDateRules.isValid(today.plusYears(1), today))
        assertTrue(NextServiceDateRules.isValid(today.plusYears(2), today))
        assertFalse(NextServiceDateRules.isValid(today.minusDays(1), today))
        assertFalse(NextServiceDateRules.isValid(today.plusYears(2).plusDays(1), today))
    }
}
