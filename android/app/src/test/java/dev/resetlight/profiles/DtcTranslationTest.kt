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
    fun `loads a translation overlay without requiring every generic message`() {
        val translation = DtcTranslationLoader().load(
            generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml"),
        )

        assertEquals(1, translation.schemaVersion)
        assertEquals("es", translation.locale)
        assertEquals("triumph-tiger-900-gt-pro-2021-en", translation.baseDictionaryId)
        assertTrue(translation.genericMessages.isEmpty())
    }

    @Test
    fun `overlay translates observed text and falls back to English for untranslated generic text`() {
        val localized = LocalizedDtcDescriptions(
            dictionary,
            DtcTranslationLoader().load(
                generatedProfile("triumph-tiger-900-gt-pro-2021.es.dtctranslation.yaml"),
            ),
        )

        val observed = localized.descriptionFor("P1577-00")
        assertEquals(
            "Código específico de la motocicleta observado; no hay una descripción verificada de forma independiente.",
            observed.message,
        )
        assertEquals(
            "Motorcycle-specific powertrain code observed; no independently verified description is available.",
            observed.originalMessage,
        )
        assertEquals(DtcMessageStatus.VEHICLE_OBSERVED, observed.status)

        val generic = localized.descriptionFor("P0030-00")
        assertEquals(DtcMessageStatus.OPEN_DATA_GENERIC, generic.status)
        assertEquals(dictionary.descriptionFor("P0030-00").message, generic.message)
        assertNull(generic.originalMessage)
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
        val trimmed = translation.copy(genericMessages = translation.genericMessages - "P0030")
        val localized = LocalizedDtcDescriptions(dictionary, trimmed)

        val english = dictionary.descriptionFor("P0030-00")
        val resolved = localized.descriptionFor("P0030-00")
        assertEquals(english.message, resolved.message)
        assertNull(resolved.originalMessage)
    }

    @Test
    fun `German overlay uses the CC0 German generic title`() {
        val localized = LocalizedDtcDescriptions(
            dictionary,
            DtcTranslationLoader().load(
                generatedProfile("triumph-tiger-900-gt-pro-2021.de.dtctranslation.yaml"),
            ),
        )

        val result = localized.descriptionFor("P0030-00")
        assertEquals("Lambdasonden-Heizung Schaltung (Bank 1, Sonde 1)", result.message)
        assertEquals(dictionary.descriptionFor("P0030-00").message, result.originalMessage)
        assertEquals(DtcMessageStatus.OPEN_DATA_GENERIC, result.status)
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
