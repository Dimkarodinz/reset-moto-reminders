package dev.resetlight.features.research

import dev.resetlight.diagnostics.DiagnosticReadChannel
import dev.resetlight.profiles.EngineReadOnlyCaptureProfile
import dev.resetlight.profiles.InstrumentReadOnlyCaptureProfile

data class HarvestCaptureResult(
    val engine: ReadOnlyEngineCaptureResult,
    val instrument: InstrumentReadOnlyCaptureResult,
)

/**
 * Runs every observed read-only operation in a single connection so one journal
 * carries everything the final build needs: engine identifiers, the DTC
 * count/detail, and the instrument status/odometer. It is purely a composition
 * of the two already-validated read captures and issues no write. The instrument
 * half is attempted even if it blocks, so a rejected cluster route never loses
 * the engine data already gathered.
 */
class HarvestCapture(
    private val engineProfile: EngineReadOnlyCaptureProfile,
    private val instrumentProfile: InstrumentReadOnlyCaptureProfile,
    private val channel: DiagnosticReadChannel,
) {
    suspend fun run(): HarvestCaptureResult {
        val engine = ReadOnlyEngineCapture(engineProfile, channel).capture()
        val instrument = InstrumentReadOnlyCapture(instrumentProfile, channel).capture()
        return HarvestCaptureResult(engine = engine, instrument = instrument)
    }
}
