package dev.resetlight.transport

interface ByteTransport {
    suspend fun connect()
    suspend fun write(bytes: ByteArray)
    suspend fun read(): ByteArray?
    suspend fun close()
}
