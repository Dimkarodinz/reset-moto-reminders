package dev.resetlight.research.triumph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResearchVehicleInputTest {
    @Test
    fun `normalizes model whitespace and parses a bounded year`() {
        val result = ResearchVehicleInput.validate("  Tiger   900 GT Pro ", "2021", currentYear = 2026)

        val valid = assertIs<VehicleInputValidation.Valid>(result)
        assertEquals("Tiger 900 GT Pro", valid.vehicle.model)
        assertEquals(2021, valid.vehicle.modelYear)
    }

    @Test
    fun `rejects blank or oversized models and implausible years`() {
        assertIs<VehicleInputValidation.Invalid>(ResearchVehicleInput.validate(" ", "2021", 2026))
        assertIs<VehicleInputValidation.Invalid>(ResearchVehicleInput.validate("T".repeat(81), "2021", 2026))
        assertIs<VehicleInputValidation.Invalid>(ResearchVehicleInput.validate("Tiger 900", "not-year", 2026))
        assertIs<VehicleInputValidation.Invalid>(ResearchVehicleInput.validate("Tiger 900", "1989", 2026))
        assertIs<VehicleInputValidation.Invalid>(ResearchVehicleInput.validate("Tiger 900", "2028", 2026))
    }
}
