package dev.resetlight.adapter.elm

import dev.resetlight.transport.ByteTransport
import dev.resetlight.transport.ReplayByteTransport
import dev.resetlight.transport.ReplayExchange
import dev.resetlight.transport.ReplayInbound
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElmCommandSessionTest {
    @Test
    fun `concurrent callers are serialized in FIFO command order`() = runTest {
        val transport = ReplayByteTransport(
            listOf(
                ReplayExchange(ElmCodec.encode("ATI"), listOf(ReplayInbound.Bytes("ELM\r>".encodeToByteArray()))),
                ReplayExchange(ElmCodec.encode("STI"), listOf(ReplayInbound.Bytes("STN\r>".encodeToByteArray()))),
            ),
        )
        transport.connect()
        val session = ElmCommandSession(transport)

        val first = async { session.execute("ATI").normalizedText }
        val second = async { session.execute("STI").normalizedText }

        assertEquals("ELM", first.await())
        assertEquals("STN", second.await())
        transport.assertConsumed()
    }

    @Test
    fun `missing prompt is a typed timeout`() = runTest {
        val never = CompletableDeferred<ByteArray?>()
        val session = ElmCommandSession(SuspendingTransport(never), commandTimeoutMillis = 10)
        assertThrows(CommandFailure.Timeout::class.java) {
            kotlinx.coroutines.runBlocking { session.execute("ATI") }
        }
    }

    @Test
    fun `disconnect after read is ordinary but after write is ambiguous`() = runTest {
        val readTransport = DisconnectingTransport()
        val readFailure = assertThrows(CommandFailure.Disconnected::class.java) {
            kotlinx.coroutines.runBlocking { ElmCommandSession(readTransport).execute("ATI", CommandIntent.READ) }
        }
        assertEquals("active command", readFailure.command)

        val writeTransport = DisconnectingTransport()
        assertThrows(CommandFailure.AmbiguousWrite::class.java) {
            kotlinx.coroutines.runBlocking { ElmCommandSession(writeTransport).execute("0414FFFFFF", CommandIntent.WRITE) }
        }
        assertEquals(1, writeTransport.writeCount)
    }

    private class SuspendingTransport(private val result: CompletableDeferred<ByteArray?>) : ByteTransport {
        override suspend fun connect() = Unit
        override suspend fun write(bytes: ByteArray) = Unit
        override suspend fun read(): ByteArray? = result.await()
        override suspend fun close() = Unit
    }

    private class DisconnectingTransport : ByteTransport {
        var writeCount = 0
        override suspend fun connect() = Unit
        override suspend fun write(bytes: ByteArray) { writeCount++ }
        override suspend fun read(): ByteArray? = null
        override suspend fun close() = Unit
    }
}
