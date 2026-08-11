package dev.resetlight.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DistanceUnitTest {
    @Test
    fun `same unit passes values through unchanged`() {
        val kmOnly = MotorcycleDistanceUnits(DistanceUnit.KILOMETERS, DistanceUnit.KILOMETERS)
        assertEquals(44662, kmOnly.wireToDisplay(44662))
        assertEquals(10000, kmOnly.displayToWire(10000))
    }

    @Test
    fun `km wire with miles display converts both directions`() {
        val units = MotorcycleDistanceUnits(DistanceUnit.KILOMETERS, DistanceUnit.MILES)
        // 44662 km ≈ 27752 mi; 10000 mi ≈ 16093 km.
        assertEquals(27752, units.wireToDisplay(44662))
        assertEquals(16093, units.displayToWire(10000))
    }

    @Test
    fun `miles wire with miles display never converts`() {
        val units = MotorcycleDistanceUnits(DistanceUnit.MILES, DistanceUnit.MILES)
        assertEquals(27751, units.wireToDisplay(27751))
        assertEquals(6000, units.displayToWire(6000))
    }

    @Test
    fun `profile values map to units and reject anything else`() {
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.fromProfileValue("km"))
        assertEquals(DistanceUnit.MILES, DistanceUnit.fromProfileValue("miles"))
        assertThrows(IllegalArgumentException::class.java) {
            DistanceUnit.fromProfileValue("nautical")
        }
    }
}
