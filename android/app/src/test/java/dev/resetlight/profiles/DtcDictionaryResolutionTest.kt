package dev.resetlight.profiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DtcDictionaryResolutionTest {
    private val dictionary = DtcMapLoader().load(
        generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
    )

    @Test
    fun `prefers observed wording then merged reference wording then generic fallback`() {
        assertEquals(
            "Brake switch 1 and brake switch 2 signals do not match",
            dictionary.descriptionFor("P1577-00").message,
        )
        assertEquals(
            "oxygen sensor heater: shorted to ground; circuit open.",
            dictionary.descriptionFor("P0030-00").message,
        )
        assertEquals(
            DtcMessageStatus.THIRD_PARTY_REFERENCE,
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
