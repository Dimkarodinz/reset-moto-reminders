package dev.resetlight.research.triumph

import dev.resetlight.diagnostics.WriteIntent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResearchWriteCommandPolicyTest {
    private val ecu = TestProfiles.ecu()
    private val policy = ResearchWriteCommandPolicy(ecu)

    @Test
    fun `allows only the exact observed clear and kilometre reset families`() {
        ecu.engineReadOnlyCapture.configurationCommands.forEach {
            assertTrue(policy.allows(it, WriteIntent.READ), it)
        }
        ecu.instrumentReadOnlyCapture.configurationCommands.forEach {
            assertTrue(policy.allows(it, WriteIntent.READ), it)
        }
        assertTrue(policy.allows(ecu.engineSecurityAccess.extendedSessionElmRequest, WriteIntent.READ))
        assertTrue(policy.allows(ecu.engineSecurityAccess.seedRequestElmRequest, WriteIntent.READ))
        assertTrue(policy.allows("042702A018", WriteIntent.WRITE))
        assertTrue(policy.allows(ecu.diagnosticTroubleCodes.clear.elmRequest, WriteIntent.WRITE))
        assertTrue(policy.allows(ecu.diagnosticTroubleCodes.clear.verificationElmRequest, WriteIntent.READ))
        assertTrue(policy.allows("334E", WriteIntent.WRITE))
        assertTrue(policy.allows("5C1B0811016E0000", WriteIntent.WRITE))
    }

    @Test
    fun `rejects identity reads and every unrelated dynamic write`() {
        listOf(
            "0322F190" to WriteIntent.READ,
            "0322F18C" to WriteIntent.READ,
            "042702A018" to WriteIntent.READ,
            "042702A01800" to WriteIntent.WRITE,
            "0414FFFFFE" to WriteIntent.WRITE,
            "042E123400" to WriteIntent.WRITE,
            "0431011234" to WriteIntent.WRITE,
            "3201" to WriteIntent.WRITE,
            "5D1B0811016E0000" to WriteIntent.WRITE,
            "5C1B0231016E0000" to WriteIntent.WRITE,
            "ATMA" to WriteIntent.READ,
        ).forEach { (command, intent) -> assertFalse(policy.allows(command, intent), command) }
    }
}
