package dev.resetlight.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DtcMapLoaderTest {
    private val loader = DtcMapLoader()

    @Test
    fun `loads the observed Tiger DTC and merged reference entries`() {
        val dictionary = loader.load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        )

        assertEquals(3, dictionary.schemaVersion)
        assertEquals("triumph-tiger-900-gt-pro-2021-en", dictionary.id)
        assertEquals("Triumph", dictionary.manufacturer)
        assertEquals("engine_ecu", dictionary.moduleKey)
        assertEquals("Brake switch 1 and brake switch 2 signals do not match",
            dictionary.descriptionFor("p1577-00").message)
        assertEquals(
            DtcMessageStatus.THIRD_PARTY_CORROBORATED,
            dictionary.descriptionFor("P1577-00").status,
        )
        assertEquals(1, dictionary.entries.size)
        assertEquals(405, dictionary.referenceEntries.size)
        assertEquals(
            DtcMessageStatus.THIRD_PARTY_REFERENCE,
            dictionary.descriptionFor("P0030-00").status,
        )
    }

    @Test
    fun `pruned low-utility codes remain safely handled by subsystem fallback`() {
        val dictionary = loader.load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        )

        PRUNED_LOW_UTILITY_CODES.forEach { code ->
            assertFalse(code, dictionary.referenceEntries.containsKey(code))
            val result = dictionary.descriptionFor("$code-00")
            assertEquals(code, DtcMessageStatus.GENERIC_CLASSIFICATION, result.status)
            assertTrue(code, result.message.contains("$code-00"))
        }
    }

    @Test
    fun `matches only the declared motorcycle module and ECU family`() {
        val dictionary = loader.load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        )
        val ecu = EcuProfileLoader().load(
            generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"),
        )

        assertTrue(dictionary.isApplicableTo(ecu, "engine_ecu"))
        assertTrue(!dictionary.isApplicableTo(ecu.copy(motorcycleId = "different-bike"), "engine_ecu"))
        assertTrue(!dictionary.isApplicableTo(ecu, "instrument_cluster"))
        assertTrue(
            !dictionary.isApplicableTo(
                ecu.copy(engineEcu = ecu.engineEcu.copy(
                    identity = ecu.engineEcu.identity.copy(family = "different-family"),
                )),
                "engine_ecu",
            ),
        )
    }

    @Test
    fun `uses a base-code entry and then generic subsystem fallback`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
            .decodeToString()
            .replace("P1577-00:", "P1577:")
        val dictionary = loader.load(source.encodeToByteArray())

        assertEquals(
            "Brake switch 1 and brake switch 2 signals do not match",
            dictionary.descriptionFor("P1577-01").message,
        )
        assertEquals(
            "Powertrain diagnostic trouble code P9999-00. " +
                "No validated manufacturer description is available.",
            dictionary.descriptionFor("P9999-00").message,
        )
        assertEquals(
            DtcMessageStatus.GENERIC_CLASSIFICATION,
            dictionary.descriptionFor("P9999-00").status,
        )
        assertEquals(
            "Chassis diagnostic trouble code C1234-56. " +
                "No validated manufacturer description is available.",
            dictionary.descriptionFor("c1234-56").message,
        )
    }

    @Test
    fun `keeps malformed values distinct from valid generic DTCs`() {
        val dictionary = loader.load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
        )

        val result = dictionary.descriptionFor("not-a-dtc")

        assertEquals("Unrecognized diagnostic trouble code format.", result.message)
        assertEquals(DtcMessageStatus.UNKNOWN, result.status)
    }

    @Test
    fun `rejects an unsupported dictionary schema version`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
            .decodeToString()
            .replaceFirst("schema_version: 3", "schema_version: 4")

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(source.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("schema_version"))
    }

    @Test
    fun `rejects an exact key that disagrees with its raw UDS value`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
            .decodeToString()
            .replace("raw_uds_code: \"0x157700\"", "raw_uds_code: \"0x157701\"")

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(source.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("raw_uds_code"))
    }

    @Test
    fun `rejects a non-OEM message that claims OEM confirmation`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
            .decodeToString()
            .replace("oem_confirmed: false", "oem_confirmed: true")

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(source.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("cannot claim OEM confirmation"))
    }

    @Test
    fun `rejects a merged reference count that does not match its entries`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml")
            .decodeToString()
            .replace("declared_entry_count: 405", "declared_entry_count: 404")

        val error = assertThrows(ProfileLoadException::class.java) {
            loader.load(source.encodeToByteArray())
        }

        assertTrue(error.message.orEmpty().contains("declared_entry_count"))
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }

    private companion object {
        val PRUNED_LOW_UTILITY_CODES = listOf(
            "P0059", // bank 2 oxygen-sensor heater on an inline-triple application
            "P0165", // rear throttle self-learning from another engine configuration
            "P0174", // bank 2 lean condition
            "P0175", // bank 2 rich condition
            "P0185", // front throttle self-learning from another engine configuration
            "P0204", // cylinder/injector 4 on a three-cylinder application
            "P0208", // secondary injector 4 on a three-cylinder application
            "P0700", // explicitly names another manufacturer's ride-control system
            "P1204", // injector 4 open/ground fault
            "P1208", // injector 4 battery/temperature fault
        )
    }
}
