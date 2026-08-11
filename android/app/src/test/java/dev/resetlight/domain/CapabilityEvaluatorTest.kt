package dev.resetlight.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityEvaluatorTest {
    @Test
    fun `fake mode enables modeled capabilities`() {
        Capability.entries.forEach { capability ->
            assertTrue(CapabilityEvaluator.enabled(capability, BuildMode.FAKE, ProfileConfidence.UNKNOWN))
        }
    }

    @Test
    fun `research build enables only observed adapter connection`() {
        assertTrue(
            CapabilityEvaluator.enabled(
                Capability.ADAPTER_CONNECT,
                BuildMode.RESEARCH,
                ProfileConfidence.OBSERVED,
            ),
        )
        listOf(Capability.DTC_READ, Capability.DTC_CLEAR, Capability.SERVICE_READ, Capability.SERVICE_RESET)
            .forEach { capability ->
                assertFalse(CapabilityEvaluator.enabled(capability, BuildMode.RESEARCH, ProfileConfidence.OBSERVED))
            }
    }

    @Test
    fun `release and unknown profiles fail closed`() {
        Capability.entries.forEach { capability ->
            assertFalse(CapabilityEvaluator.enabled(capability, BuildMode.RELEASE, ProfileConfidence.UNKNOWN))
        }
        assertTrue(
            CapabilityEvaluator.enabled(
                Capability.ADAPTER_CONNECT,
                BuildMode.RELEASE,
                ProfileConfidence.VALIDATED,
            ),
        )
    }
}
