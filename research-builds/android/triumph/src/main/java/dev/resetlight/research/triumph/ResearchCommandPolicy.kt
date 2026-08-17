package dev.resetlight.research.triumph

import dev.resetlight.profiles.AdapterProfile
import dev.resetlight.profiles.EcuProfile

/**
 * Runtime backstop for the automatic research scan. Commands must be present
 * in the finite scan plan and must also pass a semantic diagnostic check, so a
 * future map edit cannot turn the initial evidence phase into a write or
 * identity collector. Optional writes use a separate, stricter policy.
 */
class ResearchCommandPolicy(
    adapterProfile: AdapterProfile,
    ecuProfile: EcuProfile,
) {
    private val adapterCommands = buildSet {
        addAll(ADAPTER_METADATA_COMMANDS)
        addAll(adapterProfile.operations.initialize.commands.map { it.command.uppercase() })
        addAll(ecuProfile.engineReadOnlyCapture.configurationCommands.map(String::uppercase))
        addAll(ecuProfile.instrumentReadOnlyCapture.configurationCommands.map(String::uppercase))
    }

    private val diagnosticCommands = buildSet {
        addAll(ecuProfile.engineReadOnlyCapture.identifierReads.map { it.elmRequest.uppercase() })
        add(ecuProfile.diagnosticTroubleCodes.read.countElmRequest.uppercase())
        add(ecuProfile.diagnosticTroubleCodes.read.detailElmRequest.uppercase())
        add(ecuProfile.engineReadOnlyCapture.extendedSessionElmRequest.uppercase())
        add(ecuProfile.instrumentReadOnlyCapture.initializeElmRequest.uppercase())
        add(ecuProfile.instrumentReadOnlyCapture.odometerElmRequest.uppercase())
    }

    fun allows(command: String): Boolean {
        val normalized = command.filterNot(Char::isWhitespace).uppercase()
        if (normalized.startsWith("AT")) return normalized in adapterCommands
        if (normalized !in diagnosticCommands) return false
        return diagnosticPayload(normalized)?.isSafeReadOrSessionControl() == true
    }

    fun requireAllowed(command: String) {
        check(allows(command)) { "Research command is outside the bounded allowlist: $command" }
    }

    private fun diagnosticPayload(command: String): ByteArray? {
        if (command.isEmpty() || command.length % 2 != 0 || command.any { it !in HEX }) return null
        val bytes = command.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (bytes.isEmpty()) return null
        val declaredLength = bytes[0].toInt() and 0xFF
        return if (bytes.size > 1 && declaredLength == bytes.size - 1) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun ByteArray.isSafeReadOrSessionControl(): Boolean {
        if (isEmpty()) return false
        val service = first().toInt() and 0xFF
        if (service !in SAFE_SERVICES) return false
        if (service == READ_DATA_BY_IDENTIFIER && size >= 3) {
            val did = ((this[1].toInt() and 0xFF) shl 8) or (this[2].toInt() and 0xFF)
            if (did == VIN_DID || did == ECU_SERIAL_DID) return false
        }
        return true
    }

    private companion object {
        val HEX = "0123456789ABCDEF".toSet()
        val ADAPTER_METADATA_COMMANDS = setOf("ATRV", "ATDP", "ATDPN")
        val SAFE_SERVICES = setOf(0x0D, 0x10, 0x19, 0x22, 0x5E)
        const val READ_DATA_BY_IDENTIFIER = 0x22
        const val VIN_DID = 0xF190
        const val ECU_SERIAL_DID = 0xF18C
    }
}
