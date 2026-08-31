package dev.resetlight.transport.bluetooth

import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GattByteTransportTest {
    @Test
    fun `packet sizing caps payload at the documented CX maximum`() {
        assertEquals(244, BlePacketSizer.payloadBytes(512))
        assertEquals(20, BlePacketSizer.payloadBytes(23))
    }

    @Test
    fun `writer sends one acknowledged chunk at a time`() = runTest {
        val writes = mutableListOf<ByteArray>()
        val writer = SequentialBleWriter(mtu = { 23 }) { chunk -> writes += chunk.copyOf() }

        writer.write(ByteArray(45) { it.toByte() })

        assertEquals(listOf(20, 20, 5), writes.map(ByteArray::size))
        assertContentEquals(ByteArray(45) { it.toByte() }, writes.flatMap(ByteArray::toList).toByteArray())
    }

    @Test
    fun `writer stops immediately when a chunk is rejected`() = runTest {
        var attempts = 0
        val writer = SequentialBleWriter(mtu = { 23 }) {
            attempts += 1
            if (attempts == 2) throw IOException("rejected")
        }

        assertFailsWith<IOException> { writer.write(ByteArray(45)) }
        assertEquals(2, attempts)
    }

    @Test
    fun `BLE discovery requires exact name and service`() {
        val profile = BleDiscoveryProfile(
            profileId = "obdlink-cx",
            expectedName = "OBDLink CX",
            serviceUuid = java.util.UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        )

        assertEquals(
            "obdlink-cx",
            BleDeviceSelector.match("OBDLink CX", setOf(profile.serviceUuid), listOf(profile))?.profileId,
        )
        assertEquals(null, BleDeviceSelector.match("OBDLink CX", emptySet(), listOf(profile)))
        assertEquals(null, BleDeviceSelector.match("Other", setOf(profile.serviceUuid), listOf(profile)))
    }
}
