package dev.resetlight.transport

import java.io.IOException
import java.util.ArrayDeque

sealed interface ReplayInbound {
    data class Bytes(val value: ByteArray) : ReplayInbound
    data object Disconnect : ReplayInbound
    data class Failure(val error: IOException) : ReplayInbound
}

data class ReplayExchange(
    val expectedOutbound: ByteArray,
    val inbound: List<ReplayInbound>,
)

class ReplayByteTransport(exchanges: List<ReplayExchange>) : ByteTransport {
    private val pending = ArrayDeque(exchanges)
    private val incoming = ArrayDeque<ReplayInbound>()
    var closeCount: Int = 0
        private set
    private var connected = false

    override suspend fun connect() {
        check(!connected) { "Replay transport already connected" }
        connected = true
    }

    override suspend fun write(bytes: ByteArray) {
        check(connected) { "Replay transport is not connected" }
        val exchange = pending.pollFirst() ?: error("Unexpected outbound bytes: ${bytes.toHex()}")
        check(exchange.expectedOutbound.contentEquals(bytes)) {
            "Outbound mismatch. Expected ${exchange.expectedOutbound.toHex()}, actual ${bytes.toHex()}"
        }
        exchange.inbound.forEach(incoming::addLast)
    }

    override suspend fun read(): ByteArray? = when (val next = incoming.pollFirst()) {
        null -> null
        is ReplayInbound.Bytes -> next.value.copyOf()
        ReplayInbound.Disconnect -> null
        is ReplayInbound.Failure -> throw next.error
    }

    override suspend fun close() {
        if (connected) {
            connected = false
            closeCount++
        }
    }

    fun assertConsumed() {
        check(pending.isEmpty()) { "${pending.size} outbound exchanges were not consumed" }
        check(incoming.isEmpty()) { "${incoming.size} inbound chunks were not consumed" }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
