package dev.resetlight.research.triumph

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchCommandPolicyTest {
    private val adapter = TestProfiles.adapter()
    private val ecu = TestProfiles.ecu()
    private val policy = ResearchCommandPolicy(adapter, ecu)

    @Test
    fun `permits bounded adapter metadata and every packaged non-sensitive scan read`() {
        listOf("ATRV", "ATDP", "ATDPN").forEach { assertTrue(policy.allows(it), it) }
        ecu.engineReadOnlyCapture.configurationCommands.forEach { assertTrue(policy.allows(it), it) }
        ecu.engineReadOnlyCapture.identifierReads.forEach { assertTrue(policy.allows(it.elmRequest), it.name) }
        assertTrue(policy.allows(ecu.diagnosticTroubleCodes.read.countElmRequest))
        assertTrue(policy.allows(ecu.diagnosticTroubleCodes.read.detailElmRequest))
        assertTrue(policy.allows(ecu.engineReadOnlyCapture.extendedSessionElmRequest))
        ecu.instrumentReadOnlyCapture.configurationCommands.forEach { assertTrue(policy.allows(it), it) }
        assertTrue(policy.allows(ecu.instrumentReadOnlyCapture.initializeElmRequest))
        assertTrue(policy.allows(ecu.instrumentReadOnlyCapture.odometerElmRequest))
    }

    @Test
    fun `rejects identity security clear generic write routine and service reset requests`() {
        listOf(
            "0322F190", // VIN
            "0322F18C", // ECU serial
            "022701", // SecurityAccess seed
            "042702A018", // SecurityAccess key
            "0414FFFFFF", // clear DTCs
            "042E123400", // WriteDataByIdentifier
            "0431011234", // RoutineControl
            "334E", // service distance
            "5C1B080D016E0000", // service date
            "04", // OBD clear
            "ATMA", // unbounded bus monitor
        ).forEach { command -> assertFalse(policy.allows(command), command) }
    }
}
