package dev.resetlight.transport.bluetooth

import android.Manifest
import kotlin.test.assertEquals
import org.junit.Test

class BluetoothPolicyTest {
    @Test
    fun `android 11 uses manifest bluetooth permission without runtime prompt`() {
        assertEquals(emptySet(), BluetoothPermissionPolicy.runtimePermissions(apiLevel = 30))
    }

    @Test
    fun `android 12 and newer require connect permission only`() {
        assertEquals(setOf(Manifest.permission.BLUETOOTH_CONNECT), BluetoothPermissionPolicy.runtimePermissions(apiLevel = 31))
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
}
