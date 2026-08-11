package dev.resetlight.features.connection

import dev.resetlight.transport.bluetooth.BondedDevice

object AdapterSelectionPolicy {
    fun reconcile(currentAddress: String?, devices: List<BondedDevice>): String? {
        if (devices.any { it.address == currentAddress }) return currentAddress
        return devices.singleOrNull()?.address
    }
}
