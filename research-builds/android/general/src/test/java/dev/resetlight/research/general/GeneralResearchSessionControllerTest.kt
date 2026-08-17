package dev.resetlight.research.general

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralResearchSessionControllerTest {
    private val vehicle = GeneralVehicle("Honda", "Africa Twin", 2022)

    @Test
    fun `publishes stage changes and completed report`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val report = File("report.jsonl")
        val result = GeneralResearchRunResult(report, GeneralResearchSummary(18, 12, emptyMap()))
        val controller = GeneralResearchSessionController(scope) { _, _, onStage ->
            onStage(GeneralSessionStage.READING_DTCS)
            result
        }

        assertTrue(controller.start(vehicle, "private-address"))
        scope.advanceUntilIdle()

        val complete = assertIs<GeneralSessionState.Complete>(controller.state.value)
        assertEquals(report, complete.reportFile)
        assertEquals(12, complete.summary.responded)
    }

    @Test
    fun `rejects overlapping scan`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val gate = CompletableDeferred<Unit>()
        val controller = GeneralResearchSessionController(scope) { _, _, _ ->
            gate.await()
            GeneralResearchRunResult(File("report.jsonl"), GeneralResearchSummary(18, 0, emptyMap()))
        }

        assertTrue(controller.start(vehicle, "private-address"))
        assertFalse(controller.start(vehicle, "private-address"))
        scope.testScheduler.runCurrent()
        controller.cancel()
        scope.advanceUntilIdle()
        assertIs<GeneralSessionState.Cancelled>(controller.state.value)
    }
}
