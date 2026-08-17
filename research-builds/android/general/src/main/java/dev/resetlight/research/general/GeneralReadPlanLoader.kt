package dev.resetlight.research.general

import java.security.MessageDigest
import org.yaml.snakeyaml.Yaml

class GeneralReadPlanException(message: String) : IllegalArgumentException(message)

class GeneralReadPlanLoader {
    fun load(source: ByteArray): GeneralReadPlan {
        val document = Yaml().load<Any>(source.decodeToString()).asMap("root")
        val schemaVersion = document.requiredInt("schema_version")
        if (schemaVersion != 1) throw GeneralReadPlanException("Unsupported schema_version: $schemaVersion")
        val root = document["profile"].asMap("profile")
        val id = root.requiredString("id")
        requireProfileId(id)
        val description = root.requiredString("description")
        val phases = root.requiredList("phases").mapIndexed { index, item ->
            val phase = item.asMap("phases[$index]")
            val phaseId = phase.requiredString("id")
            requireIdentifier(phaseId, "phase id")
            val commands = phase.requiredList("commands").mapIndexed { commandIndex, commandItem ->
                val command = commandItem.asMap("phases[$index].commands[$commandIndex]")
                val name = command.requiredString("name")
                requireIdentifier(name, "command name")
                GeneralProbeCommand(name, normalize(command.requiredString("request")))
            }
            if (commands.isEmpty()) throw GeneralReadPlanException("Phase $phaseId has no commands")
            GeneralProbePhase(phaseId, commands)
        }
        if (phases.isEmpty()) throw GeneralReadPlanException("Plan has no phases")
        rejectDuplicates(phases.map { it.id }, "phase ids")
        rejectDuplicates(phases.flatMap { it.commands }.map { it.name }, "command names")

        val plan = GeneralReadPlan(
            schemaVersion = schemaVersion,
            id = id,
            description = description,
            phases = phases,
            sourceSha256 = MessageDigest.getInstance("SHA-256").digest(source).toHex(),
        )
        val policy = GeneralResearchCommandPolicy(plan)
        plan.phases.flatMap { it.commands }.forEach { command ->
            if (!policy.allows(command.request)) {
                throw GeneralReadPlanException("Prohibited research request: ${command.request}")
            }
        }
        return plan
    }

    private fun requireIdentifier(value: String, label: String) {
        if (!IDENTIFIER.matches(value)) throw GeneralReadPlanException("Invalid $label: $value")
    }

    private fun requireProfileId(value: String) {
        if (!PROFILE_ID.matches(value)) throw GeneralReadPlanException("Invalid plan id: $value")
    }

    private fun rejectDuplicates(values: List<String>, label: String) {
        val duplicate = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        if (duplicate != null) throw GeneralReadPlanException("Duplicate $label: $duplicate")
    }

    private fun Any?.asMap(label: String): Map<*, *> =
        this as? Map<*, *> ?: throw GeneralReadPlanException("$label must be an object")

    private fun Map<*, *>.requiredString(key: String): String =
        (this[key] as? String)?.takeIf { it.isNotBlank() }
            ?: throw GeneralReadPlanException("Missing or invalid $key")

    private fun Map<*, *>.requiredInt(key: String): Int =
        (this[key] as? Number)?.toInt() ?: throw GeneralReadPlanException("Missing or invalid $key")

    private fun Map<*, *>.requiredList(key: String): List<*> =
        this[key] as? List<*> ?: throw GeneralReadPlanException("Missing or invalid $key")

    private fun normalize(command: String): String = command.filterNot(Char::isWhitespace).uppercase()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        val IDENTIFIER = Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")
        val PROFILE_ID = Regex("^[a-z0-9]+(?:[-_][a-z0-9]+)*$")
    }
}
