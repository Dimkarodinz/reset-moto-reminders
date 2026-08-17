package dev.resetlight.research.general

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralReadPlanTest {
    private val loader = GeneralReadPlanLoader()

    @Test
    fun `loads finite ordered standard read profile`() {
        val plan = loader.load(generatedProfile())
        assertEquals("standard-obd-read-v1", plan.id)
        assertEquals(6, plan.phases.size)
        assertEquals("adapter_metadata", plan.phases.first().id)
        assertEquals("module_information", plan.phases.last().id)
        assertEquals(18, plan.phases.sumOf { it.commands.size })
        assertTrue(
            plan.phases.indexOfFirst { it.id == "selected_protocol" } >
                plan.phases.indexOfFirst { it.id == "supported_pids" },
        )
    }

    @Test
    fun `runtime policy admits the profile and rejects writes vin monitoring and unknown commands`() {
        val plan = loader.load(generatedProfile())
        val policy = GeneralResearchCommandPolicy(plan)
        plan.phases.flatMap { it.commands }.forEach { assertTrue(policy.allows(it.request), it.request) }
        listOf("04", "0902", "1422F190", "2701", "2E123400", "3101", "ATMA", "ATSH7E0", "0101").forEach {
            assertFalse(policy.allows(it), it)
        }
    }

    @Test
    fun `loader rejects duplicate names and prohibited requests`() {
        val source = generatedProfile().decodeToString()
        assertFailsWith<GeneralReadPlanException> {
            loader.load(source.replace("name: pending_dtcs", "name: stored_dtcs").encodeToByteArray())
        }
        assertFailsWith<GeneralReadPlanException> {
            loader.load(source.replace("request: \"03\"", "request: \"04\"").encodeToByteArray())
        }
    }

    private fun generatedProfile(): ByteArray =
        File("build/generated/profileAssets/profiles/standard-obd-read.researchprofile.yaml").readBytes()
}
