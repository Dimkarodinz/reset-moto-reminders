package dev.resetlight.domain

enum class BuildMode { FAKE, RESEARCH, RELEASE }
enum class ProfileConfidence { UNKNOWN, OBSERVED, VALIDATED }
enum class Capability { ADAPTER_CONNECT, DTC_READ, DTC_CLEAR, SERVICE_READ, SERVICE_RESET }

object CapabilityEvaluator {
    fun enabled(capability: Capability, build: BuildMode, confidence: ProfileConfidence): Boolean = when {
        build == BuildMode.FAKE -> true
        capability == Capability.ADAPTER_CONNECT && build == BuildMode.RESEARCH && confidence != ProfileConfidence.UNKNOWN -> true
        capability == Capability.ADAPTER_CONNECT && build == BuildMode.RELEASE && confidence == ProfileConfidence.VALIDATED -> true
        else -> false
    }
}
