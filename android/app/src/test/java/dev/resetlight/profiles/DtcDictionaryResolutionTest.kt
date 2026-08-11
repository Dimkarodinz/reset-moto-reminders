package dev.resetlight.profiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DtcDictionaryResolutionTest {
    private val dictionary = DtcMapLoader().load(
        generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
    )

    @Test
    fun `prefers observed wording then open-data generic wording then subsystem fallback`() {
        assertEquals(
            "Motorcycle-specific powertrain code observed; no independently verified description is available.",
            dictionary.descriptionFor("P1577-00").message,
        )
        assertEquals(
            "HO2S Heater Control Circuit (Bank 1 Sensor 1)",
            dictionary.descriptionFor("P0030-00").message,
        )
        assertEquals(
            DtcMessageStatus.OPEN_DATA_GENERIC,
            dictionary.descriptionFor("P0030-00").status,
        )
        assertEquals(
            DtcMessageStatus.GENERIC_CLASSIFICATION,
            dictionary.descriptionFor("P9999-00").status,
        )
        assertEquals(DtcMessageStatus.UNKNOWN, dictionary.descriptionFor("bad-code").status)
        assertEquals(DtcMessageStatus.UNKNOWN, dictionary.descriptionFor("P0030-invalid").status)
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
