package dev.resetlight.logging

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface JournalClock {
    fun wallTime(): Instant
    fun elapsedMillis(): Long
}

object SystemJournalClock : JournalClock {
    override fun wallTime(): Instant = Instant.now()
    override fun elapsedMillis(): Long = android.os.SystemClock.elapsedRealtime()
}

data class JournalEvent(
    val sessionId: UUID,
    val sequence: Long,
    val wallTime: Instant,
    val elapsedMillis: Long,
    val layer: String,
    val name: String,
    val outcome: String? = null,
    val text: String? = null,
    val rawHex: String? = null,
) {
    fun sanitized(): JournalEvent = copy(
        text = text?.let(DiagnosticRedactor::redactJournalText),
        rawHex = rawHex?.let(DiagnosticRedactor::redactDiagnosticHex),
    )

    fun toJsonLine(): String {
        val safe = sanitized()
        val fields = listOfNotNull(
            "sessionId" to safe.sessionId.toString(),
            "sequence" to safe.sequence.toString(),
            "wallTime" to safe.wallTime.toString(),
            "elapsedMillis" to safe.elapsedMillis.toString(),
            "layer" to safe.layer,
            "name" to safe.name,
            safe.outcome?.let { "outcome" to it },
            safe.text?.let { "text" to it },
            safe.rawHex?.let { "rawHex" to it },
        )
        return fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

interface JournalSink : Closeable {
    fun append(line: String)
    fun flush()
}

class FileJournalSink(file: File) : JournalSink {
    private val writer: BufferedWriter

    init {
        file.parentFile?.mkdirs()
        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8))
    }

    override fun append(line: String) {
        writer.appendLine(line)
    }

    override fun flush() = writer.flush()
    override fun close() = writer.close()
}

class EventJournal(
    scope: CoroutineScope,
    private val sink: JournalSink,
    private val clock: JournalClock = SystemJournalClock,
    val sessionId: UUID = UUID.randomUUID(),
) : Closeable {
    private sealed interface Message {
        data class Event(val value: JournalEvent, val flush: Boolean, val ack: CompletableDeferred<Unit>?) : Message
        data class Stop(val ack: CompletableDeferred<Unit>) : Message
    }

    private val channel = Channel<Message>(Channel.UNLIMITED)
    private val sequence = AtomicLong(0)
    private val enqueueLock = Any()
    private val writer: Job = scope.launch {
        for (message in channel) {
            when (message) {
                is Message.Event -> {
                    sink.append(message.value.toJsonLine())
                    // File I/O happens on this single journal coroutine, never on the
                    // Bluetooth reader. Flush every small event so adapter-only logs
                    // survive a process stop; critical callers additionally await it.
                    sink.flush()
                    message.ack?.complete(Unit)
                }
                is Message.Stop -> {
                    sink.flush()
                    sink.close()
                    message.ack.complete(Unit)
                    break
                }
            }
        }
    }

    fun record(layer: String, name: String, outcome: String? = null, text: String? = null, rawHex: String? = null) {
        enqueue(layer, name, outcome, text, rawHex, flush = false, ack = null)
    }

    suspend fun recordCritical(
        layer: String,
        name: String,
        outcome: String? = null,
        text: String? = null,
        rawHex: String? = null,
    ) {
        val ack = CompletableDeferred<Unit>()
        enqueue(layer, name, outcome, text, rawHex, flush = true, ack = ack)
        ack.await()
    }

    private fun enqueue(
        layer: String,
        name: String,
        outcome: String?,
        text: String?,
        rawHex: String?,
        flush: Boolean,
        ack: CompletableDeferred<Unit>?,
    ) = synchronized(enqueueLock) {
        val event = JournalEvent(
            sessionId = sessionId,
            sequence = sequence.incrementAndGet(),
            wallTime = clock.wallTime(),
            elapsedMillis = clock.elapsedMillis(),
            layer = layer,
            name = name,
            outcome = outcome,
            text = text,
            rawHex = rawHex,
        )
        check(channel.trySend(Message.Event(event, flush, ack)).isSuccess) { "Journal is closed" }
    }

    suspend fun shutdown() {
        val ack = CompletableDeferred<Unit>()
        channel.send(Message.Stop(ack))
        ack.await()
        writer.join()
        channel.close()
    }

    override fun close() {
        channel.close()
    }
}
