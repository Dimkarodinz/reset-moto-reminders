package dev.resetlight.features.service

import dev.resetlight.profiles.EcuProfile

/**
 * The observed identifying fingerprint of the instrument cluster we captured.
 * Because the cluster never returns a part number or software version, writes
 * are gated on the exact motorcycle profile plus this observed transport route
 * and the constant `5E01` status, rather than on module identity fields.
 */
data class ClusterFingerprint(
    val motorcycleId: String,
    val requestCanId: String,
    val responseCanId: String,
    val elmProtocolCommand: String,
    val expectedStatusAscii: String,
)

data class GateDecision(
    val authorized: Boolean,
    val reason: String,
)

/**
 * Fails closed: a write is authorized only when the connected motorcycle
 * profile is the exact captured one and the live instrument status matches the
 * observed constant. Any mismatch blocks every write with a specific reason.
 */
class ClusterFingerprintGate(ecu: EcuProfile) {
    val fingerprint: ClusterFingerprint = ClusterFingerprint(
        motorcycleId = ecu.motorcycleId,
        requestCanId = ecu.instrumentCluster.transport.requestCanId,
        responseCanId = ecu.instrumentCluster.transport.responseCanId,
        elmProtocolCommand = ecu.instrumentCluster.transport.elmProtocolCommand,
        expectedStatusAscii = ecu.instrumentReadOnlyCapture.expectedStatusAscii,
    )

    fun evaluate(motorcycleId: String, instrumentStatusAscii: String): GateDecision {
        if (motorcycleId != fingerprint.motorcycleId) {
            return GateDecision(false, "Connected motorcycle profile does not match the validated cluster.")
        }
        if (instrumentStatusAscii != fingerprint.expectedStatusAscii) {
            return GateDecision(
                false,
                "Instrument status ($instrumentStatusAscii) does not match the validated cluster " +
                    "(${fingerprint.expectedStatusAscii}).",
            )
        }
        return GateDecision(true, "Connected cluster matches the validated fingerprint.")
    }
}
