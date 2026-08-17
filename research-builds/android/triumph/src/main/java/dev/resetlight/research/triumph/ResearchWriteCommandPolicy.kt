package dev.resetlight.research.triumph

import dev.resetlight.diagnostics.WriteIntent
import dev.resetlight.profiles.EcuProfile

/** Exact backstop for the two opt-in experimental write sequences. */
class ResearchWriteCommandPolicy(private val ecu: EcuProfile) {
    private val reads = buildSet {
        addAll(ecu.engineReadOnlyCapture.configurationCommands.map(::normalize))
        addAll(ecu.instrumentReadOnlyCapture.configurationCommands.map(::normalize))
        add(normalize(ecu.engineSecurityAccess.extendedSessionElmRequest))
        add(normalize(ecu.engineSecurityAccess.seedRequestElmRequest))
        add(normalize(ecu.diagnosticTroubleCodes.clear.verificationElmRequest))
        add(normalize(ecu.instrumentReadOnlyCapture.initializeElmRequest))
        add(normalize(ecu.instrumentReadOnlyCapture.odometerElmRequest))
    }

    fun allows(command: String, intent: WriteIntent): Boolean {
        val normalized = normalize(command)
        return when (intent) {
            WriteIntent.READ -> normalized in reads
            WriteIntent.WRITE -> isExactKeyRequest(normalized) ||
                normalized == normalize(ecu.diagnosticTroubleCodes.clear.elmRequest) ||
                isDistanceWrite(normalized) ||
                isDateWrite(normalized)
        }
    }

    fun requireAllowed(command: String, intent: WriteIntent) {
        check(allows(command, intent)) {
            "Research write-validation command is outside the bounded allowlist: $command ($intent)"
        }
    }

    private fun isExactKeyRequest(command: String): Boolean {
        val prefix = normalize(ecu.engineSecurityAccess.keyRequestElmPrefix)
        val key = command.removePrefix(prefix)
        return command.startsWith(prefix) && key.length == 4 && key.all { it in HEX }
    }

    private fun isDistanceWrite(command: String): Boolean {
        val prefix = normalize(ecu.serviceReminder.distanceRequestPrefix)
        val raw = command.removePrefix(prefix)
        if (!command.startsWith(prefix) || raw.length != 2 || raw.any { it !in HEX }) return false
        return raw.toInt(16) in ecu.serviceReminder.distanceMinimumRaw..ecu.serviceReminder.distanceMaximumRaw
    }

    private fun isDateWrite(command: String): Boolean {
        val prefix = normalize(ecu.serviceReminder.dateRequestPrefix)
        val suffix = normalize(ecu.serviceReminder.dateFixedSuffix)
        if (!command.startsWith(prefix) || !command.endsWith(suffix)) return false
        val dateBytes = command.removePrefix(prefix).removeSuffix(suffix)
        if (dateBytes.length != 6 || dateBytes.any { it !in HEX }) return false
        val year = ecu.serviceReminder.yearBase + dateBytes.substring(0, 2).toInt(16)
        val month = dateBytes.substring(2, 4).toInt(16)
        val day = dateBytes.substring(4, 6).toInt(16)
        return runCatching { java.time.LocalDate.of(year, month, day) }.isSuccess
    }

    private companion object {
        val HEX = "0123456789ABCDEF".toSet()
        fun normalize(command: String): String = command.filterNot(Char::isWhitespace).uppercase()
    }
}
