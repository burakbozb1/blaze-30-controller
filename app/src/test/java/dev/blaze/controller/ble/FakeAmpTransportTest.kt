package dev.blaze.controller.ble

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeAmpTransportTest {

    private val device = FoundDevice(name = "BLAZE-30 APP", address = "AA:BB:CC:00:00:01")

    @Test
    fun writesAfterConnectAreRecordedInOrder() = runTest {
        val t = FakeAmpTransport()
        t.connect(device)
        t.write(byteArrayOf(1))
        t.write(byteArrayOf(2))
        t.write(byteArrayOf(3))
        assertEquals(3, t.written.size)
        assertArrayEquals(byteArrayOf(1), t.written[0])
        assertArrayEquals(byteArrayOf(3), t.written[2])
        assertEquals(ConnectionState.Connected, t.connection.value)
    }

    @Test
    fun writeWhileDisconnectedThrows() = runTest {
        val t = FakeAmpTransport()
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { t.write(byteArrayOf(1)) }
        }
        assertEquals(0, t.written.size)
    }

    @Test
    fun droppedConnectionMovesToDisconnectedAndBlocksWrites() = runTest {
        val t = FakeAmpTransport()
        t.connect(device)
        t.dropConnection()
        assertEquals(ConnectionState.Disconnected, t.connection.value)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { t.write(byteArrayOf(9)) }
        }
    }

    @Test
    fun disconnectMovesToDisconnected() = runTest {
        val t = FakeAmpTransport()
        t.connect(device)
        t.disconnect()
        assertEquals(ConnectionState.Disconnected, t.connection.value)
    }

    @Test
    fun incomingFramesReachCollectorsInOrder() = runTest {
        val t = FakeAmpTransport()
        val seen = mutableListOf<ByteArray>()
        backgroundScope.launch { t.incoming.collect { seen += it } }
        runCurrent()

        t.emitFromAmp(byteArrayOf(1))
        t.emitFromAmp(byteArrayOf(2))
        runCurrent()

        assertEquals(2, seen.size)
        assertArrayEquals(byteArrayOf(1), seen[0])
        assertArrayEquals(byteArrayOf(2), seen[1])
    }
}
