package dev.resetlight.transport.bluetooth

import android.Manifest
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import org.junit.Test

class BluetoothPolicyTest {
    @Test
    fun `android 11 uses manifest bluetooth permission without runtime prompt`() {
        assertEquals(setOf(Manifest.permission.ACCESS_FINE_LOCATION), BluetoothPermissionPolicy.runtimePermissions(apiLevel = 30))
    }

    @Test
    fun `android 12 and newer require scan and connect permissions`() {
        assertEquals(
            setOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
            BluetoothPermissionPolicy.runtimePermissions(apiLevel = 31),
        )
    }

    @Test
    fun `bonded candidates are filtered and stable`() {
        val devices = listOf(
            BondedDevice("AA", "Headphones"),
            BondedDevice("CC", "vLinker MC-Android"),
            BondedDevice("BB", "vLinker MC-Android"),
            BondedDevice("CC", "vLinker MC-Android"),
        )
        assertEquals(listOf("BB", "CC"), BondedDeviceSelector.candidates(devices, "vLinker MC-Android").map { it.address })
    }


    @Test
    fun `CX write payload uses negotiated ATT MTU minus protocol overhead`() {
        assertEquals(20, BlePacketSizer.payloadBytes(23))
        assertEquals(244, BlePacketSizer.payloadBytes(247))
        assertFailsWith<IllegalArgumentException> { BlePacketSizer.payloadBytes(3) }
    }

    @Test
    fun `CX writes are split without empty packets`() {
        val payload = ByteArray(45) { it.toByte() }

        val chunks = BlePacketSizer.chunks(payload, mtu = 23)

        assertEquals(listOf(20, 20, 5), chunks.map(ByteArray::size))
        assertEquals(payload.toList(), chunks.flatMap(ByteArray::toList))
        assertEquals(emptyList(), BlePacketSizer.chunks(byteArrayOf(), mtu = 23))
    }
}
