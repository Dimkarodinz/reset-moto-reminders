package dev.resetlight.adapter.elm

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class ElmProtocolTest {
    @Test
    fun `command encoder appends exactly one carriage return`() {
        assertContentEquals("ATWS\r".encodeToByteArray(), ElmCodec.encode("ATWS\r"))
    }

    @Test
    fun `fragmented response completes only at prompt`() {
        val assembler = PromptAssembler()
        assertTrue(assembler.append("ATWS\r\rELM327 ".encodeToByteArray()).isEmpty())
        val frames = assembler.append("v2.2\r>".encodeToByteArray())
        assertEquals(1, frames.size)
        assertEquals("ATWS\r\rELM327 v2.2\r>", frames.single().raw.decodeToString())
    }

    @Test
    fun `two responses in one chunk remain two responses`() {
        val frames = PromptAssembler().append("OK\r>STN1151 v4.3.2\r>".encodeToByteArray())
        assertEquals(listOf("OK", "STN1151 v4.3.2"), frames.map { it.normalizedText })
        assertFalse(frames[0].raw.contentEquals(frames[1].raw))
    }

    @Test
    fun `missing prompt cannot grow the response buffer without a bound`() {
        val assembler = PromptAssembler(maximumResponseBytes = 4)
        assertThrows(IllegalStateException::class.java) {
            assembler.append("12345".encodeToByteArray())
        }
    }
}
