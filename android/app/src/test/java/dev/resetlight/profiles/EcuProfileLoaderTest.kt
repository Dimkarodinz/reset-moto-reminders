package dev.resetlight.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EcuProfileLoaderTest {
    private val loader = EcuProfileLoader()

    @Test
    fun `loads typed engine and instrument identity and transport fields`() {
        val profile = loader.load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))

        assertEquals(3, profile.schemaVersion)
        assertEquals("triumph-tiger-900-gt-pro-2021", profile.motorcycleId)
        assertEquals("Triumph Keihin ECU", profile.engineEcu.identity.family)
        assertEquals("unknown", profile.engineEcu.identity.partNumber)
        assertEquals("ISO 15765-4 CAN", profile.engineEcu.transport.protocol)
        assertEquals("29-bit", profile.engineEcu.transport.canIdFormat)
        assertEquals(500, profile.engineEcu.transport.bitrateKbitPerSecond)
        assertEquals("0x18DAD5F1", profile.engineEcu.transport.requestCanId)
        assertEquals("0x18DAF1D5", profile.engineEcu.transport.responseCanId)
        assertEquals("ATTP7", profile.engineEcu.transport.elmProtocolCommand)

        assertEquals(
            "Triumph Tiger 900 first-generation TFT instrument",
            profile.instrumentCluster.identity.family,
        )
        assertEquals("11-bit", profile.instrumentCluster.transport.canIdFormat)
        assertEquals("0x701", profile.instrumentCluster.transport.requestCanId)
        assertEquals("0x704", profile.instrumentCluster.transport.responseCanId)
        assertEquals("ATTP6", profile.instrumentCluster.transport.elmProtocolCommand)
    }

    @Test
    fun `loads typed DTC and service operation requests from the ECU map`() {
        val profile = loader.load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))

        assertEquals("03190108", profile.diagnosticTroubleCodes.read.countElmRequest)
        assertEquals("03190208", profile.diagnosticTroubleCodes.read.detailElmRequest)
        assertEquals("0414FFFFFF", profile.diagnosticTroubleCodes.clear.elmRequest)
        assertEquals("7F1478", profile.diagnosticTroubleCodes.clear.pendingResponse)
        assertEquals("54", profile.diagnosticTroubleCodes.clear.positiveResponse)

        assertEquals("5E01", profile.serviceReminder.initializeRequest)
        assertEquals("0D01", profile.serviceReminder.odometerRequest)
        assertEquals("33", profile.serviceReminder.distanceRequestPrefixKm)
        assertEquals("34", profile.serviceReminder.distanceRequestPrefixMiles)
        assertEquals(100, profile.serviceReminder.distanceRawUnit)
        assertEquals("5C", profile.serviceReminder.dateRequestPrefix)
        assertEquals(2000, profile.serviceReminder.yearBase)
        assertEquals("016E0000", profile.serviceReminder.dateFixedSuffix)
    }

    @Test
    fun `rejects an unknown ECU schema version`() {
        val yaml = generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml")
            .decodeToString()
            .replaceFirst("schema_version: 3", "schema_version: 2")

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(yaml.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("schema_version"))
    }

    @Test
    fun `rejects a module transport without knowledge status`() {
        val yaml = generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml")
            .decodeToString()
            .replaceFirst(
                "        knowledge_status: observed_and_project_app_replay_validated\n",
                "",
            )

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(yaml.encodeToByteArray())
        }

        assertTrue(
            error.message.orEmpty()
                .contains("motorcycle.modules.engine_ecu.transport.knowledge_status"),
        )
    }

    @Test
    fun `rejects a missing required instrument module`() {
        val source = generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml").decodeToString()

        listOf("engine_ecu", "instrument_cluster").forEach { module ->
            val yaml = source.replaceFirst("    $module:\n", "    removed_$module:\n")
            val error = assertThrows(ProfileLoadException::class.java) {
                loader.load(yaml.encodeToByteArray())
            }

            assertTrue(error.message.orEmpty().contains("motorcycle.modules.$module"))
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
