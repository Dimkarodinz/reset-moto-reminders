package dev.resetlight.research.triumph

import dev.resetlight.domain.DistanceUnit
import java.io.File
import java.time.LocalDate
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
class ResearchSessionControllerTest {
    private val scope = TestScope(StandardTestDispatcher())
    private val vehicle = ResearchVehicle("Tiger 900 GT Pro", 2021)

    @Test
    fun `publishes stages and completed report`() {
        val report = File("report.jsonl")
        val summary = summary()
        val controller = ResearchSessionController(scope, ResearchSessionExecutor { _, _, onStage ->
            onStage(ResearchSessionStage.IDENTIFYING_ADAPTER)
            onStage(ResearchSessionStage.SCANNING_ENGINE)
            ResearchRunResult(report, summary)
        })

        assertTrue(controller.start(vehicle, "private-address"))
        scope.advanceUntilIdle()

        val complete = assertIs<ResearchSessionState.Complete>(controller.state.value)
        assertEquals(report, complete.reportFile)
        assertEquals(summary, complete.summary)
    }

    @Test
    fun `forwards selected write options in the same session request`() {
        val expected = ResearchSessionRequest(
            vehicle,
            ResearchWriteOptions(
                clearDtcs = true,
                serviceReset = ResearchServiceRoundTripRequest(
                    distanceUnit = DistanceUnit.KILOMETERS,
                    previousDistance = 7_800,
                    previousNextServiceDate = LocalDate.of(2027, 8, 17),
                    testDistance = 7_900,
                    testNextServiceDate = LocalDate.of(2027, 8, 18),
                ),
            ),
        )
        var received: ResearchSessionRequest? = null
        val controller = ResearchSessionController(scope, ResearchSessionExecutor { request, _, _ ->
            received = request
            ResearchRunResult(File("report.jsonl"), summary())
        })

        assertTrue(controller.start(expected, "private-address"))
        scope.advanceUntilIdle()

        assertEquals(expected, received)
    }

    @Test
    fun `does not start a second run while one is active`() {
        val release = CompletableDeferred<Unit>()
        var runCount = 0
        val controller = ResearchSessionController(scope, ResearchSessionExecutor { _, _, _ ->
            runCount++
            release.await()
            ResearchRunResult(File("report.jsonl"), summary())
        })

        assertTrue(controller.start(vehicle, "first"))
        scope.testScheduler.runCurrent()
        assertFalse(controller.start(vehicle, "second"))
        release.complete(Unit)
        scope.advanceUntilIdle()
        assertEquals(1, runCount)
    }

    @Test
    fun `failure keeps the partial report available`() {
        val report = File("partial.jsonl")
        val controller = ResearchSessionController(scope, ResearchSessionExecutor { _, _, _ ->
            throw ResearchExecutionFailure("Adapter disconnected", report)
        })

        controller.start(vehicle, "private-address")
        scope.advanceUntilIdle()

        val failed = assertIs<ResearchSessionState.Failed>(controller.state.value)
        assertEquals("Adapter disconnected", failed.message)
        assertEquals(report, failed.reportFile)
    }

    @Test
    fun `cancel returns a terminal cancelled state`() {
        val started = CompletableDeferred<Unit>()
        val controller = ResearchSessionController(scope, ResearchSessionExecutor { _, _, _ ->
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
            error("unreachable")
        })

        controller.start(vehicle, "private-address")
        scope.testScheduler.runCurrent()
        controller.cancel()
        scope.advanceUntilIdle()

        assertIs<ResearchSessionState.Cancelled>(controller.state.value)
    }

    @Test
    fun `executor cancellation preserves its partial report`() {
        val report = File("cancelled-partial.jsonl")
        val controller = ResearchSessionController(scope, ResearchSessionExecutor { _, _, _ ->
            throw ResearchExecutionCancelled(report)
        })

        controller.start(vehicle, "private-address")
        scope.advanceUntilIdle()

        val cancelled = assertIs<ResearchSessionState.Cancelled>(controller.state.value)
        assertEquals(report, cancelled.reportFile)
    }

    private fun summary() = ResearchScanSummary(
        adapterMetadataResponses = 3,
        identifierAttempts = 6,
        identifierResponses = 6,
        dtcReadConfirmed = true,
        dtcCount = 0,
        dtcDetailRecords = 0,
        extendedSessionUsed = false,
        instrumentStatusAscii = "043",
        odometerKm = 44_756,
        serviceReadConfirmed = true,
        dtcClearCandidate = true,
        serviceResetCandidate = true,
    )
}
