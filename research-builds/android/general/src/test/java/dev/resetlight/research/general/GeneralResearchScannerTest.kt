package dev.resetlight.research.general

import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneralResearchScannerTest {
    private val plan = GeneralReadPlanLoader().load(
        File("build/generated/profileAssets/profiles/standard-obd-read.researchprofile.yaml").readBytes(),
    )

    @Test
    fun `runs every bounded probe in deterministic phase order`() = runTest {
        val channel = RecordingGeneralChannel { "RESPONSE" }
        val phases = mutableListOf<String>()
        val events = mutableListOf<GeneralResearchEvent>()
        val summary = GeneralResearchScanner(
            plan,
            channel,
            GeneralResearchCommandPolicy(plan),
            GeneralResearchEventRecorder(events::add),
            phases::add,
        ).scan()

        assertEquals(plan.phases.flatMap { it.commands }.map { it.request }, channel.commands)
        assertEquals(plan.phases.map { it.id }, phases)
        assertEquals(18, summary.attempted)
        assertEquals(18, summary.responded)
        assertTrue(events.any { it.name == "general_scan_finished" })
    }

    @Test
    fun `unsupported probe does not stop later independent probes`() = runTest {
        val channel = RecordingGeneralChannel { if (it == "03") "NO DATA" else "OK" }
        val summary = GeneralResearchScanner(
            plan,
            channel,
            GeneralResearchCommandPolicy(plan),
            GeneralResearchEventRecorder {},
        ).scan()

        assertEquals(18, summary.attempted)
        assertEquals(17, summary.responded)
        assertTrue(channel.commands.contains("090A"))
    }
}

private class RecordingGeneralChannel(private val response: (String) -> String) : GeneralResearchChannel {
    val commands = mutableListOf<String>()
    override suspend fun execute(command: String): String = response(command).also { commands += command }
}
