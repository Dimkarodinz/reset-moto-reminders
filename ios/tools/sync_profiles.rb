#!/usr/bin/env ruby
# Generates the typed iOS runtime profile from the repository's YAML maps.
# The YAML files remain the source of truth; do not hand-edit the JSON output.

require "json"
require "yaml"

root = File.expand_path("../..", __dir__)
ecu = YAML.load_file(File.join(root, "ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml"))
dtc = YAML.load_file(File.join(root, "dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"))
adapter = YAML.load_file(File.join(root, "adapter-maps/vlinker-mc-ios.adaptermap.yaml"))

engine = ecu.fetch("motorcycle").fetch("modules").fetch("engine_ecu")
instrument = ecu.fetch("motorcycle").fetch("modules").fetch("instrument_cluster")
engine_commands = engine.fetch("commands")
instrument_reset = instrument.fetch("commands").fetch("reset_service_reminder").fetch("replay_template")
primary = adapter.fetch("adapter").fetch("transport").fetch("channel")
identity = adapter.fetch("adapter").fetch("operations").fetch("identify_adapter").fetch("command")

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

profile = {
  "schemaVersion" => 1,
  "motorcycle" => {
    "id" => ecu.fetch("motorcycle").fetch("id"),
    "manufacturer" => ecu.fetch("motorcycle").fetch("manufacturer"),
    "model" => ecu.fetch("motorcycle").fetch("model"),
    "modelYear" => ecu.fetch("motorcycle").fetch("model_year"),
  },
  "adapter" => {
    "advertisedName" => adapter.fetch("adapter").fetch("identity").fetch("bluetooth_name").fetch("value"),
    "serviceUUID" => primary.fetch("command_endpoint").fetch("service_uuid").sub(/^0x/, ""),
    "commandCharacteristicUUID" => primary.fetch("command_endpoint").fetch("characteristic_uuid").sub(/^0x/, ""),
    "responseCharacteristicUUID" => primary.fetch("response_endpoint").fetch("characteristic_uuid").sub(/^0x/, ""),
    "identifyCommand" => identity.fetch("text"),
    "promptByte" => adapter.fetch("adapter").fetch("transport").fetch("framing").fetch("response_completion_prompt").fetch("hex").to_i(16),
  },
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
  "dtcDescriptions" => descriptions.sort.to_h,
  "dtcDescriptionsByLanguage" => descriptions_by_language,
}

output = File.join(root, "ios/ResetMotoCore/Sources/ResetMotoCore/Resources/tiger-900-profile.json")
File.write(output, JSON.pretty_generate(profile) + "\n")
puts output
