package dev.resetlight.profiles

import java.io.InputStream

enum class ProfileValidationStatus(val serializedValue: String) {
    VALIDATED("validated"),
    EXPERIMENTAL("experimental"),
}

enum class CapabilityStatus(val serializedValue: String) {
    VALIDATED("validated"),
    EXPERIMENTAL("experimental"),
    UNAVAILABLE("unavailable"),
}

enum class DtcClearStrategy(val serializedValue: String) {
    SECURITY_ACCESS("security_access"),
    DIRECT("direct"),
}

enum class ServiceReminderStrategy(val serializedValue: String) {
    ORIGINAL_SPLIT("original_split"),
    UPDATED_COMBINED("updated_combined"),
    HYBRID_COMBINED("hybrid_combined"),
    ADAPTIVE_COMBINED("adaptive_combined"),
}

data class EngineFamilyProfile(
    val schemaVersion: Int,
    val id: String,
    val displayName: String,
    val module: MotorcycleModuleProfile,
    val readOnlyCapture: EngineReadOnlyCaptureProfile,
    val securityAccess: EngineSecurityAccessProfile,
    val identityRequest: String,
    val dtcRead: DiagnosticTroubleCodeReadProfile,
    val dtcClear: DiagnosticTroubleCodeClearProfile,
    val clearStrategies: Set<DtcClearStrategy>,
    val sourceSha256: String,
)

data class CombinedServiceWriteProfile(
    val requestPrefix: String,
    val inputStepKm: Int,
    val hybridOdometerDivisorKm: Int?,
    val minimumDistanceKm: Int,
    val maximumDistanceKm: Int,
    val yearBase: Int,
    val sessionRequest: String,
    val sessionPositivePrefix: String,
    val seedRequest: String,
    val seedPositivePrefix: String,
    val keyRequestPrefix: String,
    val keyPositivePrefix: String,
    val securityKeys: List<InstrumentSecurityKey>,
    val readRequests: List<String>,
    val writePositiveResponse: String,
)

data class InstrumentSecurityKey(
    val id: String,
    val timingResponseSuffix: String?,
    val aesKey: String,
)

fun CombinedServiceWriteProfile.securityKeyFor(sessionResponse: String): InstrumentSecurityKey {
    val normalized = sessionResponse.filter(Char::isLetterOrDigit).uppercase()
    require(normalized.startsWith(sessionPositivePrefix.uppercase())) {
        "Instrument rejected the extended diagnostic session"
    }
    return securityKeys.firstOrNull { key ->
        key.timingResponseSuffix?.let(normalized::endsWith) == true
    } ?: securityKeys.single { it.timingResponseSuffix == null }
}

data class InstrumentFamilyProfile(
    val schemaVersion: Int,
    val id: String,
    val displayName: String,
    val strategy: ServiceReminderStrategy,
    val module: MotorcycleModuleProfile,
    val readOnlyCapture: InstrumentReadOnlyCaptureProfile?,
    val originalSplit: ServiceReminderOperationProfile?,
    val combinedWrite: CombinedServiceWriteProfile?,
    val sourceSha256: String,
)

data class MotorcycleCapabilities(
    val dtcRead: CapabilityStatus,
    val dtcClear: CapabilityStatus,
    val dashboardRead: CapabilityStatus,
    val serviceReset: CapabilityStatus,
)

data class MotorcycleCompatibilityProfile(
    val id: String,
    val displayName: String,
    val modelCodes: List<String>,
    val validationStatus: ProfileValidationStatus,
    val engineFamily: EngineFamilyProfile,
    val instrumentFamily: InstrumentFamilyProfile?,
    val dtcClearStrategy: DtcClearStrategy,
    val capabilities: MotorcycleCapabilities,
)

data class MotorcycleProfileCatalog(
    val schemaVersion: Int,
    val defaultProfileId: String,
    val profiles: List<MotorcycleCompatibilityProfile>,
    val sourceSha256: String,
) {
    val defaultProfile: MotorcycleCompatibilityProfile
        get() = profiles.single { it.id == defaultProfileId }
}

class EngineFamilyProfileLoader {
    fun load(source: InputStream): EngineFamilyProfile = load(source.use(InputStream::readBytes))

    fun load(source: ByteArray): EngineFamilyProfile {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        requireSchema(root)
        val family = root.child("engine_family")
        val module = familyModule("engine_ecu", family.child("module"))
        val capture = family.child("read_only_capture")
        val dtc = family.child("diagnostic_trouble_codes")
        val read = dtc.child("read")
        val clear = dtc.child("clear")
        val security = family.child("security_access")

        val readProfile = DiagnosticTroubleCodeReadProfile(
            countElmRequest = read.child("count_request").familyHex(),
            detailElmRequest = read.child("detail_request").familyHex(),
            statusMask = read.child("status_mask").string(),
        )
        val clearProfile = DiagnosticTroubleCodeClearProfile(
            elmRequest = clear.child("request").familyHex(),
            pendingResponse = clear.child("pending_response").familyHex(),
            positiveResponse = clear.child("positive_response").familyHex(),
            verificationElmRequest = clear.child("verification_request").familyHex(),
        )
        val strategies = clear.child("strategies").requireNonEmptyList()
            .map { it.dtcClearStrategy() }
            .toSet()

        return EngineFamilyProfile(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            id = family.child("id").string(),
            displayName = family.child("display_name").string(),
            module = module,
            readOnlyCapture = EngineReadOnlyCaptureProfile(
                configurationCommands = capture.child("configuration_commands")
                    .requireNonEmptyList().map(YamlNode::string),
                identifierReads = capture.child("identifier_reads").requireNonEmptyList().map { readNode ->
                    ReadOnlyIdentifierProfile(
                        name = readNode.child("name").string(),
                        elmRequest = readNode.child("request").familyHex(),
                    )
                },
                extendedSessionElmRequest = security.child("extended_session_request").familyHex(),
                dtcCountElmRequest = readProfile.countElmRequest,
                dtcDetailElmRequest = readProfile.detailElmRequest,
            ),
            securityAccess = EngineSecurityAccessProfile(
                extendedSessionElmRequest = security.child("extended_session_request").familyHex(),
                extendedSessionPositivePrefix = security.child("extended_session_positive_prefix").familyHex(),
                seedRequestElmRequest = security.child("seed_request").familyHex(),
                keyRequestElmPrefix = security.child("key_request_prefix").familyHex(),
                seedKeyMultiplier = security.child("seed_key_multiplier").string()
                    .removePrefix("0x").removePrefix("0X").toInt(16),
                compatibilityScope = security.child("compatibility_scope").string(),
            ),
            identityRequest = dtc.child("identity_request").familyHex(),
            dtcRead = readProfile,
            dtcClear = clearProfile,
            clearStrategies = strategies,
            sourceSha256 = document.sourceSha256,
        )
    }
}

class InstrumentFamilyProfileLoader {
    fun load(source: InputStream): InstrumentFamilyProfile = load(source.use(InputStream::readBytes))

    fun load(source: ByteArray): InstrumentFamilyProfile {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        requireSchema(root)
        val family = root.child("instrument_family")
        val strategy = family.child("strategy").serviceStrategy()
        val module = familyModule("instrument_cluster", family.child("module"))
        val service = family.child("service_reminder")

        val readOnly = family.optionalChild("read_only_capture")?.let { capture ->
            InstrumentReadOnlyCaptureProfile(
                configurationCommands = capture.child("configuration_commands")
                    .requireNonEmptyList().map(YamlNode::string),
                initializeElmRequest = capture.child("initialize_request").familyHex(),
                odometerElmRequest = capture.child("odometer_request").familyHex(),
                odometerRequestSemantics = capture.child("odometer_semantics").string(),
                expectedStatusAscii = capture.child("expected_status_ascii").string(),
            )
        }
        val original = if (strategy == ServiceReminderStrategy.ORIGINAL_SPLIT) {
            val distance = service.child("distance")
            val date = service.child("date")
            ServiceReminderOperationProfile(
                status = KnowledgeStatus(service.child("knowledge_status").string()),
                initializeRequest = checkNotNull(readOnly).initializeElmRequest,
                odometerRequest = readOnly.odometerElmRequest,
                odometerRequestSemantics = readOnly.odometerRequestSemantics,
                distanceRequestPrefixKm = distance.child("request_prefix_km").familyHex(),
                distanceRequestPrefixMiles = distance.child("request_prefix_miles").familyHex(),
                distanceRawUnit = distance.child("raw_unit").integer(),
                distanceMinimumRaw = distance.child("minimum_raw").integer(),
                distanceMaximumRaw = distance.child("maximum_raw").integer(),
                dateRequestPrefix = date.child("request_prefix").familyHex(),
                yearBase = date.child("year_base").integer(),
                dateFixedSuffix = date.child("fixed_suffix").familyHex(),
                dateFixedSuffixSemantics = date.child("fixed_suffix_semantics").string(),
            )
        } else null
        val combined = if (strategy != ServiceReminderStrategy.ORIGINAL_SPLIT) {
            CombinedServiceWriteProfile(
                requestPrefix = service.child("request_prefix").familyHex(),
                inputStepKm = service.child("input_step_km").integer(),
                hybridOdometerDivisorKm = service.optionalChild("hybrid_odometer_divisor_km")?.integer(),
                minimumDistanceKm = service.child("minimum_distance_km").integer(),
                maximumDistanceKm = service.child("maximum_distance_km").integer(),
                yearBase = service.child("year_base").integer(),
                sessionRequest = service.child("session_request").familyHex(),
                sessionPositivePrefix = service.child("session_positive_prefix").familyHex(),
                seedRequest = service.child("seed_request").familyHex(),
                seedPositivePrefix = service.child("seed_positive_prefix").familyHex(),
                keyRequestPrefix = service.child("key_request_prefix").familyHex(),
                keyPositivePrefix = service.child("key_positive_prefix").familyHex(),
                securityKeys = service.child("security_keys").requireNonEmptyList().map { key ->
                    InstrumentSecurityKey(
                        id = key.child("id").string(),
                        timingResponseSuffix = key.optionalChild("timing_response_suffix")?.familyHex(),
                        aesKey = key.child("aes_key").familyHex().also {
                            if (it.length != 32) {
                                throw ProfileLoadException("Instrument AES keys must contain 16 bytes")
                            }
                        },
                    )
                }.also { keys ->
                    if (keys.count { it.timingResponseSuffix == null } != 1) {
                        throw ProfileLoadException("Instrument security must define exactly one fallback key")
                    }
                },
                readRequests = service.child("read_requests").requireNonEmptyList()
                    .map(YamlNode::familyHex)
                    .also { requests ->
                        if (requests.toSet() != COMBINED_SERVICE_READ_REQUESTS) {
                            throw ProfileLoadException(
                                "Combined service reset must define exactly A500, A000 and A010 reads",
                            )
                        }
                    },
                writePositiveResponse = service.child("write_positive_response").familyHex(),
            ).also { profile ->
                if (profile.inputStepKm <= 0) {
                    throw ProfileLoadException("Combined service input step must be positive")
                }
                if (strategy in setOf(
                        ServiceReminderStrategy.HYBRID_COMBINED,
                        ServiceReminderStrategy.ADAPTIVE_COMBINED,
                    ) && (profile.hybridOdometerDivisorKm ?: 0) <= 0
                ) {
                    throw ProfileLoadException("Hybrid combined service encoding requires a positive odometer divisor")
                }
            }
        } else null

        return InstrumentFamilyProfile(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            id = family.child("id").string(),
            displayName = family.child("display_name").string(),
            strategy = strategy,
            module = module,
            readOnlyCapture = readOnly,
            originalSplit = original,
            combinedWrite = combined,
            sourceSha256 = document.sourceSha256,
        )
    }
}

class MotorcycleProfileCatalogLoader {
    fun load(
        source: InputStream,
        engineFamilies: Map<String, EngineFamilyProfile>,
        instrumentFamilies: Map<String, InstrumentFamilyProfile>,
    ): MotorcycleProfileCatalog = load(source.use(InputStream::readBytes), engineFamilies, instrumentFamilies)

    fun load(
        source: ByteArray,
        engineFamilies: Map<String, EngineFamilyProfile>,
        instrumentFamilies: Map<String, InstrumentFamilyProfile>,
    ): MotorcycleProfileCatalog {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        requireSchema(root)
        val defaultId = root.child("default_profile").string()
        val profiles = root.child("motorcycles").requireNonEmptyList().map { motorcycle ->
            val id = motorcycle.child("id").string()
            val engine = engineFamilies[motorcycle.child("engine_family").string()]
                ?: throw ProfileLoadException("Motorcycle $id references an unknown engine family")
            val instrumentId = motorcycle.child("instrument_family").string()
            val instrument = if (instrumentId == "none") null else instrumentFamilies[instrumentId]
                ?: throw ProfileLoadException("Motorcycle $id references an unknown instrument family")
            val validation = motorcycle.child("validation_status").validationStatus()
            val clearStrategy = motorcycle.child("dtc_clear_strategy").dtcClearStrategy()
            if (clearStrategy !in engine.clearStrategies) {
                throw ProfileLoadException("Motorcycle $id selects an unsupported DTC clear strategy")
            }
            val capabilityNode = motorcycle.child("capabilities")
            val capabilities = MotorcycleCapabilities(
                dtcRead = capabilityNode.child("dtc_read").capabilityStatus(),
                dtcClear = capabilityNode.child("dtc_clear").capabilityStatus(),
                dashboardRead = capabilityNode.child("dashboard_read").capabilityStatus(),
                serviceReset = capabilityNode.child("service_reset").capabilityStatus(),
            )
            if (instrument == null &&
                (capabilities.dashboardRead != CapabilityStatus.UNAVAILABLE ||
                    capabilities.serviceReset != CapabilityStatus.UNAVAILABLE)
            ) {
                throw ProfileLoadException("Motorcycle $id exposes instrument capabilities without an instrument family")
            }
            if (capabilities.serviceReset != CapabilityStatus.UNAVAILABLE &&
                instrument?.originalSplit == null && instrument?.combinedWrite == null
            ) {
                throw ProfileLoadException(
                    "Motorcycle $id exposes a service reset without an executable instrument strategy",
                )
            }
            if (validation == ProfileValidationStatus.EXPERIMENTAL &&
                capabilities.asList().any { it == CapabilityStatus.VALIDATED }
            ) {
                throw ProfileLoadException("Experimental motorcycle $id cannot claim a validated capability")
            }
            MotorcycleCompatibilityProfile(
                id = id,
                displayName = motorcycle.child("display_name").string(),
                modelCodes = motorcycle.child("model_codes").requireNonEmptyList().map(YamlNode::string),
                validationStatus = validation,
                engineFamily = engine,
                instrumentFamily = instrument,
                dtcClearStrategy = clearStrategy,
                capabilities = capabilities,
            )
        }
        if (profiles.distinctBy { it.id }.size != profiles.size) {
            throw ProfileLoadException("Motorcycle profile IDs must be unique")
        }
        val duplicateModelCodes = profiles.flatMap { it.modelCodes }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateModelCodes.isNotEmpty()) {
            throw ProfileLoadException(
                "Motorcycle model codes must be unique: ${duplicateModelCodes.sorted().joinToString()}",
            )
        }
        val default = profiles.singleOrNull { it.id == defaultId }
            ?: throw ProfileLoadException("default_profile must reference exactly one motorcycle")
        if (default.validationStatus != ProfileValidationStatus.VALIDATED) {
            throw ProfileLoadException("default_profile must be validated")
        }
        return MotorcycleProfileCatalog(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            defaultProfileId = defaultId,
            profiles = profiles,
            sourceSha256 = document.sourceSha256,
        )
    }
}

private fun requireSchema(root: YamlNode) {
    val version = root.child("schema_version").integer()
    if (version != SUPPORTED_SCHEMA_VERSION) {
        throw ProfileLoadException("schema_version $version is unsupported; expected $SUPPORTED_SCHEMA_VERSION")
    }
}

private fun familyModule(key: String, node: YamlNode): MotorcycleModuleProfile {
    val transport = node.child("transport")
    return MotorcycleModuleProfile(
        key = key,
        identity = MotorcycleModuleIdentity(
            role = node.child("role").string(),
            family = node.child("family").string(),
            supplier = node.child("supplier").string(),
            hardwareFamily = node.child("hardware_family").string(),
            partNumber = node.child("part_number").string(),
            softwareVersion = node.child("software_version").string(),
        ),
        transport = MotorcycleModuleTransport(
            status = transport.child("knowledge_status").status(),
            minimalConfigurationConfirmed = transport.child("minimal_configuration_confirmed").boolean(),
            protocol = transport.child("protocol").string(),
            canIdFormat = transport.child("can_id_format").string(),
            bitrateKbitPerSecond = transport.child("bitrate_kbit_s").integer(),
            requestCanId = transport.child("request_can_id").string(),
            responseCanId = transport.child("response_can_id").string(),
            elmProtocolCommand = transport.child("elm_protocol_command").string(),
            observedElmAdapterConfiguration = transport.child("configuration_commands")
                .requireNonEmptyList().map(YamlNode::string),
        ),
    )
}

private fun YamlNode.familyHex(): String = string().also { value ->
    if (value.length % 2 != 0 || value.any { it !in "0123456789abcdefABCDEF" }) {
        throw ProfileLoadException("Expected an even-length hexadecimal string, got $value")
    }
}

private fun YamlNode.validationStatus(): ProfileValidationStatus = enumValue(
    ProfileValidationStatus.entries,
    ProfileValidationStatus::serializedValue,
    "validation status",
)

private fun YamlNode.capabilityStatus(): CapabilityStatus = enumValue(
    CapabilityStatus.entries,
    CapabilityStatus::serializedValue,
    "capability status",
)

private fun YamlNode.dtcClearStrategy(): DtcClearStrategy = enumValue(
    DtcClearStrategy.entries,
    DtcClearStrategy::serializedValue,
    "DTC clear strategy",
)

private fun YamlNode.serviceStrategy(): ServiceReminderStrategy = enumValue(
    ServiceReminderStrategy.entries,
    ServiceReminderStrategy::serializedValue,
    "service reminder strategy",
)

private fun <T> YamlNode.enumValue(values: Iterable<T>, serialized: (T) -> String, label: String): T {
    val raw = string()
    return values.firstOrNull { serialized(it) == raw }
        ?: throw ProfileLoadException("Unsupported $label $raw")
}

private fun MotorcycleCapabilities.asList(): List<CapabilityStatus> =
    listOf(dtcRead, dtcClear, dashboardRead, serviceReset)

private const val SUPPORTED_SCHEMA_VERSION = 1
private val COMBINED_SERVICE_READ_REQUESTS = setOf("0322A500", "0322A000", "0322A010")
