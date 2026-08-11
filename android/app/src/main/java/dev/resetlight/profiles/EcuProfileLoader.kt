package dev.resetlight.profiles

import java.io.InputStream

class EcuProfileLoader {
    fun load(source: InputStream): EcuProfile = load(source.use(InputStream::readBytes))

    fun load(source: ByteArray): EcuProfile {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        val schemaVersion = root.child("schema_version").integer()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw ProfileLoadException(
                "schema_version $schemaVersion is unsupported; expected $SUPPORTED_SCHEMA_VERSION",
            )
        }

        val motorcycle = root.child("motorcycle")
        val modules = motorcycle.child("modules")
        val engine = modules.child("engine_ecu")
        val instrument = modules.child("instrument_cluster")
        val dtcOperations = loadDtcOperations(engine.child("commands"))

        return EcuProfile(
            schemaVersion = schemaVersion,
            motorcycleId = motorcycle.child("id").string(),
            manufacturer = motorcycle.child("manufacturer").string(),
            model = motorcycle.child("model").string(),
            modelYear = motorcycle.child("model_year").integer(),
            engineEcu = loadModule("engine_ecu", engine),
            instrumentCluster = loadModule("instrument_cluster", instrument),
            engineReadOnlyCapture = loadEngineReadOnlyCapture(engine, dtcOperations.read),
            instrumentReadOnlyCapture = loadInstrumentReadOnlyCapture(instrument),
            engineSecurityAccess = loadEngineSecurityAccess(engine.child("commands")),
            diagnosticTroubleCodes = dtcOperations,
            serviceReminder = loadServiceReminder(instrument.child("commands")),
            sourceSha256 = document.sourceSha256,
        )
    }

    private fun loadEngineReadOnlyCapture(
        engine: YamlNode,
        dtcRead: DiagnosticTroubleCodeReadProfile,
    ): EngineReadOnlyCaptureProfile {
        val commands = engine.child("commands")
        val extendedSession = commands.child("connect").child("observed_sequence")
            .requireNonEmptyList()
            .firstOrNull { it.child("name").string() == "enter_extended_diagnostic_session" }
            ?: throw ProfileLoadException("Observed extended diagnostic session request is required")
        val identifiers = commands.child("read_module_identifiers").child("observed_sequence")
            .requireNonEmptyList()
            .filterNot { it.child("sensitive_response").boolean() }
            .map { identifier ->
                ReadOnlyIdentifierProfile(
                    name = identifier.child("name").string(),
                    elmRequest = identifier.child("elm_request").hexString(),
                )
            }
        if (identifiers.isEmpty()) {
            throw ProfileLoadException("At least one non-sensitive read-only identifier is required")
        }

        return EngineReadOnlyCaptureProfile(
            configurationCommands = engine.child("transport")
                .child("observed_elm_adapter_configuration")
                .requireNonEmptyList()
                .map(YamlNode::string),
            identifierReads = identifiers,
            extendedSessionElmRequest = extendedSession.child("elm_request").hexString(),
            dtcCountElmRequest = dtcRead.countElmRequest,
            dtcDetailElmRequest = dtcRead.detailElmRequest,
        )
    }

    private fun loadEngineSecurityAccess(commands: YamlNode): EngineSecurityAccessProfile {
        val connect = commands.child("connect")
        val sequence = connect.child("observed_sequence").requireNonEmptyList()
        val extended = sequence.firstOrNull { it.child("name").string() == "enter_extended_diagnostic_session" }
            ?: throw ProfileLoadException("Observed extended diagnostic session request is required")
        val seedRequest = sequence.firstOrNull { it.child("name").string() == "request_security_seed" }
            ?: throw ProfileLoadException("Observed SecurityAccess seed request is required")
        val keyRequest = sequence.firstOrNull { it.child("name").string() == "send_security_key" }
            ?: throw ProfileLoadException("Observed SecurityAccess key request is required")
        val derivation = connect.child("seed_key_derivation")
        val multiplier = derivation.child("multiplier").string()
            .removePrefix("0x").removePrefix("0X")
            .toIntOrNull(16)
            ?: throw ProfileLoadException("SecurityAccess seed-key multiplier must be hexadecimal")

        return EngineSecurityAccessProfile(
            extendedSessionElmRequest = extended.child("elm_request").hexString(),
            extendedSessionPositivePrefix = extended.child("observed_response_prefix").hexString(),
            seedRequestElmRequest = seedRequest.child("elm_request").hexString(),
            keyRequestElmPrefix = keyRequest.child("elm_request_pattern").string()
                .substringBefore("<"),
            seedKeyMultiplier = multiplier,
            compatibilityScope = derivation.child("compatibility_scope").string(),
        )
    }

    private fun loadDtcOperations(commands: YamlNode): DiagnosticTroubleCodeOperations {
        val read = commands.child("read_diagnostic_trouble_codes")
        val requests = read.child("request_sequence").requireNonEmptyList()
        if (requests.size < 2) {
            throw ProfileLoadException(
                "motorcycle.modules.engine_ecu.commands.read_diagnostic_trouble_codes.request_sequence must contain count and detail requests",
            )
        }
        val clear = commands.child("clear_diagnostic_trouble_codes")
        val clearResponses = clear.child("observed_response_sequence").requireNonEmptyList()
        val pending = clearResponses.firstOrNull {
            it.child("response").string().startsWith("7F")
        } ?: throw ProfileLoadException("DTC clear response-pending value is required")
        val positive = clearResponses.firstOrNull {
            !it.child("response").string().startsWith("7F")
        } ?: throw ProfileLoadException("DTC clear positive response is required")

        return DiagnosticTroubleCodeOperations(
            read = DiagnosticTroubleCodeReadProfile(
                countElmRequest = requests[0].child("elm_request").hexString(),
                detailElmRequest = requests[1].child("elm_request").hexString(),
                statusMask = requests[0].child("status_mask").string(),
            ),
            clear = DiagnosticTroubleCodeClearProfile(
                elmRequest = clear.child("request").child("elm_request").hexString(),
                pendingResponse = pending.child("response").hexString(),
                positiveResponse = positive.child("response").hexString(),
                verificationElmRequest = clear.child("observed_postcondition")
                    .child("verification_elm_request")
                    .hexString(),
            ),
        )
    }

    private fun loadInstrumentReadOnlyCapture(instrument: YamlNode): InstrumentReadOnlyCaptureProfile {
        val template = instrument.child("commands").child("reset_service_reminder").child("replay_template")
        return InstrumentReadOnlyCaptureProfile(
            configurationCommands = instrument.child("transport")
                .child("observed_elm_adapter_configuration")
                .requireNonEmptyList()
                .map(YamlNode::string),
            initializeElmRequest = template.child("initialize_request").hexString(),
            odometerElmRequest = template.child("odometer_request").hexString(),
            odometerRequestSemantics = template.child("odometer_request_semantics").string(),
            expectedStatusAscii = template.child("initialize_expected_status_ascii").string(),
        )
    }

    private fun loadServiceReminder(commands: YamlNode): ServiceReminderOperationProfile {
        val reset = commands.child("reset_service_reminder")
        val template = reset.child("replay_template")
        val distance = template.child("distance")
        val date = template.child("date")
        return ServiceReminderOperationProfile(
            status = template.child("knowledge_status").status(),
            initializeRequest = template.child("initialize_request").hexString(),
            odometerRequest = template.child("odometer_request").hexString(),
            odometerRequestSemantics = template.child("odometer_request_semantics").string(),
            distanceRequestPrefix = distance.child("request_prefix_km").hexString(),
            distanceRawUnitKm = distance.child("raw_unit_km").integer(),
            distanceMinimumRaw = distance.child("minimum_raw").integer(),
            distanceMaximumRaw = distance.child("maximum_raw").integer(),
            dateRequestPrefix = date.child("request_prefix").hexString(),
            yearBase = date.child("year_base").integer(),
            dateFixedSuffix = date.child("fixed_suffix").hexString(),
            dateFixedSuffixSemantics = date.child("fixed_suffix_semantics").string(),
        )
    }

    private fun loadModule(key: String, module: YamlNode): MotorcycleModuleProfile {
        val identity = module.child("identity")
        val transport = module.child("transport")
        return MotorcycleModuleProfile(
            key = key,
            identity = MotorcycleModuleIdentity(
                role = module.child("role").string(),
                family = module.child("family").string(),
                supplier = module.child("supplier").string(),
                hardwareFamily = identity.child("hardware_family").string(),
                partNumber = identity.child("part_number").string(),
                softwareVersion = identity.child("software_version").string(),
            ),
            transport = MotorcycleModuleTransport(
                status = transport.child("knowledge_status").status(),
                minimalConfigurationConfirmed = transport
                    .child("minimal_configuration_confirmed")
                    .boolean(),
                protocol = transport.child("protocol").string(),
                canIdFormat = transport.child("can_id_format").string(),
                bitrateKbitPerSecond = transport.child("bitrate_kbit_s").integer(),
                requestCanId = transport.child("request_can_id").string(),
                responseCanId = transport.child("response_can_id").string(),
                elmProtocolCommand = transport.child("elm_protocol_command").string(),
                observedElmAdapterConfiguration = transport
                    .child("observed_elm_adapter_configuration")
                    .requireNonEmptyList()
                    .map(YamlNode::string),
            ),
        )
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 3
    }
}

private fun YamlNode.hexString(): String = string().also { value ->
    if (value.length % 2 != 0 || value.any { it !in "0123456789abcdefABCDEF" }) {
        throw ProfileLoadException("Expected an even-length hexadecimal string, got $value")
    }
}
