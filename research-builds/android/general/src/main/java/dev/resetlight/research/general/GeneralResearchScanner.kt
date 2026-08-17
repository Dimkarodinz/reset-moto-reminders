package dev.resetlight.research.general

class GeneralResearchScanner(
    private val plan: GeneralReadPlan,
    private val channel: GeneralResearchChannel,
    private val policy: GeneralResearchCommandPolicy,
    private val events: GeneralResearchEventRecorder,
    private val onPhase: (String) -> Unit = {},
) {
    suspend fun scan(): GeneralResearchSummary {
        var attempted = 0
        var responded = 0
        val phaseResponses = linkedMapOf<String, Int>()

        plan.phases.forEach { phase ->
            onPhase(phase.id)
            var responses = 0
            phase.commands.forEach { command ->
                check(policy.allows(command.request)) { "Research policy rejected ${command.request}" }
                attempted += 1
                events.record(
                    GeneralResearchEvent(
                        name = "general_probe_started",
                        text = "phase=${phase.id} name=${command.name} command=${command.request}",
                    ),
                )
                val response = channel.execute(command.request)
                val useful = response.isUsableResponse()
                if (useful) {
                    responded += 1
                    responses += 1
                }
                events.record(
                    GeneralResearchEvent(
                        name = "general_probe_finished",
                        outcome = if (useful) "response" else "no_response",
                        text = "phase=${phase.id} name=${command.name}",
                    ),
                )
            }
            phaseResponses[phase.id] = responses
        }

        return GeneralResearchSummary(attempted, responded, phaseResponses).also { summary ->
            events.record(
                GeneralResearchEvent(
                    name = "general_scan_finished",
                    outcome = "complete",
                    text = "attempted=${summary.attempted} responded=${summary.responded}",
                ),
            )
        }
    }

    private fun String.isUsableResponse(): Boolean {
        val normalized = trim().uppercase()
        return normalized.isNotEmpty() && NEGATIVE_MARKERS.none { marker -> normalized.contains(marker) }
    }

    private companion object {
        val NEGATIVE_MARKERS = listOf(
            "?",
            "NO DATA",
            "CAN ERROR",
            "BUS INIT",
            "STOPPED",
            "UNABLE TO CONNECT",
            "BUFFER FULL",
            "ERROR",
        )
    }
}
