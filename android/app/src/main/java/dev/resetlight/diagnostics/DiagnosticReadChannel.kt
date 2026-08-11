package dev.resetlight.diagnostics

/**
 * Read-only command channel shared by every read path (research captures, the
 * mainline DTC read). Reads are safe to model as a single interface; the
 * write paths keep the separate [DiagnosticWriteChannel] because its
 * [WriteIntent] lets the transport refuse to retry an interrupted send.
 */
fun interface DiagnosticReadChannel {
    suspend fun execute(request: String): String
}
