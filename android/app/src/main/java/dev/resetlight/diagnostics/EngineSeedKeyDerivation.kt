package dev.resetlight.diagnostics

import java.util.Locale

/**
 * Derives the UDS SecurityAccess key for the captured 2021 Tiger 900 GT Pro
 * engine ECU. The transform is a plain unsigned 16-bit multiply-modulo, not
 * general-purpose cryptography, and is scoped to that one engine-ECU path only.
 *
 * This type is only ever instantiated from the research build for the DTC-clear
 * operation, behind the exact-cluster fingerprint gate and an explicit user
 * confirmation. It never runs for a default-session read.
 */
class EngineSeedKeyDerivation(private val multiplier: Int) {
    /** Derives the two-byte key (as four hex characters) for a raw two-byte seed. */
    fun keyForSeedHex(seedHex: String): String =
        deriveKeyHex(parseSeed(seedHex.diagnosticHexBytes()))

    /**
     * Builds the `2702` key request from an observed `6701` seed response.
     * [keyRequestPrefix] is the profile's ELM request prefix (e.g. `042702`).
     */
    fun keyRequestFor(seedResponseHex: String, keyRequestPrefix: String): String {
        val bytes = seedResponseHex.diagnosticHexBytes()
        if (bytes.size != 4 || bytes[0].u() != SEED_RESPONSE_SERVICE || bytes[1].u() != SEED_SUBFUNCTION) {
            throw DiagnosticParseException("Expected a 6701 SecurityAccess seed response")
        }
        return keyRequestPrefix + deriveKeyHex(parseSeed(byteArrayOf(bytes[2], bytes[3])))
    }

    private fun deriveKeyHex(seed: Int): String =
        String.format(Locale.ROOT, "%04X", seed * multiplier and 0xFFFF)

    private fun parseSeed(seedBytes: ByteArray): Int {
        if (seedBytes.size != 2) {
            throw DiagnosticParseException("SecurityAccess seed must be two bytes")
        }
        return seedBytes[0].u() shl 8 or seedBytes[1].u()
    }

    private companion object {
        const val SEED_RESPONSE_SERVICE = 0x67
        const val SEED_SUBFUNCTION = 0x01
    }
}
