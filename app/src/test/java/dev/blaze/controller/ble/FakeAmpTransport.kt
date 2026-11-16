package dev.blaze.controller.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/** Test double: records written frames and lets tests drop the link. */
class FakeAmpTransport(var devices: List<FoundDevice> = emptyList()) : AmpTransport {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    override val incoming: Flow<ByteArray> = _incoming

    /** Simulates a notification from the amp. */
    fun emitFromAmp(bytes: ByteArray) {
        _incoming.tryEmit(bytes.copyOf())
    }

    private val _written = mutableListOf<ByteArray>()
    val written: List<ByteArray> get() = _written
    var connectCalls = 0
        private set
    var scanCalls = 0
        private set

    /** When true, connect() leaves the link in Failed instead of Connected. */
    var failConnects = false

    /** Thrown by the next write() only (e.g. a write timeout or a cancelled acknowledgement). */
    var failNextWrite: Throwable? = null

    override fun scan(): Flow<FoundDevice> {
        scanCalls++
        return flowOf(*devices.toTypedArray())
    }

    override suspend fun connect(device: FoundDevice) {
        connectCalls++
        _connection.value = if (failConnects) ConnectionState.Failed("test") else ConnectionState.Connected
    }

    override suspend fun write(bytes: ByteArray) {
        check(_connection.value == ConnectionState.Connected) { "not connected" }
        failNextWrite?.let { failNextWrite = null; throw it }
        _written += bytes.copyOf()
    }

    override fun disconnect() {
        _connection.value = ConnectionState.Disconnected
    }

    /** Simulates the amp powering off / going out of range. */
    fun dropConnection() {
        _connection.value = ConnectionState.Disconnected
    }
}
