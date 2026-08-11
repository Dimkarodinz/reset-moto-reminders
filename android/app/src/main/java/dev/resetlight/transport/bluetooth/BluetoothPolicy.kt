package dev.resetlight.transport.bluetooth

import android.Manifest

object BluetoothPermissionPolicy {
    fun runtimePermissions(apiLevel: Int): Set<String> =
        if (apiLevel >= 31) {
            setOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
}

object BlePacketSizer {
    private const val ATT_OVERHEAD = 3
    private const val CX_MAXIMUM_PAYLOAD = 244

    fun payloadBytes(mtu: Int): Int {
        require(mtu > ATT_OVERHEAD) { "ATT MTU must exceed protocol overhead" }
        return minOf(mtu - ATT_OVERHEAD, CX_MAXIMUM_PAYLOAD)
    }

    fun chunks(bytes: ByteArray, mtu: Int): List<ByteArray> =
        if (bytes.isEmpty()) emptyList() else bytes.asList().chunked(payloadBytes(mtu)).map { it.toByteArray() }
}

data class BondedDevice(
    val address: String,
    val name: String?,
    val profileId: String = "",
    val experimental: Boolean = false,
)

object BondedDeviceSelector {
    fun candidates(devices: Collection<BondedDevice>, expectedName: String): List<BondedDevice> = devices
        .asSequence()
        .filter { it.name == expectedName }
        .distinctBy(BondedDevice::address)
        .sortedBy(BondedDevice::address)
        .toList()

    /** True when [address] is present in the bonded set. */
    fun isBonded(devices: Collection<BondedDevice>, address: String): Boolean =
        devices.any { it.address == address }
}
