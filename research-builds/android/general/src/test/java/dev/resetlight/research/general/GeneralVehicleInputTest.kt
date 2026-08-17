package dev.resetlight.research.general

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeneralVehicleInputTest {
    @Test
    fun `normalizes valid manufacturer model and year`() {
        val valid = assertIs<GeneralVehicleValidation.Valid>(
            GeneralVehicleInput.validate("  Honda ", " Africa   Twin ", "2022", 2026),
        )
        assertEquals(GeneralVehicle("Honda", "Africa Twin", 2022), valid.vehicle)
    }

    @Test
    fun `rejects blank control character oversized and implausible input`() {
        assertIs<GeneralVehicleValidation.Invalid>(GeneralVehicleInput.validate("", "Africa Twin", "2022", 2026))
        assertIs<GeneralVehicleValidation.Invalid>(GeneralVehicleInput.validate("Honda", "\u0000bad", "2022", 2026))
        assertIs<GeneralVehicleValidation.Invalid>(GeneralVehicleInput.validate("H".repeat(81), "Model", "2022", 2026))
        assertIs<GeneralVehicleValidation.Invalid>(GeneralVehicleInput.validate("Honda", "Model", "1989", 2026))
    }
}
