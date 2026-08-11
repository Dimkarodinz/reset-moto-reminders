package dev.resetlight.transport.bluetooth

import dev.resetlight.transport.ByteTransport
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BleDiscoveryProfile(
    val profileId: String,
    val expectedName: String,
    val serviceUuid: UUID,
)

data class BleScanResult(
    val address: String,
    val name: String,
    val profileId: String,
)

object BleDeviceSelector {
    fun match(
        name: String?,
        serviceUuids: Set<UUID>,
        profiles: Collection<BleDiscoveryProfile>,
    ): BleDiscoveryProfile? = profiles.singleOrNull { profile ->
        name == profile.expectedName && profile.serviceUuid in serviceUuids
    }
}

fun interface BleChunkSink {
    suspend fun write(chunk: ByteArray)
}

class SequentialBleWriter(
    private val mtu: () -> Int,
    private val sink: BleChunkSink,
) {
    private val mutex = Mutex()

    suspend fun write(bytes: ByteArray) = mutex.withLock {
        BlePacketSizer.chunks(bytes, mtu()).forEach { chunk -> sink.write(chunk) }
    }
}

interface BleAdapterFacade {
    suspend fun scan(profiles: Collection<BleDiscoveryProfile>): List<BleScanResult>
    fun createGattTransport(address: String, profile: dev.resetlight.profiles.AdapterProfile): ByteTransport
}

class GattByteTransport(private val delegate: ByteTransport) : ByteTransport by delegate
