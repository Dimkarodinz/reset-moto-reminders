package dev.resetlight.adapter.elm

import dev.resetlight.profiles.AdapterProfile

data class AdapterIdentityResult(
    val elm: String,
    val stn: String,
)

class AdapterIdentityMismatch(message: String) : Exception(message)

class AdapterInitializer(
    private val session: ElmCommandSession,
) {
    suspend fun initialize(profile: AdapterProfile, onStage: suspend (InitializationStage) -> Unit = {}): AdapterIdentityResult {
        onStage(InitializationStage.IDENTIFYING)
        val elm = session.execute(profile.operations.identify.command).normalizedText
        requireIdentity("ELM", profile.operations.identify.expectedIdentity, elm)

        onStage(InitializationStage.INITIALIZING)
        var stn = ""
        profile.operations.initialize.commands.forEach { command ->
            val response = session.execute(command.command).normalizedText
            if (command.command.equals("STI", ignoreCase = true)) {
                stn = response
                requireIdentity("STN", profile.identity.stnChipIdentity.value, stn)
            } else if (!response.lineSequence().any { it.equals("OK", ignoreCase = true) }) {
                throw AdapterIdentityMismatch("Unexpected response to ${command.command}: $response")
            }
        }
        if (stn.isBlank()) throw AdapterIdentityMismatch("Adapter profile did not produce an STN identity")
        return AdapterIdentityResult(elm = elm, stn = stn)
    }

    private fun requireIdentity(kind: String, expected: String, actual: String) {
        val prefixMatch = expected.endsWith("*")
        val value = expected.removeSuffix("*")
        if (!actual.lineSequence().map(String::trim).any {
                when {
                    !prefixMatch -> it.equals(value, ignoreCase = true)
                    // Product names contain spaces. Treat their wildcard as a
                    // version suffix boundary, so `OBDLink MX*` accepts
                    // `OBDLink MX v5.6.19` but never the different `MX+`
                    // product. Chip-family prefixes such as `STN*` retain the
                    // compact `STN1170` form.
                    value.contains(' ') ->
                        it.equals(value, ignoreCase = true) ||
                            it.startsWith("$value ", ignoreCase = true)
                    else -> it.startsWith(value, ignoreCase = true)
                }
            }
        ) {
            throw AdapterIdentityMismatch("$kind identity mismatch: expected '$expected', received '$actual'")
        }
    }
}

enum class InitializationStage { IDENTIFYING, INITIALIZING }
