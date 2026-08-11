package dev.resetlight.features.connection

import dev.resetlight.transport.bluetooth.BondedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdapterSelectionPolicyTest {
    private val first = BondedDevice("first", "vLinker MC-Android")
    private val second = BondedDevice("second", "vLinker MC-Android")

    @Test
    fun `automatically selects the sole matching bonded adapter`() {
        assertEquals("first", AdapterSelectionPolicy.reconcile(null, listOf(first)))
    }

    @Test
    fun `does not guess when multiple matching adapters exist`() {
        assertNull(AdapterSelectionPolicy.reconcile(null, listOf(first, second)))
    }

    @Test
    fun `preserves a present selection and clears a stale one`() {
        assertEquals("second", AdapterSelectionPolicy.reconcile("second", listOf(first, second)))
        assertNull(AdapterSelectionPolicy.reconcile("missing", listOf(first, second)))
    }
}
