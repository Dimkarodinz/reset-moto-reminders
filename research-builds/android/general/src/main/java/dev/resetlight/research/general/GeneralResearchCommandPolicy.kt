package dev.resetlight.research.general

class GeneralResearchCommandPolicy(plan: GeneralReadPlan) {
    private val declared = plan.phases.flatMap { it.commands }.map { normalize(it.request) }.toSet()

    fun allows(command: String): Boolean {
        val normalized = normalize(command)
        return normalized in declared && isReadOnlyStandardCommand(normalized)
    }

    private fun isReadOnlyStandardCommand(command: String): Boolean = when {
        command in ADAPTER_COMMANDS -> true
        command in DTC_READ_COMMANDS -> true
        command.length == 4 && command.startsWith("01") -> command.drop(2) in SUPPORTED_PID_PAGES
        command.length == 4 && command.startsWith("09") -> command.drop(2) in MODULE_INFO_PIDS
        else -> false
    }

    private fun normalize(command: String): String = command.filterNot(Char::isWhitespace).uppercase()

    private companion object {
        val ADAPTER_COMMANDS = setOf("ATRV", "ATDP", "ATDPN", "ATSP0", "ATH1")
        val DTC_READ_COMMANDS = setOf("03", "07", "0A")
        val SUPPORTED_PID_PAGES = setOf("00", "20", "40", "60", "80", "A0")
        val MODULE_INFO_PIDS = setOf("00", "04", "06", "0A")
    }
}
