package dev.resetlight.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class AdapterProfileLoaderTest {
    private val loader = AdapterProfileLoader()

    @Test
    fun `loads the generated MC Android profile into typed values`() {
        val bytes = generatedProfile("vlinker-mc-android.adaptermap.yaml")

        val profile = loader.load(bytes)

        assertEquals(2, profile.schemaVersion)
        assertEquals("vlinker-mc-android", profile.id)
        assertEquals("vLinker MC-Android", profile.identity.bluetoothName.value)
        assertEquals(KnowledgeStatus("observed"), profile.identity.bluetoothName.status)
        assertEquals("ELM327 v2.2", profile.identity.elmCompatibilityVersion.value)
        assertEquals("STN1151 v4.3.2", profile.identity.stnChipIdentity.value)
        assertEquals("bluetooth_classic_rfcomm", profile.transport.kind)
        assertEquals(
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"),
            profile.transport.sppServiceUuid,
        )
        assertEquals("1234", profile.pairingPin)
        assertEquals("ATWS", profile.operations.identify.command)
        assertEquals("ELM327 v2.2", profile.operations.identify.expectedIdentity)
        assertEquals(
            listOf("ATE0", "ATL0", "ATS0", "STI", "ATH1"),
            profile.operations.initialize.commands.map(AdapterInitializationCommand::command),
        )
        assertEquals(sha256(bytes), profile.sourceSha256)
    }

    @Test
    fun `hash represents the exact source bytes`() {
        val original = generatedProfile("vlinker-mc-android.adaptermap.yaml")
        val edited = original + byteArrayOf('\n'.code.toByte())

        assertNotEquals(loader.load(original).sourceSha256, loader.load(edited).sourceSha256)
    }

    @Test
    fun `rejects an unknown adapter schema version`() {
        val yaml = generatedProfile("vlinker-mc-android.adaptermap.yaml")
            .decodeToString()
            .replaceFirst("schema_version: 2", "schema_version: 99")

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(yaml.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("schema_version"))
    }

    @Test
    fun `rejects a required operation without knowledge status`() {
        val yaml = generatedProfile("vlinker-mc-android.adaptermap.yaml")
            .decodeToString()
            .replaceFirst(
                "    disconnect:\n      knowledge_status: observed\n",
                "    disconnect:\n",
            )

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(yaml.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("adapter.operations.disconnect.knowledge_status"))
    }

    @Test
    fun `rejects a missing required adapter operation`() {
        val source = generatedProfile("vlinker-mc-android.adaptermap.yaml").decodeToString()

        listOf("connect", "identify_adapter", "initialize_adapter", "disconnect").forEach { operation ->
            val yaml = source.replaceFirst("    $operation:\n", "    removed_$operation:\n")
            val error = assertThrows(ProfileLoadException::class.java) {
                loader.load(yaml.encodeToByteArray())
            }

            assertTrue(error.message.orEmpty().contains("adapter.operations.$operation"))
        }
    }

    @Test
    fun `rejects mismatched SPP endpoint UUIDs`() {
        val yaml = generatedProfile("vlinker-mc-android.adaptermap.yaml")
            .decodeToString()
            .replaceFirst(
                "primary_service_uuid: 00001101-0000-1000-8000-00805f9b34fb",
                "primary_service_uuid: 00001102-0000-1000-8000-00805f9b34fb",
            )

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(yaml.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("SPP UUID"))
    }

    @Test
    fun `rejects an identity value without evidence status`() {
        val yaml = generatedProfile("vlinker-mc-android.adaptermap.yaml")
            .decodeToString()
            .replaceFirst(
                "    bluetooth_name:\n      value: vLinker MC-Android\n      status: observed\n",
                "    bluetooth_name:\n      value: vLinker MC-Android\n",
            )

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(yaml.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("adapter.identity.bluetooth_name.status"))
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
