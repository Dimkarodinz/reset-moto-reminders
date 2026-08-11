package dev.resetlight.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import dev.resetlight.profiles.AdapterProfile
import dev.resetlight.transport.ByteTransport
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@SuppressLint("MissingPermission")
class AndroidBleAdapterFacade(context: Context) : BleAdapterFacade {
    private val applicationContext = context.applicationContext
    private val adapter = checkNotNull(
        applicationContext.getSystemService(BluetoothManager::class.java).adapter,
    ) { "Bluetooth is unavailable" }

    override suspend fun scan(profiles: Collection<BleDiscoveryProfile>): List<BleScanResult> {
        if (profiles.isEmpty()) return emptyList()
        val scanner = checkNotNull(adapter.bluetoothLeScanner) { "Bluetooth LE scanner is unavailable" }
        val found = linkedMapOf<Pair<String, String>, BleScanResult>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertised = result.scanRecord?.serviceUuids.orEmpty().map(ParcelUuid::getUuid).toSet()
                val name = result.scanRecord?.deviceName ?: result.device.name
                val match = BleDeviceSelector.match(name, advertised, profiles) ?: return
                synchronized(found) {
                    found[result.device.address to match.profileId] = BleScanResult(
                        address = result.device.address,
                        name = match.expectedName,
                        profileId = match.profileId,
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                // The bounded scan will return any devices already found. A
                // connection attempt still performs the full GATT validation.
            }
        }
        val filters = profiles.map { profile ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(profile.serviceUuid)).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(filters, settings, callback)
        try {
            delay(SCAN_MILLIS)
        } finally {
            scanner.stopScan(callback)
        }
        return synchronized(found) { found.values.sortedWith(compareBy(BleScanResult::name, BleScanResult::address)) }
    }

    override fun createGattTransport(address: String, profile: AdapterProfile): ByteTransport =
        GattByteTransport(AndroidGattConnection(applicationContext, adapter.getRemoteDevice(address), profile))

    private companion object {
        const val SCAN_MILLIS = 4_000L
    }
}

@SuppressLint("MissingPermission")
private class AndroidGattConnection(
    private val context: Context,
    private val device: BluetoothDevice,
    private val profile: AdapterProfile,
) : ByteTransport {
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()
    private val closed = AtomicBoolean(false)
    private val activeWrite = AtomicReference<CompletableDeferred<Unit>?>(null)
    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var responseCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var negotiatedMtu = DEFAULT_MTU
    private val writer = SequentialBleWriter({ negotiatedMtu }) { chunk -> writeChunk(chunk) }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.discoverServices()) fail(IOException("GATT service discovery could not start"))
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> fail(IOException("BLE adapter disconnected"))
                status != BluetoothGatt.GATT_SUCCESS -> fail(IOException("GATT connection failed: $status"))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return fail(IOException("GATT discovery failed: $status"))
            val service = gatt.getService(profile.transport.primaryServiceUuid)
                ?: return fail(IOException("Required CX UART service is missing"))
            val command = profile.transport.commandCharacteristicUuid?.let(service::getCharacteristic)
                ?: return fail(IOException("Required CX write characteristic is missing"))
            val response = profile.transport.responseCharacteristicUuid?.let(service::getCharacteristic)
                ?: return fail(IOException("Required CX notification characteristic is missing"))
            val canWrite = command.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val canNotify = response.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            if (!canWrite || !canNotify) return fail(IOException("CX GATT characteristic properties do not match the profile"))
            commandCharacteristic = command
            responseCharacteristic = response
            if (!gatt.setCharacteristicNotification(response, true)) {
                return fail(IOException("CX notifications could not be enabled"))
            }
            val descriptor = response.getDescriptor(CCCD_UUID)
                ?: return fail(IOException("CX notification descriptor is missing"))
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            if (!started) fail(IOException("CX notification subscription could not start"))
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CCCD_UUID) return
            if (status != BluetoothGatt.GATT_SUCCESS) return fail(IOException("CX notification subscription failed: $status"))
            if (!gatt.requestMtu(REQUESTED_MTU)) ready.complete(Unit)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS && mtu > ATT_OVERHEAD) mtu else DEFAULT_MTU
            ready.complete(Unit)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == profile.transport.responseCharacteristicUuid) {
                @Suppress("DEPRECATION")
                incoming.trySend(characteristic.value.copyOf())
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == profile.transport.responseCharacteristicUuid) incoming.trySend(value.copyOf())
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val pending = activeWrite.getAndSet(null) ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) pending.complete(Unit)
            else pending.completeExceptionally(IOException("CX acknowledged write failed: $status"))
        }
    }

    override suspend fun connect() {
        check(gatt == null) { "GATT transport is already used" }
        val created = withContext(Dispatchers.Main.immediate) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }
        gatt = created
        try {
            withTimeout(CONNECT_TIMEOUT_MILLIS) { ready.await() }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    override suspend fun write(bytes: ByteArray) = writer.write(bytes)

    override suspend fun read(): ByteArray? = incoming.receiveCatching().getOrNull()

    override suspend fun close() {
        if (!closed.compareAndSet(false, true)) return
        activeWrite.getAndSet(null)?.completeExceptionally(IOException("BLE adapter closed"))
        incoming.close()
        withContext(Dispatchers.Main.immediate) {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
    }

    private suspend fun writeChunk(chunk: ByteArray) {
        val currentGatt = gatt ?: throw IOException("GATT transport is not connected")
        val characteristic = commandCharacteristic ?: throw IOException("CX write characteristic is unavailable")
        val acknowledgement = CompletableDeferred<Unit>()
        check(activeWrite.compareAndSet(null, acknowledgement)) { "A BLE write is already pending" }
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val started = if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                characteristic,
                chunk,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            @Suppress("DEPRECATION")
            currentGatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            activeWrite.compareAndSet(acknowledgement, null)
            throw IOException("CX acknowledged write could not start")
        }
        try {
            withTimeout(WRITE_TIMEOUT_MILLIS) { acknowledgement.await() }
        } finally {
            activeWrite.compareAndSet(acknowledgement, null)
        }
    }

    private fun fail(failure: IOException) {
        ready.completeExceptionally(failure)
        activeWrite.getAndSet(null)?.completeExceptionally(failure)
        incoming.close(failure)
    }

    private companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEFAULT_MTU = 23
        const val REQUESTED_MTU = 512
        const val ATT_OVERHEAD = 3
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        const val WRITE_TIMEOUT_MILLIS = 5_000L
    }
}
