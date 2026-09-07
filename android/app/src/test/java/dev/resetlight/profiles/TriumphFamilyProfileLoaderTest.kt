package dev.resetlight.profiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TriumphFamilyProfileLoaderTest {
    private val engine = EngineFamilyProfileLoader().load(generatedProfile(ENGINE_MAP))
    private val instruments = INSTRUMENT_MAPS.associate { name ->
        InstrumentFamilyProfileLoader().load(generatedProfile(name)).let { it.id to it }
    }

    @Test
    fun `modern engine family holds one shared route and two explicit clear strategies`() {
        assertEquals("triumph-modern-can-engine", engine.id)
        assertEquals("0x18DAD5F1", engine.module.transport.requestCanId)
        assertEquals("0x18DAF1D5", engine.module.transport.responseCanId)
        assertEquals("03190108", engine.dtcRead.countElmRequest)
        assertEquals("0414FFFFFF", engine.dtcClear.elmRequest)
        assertEquals("0322F18C", engine.identityRequest)
        assertEquals(
            setOf(DtcClearStrategy.SECURITY_ACCESS, DtcClearStrategy.DIRECT),
            engine.clearStrategies,
        )
    }

    @Test
    fun `instrument families keep incompatible encodings separate`() {
        val original = instruments.getValue("triumph-original-tft")
        val updated = instruments.getValue("triumph-updated-tft")
        val hybrid = instruments.getValue("triumph-hybrid-display")
        val adaptive = instruments.getValue("triumph-adaptive-combined")

        assertEquals(ServiceReminderStrategy.ORIGINAL_SPLIT, original.strategy)
        assertEquals("0x701", original.module.transport.requestCanId)
        assertEquals("33", original.originalSplit?.distanceRequestPrefixKm)
        assertNull(original.combinedWrite)

        assertEquals(ServiceReminderStrategy.UPDATED_COMBINED, updated.strategy)
        assertEquals("0x18DAC1F1", updated.module.transport.requestCanId)
        assertEquals(1, updated.combinedWrite?.inputStepKm)
        assertNull(updated.combinedWrite?.hybridOdometerDivisorKm)
        assertEquals("2EA000", updated.combinedWrite?.requestPrefix)
        assertEquals("5003", updated.combinedWrite?.sessionPositivePrefix)
        assertEquals("6701", updated.combinedWrite?.seedPositivePrefix)
        assertEquals("062702", updated.combinedWrite?.keyRequestPrefix)
        assertEquals(3, updated.combinedWrite?.securityKeys?.size)

        assertEquals(ServiceReminderStrategy.HYBRID_COMBINED, hybrid.strategy)
        assertEquals(1, hybrid.combinedWrite?.inputStepKm)
        assertEquals(25, hybrid.combinedWrite?.hybridOdometerDivisorKm)
        assertEquals("2EA000", hybrid.combinedWrite?.requestPrefix)

        assertEquals(ServiceReminderStrategy.ADAPTIVE_COMBINED, adaptive.strategy)
        assertEquals(1, adaptive.combinedWrite?.inputStepKm)
        assertEquals(25, adaptive.combinedWrite?.hybridOdometerDivisorKm)
    }

    @Test
    fun `catalog composes motorcycles from family references`() {
        val catalog = MotorcycleProfileCatalogLoader().load(
            generatedProfile(CATALOG),
            engineFamilies = mapOf(engine.id to engine),
            instrumentFamilies = instruments,
        )

        val validated = catalog.defaultProfile
        assertEquals("triumph-tiger-900-gt-pro-2021", validated.id)
        assertEquals(ProfileValidationStatus.VALIDATED, validated.validationStatus)
        assertEquals(DtcClearStrategy.SECURITY_ACCESS, validated.dtcClearStrategy)
        assertEquals(ServiceReminderStrategy.ORIGINAL_SPLIT, validated.instrumentFamily?.strategy)

        val gen2 = catalog.profiles.single { it.id == "triumph-tiger-900-gen2" }
        assertEquals(ProfileValidationStatus.EXPERIMENTAL, gen2.validationStatus)
        assertEquals(ServiceReminderStrategy.UPDATED_COMBINED, gen2.instrumentFamily?.strategy)
        assertEquals(CapabilityStatus.EXPERIMENTAL, gen2.capabilities.serviceReset)

        val hybrid = catalog.profiles.single { it.id == "triumph-tiger-sport-660" }
        assertEquals(CapabilityStatus.EXPERIMENTAL, hybrid.capabilities.serviceReset)

        val adaptiveProfileIds = setOf(
            "triumph-street-triple-765-modern",
            "triumph-modern-classics",
            "triumph-scrambler-modern",
            "triumph-660-800-modern",
            "triumph-speed-triple-1200-modern",
            "triumph-tiger-1200-modern",
        )
        adaptiveProfileIds.forEach { profileId ->
            val motorcycle = catalog.profiles.single { it.id == profileId }
            assertEquals(ServiceReminderStrategy.ADAPTIVE_COMBINED, motorcycle.instrumentFamily?.strategy)
            assertEquals(CapabilityStatus.EXPERIMENTAL, motorcycle.capabilities.serviceReset)
            assertEquals(CapabilityStatus.EXPERIMENTAL, motorcycle.capabilities.dtcClear)
        }

        assertTrue(catalog.profiles.distinctBy { it.id }.size == catalog.profiles.size)
    }

    @Test
    fun `validated family composition is wire equivalent to the legacy 2021 map`() {
        val legacy = EcuProfileLoader().load(generatedProfile("tiger-900-gt-pro-2021.ecumap.yaml"))
        val catalog = MotorcycleProfileCatalogLoader().load(
            generatedProfile(CATALOG),
            engineFamilies = mapOf(engine.id to engine),
            instrumentFamilies = instruments,
        )
        val composed = catalog.defaultProfile
        val instrument = composed.instrumentFamily ?: error("instrument family missing")

        assertEquals(legacy.engineEcu.transport, composed.engineFamily.module.transport)
        assertEquals(legacy.engineReadOnlyCapture, composed.engineFamily.readOnlyCapture)
        assertEquals(legacy.engineSecurityAccess, composed.engineFamily.securityAccess)
        assertEquals(legacy.diagnosticTroubleCodes.read, composed.engineFamily.dtcRead)
        assertEquals(legacy.diagnosticTroubleCodes.clear, composed.engineFamily.dtcClear)
        assertEquals(legacy.instrumentCluster.transport, instrument.module.transport)
        assertEquals(legacy.instrumentReadOnlyCapture, instrument.readOnlyCapture)
        assertEquals(legacy.serviceReminder, instrument.originalSplit)
    }

    @Test
    fun `catalog rejects missing family references`() {
        val broken = generatedProfile(CATALOG).decodeToString()
            .replaceFirst("engine_family: triumph-modern-can-engine", "engine_family: missing-engine")

        assertThrows(ProfileLoadException::class.java) {
            MotorcycleProfileCatalogLoader().load(
                broken.encodeToByteArray(),
                engineFamilies = mapOf(engine.id to engine),
                instrumentFamilies = instruments,
            )
        }
    }

    @Test
    fun `catalog rejects a service capability without an instrument family`() {
        val broken = generatedProfile(CATALOG).decodeToString()
            .replaceFirst(
                "instrument_family: triumph-adaptive-combined",
                "instrument_family: none",
            )

        assertThrows(ProfileLoadException::class.java) {
            MotorcycleProfileCatalogLoader().load(
                broken.encodeToByteArray(),
                engineFamilies = mapOf(engine.id to engine),
                instrumentFamilies = instruments,
            )
        }
    }

    @Test
    fun `catalog rejects a model code assigned to two profiles`() {
        val broken = generatedProfile(CATALOG).decodeToString()
            .replaceFirst("model_codes: [A55, A60, A61, A62, D31]", "model_codes: [E64, A60, A61, A62, D31]")

        assertThrows(ProfileLoadException::class.java) {
            MotorcycleProfileCatalogLoader().load(
                broken.encodeToByteArray(),
                engineFamilies = mapOf(engine.id to engine),
                instrumentFamilies = instruments,
            )
        }
    }

    @Test
    fun `adaptive instrument requires a positive hybrid odometer divisor`() {
        val broken = generatedProfile("triumph-adaptive-combined.instrumentfamily.yaml")
            .decodeToString()
            .replace("hybrid_odometer_divisor_km: 25", "hybrid_odometer_divisor_km: 0")

        assertThrows(ProfileLoadException::class.java) {
            InstrumentFamilyProfileLoader().load(broken.encodeToByteArray())
        }
    }

    private fun generatedProfile(name: String): ByteArray {
        val file = File("build/generated/profileAssets/profiles/$name")
        check(file.isFile) { "Generated profile is missing: ${file.absolutePath}" }
        return file.readBytes()
    }

    private companion object {
        const val ENGINE_MAP = "triumph-modern-can.enginefamily.yaml"
        const val CATALOG = "triumph.motorcycleprofiles.yaml"
        val INSTRUMENT_MAPS = listOf(
            "triumph-original-tft.instrumentfamily.yaml",
            "triumph-updated-tft.instrumentfamily.yaml",
            "triumph-hybrid-display.instrumentfamily.yaml",
            "triumph-adaptive-combined.instrumentfamily.yaml",
        )
    }
}
