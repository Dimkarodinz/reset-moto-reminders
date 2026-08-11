package dev.resetlight.adapter.elm

object ElmCodec {
    fun encode(command: String): ByteArray = command.trimEnd('\r', '\n').plus('\r').encodeToByteArray()
}

data class ElmFrame(
    val raw: ByteArray,
    val normalizedText: String,
)
