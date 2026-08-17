package dev.resetlight.research.triumph

import dev.resetlight.diagnostics.CanResponseExtractor
import dev.resetlight.profiles.EcuProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriumphResearchScannerTest {
    private val adapter = TestProfiles.adapter()
    private val ecu = TestProfiles.ecu()

    @Test
    fun `one scan gathers adapter engine dtc and instrument evidence`() = runTest {
        val channel = FakeResearchChannel(ecu)
        val recorder = RecordingResearchEvents()

        val summary = scanner(channel, recorder).scan()

        assertEquals(ecu.engineReadOnlyCapture.identifierReads.size, summary.identifierResponses)
        assertEquals(0, summary.dtcCount)
        assertTrue(summary.dtcReadConfirmed)
        assertTrue(summary.dtcClearCandidate)
        assertEquals("043", summary.instrumentStatusAscii)
        assertEquals(100, summary.odometerKm)
        assertTrue(summary.serviceReadConfirmed)
        assertTrue(summary.serviceResetCandidate)
        assertFalse(summary.extendedSessionUsed)
        assertTrue(recorder.events.any { it.name == "scan_finished" })
        assertTrue(channel.commands.none { it.contains("F190") || it.contains("F18C") })
    }

    @Test
    fun `unsupported identifier is recorded and later dtc and instrument probes continue`() = runTest {
        val unavailable = ecu.engineReadOnlyCapture.identifierReads.first().elmRequest
        val channel = FakeResearchChannel(ecu, noDataCommands = setOf(unavailable))

        val summary = scanner(channel).scan()

        assertEquals(ecu.engineReadOnlyCapture.identifierReads.size - 1, summary.identifierResponses)
        assertTrue(summary.dtcReadConfirmed)
        assertTrue(summary.serviceReadConfirmed)
        assertTrue(channel.commands.contains(ecu.instrumentReadOnlyCapture.odometerElmRequest))
    }

    @Test
    fun `default-session miss gets one extended-session retry and reads nonzero details`() = runTest {
        val channel = FakeResearchChannel(ecu, defaultCountUnavailable = true, dtcCount = 1)

        val summary = scanner(channel).scan()

        assertTrue(summary.extendedSessionUsed)
        assertEquals(1, summary.dtcCount)
        assertEquals(1, summary.dtcDetailRecords)
        assertEquals(2, channel.commands.count { it == ecu.diagnosticTroubleCodes.read.countElmRequest })
        assertEquals(1, channel.commands.count { it == ecu.engineReadOnlyCapture.extendedSessionElmRequest })
        assertEquals(1, channel.commands.count { it == ecu.diagnosticTroubleCodes.read.detailElmRequest })
    }

    private fun scanner(
        channel: ResearchCommandChannel,
        events: ResearchEventRecorder = RecordingResearchEvents(),
    ) = TriumphResearchScanner(
        ecuProfile = ecu,
        channel = channel,
        policy = ResearchCommandPolicy(adapter, ecu),
        events = events,
    )
}

private class FakeResearchChannel(
    private val ecu: EcuProfile,
    private val noDataCommands: Set<String> = emptySet(),
    private val defaultCountUnavailable: Boolean = false,
    private val dtcCount: Int = 0,
) : ResearchCommandChannel {
    val commands = mutableListOf<String>()
    private var countRequests = 0

    override suspend fun execute(command: String): String {
        commands += command
        if (command in noDataCommands) return "NO DATA"
        return when (command) {
            "ATRV" -> "12.6V"
            "ATDP" -> "ISO 15765-4 (CAN 29/500)"
            "ATDPN" -> "A7"
            "ATWS" -> "ELM327 v2.2"
            ecu.diagnosticTroubleCodes.read.countElmRequest -> {
                countRequests++
                if (defaultCountUnavailable && countRequests == 1) {
                    "NO DATA"
                } else {
                    "18DAF1D5 06 59010C00${dtcCount.toString(16).padStart(4, '0')} AA"
                }
            }
            ecu.diagnosticTroubleCodes.read.detailElmRequest -> "18DAF1D5 07 59020C15770008"
            ecu.engineReadOnlyCapture.extendedSessionElmRequest -> "18DAF1D5 02 5003 AAAAAAAAAA"
            ecu.instrumentReadOnlyCapture.initializeElmRequest -> "704 DE303433FFFFFFFF"
            ecu.instrumentReadOnlyCapture.odometerElmRequest -> "704 8D01000064000000"
            else -> if (command.startsWith("AT")) "OK" else identifierResponse(command)
        }
    }

    private fun identifierResponse(command: String): String {
        val did = command.takeLast(4)
        return "18DAF1D5 03 62$did AAAAAAAA"
    }
}

private class RecordingResearchEvents : ResearchEventRecorder {
    val events = mutableListOf<ResearchEvent>()
    override fun record(event: ResearchEvent) {
        events += event
    }
}
