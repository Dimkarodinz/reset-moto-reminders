package dev.resetlight.research.general

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralSessionMetadataTest {
    @Test
    fun `records normalized motorcycle and profile provenance without private adapter data`() {
        val plan = GeneralReadPlanLoader().load(
            File("build/generated/profileAssets/profiles/standard-obd-read.researchprofile.yaml").readBytes(),
        )
        val metadata = GeneralSessionMetadata.describe(
            GeneralVehicle("Honda", "Africa Twin", 2022),
            "adapter-hash",
            plan,
        )

        assertTrue(metadata.contains("manufacturer=\"Honda\""))
        assertTrue(metadata.contains("model=\"Africa Twin\""))
        assertTrue(metadata.contains("model_year=2022"))
        assertTrue(metadata.contains("adapter_profile_sha256=adapter-hash"))
        assertTrue(metadata.contains("read_plan_sha256=${plan.sourceSha256}"))
        assertFalse(metadata.contains("address", ignoreCase = true))
        assertFalse(metadata.contains("vin", ignoreCase = true))
    }
}
