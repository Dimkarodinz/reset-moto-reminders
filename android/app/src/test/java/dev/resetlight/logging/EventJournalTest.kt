package dev.resetlight.logging

import java.time.Instant
import java.util.Collections
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventJournalTest {
    @Test
    fun `concurrent events persist in sequence order`() = runTest {
        val sink = MemorySink()
        val journal = EventJournal(this, sink, FixedClock())
        (1..40).map { async { journal.record("elm", "event-$it") } }.awaitAll()
        journal.shutdown()

        val sequences = sink.lines.map { Regex("\\\"sequence\\\":\\\"(\\d+)\\\"").find(it)!!.groupValues[1].toLong() }
        assertEquals((1L..40L).toList(), sequences)
    }

    @Test
    fun `redaction happens before critical event reaches sink`() = runTest {
        val sink = MemorySink()
        val journal = EventJournal(this, sink, FixedClock())
        journal.recordCritical("uds", "security", text = "AA:BB:CC:DD:EE:FF", rawHex = "042702C3D4")
        assertFalse(sink.lines.single().contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(sink.lines.single().contains("C3D4"))
        assertTrue(sink.flushes > 0)
        journal.shutdown()
    }

    private class FixedClock : JournalClock {
        override fun wallTime(): Instant = Instant.parse("2026-08-09T12:00:00Z")
        override fun elapsedMillis(): Long = 42
    }

    private class MemorySink : JournalSink {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        var flushes = 0
        override fun append(line: String) { lines += line }
        override fun flush() { flushes++ }
        override fun close() = Unit
    }
}
