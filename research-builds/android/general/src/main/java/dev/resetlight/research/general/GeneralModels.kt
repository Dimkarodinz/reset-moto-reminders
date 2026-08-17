package dev.resetlight.research.general

data class GeneralVehicle(
    val manufacturer: String,
    val model: String,
    val modelYear: Int,
)

sealed interface GeneralVehicleValidation {
    data class Valid(val vehicle: GeneralVehicle) : GeneralVehicleValidation
    data class Invalid(val message: String) : GeneralVehicleValidation
}

object GeneralVehicleInput {
    private const val MINIMUM_MODEL_YEAR = 1990
    private const val MAXIMUM_TEXT_LENGTH = 80

    fun validate(
        manufacturer: String,
        model: String,
        year: String,
        currentYear: Int,
    ): GeneralVehicleValidation {
        val normalizedManufacturer = normalize(manufacturer)
        val normalizedModel = normalize(model)
        if (normalizedManufacturer.isEmpty()) return GeneralVehicleValidation.Invalid("Enter the manufacturer")
        if (normalizedModel.isEmpty()) return GeneralVehicleValidation.Invalid("Enter the motorcycle model")
        if (normalizedManufacturer.length > MAXIMUM_TEXT_LENGTH || normalizedModel.length > MAXIMUM_TEXT_LENGTH) {
            return GeneralVehicleValidation.Invalid("Manufacturer and model must be at most $MAXIMUM_TEXT_LENGTH characters")
        }
        if (normalizedManufacturer.any(Char::isISOControl) || normalizedModel.any(Char::isISOControl)) {
            return GeneralVehicleValidation.Invalid("Manufacturer and model cannot contain control characters")
        }
        val parsedYear = year.trim().toIntOrNull()
            ?: return GeneralVehicleValidation.Invalid("Enter a four-digit model year")
        if (parsedYear !in MINIMUM_MODEL_YEAR..(currentYear + 1)) {
            return GeneralVehicleValidation.Invalid(
                "Model year must be between $MINIMUM_MODEL_YEAR and ${currentYear + 1}",
            )
        }
        return GeneralVehicleValidation.Valid(
            GeneralVehicle(normalizedManufacturer, normalizedModel, parsedYear),
        )
    }

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
}

data class GeneralProbeCommand(val name: String, val request: String)
data class GeneralProbePhase(val id: String, val commands: List<GeneralProbeCommand>)
data class GeneralReadPlan(
    val schemaVersion: Int,
    val id: String,
    val description: String,
    val phases: List<GeneralProbePhase>,
    val sourceSha256: String,
)

data class GeneralResearchEvent(
    val name: String,
    val outcome: String? = null,
    val text: String? = null,
)

fun interface GeneralResearchEventRecorder {
    fun record(event: GeneralResearchEvent)
}

fun interface GeneralResearchChannel {
    suspend fun execute(command: String): String
}

data class GeneralResearchSummary(
    val attempted: Int,
    val responded: Int,
    val phaseResponses: Map<String, Int>,
)

object GeneralSessionMetadata {
    fun describe(vehicle: GeneralVehicle, adapterProfileSha256: String, plan: GeneralReadPlan): String =
        "manufacturer=${vehicle.manufacturer.quoted()} model=${vehicle.model.quoted()} " +
            "model_year=${vehicle.modelYear} adapter_profile_sha256=$adapterProfileSha256 " +
            "read_plan=${plan.id} read_plan_sha256=${plan.sourceSha256}"

    private fun String.quoted(): String = buildString {
        append('"')
        this@quoted.forEach { character ->
            if (character == '"' || character == '\\') append('\\')
            append(character)
        }
        append('"')
    }
}
