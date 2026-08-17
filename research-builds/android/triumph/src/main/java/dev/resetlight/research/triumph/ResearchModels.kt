package dev.resetlight.research.triumph

import dev.resetlight.domain.NextServiceDateRules
import dev.resetlight.domain.ServiceIntervalConstraints
import java.time.LocalDate

data class ResearchVehicle(
    val model: String,
    val modelYear: Int,
)

sealed interface VehicleInputValidation {
    data class Valid(val vehicle: ResearchVehicle) : VehicleInputValidation
    data class Invalid(val message: String) : VehicleInputValidation
}

object ResearchVehicleInput {
    private const val MINIMUM_MODEL_YEAR = 1990
    private const val MAXIMUM_MODEL_LENGTH = 80

    fun validate(model: String, year: String, currentYear: Int): VehicleInputValidation {
        val normalizedModel = model.trim().replace(Regex("\\s+"), " ")
        if (normalizedModel.isEmpty()) return VehicleInputValidation.Invalid("Enter the motorcycle model")
        if (normalizedModel.length > MAXIMUM_MODEL_LENGTH) {
            return VehicleInputValidation.Invalid("Model must be at most $MAXIMUM_MODEL_LENGTH characters")
        }
        val parsedYear = year.trim().toIntOrNull()
            ?: return VehicleInputValidation.Invalid("Enter a four-digit model year")
        if (parsedYear !in MINIMUM_MODEL_YEAR..(currentYear + 1)) {
            return VehicleInputValidation.Invalid(
                "Model year must be between $MINIMUM_MODEL_YEAR and ${currentYear + 1}",
            )
        }
        return VehicleInputValidation.Valid(ResearchVehicle(normalizedModel, parsedYear))
    }
}

enum class ResearchScanStage {
    ADAPTER_METADATA,
    ENGINE_IDENTIFIERS,
    DIAGNOSTIC_TROUBLE_CODES,
    INSTRUMENT_CLUSTER,
}

data class ResearchServiceRoundTripRequest(
    val previousDistanceKm: Int,
    val previousNextServiceDate: LocalDate,
    val testDistanceKm: Int,
    val testNextServiceDate: LocalDate,
)

data class ResearchWriteOptions(
    val clearDtcs: Boolean = false,
    val serviceReset: ResearchServiceRoundTripRequest? = null,
) {
    val requested: Boolean get() = clearDtcs || serviceReset != null
}

sealed interface ResearchWriteInputValidation {
    data class Valid(val options: ResearchWriteOptions) : ResearchWriteInputValidation
    data class Invalid(val message: String) : ResearchWriteInputValidation
}

object ResearchWriteInput {
    fun validate(
        clearDtcs: Boolean,
        resetService: Boolean,
        distanceKm: String,
        nextServiceDate: String,
        today: LocalDate,
        constraints: ServiceIntervalConstraints,
    ): ResearchWriteInputValidation {
        if (!resetService) {
            return ResearchWriteInputValidation.Valid(ResearchWriteOptions(clearDtcs = clearDtcs))
        }

        val parsedDistance = distanceKm.trim().toIntOrNull()
            ?: return ResearchWriteInputValidation.Invalid("Enter the current stored service interval in kilometres")
        val testDistance = parsedDistance + constraints.stepKm
        if (parsedDistance !in constraints.minKm..constraints.maxKm ||
            parsedDistance % constraints.stepKm != 0
        ) {
            return ResearchWriteInputValidation.Invalid(
                "Service interval must be ${constraints.minKm} to ${constraints.maxKm} km " +
                    "in ${constraints.stepKm} km increments",
            )
        }

        val parsedDate = runCatching { LocalDate.parse(nextServiceDate.trim()) }.getOrNull()
            ?: return ResearchWriteInputValidation.Invalid("Enter the next service date as YYYY-MM-DD")
        val testDate = parsedDate.plusDays(1)
        if (!NextServiceDateRules.isValid(parsedDate, today) ||
            !NextServiceDateRules.isValid(testDate, today)
        ) {
            return ResearchWriteInputValidation.Invalid(
                "Current date and the +1 day test must both be between today and two years from today",
            )
        }
        if (testDistance > constraints.maxKm) {
            return ResearchWriteInputValidation.Invalid(
                "Current interval must be at most ${constraints.maxKm - constraints.stepKm} km " +
                    "so the +${constraints.stepKm} km test is representable",
            )
        }

        return ResearchWriteInputValidation.Valid(
            ResearchWriteOptions(
                clearDtcs = clearDtcs,
                serviceReset = ResearchServiceRoundTripRequest(
                    previousDistanceKm = parsedDistance,
                    previousNextServiceDate = parsedDate,
                    testDistanceKm = testDistance,
                    testNextServiceDate = testDate,
                ),
            ),
        )
    }
}

data class ResearchSessionRequest(
    val vehicle: ResearchVehicle,
    val writeOptions: ResearchWriteOptions = ResearchWriteOptions(),
)

enum class ResearchWriteOutcome {
    NOT_REQUESTED,
    INELIGIBLE,
    VALIDATED,
    REJECTED,
}

enum class ResearchRestoreOutcome {
    NOT_REQUIRED,
    RESTORED,
    REJECTED,
    UNKNOWN,
}

data class ResearchOperationValidation(
    val outcome: ResearchWriteOutcome = ResearchWriteOutcome.NOT_REQUESTED,
    val detail: String? = null,
    val restoreOutcome: ResearchRestoreOutcome = ResearchRestoreOutcome.NOT_REQUIRED,
)

data class ResearchWriteValidationSummary(
    val serviceReset: ResearchOperationValidation = ResearchOperationValidation(),
    val dtcClear: ResearchOperationValidation = ResearchOperationValidation(),
)

data class ResearchScanSummary(
    val adapterMetadataResponses: Int,
    val identifierAttempts: Int,
    val identifierResponses: Int,
    val dtcReadConfirmed: Boolean,
    val dtcCount: Int?,
    val dtcDetailRecords: Int?,
    val extendedSessionUsed: Boolean,
    val instrumentStatusAscii: String?,
    val odometerKm: Int?,
    val serviceReadConfirmed: Boolean,
    val dtcClearCandidate: Boolean,
    val serviceResetCandidate: Boolean,
    val writeValidation: ResearchWriteValidationSummary = ResearchWriteValidationSummary(),
)

data class ResearchEvent(
    val name: String,
    val outcome: String? = null,
    val text: String? = null,
)

fun interface ResearchEventRecorder {
    fun record(event: ResearchEvent)
}

fun interface ResearchCommandChannel {
    suspend fun execute(command: String): String
}
