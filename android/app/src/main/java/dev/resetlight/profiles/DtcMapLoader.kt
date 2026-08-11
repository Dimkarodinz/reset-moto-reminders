package dev.resetlight.profiles

import java.io.InputStream
import java.util.Locale

class DtcMapLoader {
    fun load(source: InputStream): DtcDictionary = load(source.use(InputStream::readBytes))

    fun load(source: ByteArray): DtcDictionary {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        val schemaVersion = root.child("schema_version").integer()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw ProfileLoadException(
                "schema_version $schemaVersion is unsupported; expected $SUPPORTED_SCHEMA_VERSION",
            )
        }

        val dictionary = root.child("dictionary")
        val applicability = dictionary.child("applicability")
        val referenceCatalog = dictionary.child("reference_catalog")
        val lookup = dictionary.child("lookup")
        val order = lookup.child("order").requireNonEmptyList().map(YamlNode::string)
        if (order != EXPECTED_LOOKUP_ORDER) {
            throw ProfileLoadException("dictionary.lookup.order must be $EXPECTED_LOOKUP_ORDER")
        }

        val entries = root.child("entries").mapping().map { (rawKey, node) ->
            val key = rawKey.uppercaseAsciiStrict()
            if (key != rawKey) throw ProfileLoadException("DTC entry key $rawKey must be uppercase ASCII")
            val status = node.child("message_status").string().toMessageStatus()
            val baseCode = node.child("base_code").string()
            val rawUdsCode = node.optionalChild("raw_uds_code")?.string()
            validateEntry(key, baseCode, rawUdsCode)
            val evidenceNode = node.child("evidence")
            val evidence = DtcMessageEvidence(
                vehicleObserved = evidenceNode.child("vehicle_observed").boolean(),
                sourceKind = evidenceNode.child("message_source_kind").string(),
                sourceName = evidenceNode.child("message_source_name").string(),
                sourceVersion = evidenceNode.child("message_source_version").string(),
                wording = evidenceNode.child("wording").string(),
                oemConfirmed = evidenceNode.child("oem_confirmed").boolean(),
            )
            validateAuthority(status, evidence)
            key to DtcMessage(
                message = node.child("message").string(),
                status = status,
                baseCode = baseCode,
                rawUdsCode = rawUdsCode,
                evidence = evidence,
            )
        }.toMap()

        if (entries.isEmpty()) throw ProfileLoadException("entries must not be empty")

        if (referenceCatalog.child("usage").string() != "third_party_reference_only" ||
            referenceCatalog.child("compatibility_status").string() != "reference_only_unvalidated" ||
            referenceCatalog.child("source_kind").string() != "third_party_tool" ||
            referenceCatalog.child("wording").string() != "project_paraphrase"
        ) {
            throw ProfileLoadException(
                "DTC reference entries must remain unvalidated, third-party paraphrases",
            )
        }
        val referenceEntries = root.child("reference_entries").mapping().map { (key, node) ->
            if (!BASE_CODE.matches(key)) {
                throw ProfileLoadException("DTC reference key $key must be an uppercase base code")
            }
            key to node.string()
        }.toMap()
        val declaredReferenceCount = referenceCatalog.child("declared_entry_count").integer()
        if (declaredReferenceCount != referenceEntries.size) {
            throw ProfileLoadException(
                "declared_entry_count $declaredReferenceCount does not match " +
                    "${referenceEntries.size} reference entries",
            )
        }

        val genericFallbackMessages = lookup.child("generic_subsystem_messages")
            .mapping()
            .mapKeys { (key, _) ->
                if (key.length != 1 || key[0] !in DTC_SYSTEMS) {
                    throw ProfileLoadException("Unsupported generic DTC subsystem $key")
                }
                key[0]
            }
            .mapValues { (_, node) ->
                node.string().also { template ->
                    if ("{code}" !in template) {
                        throw ProfileLoadException("Generic DTC messages must contain {code}")
                    }
                }
            }
        if (genericFallbackMessages.keys != DTC_SYSTEMS) {
            throw ProfileLoadException("Generic DTC messages must define $DTC_SYSTEMS")
        }

        return DtcDictionary(
            schemaVersion = schemaVersion,
            id = dictionary.child("id").string(),
            locale = dictionary.child("locale").string(),
            manufacturer = applicability.child("manufacturer").string(),
            motorcycleProfileIds = applicability.child("motorcycle_profile_ids")
                .requireNonEmptyList().map(YamlNode::string).toSet(),
            moduleKey = applicability.child("module").string(),
            ecuFamily = applicability.child("ecu_family").string(),
            genericFallbackMessages = genericFallbackMessages,
            unknownMessage = lookup.child("unknown_message").string(),
            entries = entries,
            referenceCatalog = DtcReferenceMetadata(
                sourceName = referenceCatalog.child("source_name").string(),
                sourceVersion = referenceCatalog.child("source_version").string(),
            ),
            referenceEntries = referenceEntries,
            sourceSha256 = document.sourceSha256,
        )
    }

    private fun validateEntry(key: String, baseCode: String, rawUdsCode: String?) {
        if (!BASE_CODE.matches(baseCode)) {
            throw ProfileLoadException("DTC base code $baseCode is invalid")
        }
        if (key.length == 8) {
            if (key.take(5) != baseCode) {
                throw ProfileLoadException("DTC entry $key does not match base_code $baseCode")
            }
            val raw = rawUdsCode ?: throw ProfileLoadException("DTC entry $key requires raw_uds_code")
            if (!RAW_CODE.matches(raw) || displayCode(raw) != key) {
                throw ProfileLoadException("DTC entry $key does not match raw_uds_code $raw")
            }
        } else if (key != baseCode) {
            throw ProfileLoadException("Base DTC entry $key does not match base_code $baseCode")
        }
    }

    private fun validateAuthority(status: DtcMessageStatus, evidence: DtcMessageEvidence) {
        if (status == DtcMessageStatus.OEM_CONFIRMED &&
            (!evidence.oemConfirmed || evidence.sourceKind != "oem")
        ) {
            throw ProfileLoadException("OEM-confirmed DTC messages require OEM evidence")
        }
        if (status != DtcMessageStatus.OEM_CONFIRMED && evidence.oemConfirmed) {
            throw ProfileLoadException("Non-OEM DTC messages cannot claim OEM confirmation")
        }
    }

    private fun displayCode(rawCode: String): String {
        val value = rawCode.removePrefix("0x").toInt(16)
        val first = value shr 16
        val second = value shr 8 and 0xFF
        val third = value and 0xFF
        val family = "PCBU"[first shr 6 and 0x03]
        return "%c%X%X%X%X-%02X".format(
            Locale.ROOT,
            family,
            first shr 4 and 0x03,
            first and 0x0F,
            second shr 4,
            second and 0x0F,
            third,
        )
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 3
        val EXPECTED_LOOKUP_ORDER = listOf(
            "exact_code",
            "validated_base_code",
            "reference_base_code",
            "generic_subsystem",
            "invalid_fallback",
        )
        val DTC_SYSTEMS = setOf('P', 'C', 'B', 'U')
        val BASE_CODE = Regex("^[PCBU][0-9A-F]{4}$")
        val RAW_CODE = Regex("^0x[0-9A-F]{6}$")
    }
}

private fun String.toMessageStatus(): DtcMessageStatus =
    DtcMessageStatus.entries.firstOrNull { it.serializedValue == this }
        ?: throw ProfileLoadException("Unknown DTC message_status $this")

private fun String.uppercaseAsciiStrict(): String = buildString(length) {
    this@uppercaseAsciiStrict.forEach { character ->
        if (character.code > 0x7F) throw ProfileLoadException("DTC codes must be ASCII")
        append(if (character in 'a'..'z') character - 32 else character)
    }
}
