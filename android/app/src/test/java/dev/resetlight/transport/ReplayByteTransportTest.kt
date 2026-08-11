package dev.resetlight.transport

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayByteTransportTest {
    @Test
    fun `preserves arbitrary inbound chunks and closes once`() = runTest {
        val transport = ReplayByteTransport(
            listOf(
                ReplayExchange(
                    "ATI\r".encodeToByteArray(),
                    listOf(
                        ReplayInbound.Bytes("ELM".encodeToByteArray()),
                        ReplayInbound.Bytes("327\r>".encodeToByteArray()),
                        ReplayInbound.Disconnect,
                    ),
                ),
            ),
        )
        transport.connect()
        transport.write("ATI\r".encodeToByteArray())
        assertArrayEquals("ELM".encodeToByteArray(), transport.read())
        assertArrayEquals("327\r>".encodeToByteArray(), transport.read())
        assertNull(transport.read())
        transport.close()
        transport.close()
        assertEquals(1, transport.closeCount)
        transport.assertConsumed()
    }

    @Test(expected = IllegalStateException::class)
    fun `unexpected outbound bytes fail with transcript mismatch`() = runTest {
        val transport = ReplayByteTransport(listOf(ReplayExchange("ATI\r".encodeToByteArray(), emptyList())))
        transport.connect()
        transport.write("ATWS\r".encodeToByteArray())
    }
}
