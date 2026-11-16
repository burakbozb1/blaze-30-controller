package dev.blaze.controller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.blaze.controller.BuildConfig
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Real BLE link. UUIDs, device name and write type are from docs/protocol.md (observed on hardware).
 * Callers must hold BLUETOOTH_SCAN / BLUETOOTH_CONNECT (the UI layer requests them).
 */
@SuppressLint("MissingPermission")
class BleAmpTransport(context: Context) : AmpTransport {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // Callbacks run on the main looper (see connect()); a burst of notifications (knob sweep) must not block
    // the Bluetooth thread, so overflow drops the oldest frame.
    private val _incoming = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incoming: Flow<ByteArray> = _incoming

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    // All GATT callbacks are delivered on the main thread (see connect()), the same thread the app uses.
    // @Volatile still documents that these are touched from callbacks and from coroutines.
    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null

    private val writeLock = Mutex()
    @Volatile private var pendingWrite: CompletableDeferred<Int>? = null
    @Volatile private var ready: CompletableDeferred<Unit>? = null

    override fun scan(): Flow<FoundDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth unavailable or off"))
            return@callbackFlow
        }
        val seen = HashSet<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                Log.d(TAG, "scan result name=$name addr=${result.device.address} rssi=${result.rssi}")
                if (name == null) return
                if (!name.startsWith(NAME_PREFIX, ignoreCase = true)) return
                if (seen.add(result.device.address)) trySend(FoundDevice(name, result.device.address))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("scan failed: $errorCode"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    override suspend fun connect(device: FoundDevice) {
        val adapter = adapter ?: run {
            _connection.value = ConnectionState.Failed("Bluetooth unavailable")
            return
        }
        disconnect()
        _connection.value = ConnectionState.Connecting
        val ready = CompletableDeferred<Unit>().also { this.ready = it }
        val remote: BluetoothDevice = adapter.getRemoteDevice(device.address)
        // Callbacks on the main looper: no binder-thread races with the fields above or with the coroutines.
        val g = remote.connectGatt(
            appContext, false, callback, BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_1M_MASK, Handler(Looper.getMainLooper()),
        )
        gatt = g
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { ready.await() }
            // Only claim "Connected" if this is still the link we opened (a disconnect may have raced in).
            if (gatt === g) _connection.value = ConnectionState.Connected
        } catch (e: TimeoutCancellationException) {
            disconnect()
            _connection.value = ConnectionState.Failed("connect timeout")
        } catch (e: CancellationException) {
            disconnect()
            throw e // a cancelled connect must stay cancelled, not turn into a bogus Failed state
        } catch (e: Exception) {
            disconnect()
            _connection.value = ConnectionState.Failed(e.message ?: "connect failed")
        }
    }

    override suspend fun write(bytes: ByteArray) {
        writeLock.withLock {
            val g = gatt
            val c = writeChar
            check(_connection.value == ConnectionState.Connected && g != null && c != null) { "not connected" }
            val done = CompletableDeferred<Int>().also { pendingWrite = it }
            val accepted = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    c.value = bytes
                    g.writeCharacteristic(c)
                }
            }
            check(accepted) { "write rejected by stack" }
            val status = try {
                withTimeout(WRITE_TIMEOUT_MS) { done.await() }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException("write timeout") // callers handle failed writes, not cancellations
            } finally {
                if (pendingWrite === done) pendingWrite = null // a late acknowledgement must not hit the next write
            }
            Log.d(TAG, "write ${bytes.toHex()} accepted=$accepted status=$status")
            check(status == BluetoothGatt.GATT_SUCCESS) { "write failed: status $status" }
        }
    }

    override fun disconnect() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        writeChar = null
        pendingWrite?.completeExceptionally(IllegalStateException("link closed"))
        _connection.value = ConnectionState.Disconnected
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) { // late callback from a link we already replaced or closed
                if (newState == BluetoothProfile.STATE_DISCONNECTED) g.close()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                ready?.completeExceptionally(IllegalStateException("gatt closed, status $status"))
                pendingWrite?.completeExceptionally(IllegalStateException("link closed"))
                g.close()
                gatt = null
                writeChar = null
                _connection.value = ConnectionState.Disconnected
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt) return
            val service = g.getService(SERVICE_UUID)
            val ch = service?.getCharacteristic(WRITE_UUID)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null) {
                ready?.completeExceptionally(IllegalStateException("amp service not found"))
                return
            }
            writeChar = ch
            // The vendor app subscribes to the amp's notify characteristic before talking; do the same.
            if (!enableNotifications(g, service.getCharacteristic(NOTIFY_UUID))) ready?.complete(Unit)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (g !== gatt) return
            Log.d(TAG, "notifications enabled status=$status")
            ready?.complete(Unit) // a failed subscribe is logged but not fatal; writes may still work
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (g === gatt && c.uuid == NOTIFY_UUID) {
                if (BuildConfig.DEBUG) Log.d("AmpRx", "fff2 ${value.toHex()}") // debug builds only: protocol work needs the raw frames
                _incoming.tryEmit(value.copyOf())
            }
        }

        @Deprecated("Used below API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && g === gatt && c.uuid == NOTIFY_UUID) _incoming.tryEmit(c.value.copyOf())
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (g !== gatt) return
            pendingWrite?.complete(status)
        }
    }

    /** Returns true when a descriptor write was started (completion then arrives in onDescriptorWrite). */
    private fun enableNotifications(g: BluetoothGatt, notify: BluetoothGattCharacteristic?): Boolean {
        val cccd = notify?.getDescriptor(CCCD_UUID) ?: return false
        g.setCharacteristicNotification(notify, true)
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(cccd)
            }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        private const val TAG = "BleAmpTransport"
        const val NAME_PREFIX = "BLAZE-30"
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val WRITE_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        val NOTIFY_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val WRITE_TIMEOUT_MS = 2_000L
    }
}
