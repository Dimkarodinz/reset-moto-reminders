package dev.resetlight.profiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcTranslationTest {
    private val dictionary = DtcMapLoader().load(
        generatedProfile("triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml"),
    )

    @Test
    fun `loads the Spanish overlay with full reference coverage`() {
        val translation = DtcTranslationLoader().load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml"),
        )

        assertEquals(1, translation.schemaVersion)
        assertEquals("es", translation.locale)
        assertEquals("triumph-tiger-900-gt-pro-2021-en", translation.baseDictionaryId)
        assertEquals(dictionary.referenceEntries.size, translation.referenceMessages.size)
        assertEquals(dictionary.referenceEntries.keys, translation.referenceMessages.keys)
    }

    @Test
    fun `overlay translates the observed and reference messages while English carries meaning`() {
        val localized = LocalizedDtcDescriptions(
            dictionary,
            DtcTranslationLoader().load(
                generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml"),
            ),
        )

        val observed = localized.descriptionFor("P1577-00")
        assertEquals(
            "Las señales del interruptor de freno 1 y del interruptor de freno 2 no coinciden",
            observed.message,
        )
        assertEquals(
            "Brake switch 1 and brake switch 2 signals do not match",
            observed.originalMessage,
        )
        assertEquals(DtcMessageStatus.THIRD_PARTY_CORROBORATED, observed.status)

        val reference = localized.descriptionFor("P0030-00")
        assertEquals(DtcMessageStatus.THIRD_PARTY_REFERENCE, reference.status)
        assertEquals(dictionary.descriptionFor("P0030-00").message, reference.originalMessage)
        assertTrue(reference.message != reference.originalMessage)
    }

    @Test
    fun `generic and unknown messages localize and keep the English original`() {
        val localized = LocalizedDtcDescriptions(
            dictionary,
            DtcTranslationLoader().load(
                generatedProfile("triumph-tiger-900-gt-pro-2021.uk.dtctranslation.yaml"),
            ),
        )

        val generic = localized.descriptionFor("P9999-00")
        assertEquals(DtcMessageStatus.GENERIC_CLASSIFICATION, generic.status)
        assertTrue("expected the code to be substituted", generic.message.contains("P9999-00"))
        assertEquals(dictionary.descriptionFor("P9999-00").message, generic.originalMessage)

        val unknown = localized.descriptionFor("not-a-dtc")
        assertEquals(DtcMessageStatus.UNKNOWN, unknown.status)
        assertEquals(dictionary.descriptionFor("not-a-dtc").message, unknown.originalMessage)
    }

    @Test
    fun `a missing translation falls back to English with no original marker`() {
        val translation = DtcTranslationLoader().load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml"),
        )
        val trimmed = translation.copy(referenceMessages = translation.referenceMessages - "P0030")
        val localized = LocalizedDtcDescriptions(dictionary, trimmed)

        val english = dictionary.descriptionFor("P0030-00")
        val resolved = localized.descriptionFor("P0030-00")
        assertEquals(english.message, resolved.message)
        assertNull(resolved.originalMessage)
    }

    @Test
    fun `rejects an overlay whose base dictionary does not match`() {
        val translation = DtcTranslationLoader().load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LocalizedDtcDescriptions(dictionary, translation.copy(baseDictionaryId = "other-dictionary"))
        }
    }

    @Test
    fun `rejects an unsupported translation locale`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml")
            .decodeToString()
            .replaceFirst("locale: es", "locale: it")

        val error = assertThrows(ProfileLoadException::class.java) {
            DtcTranslationLoader().load(source.encodeToByteArray())
        }
        assertTrue(error.message.orEmpty().contains("locale"))
    }

    @Test
    fun `rejects a generic template missing the code placeholder`() {
        val source = generatedProfile("triumph-tiger-900-gt-pro-2021.uk.dtctranslation.yaml")
            .decodeToString()
            .replaceFirst("{code}", "")

        val error = assertThrows(ProfileLoadException::class.java) {
            DtcTranslationLoader().load(source.encodeToByteArray())
        }
        assertTrue(error.message.orEmpty().contains("{code}"))
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }
}
