package dev.resetlight.research.triumph

import dev.resetlight.domain.DistanceUnit
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResearchWriteInputTest {
    private val today = LocalDate.of(2026, 8, 17)
    private val constraints = ResearchDistanceConstraints(100, 100, 25_500)

    @Test
    fun `disabled write tests ignore empty operation fields`() {
        val valid = assertIs<ResearchWriteInputValidation.Valid>(
            ResearchWriteInput.validate(
                clearDtcs = false,
                resetService = false,
                distanceUnit = DistanceUnit.KILOMETERS,
                distance = "",
                nextServiceDate = "",
                today = today,
                constraints = constraints,
            ),
        )

        assertTrue(!valid.options.clearDtcs)
        assertNull(valid.options.serviceReset)
    }

    @Test
    fun `builds explicit clear and minimal service round trip options`() {
        val valid = assertIs<ResearchWriteInputValidation.Valid>(
            ResearchWriteInput.validate(
                clearDtcs = true,
                resetService = true,
                distanceUnit = DistanceUnit.KILOMETERS,
                distance = "7800",
                nextServiceDate = "2027-08-17",
                today = today,
                constraints = constraints,
            ),
        )

        assertTrue(valid.options.clearDtcs)
        assertEquals(DistanceUnit.KILOMETERS, valid.options.serviceReset?.distanceUnit)
        assertEquals(7_800, valid.options.serviceReset?.previousDistance)
        assertEquals(LocalDate.of(2027, 8, 17), valid.options.serviceReset?.previousNextServiceDate)
        assertEquals(7_900, valid.options.serviceReset?.testDistance)
        assertEquals(LocalDate.of(2027, 8, 18), valid.options.serviceReset?.testNextServiceDate)
    }

    @Test
    fun `builds the same minimal round trip in miles without conversion`() {
        val valid = assertIs<ResearchWriteInputValidation.Valid>(
            ResearchWriteInput.validate(
                clearDtcs = false,
                resetService = true,
                distanceUnit = DistanceUnit.MILES,
                distance = "6000",
                nextServiceDate = "2027-08-17",
                today = today,
                constraints = constraints,
            ),
        )

        assertEquals(DistanceUnit.MILES, valid.options.serviceReset?.distanceUnit)
        assertEquals(6_000, valid.options.serviceReset?.previousDistance)
        assertEquals(6_100, valid.options.serviceReset?.testDistance)
    }

    @Test
    fun `rejects nonrepresentable distance and out of range date`() {
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, DistanceUnit.KILOMETERS, "7850", "2027-08-17", today, constraints),
        )
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, DistanceUnit.KILOMETERS, "7800", "2026-08-16", today, constraints),
        )
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, DistanceUnit.KILOMETERS, "7800", "2029-08-17", today, constraints),
        )
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, DistanceUnit.KILOMETERS, "25500", "2027-08-17", today, constraints),
        )
    }
}
