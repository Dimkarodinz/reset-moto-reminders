package dev.resetlight.profiles

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID

internal class YamlProfileDocument private constructor(
    val root: YamlNode,
    val sourceSha256: String,
) {
    companion object {
        private const val MAX_PROFILE_BYTES = 1_048_576

        fun parse(source: ByteArray): YamlProfileDocument {
            if (source.isEmpty()) throw ProfileLoadException("Profile is empty")
            if (source.size > MAX_PROFILE_BYTES) {
                throw ProfileLoadException("Profile exceeds the $MAX_PROFILE_BYTES byte limit")
            }

            val text = try {
                source.decodeToString(throwOnInvalidSequence = true)
            } catch (error: IllegalArgumentException) {
                throw ProfileLoadException("Profile is not valid UTF-8", error)
            }

            val options = LoaderOptions().apply {
                isAllowDuplicateKeys = false
                maxAliasesForCollections = 50
                codePointLimit = MAX_PROFILE_BYTES
            }
            val parsed = try {
                Yaml(SafeConstructor(options)).load<Any?>(text)
            } catch (error: YAMLException) {
                throw ProfileLoadException("Profile is not valid YAML", error)
            }

            return YamlProfileDocument(
                root = YamlNode(parsed, "profile"),
                sourceSha256 = source.sha256(),
            )
        }
    }
}

internal class YamlNode(
    private val value: Any?,
    private val path: String,
) {
    fun child(key: String): YamlNode {
        val map = asMap()
        if (!map.containsKey(key)) required(path.childPath(key))
        return YamlNode(map[key], path.childPath(key))
    }

    fun string(): String {
        val result = value as? String ?: invalid("must be a string")
        if (result.isBlank()) invalid("must not be blank")
        return result
    }

    fun integer(): Int = when (value) {
        is Byte -> value.toInt()
        is Short -> value.toInt()
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            ?: invalid("must be an integer")
        is BigInteger -> if (
            value >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) &&
            value <= BigInteger.valueOf(Int.MAX_VALUE.toLong())
        ) {
            value.toInt()
        } else {
            invalid("must be an integer")
        }
        else -> invalid("must be an integer")
    }

    fun boolean(): Boolean = value as? Boolean ?: invalid("must be a boolean")

    fun status(): KnowledgeStatus = KnowledgeStatus(string())

    fun uuid(): UUID = try {
        UUID.fromString(string())
    } catch (error: IllegalArgumentException) {
        throw ProfileLoadException("$path must be a UUID", error)
    }

    fun list(): List<YamlNode> {
        val values = value as? List<*> ?: invalid("must be a list")
        return values.mapIndexed { index, item -> YamlNode(item, "$path[$index]") }
    }

    fun requireNonEmptyList(): List<YamlNode> = list().also {
        if (it.isEmpty()) invalid("must not be empty")
    }

    fun mapping(): Map<String, YamlNode> = asMap().entries.associate { (key, item) ->
        val stringKey = key as? String ?: invalid("must use string keys")
        stringKey to YamlNode(item, path.childPath(stringKey))
    }

    fun optionalChild(key: String): YamlNode? {
        val map = asMap()
        return if (map.containsKey(key)) YamlNode(map[key], path.childPath(key)) else null
    }

    private fun asMap(): Map<*, *> = value as? Map<*, *> ?: invalid("must be a mapping")

    private fun invalid(reason: String): Nothing = throw ProfileLoadException("$path $reason")

    private fun required(requiredPath: String): Nothing =
        throw ProfileLoadException("$requiredPath is required")
}

internal fun YamlNode.profileValue(): ProfileValue<String> = ProfileValue(
    value = child("value").string(),
    status = child("status").status(),
)

/**
 * Parses a `generic_subsystem_messages` mapping: one `{code}`-bearing template
 * per DTC subsystem letter, keyed by [DTC_SUBSYSTEMS] with all four required.
 */
internal fun YamlNode.genericSubsystemMessages(): Map<Char, String> {
    val messages = mapping()
        .mapKeys { (key, _) ->
            if (key.length != 1 || key[0] !in DTC_SUBSYSTEMS) {
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
    if (messages.keys != DTC_SUBSYSTEMS) {
        throw ProfileLoadException("Generic DTC messages must define $DTC_SUBSYSTEMS")
    }
    return messages
}

private fun String.childPath(key: String): String =
    if (this == "profile") key else "$this.$key"

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
