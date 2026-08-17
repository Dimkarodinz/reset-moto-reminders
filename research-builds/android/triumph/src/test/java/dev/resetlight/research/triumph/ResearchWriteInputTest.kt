package dev.resetlight.research.triumph

import dev.resetlight.domain.ServiceIntervalConstraints
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResearchWriteInputTest {
    private val today = LocalDate.of(2026, 8, 17)
    private val constraints = ServiceIntervalConstraints(100, 100, 25_500)

    @Test
    fun `disabled write tests ignore empty operation fields`() {
        val valid = assertIs<ResearchWriteInputValidation.Valid>(
            ResearchWriteInput.validate(
                clearDtcs = false,
                resetService = false,
                distanceKm = "",
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
                distanceKm = "7800",
                nextServiceDate = "2027-08-17",
                today = today,
                constraints = constraints,
            ),
        )

        assertTrue(valid.options.clearDtcs)
        assertEquals(7_800, valid.options.serviceReset?.previousDistanceKm)
        assertEquals(LocalDate.of(2027, 8, 17), valid.options.serviceReset?.previousNextServiceDate)
        assertEquals(7_900, valid.options.serviceReset?.testDistanceKm)
        assertEquals(LocalDate.of(2027, 8, 18), valid.options.serviceReset?.testNextServiceDate)
    }

    @Test
    fun `rejects nonrepresentable distance and out of range date`() {
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, "7850", "2027-08-17", today, constraints),
        )
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, "7800", "2026-08-16", today, constraints),
        )
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, "7800", "2029-08-17", today, constraints),
        )
        assertIs<ResearchWriteInputValidation.Invalid>(
            ResearchWriteInput.validate(false, true, "25500", "2027-08-17", today, constraints),
        )
    }
}
