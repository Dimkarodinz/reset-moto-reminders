#!/usr/bin/env ruby
# Generates the typed iOS runtime profile from the repository's YAML maps.
# The YAML files remain the source of truth; do not hand-edit the JSON output.

require "json"
require "yaml"

root = File.expand_path("../..", __dir__)
ecu = YAML.load_file(File.join(root, "ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml"))
dtc = YAML.load_file(File.join(root, "dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"))
adapter_maps = [
  YAML.load_file(File.join(root, "adapter-maps/vlinker-mc-ios.adaptermap.yaml")),
  YAML.load_file(File.join(root, "adapter-maps/obdlink-cx.adaptermap.yaml")),
]
engine_family = YAML.load_file(File.join(root, "ecu-maps/triumph-modern-can.enginefamily.yaml"))
instrument_family_maps = %w[
  triumph-original-tft.instrumentfamily.yaml
  triumph-updated-tft.instrumentfamily.yaml
  triumph-hybrid-display.instrumentfamily.yaml
  triumph-adaptive-combined.instrumentfamily.yaml
].map { |name| YAML.load_file(File.join(root, "ecu-maps", name)) }
motorcycle_catalog = YAML.load_file(File.join(root, "ecu-maps/triumph.motorcycleprofiles.yaml"))

engine = ecu.fetch("motorcycle").fetch("modules").fetch("engine_ecu")
instrument = ecu.fetch("motorcycle").fetch("modules").fetch("instrument_cluster")
engine_commands = engine.fetch("commands")
instrument_reset = instrument.fetch("commands").fetch("reset_service_reminder").fetch("replay_template")
instrument_families_by_id = instrument_family_maps.to_h do |map|
  family = map.fetch("instrument_family")
  [family.fetch("id"), family]
end
adapters = adapter_maps.map do |adapter_map|
  adapter = adapter_map.fetch("adapter")
  primary = adapter.fetch("transport").fetch("channel")
  identity = adapter.fetch("operations").fetch("identify_adapter").fetch("command")
  {
    "id" => adapter.fetch("id"),
    "advertisedName" => adapter.fetch("identity").fetch("bluetooth_name").fetch("value"),
    "serviceUUID" => primary.fetch("command_endpoint").fetch("service_uuid").sub(/^0x/, "").sub(/^0000([0-9A-F]{4})-0000-1000-8000-00805F9B34FB$/i, '\\1'),
    "commandCharacteristicUUID" => primary.fetch("command_endpoint").fetch("characteristic_uuid").sub(/^0x/, "").sub(/^0000([0-9A-F]{4})-0000-1000-8000-00805F9B34FB$/i, '\\1'),
    "responseCharacteristicUUID" => primary.fetch("response_endpoint").fetch("characteristic_uuid").sub(/^0x/, "").sub(/^0000([0-9A-F]{4})-0000-1000-8000-00805F9B34FB$/i, '\\1'),
    "identifyCommand" => identity.fetch("text"),
    "promptByte" => adapter.fetch("transport").fetch("framing").fetch("response_completion_prompt").fetch("hex").to_i(16),
    "experimental" => adapter.fetch("id") == "obdlink-cx",
  }
end

descriptions = dtc.fetch("reference_entries").dup
dtc.fetch("entries").each { |code, entry| descriptions[code] = entry.fetch("message") }
dtc.fetch("dictionary").fetch("lookup").fetch("generic_subsystem_messages").each do |prefix, message|
  descriptions["__generic_#{prefix}"] = message
end
descriptions["__unknown"] = dtc.fetch("dictionary").fetch("lookup").fetch("unknown_message")

descriptions_by_language = {"en" => descriptions.sort.to_h}
%w[de es fr uk].each do |locale|
  translation = YAML.load_file(
    File.join(root, "dtc-maps/triumph-tiger-900-gt-pro-2021.#{locale}.dtctranslation.yaml")
  )
  localized = descriptions.merge(translation.fetch("reference_messages"))
  translation.fetch("messages").each { |code, message| localized[code] = message }
  translation.fetch("translation").fetch("generic_subsystem_messages").each do |prefix, message|
    localized["__generic_#{prefix}"] = message
  end
  localized["__unknown"] = translation.fetch("translation").fetch("unknown_message")
  descriptions_by_language[locale] = localized.sort.to_h
end

motorcycles = motorcycle_catalog.fetch("motorcycles").map do |entry|
  instrument_family_id = entry.fetch("instrument_family")
  family = instrument_families_by_id.fetch(instrument_family_id)
  capabilities = entry.fetch("capabilities")
  {
    "id" => entry.fetch("id"),
    "displayName" => entry.fetch("display_name"),
    "modelCodes" => entry.fetch("model_codes"),
    "validationStatus" => entry.fetch("validation_status"),
    "instrumentFamilyID" => instrument_family_id,
    "serviceReminderStrategy" => family.fetch("strategy"),
    "dtcClearStrategy" => entry.fetch("dtc_clear_strategy"),
    "capabilities" => {
      "dtcRead" => capabilities.fetch("dtc_read"),
      "dtcClear" => capabilities.fetch("dtc_clear"),
      "dashboardRead" => capabilities.fetch("dashboard_read"),
      "serviceReset" => capabilities.fetch("service_reset"),
    },
  }
end

combined_instrument_families = instrument_families_by_id.values.map do |family|
  next if family.fetch("strategy") == "original_split"

  transport = family.fetch("module").fetch("transport")
  service = family.fetch("service_reminder")
  reads = service.fetch("read_requests")
  read_for = lambda do |did|
    reads.find { |request| request.end_with?(did) } || raise(KeyError, "Missing read for #{did}")
  end
  {
    "id" => family.fetch("id"),
    "strategy" => family.fetch("strategy"),
    "configurationCommands" => transport.fetch("configuration_commands"),
    "responseCANID" => transport.fetch("response_can_id").sub(/^0x/, ""),
    "requestPrefix" => service.fetch("request_prefix"),
    "inputStepKilometres" => service.fetch("input_step_km"),
    "hybridOdometerDivisorKilometres" => service["hybrid_odometer_divisor_km"],
    "minimumDistanceKilometres" => service.fetch("minimum_distance_km"),
    "maximumDistanceKilometres" => service.fetch("maximum_distance_km"),
    "yearBase" => service.fetch("year_base"),
    "sessionCommand" => service.fetch("session_request"),
    "sessionPositivePrefix" => service.fetch("session_positive_prefix"),
    "seedCommand" => service.fetch("seed_request"),
    "seedPositivePrefix" => service.fetch("seed_positive_prefix"),
    "keyRequestPrefix" => service.fetch("key_request_prefix"),
    "keyPositivePrefix" => service.fetch("key_positive_prefix"),
    "securityKeys" => service.fetch("security_keys").map do |key|
      {
        "id" => key.fetch("id"),
        "timingResponseSuffix" => key["timing_response_suffix"],
        "aesKey" => key.fetch("aes_key"),
      }
    end,
    "a500Command" => read_for.call("A500"),
    "a000Command" => read_for.call("A000"),
    "a010Command" => read_for.call("A010"),
    "writePositiveResponse" => service.fetch("write_positive_response"),
  }
end.compact

profile = {
  "schemaVersion" => 2,
  "motorcycle" => {
    "id" => ecu.fetch("motorcycle").fetch("id"),
    "manufacturer" => ecu.fetch("motorcycle").fetch("manufacturer"),
    "model" => ecu.fetch("motorcycle").fetch("model"),
    "modelYear" => ecu.fetch("motorcycle").fetch("model_year"),
  },
  "motorcycles" => motorcycles,
  "adapters" => adapters,
  "engine" => {
    "configurationCommands" => engine.fetch("transport").fetch("observed_elm_adapter_configuration"),
    "responseCANID" => engine.fetch("transport").fetch("response_can_id").sub(/^0x/, ""),
    "dtcCountCommand" => engine_commands.fetch("read_diagnostic_trouble_codes").fetch("request_sequence")[0].fetch("elm_request"),
    "dtcDetailCommand" => engine_commands.fetch("read_diagnostic_trouble_codes").fetch("request_sequence")[1].fetch("elm_request"),
    "extendedSessionCommand" => engine_commands.fetch("connect").fetch("observed_sequence")[0].fetch("elm_request"),
    "seedCommand" => engine_commands.fetch("connect").fetch("observed_sequence")[1].fetch("elm_request"),
    "keyRequestPrefix" => "042702",
    "seedMultiplier" => engine_commands.fetch("connect").fetch("seed_key_derivation").fetch("multiplier").to_i(16),
    "dtcClearCommand" => engine_commands.fetch("clear_diagnostic_trouble_codes").fetch("request").fetch("elm_request"),
    "identityCommand" => engine_family.fetch("engine_family").fetch("diagnostic_trouble_codes").fetch("identity_request"),
  },
  "instrument" => {
    "configurationCommands" => instrument.fetch("transport").fetch("observed_elm_adapter_configuration"),
    "responseCANID" => instrument.fetch("transport").fetch("response_can_id").sub(/^0x/, ""),
    "statusCommand" => instrument_reset.fetch("initialize_request"),
    "expectedStatusASCII" => instrument_reset.fetch("initialize_expected_status_ascii"),
    "odometerCommand" => instrument_reset.fetch("odometer_request"),
    "distancePrefixKilometres" => instrument_reset.fetch("distance").fetch("request_prefix_by_unit").fetch("km"),
    "distancePrefixMiles" => instrument_reset.fetch("distance").fetch("request_prefix_by_unit").fetch("miles"),
    "distanceRawUnit" => instrument_reset.fetch("distance").fetch("raw_unit"),
    "distanceMinimumRaw" => instrument_reset.fetch("distance").fetch("minimum_raw"),
    "distanceMaximumRaw" => instrument_reset.fetch("distance").fetch("maximum_raw"),
    "datePrefix" => instrument_reset.fetch("date").fetch("request_prefix"),
    "yearBase" => instrument_reset.fetch("date").fetch("year_base"),
    "dateFixedSuffix" => instrument_reset.fetch("date").fetch("fixed_suffix"),
  },
  "combinedInstrumentFamilies" => combined_instrument_families,
  "dtcDescriptions" => descriptions.sort.to_h,
  "dtcDescriptionsByLanguage" => descriptions_by_language,
}

output = File.join(root, "ios/ResetMotoCore/Sources/ResetMotoCore/Resources/tiger-900-profile.json")
File.write(output, JSON.pretty_generate(profile) + "\n")
puts output
