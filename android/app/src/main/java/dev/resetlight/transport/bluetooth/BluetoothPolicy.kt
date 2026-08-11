package dev.resetlight.transport.bluetooth

object BluetoothPermissionPolicy {
    fun runtimePermissions(apiLevel: Int): Set<String> =
        if (apiLevel >= 31) setOf("android.permission.BLUETOOTH_CONNECT") else emptySet()
}

data class BondedDevice(val address: String, val name: String?)

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
