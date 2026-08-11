package dev.resetlight.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterOperationGateTest {
    @Test
    fun `only one complete adapter operation can hold the gate`() {
        val gate = AdapterOperationGate()

        val first = gate.tryAcquire()

        assertNotNull(first)
        assertTrue(gate.inProgress.value)
        assertNull(gate.tryAcquire())

        first!!.close()

        assertFalse(gate.inProgress.value)
        assertNotNull(gate.tryAcquire())
    }

    @Test
    fun `closing a lease twice does not release a newer operation`() {
        val gate = AdapterOperationGate()
        val first = requireNotNull(gate.tryAcquire())
        first.close()
        val second = requireNotNull(gate.tryAcquire())

        first.close()

        assertTrue(gate.inProgress.value)
        assertNull(gate.tryAcquire())
        second.close()
        assertFalse(gate.inProgress.value)
    }
}
