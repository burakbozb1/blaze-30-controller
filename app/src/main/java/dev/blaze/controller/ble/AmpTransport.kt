package dev.blaze.controller.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class FoundDevice(val name: String, val address: String)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/** Byte-level link to the amp. Everything above this layer is Bluetooth-free and unit-testable. */
interface AmpTransport {
    val connection: StateFlow<ConnectionState>

    /** Notifications from the amp, one element per notification (bytes copied). Hot flow, no replay. */
    val incoming: Flow<ByteArray>

    /** Emits Blaze devices as they are found; collection cancellation stops the scan. */
    fun scan(): Flow<FoundDevice>

    suspend fun connect(device: FoundDevice)

    /** Throws [IllegalStateException] when not connected. Returns after the link accepted the bytes. */
    suspend fun write(bytes: ByteArray)

    fun disconnect()
}
