package dev.resetlight.profiles

import java.io.InputStream

class AdapterProfileLoader {
    fun load(source: InputStream): AdapterProfile = load(source.use(InputStream::readBytes))

    fun load(source: ByteArray): AdapterProfile {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        val schemaVersion = root.child("schema_version").integer()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw ProfileLoadException(
                "schema_version $schemaVersion is unsupported; expected $SUPPORTED_SCHEMA_VERSION",
            )
        }

        val adapter = root.child("adapter")
        val identity = adapter.child("identity")
        val protocolIdentity = identity.child("adapter_protocol")
        val transport = adapter.child("transport")
        val discovery = transport.child("discovery")
        val channel = transport.child("channel")
        val framing = transport.child("framing")
        val operations = adapter.child("operations")

        val connect = operations.child("connect")
        val identify = operations.child("identify_adapter")
        val initialize = operations.child("initialize_adapter")
        val disconnect = operations.child("disconnect")

        // Required even though the first loader does not yet interpret individual
        // connect/disconnect steps. An empty operation is not executable evidence.
        connect.child("sequence").requireNonEmptyList()
        disconnect.child("sequence").requireNonEmptyList()

        val discoveryUuid = discovery.child("primary_service_uuid").uuid()
        val commandEndpoint = channel.child("command_endpoint")
        val responseEndpoint = channel.child("response_endpoint")
        val commandEndpointUuid = commandEndpoint.child("service_uuid").uuid()
        val responseEndpointUuid = responseEndpoint.child("service_uuid").uuid()
        val transportKind = transport.child("kind").string()
        if (discoveryUuid != commandEndpointUuid || discoveryUuid != responseEndpointUuid) {
            val label = if (transportKind == "bluetooth_low_energy_gatt") "GATT service UUID" else "SPP UUID"
            throw ProfileLoadException(
                "Adapter $label must match across discovery, command and response endpoints",
            )
        }
        val commandCharacteristicUuid = commandEndpoint.optionalChild("characteristic_uuid")?.uuid()
        val responseCharacteristicUuid = responseEndpoint.optionalChild("characteristic_uuid")?.uuid()
        if (transportKind == "bluetooth_low_energy_gatt" &&
            (commandCharacteristicUuid == null || responseCharacteristicUuid == null)
        ) {
            throw ProfileLoadException("BLE GATT endpoints require characteristic UUIDs")
        }
        val commandProperties = commandEndpoint.optionalChild("properties")
            ?.list()
            ?.map(YamlNode::string)
            .orEmpty()
        if (transportKind == "bluetooth_low_energy_gatt" && "write" !in commandProperties) {
            throw ProfileLoadException("BLE command endpoint must support acknowledged write")
        }

        val identificationCommand = identify.child("command")
        val identificationResponse = identify.child("response")
        val initializationCommands = initialize.child("sequence").requireNonEmptyList().map { command ->
            AdapterInitializationCommand(
                name = command.child("name").string(),
                command = command.child("command_text").string(),
                payloadHex = command.child("payload_hex").string(),
                observedResponse = command.child("observed_response").string(),
            )
        }

        return AdapterProfile(
            schemaVersion = schemaVersion,
            id = adapter.child("id").string(),
            family = adapter.child("family").string(),
            variant = adapter.child("variant").string(),
            manufacturerBrand = adapter.child("manufacturer_brand").string(),
            identity = AdapterIdentity(
                bluetoothName = identity.child("bluetooth_name").profileValue(),
                elmCompatibilityVersion = protocolIdentity
                    .child("elm_compatibility_version")
                    .profileValue(),
                stnChipIdentity = protocolIdentity.child("stn_chip_identity").profileValue(),
            ),
            transport = AdapterTransport(
                kind = transportKind,
                status = transport.child("knowledge_status").status(),
                discoveryStatus = discovery.child("knowledge_status").status(),
                channelStatus = channel.child("knowledge_status").status(),
                framingStatus = framing.child("knowledge_status").status(),
                primaryServiceUuid = discoveryUuid,
                commandCharacteristicUuid = commandCharacteristicUuid,
                responseCharacteristicUuid = responseCharacteristicUuid,
                commandSupportsWriteWithResponse = "write" in commandProperties,
                framing = AdapterFraming(
                    commandEncoding = framing.child("command_encoding").string(),
                    commandTerminatorHex = framing.child("command_terminator_hex").string(),
                    responseCompletionPromptHex = framing
                        .child("response_completion_prompt")
                        .child("hex")
                        .string(),
                    responsesMayFragment = framing.child("responses_may_fragment").boolean(),
                    reassembleUntilPrompt = framing.child("reassemble_until_prompt").boolean(),
                ),
            ),
            operations = AdapterOperations(
                connectStatus = connect.child("knowledge_status").status(),
                identify = AdapterIdentificationOperation(
                    status = identify.child("knowledge_status").status(),
                    command = identificationCommand.child("text").string(),
                    commandTerminatorHex = identificationCommand.child("terminator_hex").string(),
                    expectedIdentity = identificationResponse.child("identity_value").string(),
                ),
                initialize = AdapterInitializationOperation(
                    status = initialize.child("knowledge_status").status(),
                    commands = initializationCommands,
                ),
                disconnectStatus = disconnect.child("knowledge_status").status(),
            ),
            pairingPin = transport
                .child("details")
                .child("baseband_connection")
                .child("pairing_pin")
                .string(),
            sourceSha256 = document.sourceSha256,
        )
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 2
    }
}
