package dev.resetlight.adapter.elm

import java.io.ByteArrayOutputStream

class PromptAssembler(
    private val prompt: Byte = '>'.code.toByte(),
    private val maximumResponseBytes: Int = 64 * 1024,
) {
    private val pending = ByteArrayOutputStream()

    fun append(chunk: ByteArray): List<ElmFrame> {
        val completed = mutableListOf<ElmFrame>()
        chunk.forEach { byte ->
            pending.write(byte.toInt())
            check(pending.size() <= maximumResponseBytes) {
                "ELM response exceeded $maximumResponseBytes bytes without a prompt"
            }
            if (byte == prompt) {
                val raw = pending.toByteArray()
                pending.reset()
                completed += ElmFrame(raw.copyOf(), normalize(raw))
            }
        }
        return completed
    }

    fun clear() = pending.reset()

    private fun normalize(raw: ByteArray): String = raw
        .decodeToString()
        .removeSuffix(">")
        .split('\r', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n")
}
