package dev.resetlight.profiles

import java.io.InputStream

/**
 * Loads a display-only DTC translation overlay. The overlay never validates or
 * resolves DTC meaning — that stays with the English [DtcDictionary] — so this
 * loader only checks structural shape and key formats, and tolerates a partial
 * set of translated messages (missing codes fall back to English at lookup).
 */
class DtcTranslationLoader {
    fun load(source: InputStream): DtcTranslation = load(source.use(InputStream::readBytes))

    fun load(source: ByteArray): DtcTranslation {
        val document = YamlProfileDocument.parse(source)
        val root = document.root
        val schemaVersion = root.child("schema_version").integer()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw ProfileLoadException(
                "schema_version $schemaVersion is unsupported; expected $SUPPORTED_SCHEMA_VERSION",
            )
        }

        val translation = root.child("translation")
        val locale = translation.child("locale").string()
        if (locale !in SUPPORTED_LOCALES) {
            throw ProfileLoadException("Unsupported translation locale $locale")
        }

        val messages = root.child("messages").readCodeMessages(DTC_EXACT_OR_BASE_CODE, "code")
        val referenceMessages = root.child("reference_messages").readCodeMessages(DTC_BASE_CODE, "base code")
        if (referenceMessages.isEmpty()) {
            throw ProfileLoadException("reference_messages must not be empty")
        }

        return DtcTranslation(
            schemaVersion = schemaVersion,
            locale = locale,
            baseDictionaryId = translation.child("base_dictionary").string(),
            genericSubsystemMessages = translation.child("generic_subsystem_messages").genericSubsystemMessages(),
            unknownMessage = translation.child("unknown_message").string(),
            messages = messages,
            referenceMessages = referenceMessages,
            sourceSha256 = document.sourceSha256,
        )
    }

    /** Reads a code-keyed message mapping, requiring every key to match [keyFormat]. */
    private fun YamlNode.readCodeMessages(keyFormat: Regex, keyKind: String): Map<String, String> =
        mapping().mapValues { (key, node) ->
            if (!keyFormat.matches(key)) {
                throw ProfileLoadException("DTC translation key $key must be an uppercase $keyKind")
            }
            node.string()
        }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        val SUPPORTED_LOCALES = setOf("es", "uk", "fr", "de")
    }
}
