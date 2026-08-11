package dev.resetlight.diagnostics

/**
 * Whether an ELM configuration command was accepted. `ATWS` answers with the
 * identity banner instead of `OK`; every other configuration command must
 * answer with a bare `OK` line.
 */
fun elmConfigurationAccepted(command: String, response: String): Boolean {
    val normalized = response.uppercase()
    return if (command == "ATWS") {
        normalized.contains("ELM327")
    } else {
        normalized.lines().any { it.trim() == "OK" }
    }
}
