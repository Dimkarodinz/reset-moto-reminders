package dev.resetlight.profiles

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSchemaTest {
    private val json = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory())
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    @Test
    fun `canonical adapter map satisfies schema`() {
        assertValid("adaptermap.schema.json", "vlinker-mc-android.adaptermap.yaml")
        assertValid("adaptermap.schema.json", "obdlink-cx.adaptermap.yaml")
        assertValid("adaptermap.schema.json", "obdlink-mx-android.adaptermap.yaml")
    }

    @Test
    fun `canonical ecu map satisfies schema`() {
        assertValid("ecumap.schema.json", "tiger-900-gt-pro-2021.ecumap.yaml")
    }

    @Test
    fun `canonical DTC dictionary satisfies schema`() {
        assertValid(
            "dtcmap.schema.json",
            "triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml",
        )
    }

    @Test
    fun `canonical DTC translations satisfy the translation schema`() {
        TRANSLATION_LOCALES.forEach { locale ->
            assertValid("dtctranslation.schema.json", "triumph-tiger-900-gt-pro-2021.$locale.dtctranslation.yaml")
        }
    }

    @Test
    fun `DTC translations cover every English reference code`() {
        val root = File("build/generated/profileAssets/profiles")
        val referenceKeys = yaml.readTree(
            File(root, "triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        ).path("reference_entries").fieldNames().asSequence().toSet()

        TRANSLATION_LOCALES.forEach { locale ->
            val translated = yaml.readTree(
                File(root, "triumph-tiger-900-gt-pro-2021.$locale.dtctranslation.yaml"),
            ).path("reference_messages").fieldNames().asSequence().toSet()
            assertEquals("$locale reference coverage", referenceKeys, translated)
        }
    }

    @Test
    fun `DTC schema rejects a lowercase code`() {
        val root = File("build/generated/profileAssets/profiles")
        val schema = factory.getSchema(json.readTree(File(root, "dtcmap.schema.json")))
        val map = yaml.readTree(
            File(root, "triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        ) as com.fasterxml.jackson.databind.node.ObjectNode
        val entries = map.withObject("/entries")
        entries.set<com.fasterxml.jackson.databind.JsonNode>(
            "p1577-00",
            entries.remove("P1577-00"),
        )

        assertTrue(schema.validate(map).isNotEmpty())
    }

    @Test
    fun `ECU map DTC description keys exist in the packaged dictionary`() {
        val root = File("build/generated/profileAssets/profiles")
        val ecuMap = yaml.readTree(File(root, "tiger-900-gt-pro-2021.ecumap.yaml"))
        val dictionary = yaml.readTree(
            File(root, "triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        )
        val referencedKeys = ecuMap.findValues("description_key").map { it.asText() }.toSet()
        val dictionaryKeys = dictionary.path("entries").fieldNames().asSequence().toSet()

        assertTrue("The ECU map should reference at least one DTC description", referencedKeys.isNotEmpty())
        assertEquals(referencedKeys, referencedKeys.intersect(dictionaryKeys))
    }

    @Test
    fun `ecu schema rejects a map without schema version`() {
        val root = File("build/generated/profileAssets/profiles")
        val schema = factory.getSchema(json.readTree(File(root, "ecumap.schema.json")))
        val map = yaml.readTree(File(root, "tiger-900-gt-pro-2021.ecumap.yaml"))
        (map as com.fasterxml.jackson.databind.node.ObjectNode).remove("schema_version")
        assertTrue(schema.validate(map).isNotEmpty())
    }

    private fun assertValid(schemaName: String, mapName: String) {
        val root = File("build/generated/profileAssets/profiles")
        val schema = factory.getSchema(json.readTree(File(root, schemaName)))
        val errors = schema.validate(yaml.readTree(File(root, mapName)))
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    private companion object {
        val TRANSLATION_LOCALES = DtcTranslationLoader.SUPPORTED_LOCALES
    }
}
