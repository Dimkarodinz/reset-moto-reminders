package dev.resetlight.diagnostics

enum class WriteIntent { READ, WRITE }

/**
 * Command channel shared by the write operations (DTC clear, service reset).
 * [WriteIntent.WRITE] lets the transport treat an interrupted send as ambiguous
 * and refuse to retry it, so a half-sent write is never silently repeated.
 */
fun interface DiagnosticWriteChannel {
    suspend fun execute(request: String, intent: WriteIntent): String
}
