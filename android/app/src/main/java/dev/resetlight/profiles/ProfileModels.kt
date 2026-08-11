package dev.resetlight.profiles

import java.util.UUID

@JvmInline
value class KnowledgeStatus(val value: String)

data class ProfileValue<T>(
    val value: T,
    val status: KnowledgeStatus,
)

data class AdapterProfile(
    val schemaVersion: Int,
    val id: String,
    val family: String,
    val variant: String,
    val manufacturerBrand: String,
    val identity: AdapterIdentity,
    val transport: AdapterTransport,
    val operations: AdapterOperations,
    val pairingPin: String,
    val sourceSha256: String,
)

data class AdapterIdentity(
    val bluetoothName: ProfileValue<String>,
    val elmCompatibilityVersion: ProfileValue<String>,
    val stnChipIdentity: ProfileValue<String>,
)

data class AdapterTransport(
    val kind: String,
    val status: KnowledgeStatus,
    val discoveryStatus: KnowledgeStatus,
    val channelStatus: KnowledgeStatus,
    val framingStatus: KnowledgeStatus,
    val sppServiceUuid: UUID,
    val framing: AdapterFraming,
)

data class AdapterFraming(
    val commandEncoding: String,
    val commandTerminatorHex: String,
    val responseCompletionPromptHex: String,
    val responsesMayFragment: Boolean,
    val reassembleUntilPrompt: Boolean,
)

data class AdapterOperations(
    val connectStatus: KnowledgeStatus,
    val identify: AdapterIdentificationOperation,
    val initialize: AdapterInitializationOperation,
    val disconnectStatus: KnowledgeStatus,
)

data class AdapterIdentificationOperation(
    val status: KnowledgeStatus,
    val command: String,
    val commandTerminatorHex: String,
    val expectedIdentity: String,
)

data class AdapterInitializationOperation(
    val status: KnowledgeStatus,
    val commands: List<AdapterInitializationCommand>,
)

data class AdapterInitializationCommand(
    val name: String,
    val command: String,
    val payloadHex: String,
    val observedResponse: String,
)

data class EcuProfile(
    val schemaVersion: Int,
    val motorcycleId: String,
    val manufacturer: String,
    val model: String,
    val modelYear: Int,
    val engineEcu: MotorcycleModuleProfile,
    val instrumentCluster: MotorcycleModuleProfile,
    val engineReadOnlyCapture: EngineReadOnlyCaptureProfile,
    val instrumentReadOnlyCapture: InstrumentReadOnlyCaptureProfile,
    val engineSecurityAccess: EngineSecurityAccessProfile,
    val diagnosticTroubleCodes: DiagnosticTroubleCodeOperations,
    val serviceReminder: ServiceReminderOperationProfile,
    val sourceSha256: String,
)

/**
 * The observed UDS SecurityAccess handshake for the captured engine ECU. The
 * [seedKeyMultiplier] drives [dev.resetlight.diagnostics.EngineSeedKeyDerivation];
 * it is only executed from the research build for the gated DTC-clear operation.
 */
data class EngineSecurityAccessProfile(
    val extendedSessionElmRequest: String,
    val extendedSessionPositivePrefix: String,
    val seedRequestElmRequest: String,
    val keyRequestElmPrefix: String,
    val seedKeyMultiplier: Int,
    val compatibilityScope: String,
)

data class EngineReadOnlyCaptureProfile(
    val configurationCommands: List<String>,
    val identifierReads: List<ReadOnlyIdentifierProfile>,
    val extendedSessionElmRequest: String,
    val dtcCountElmRequest: String,
    val dtcDetailElmRequest: String,
)

data class ReadOnlyIdentifierProfile(
    val name: String,
    val elmRequest: String,
)

/**
 * Research-only description of the two observed instrument-cluster reads. The
 * cluster is configured on its own 11-bit route before the reads are sent.
 * `0D01` semantics are still unconfirmed in the map, so this profile is only
 * ever used behind the research build and never issues a write.
 */
data class InstrumentReadOnlyCaptureProfile(
    val configurationCommands: List<String>,
    val initializeElmRequest: String,
    val odometerElmRequest: String,
    val odometerRequestSemantics: String,
    val expectedStatusAscii: String,
)

data class DiagnosticTroubleCodeOperations(
    val read: DiagnosticTroubleCodeReadProfile,
    val clear: DiagnosticTroubleCodeClearProfile,
)

data class DiagnosticTroubleCodeReadProfile(
    val countElmRequest: String,
    val detailElmRequest: String,
    val statusMask: String,
)

data class DiagnosticTroubleCodeClearProfile(
    val elmRequest: String,
    val pendingResponse: String,
    val positiveResponse: String,
    val verificationElmRequest: String,
)

data class ServiceReminderOperationProfile(
    val status: KnowledgeStatus,
    val initializeRequest: String,
    val odometerRequest: String,
    val odometerRequestSemantics: String,
    val distanceRequestPrefix: String,
    val distanceRawUnitKm: Int,
    val distanceMinimumRaw: Int,
    val distanceMaximumRaw: Int,
    val dateRequestPrefix: String,
    val yearBase: Int,
    val dateFixedSuffix: String,
    val dateFixedSuffixSemantics: String,
)

data class MotorcycleModuleProfile(
    val key: String,
    val identity: MotorcycleModuleIdentity,
    val transport: MotorcycleModuleTransport,
)

data class MotorcycleModuleIdentity(
    val role: String,
    val family: String,
    val supplier: String,
    val hardwareFamily: String,
    val partNumber: String,
    val softwareVersion: String,
)

data class MotorcycleModuleTransport(
    val status: KnowledgeStatus,
    val minimalConfigurationConfirmed: Boolean,
    val protocol: String,
    val canIdFormat: String,
    val bitrateKbitPerSecond: Int,
    val requestCanId: String,
    val responseCanId: String,
    val elmProtocolCommand: String,
    val observedElmAdapterConfiguration: List<String>,
)

enum class DtcMessageStatus(val serializedValue: String) {
    OEM_CONFIRMED("oem_confirmed"),
    THIRD_PARTY_CORROBORATED("third_party_corroborated"),
    THIRD_PARTY_REFERENCE("third_party_reference"),
    INFERRED("inferred"),
    GENERIC_CLASSIFICATION("generic_classification"),
    UNKNOWN("unknown"),
}

data class DtcMessageEvidence(
    val vehicleObserved: Boolean,
    val sourceKind: String,
    val sourceName: String,
    val sourceVersion: String,
    val wording: String,
    val oemConfirmed: Boolean,
)

data class DtcMessage(
    val message: String,
    val status: DtcMessageStatus,
    val baseCode: String = "",
    val rawUdsCode: String? = null,
    val evidence: DtcMessageEvidence? = null,
)

fun interface DtcDescriptionLookup {
    fun descriptionFor(code: String): DtcMessage
}

data class DtcReferenceMetadata(
    val sourceName: String,
    val sourceVersion: String,
)

data class DtcDictionary(
    val schemaVersion: Int,
    val id: String,
    val locale: String,
    val manufacturer: String,
    val motorcycleProfileIds: Set<String>,
    val moduleKey: String,
    val ecuFamily: String,
    val genericFallbackMessages: Map<Char, String>,
    val unknownMessage: String,
    val entries: Map<String, DtcMessage>,
    val referenceCatalog: DtcReferenceMetadata,
    val referenceEntries: Map<String, String>,
    val sourceSha256: String,
) : DtcDescriptionLookup {
    fun isApplicableTo(profile: EcuProfile, moduleKey: String): Boolean {
        if (this.moduleKey != moduleKey ||
            profile.motorcycleId !in motorcycleProfileIds ||
            profile.manufacturer != manufacturer
        ) {
            return false
        }

        val module = when (moduleKey) {
            "engine_ecu" -> profile.engineEcu
            "instrument_cluster" -> profile.instrumentCluster
            else -> return false
        }
        return module.identity.family == ecuFamily
    }

    override fun descriptionFor(code: String): DtcMessage {
        val normalized = code.uppercaseAscii()
        return mappedDescriptionFor(normalized)
            ?: genericDescription(normalized)
            ?: DtcMessage(unknownMessage, DtcMessageStatus.UNKNOWN)
    }

    fun mappedDescriptionFor(code: String): DtcMessage? {
        val normalized = code.uppercaseAscii()
        if (!DTC_CODE.matches(normalized)) return null
        return entries[normalized]
            ?: entries[normalized.takeIf { it.length >= 5 }?.take(5)]
            ?: referenceEntries[normalized.takeIf { it.length >= 5 }?.take(5)]?.let { message ->
                DtcMessage(
                    message = message,
                    status = DtcMessageStatus.THIRD_PARTY_REFERENCE,
                    baseCode = normalized.take(5),
                    evidence = DtcMessageEvidence(
                        vehicleObserved = false,
                        sourceKind = "third_party_tool",
                        sourceName = referenceCatalog.sourceName,
                        sourceVersion = referenceCatalog.sourceVersion,
                        wording = "project_paraphrase",
                        oemConfirmed = false,
                    ),
                )
            }
    }

    private fun genericDescription(code: String): DtcMessage? {
        if (!DTC_CODE.matches(code)) return null
        val template = genericFallbackMessages[code.first()] ?: return null
        return DtcMessage(
            message = template.replace("{code}", code),
            status = DtcMessageStatus.GENERIC_CLASSIFICATION,
            baseCode = code.take(5),
        )
    }

    private companion object {
        val DTC_CODE = Regex("^[PCBU][0-9A-F]{4}(?:-[0-9A-F]{2})?$")
    }
}

private fun String.uppercaseAscii(): String = buildString(length) {
    this@uppercaseAscii.forEach { character ->
        append(if (character in 'a'..'z') character - 32 else character)
    }
}
