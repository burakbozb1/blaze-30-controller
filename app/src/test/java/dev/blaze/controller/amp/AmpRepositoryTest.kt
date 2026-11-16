package dev.blaze.controller.amp

import dev.blaze.controller.ble.ConnectionState
import dev.blaze.controller.ble.FakeAmpTransport
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.ProtocolCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmpRepositoryTest {

    private val device = FoundDevice("BLAZE-30 APP", "AA:BB:CC:00:00:01")

    private fun TestScope.repo(fake: FakeAmpTransport) =
        AmpRepository(fake, backgroundScope, reconnectDelayMs = 1_000L, syncSettleMs = 0L, maxReconnectAttempts = 3)

    private fun frame(cmd: AmpCommand) = ProtocolCodec.encode(cmd)
    private val syncFrame = ProtocolCodec.encode(AmpCommand.Sync)
    private fun FakeAmpTransport.userFrames() = written.filterNot { it.contentEquals(syncFrame) }
    private fun FakeAmpTransport.syncCount() = written.count { it.contentEquals(syncFrame) }

    @Test
    fun sendWritesEncodedFrameAndUpdatesState() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()

        repo.send(AmpCommand.SetAmpType(AmpType.DRIVE)); runCurrent()

        assertEquals(1, fake.userFrames().size)
        assertArrayEquals(frame(AmpCommand.SetAmpType(AmpType.DRIVE)), fake.userFrames()[0])
        assertEquals(AmpType.DRIVE, repo.state.value.ampType)
    }

    @Test
    fun rapidTapsAreWrittenInTapOrderAndFinalStateIsLastTap() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()

        repo.send(AmpCommand.SetAmpType(AmpType.METAL))
        repo.send(AmpCommand.SetAmpType(AmpType.CLEAN))
        repo.send(AmpCommand.SetAmpType(AmpType.DRIVE))
        runCurrent()

        assertEquals(3, fake.userFrames().size)
        assertArrayEquals(frame(AmpCommand.SetAmpType(AmpType.METAL)), fake.userFrames()[0])
        assertArrayEquals(frame(AmpCommand.SetAmpType(AmpType.CLEAN)), fake.userFrames()[1])
        assertArrayEquals(frame(AmpCommand.SetAmpType(AmpType.DRIVE)), fake.userFrames()[2])
        assertEquals(AmpType.DRIVE, repo.state.value.ampType)
    }

    @Test
    fun droppedConnectionReconnectsAndResyncsButDoesNotReplayUserValues() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        repo.send(AmpCommand.SetAmpType(AmpType.METAL))
        repo.send(AmpCommand.SetGain(20))
        runCurrent()

        fake.dropConnection(); runCurrent()
        assertEquals(ConnectionState.Disconnected, repo.connection.value)
        advanceTimeBy(1_001); runCurrent()

        assertEquals(2, fake.connectCalls)
        assertEquals(ConnectionState.Connected, repo.connection.value)
        assertEquals(2, fake.syncCount())     // a new link always starts with the sync (the amp answers with its dump)
        assertEquals(2, fake.userFrames().size) // the two original writes only: the amp's own state is the truth now
    }

    @Test
    fun firstConnectionDoesNotPushAnythingToTheAmp() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        assertEquals(0, fake.userFrames().size) // never overwrite the amp's own settings with our defaults
        assertEquals(1, fake.syncCount())       // but the session handshake is required before the amp accepts commands
        assertArrayEquals(syncFrame, fake.written[0])
    }

    @Test
    fun sendWhileDisconnectedChangesNothingAndDoesNotThrow() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        val before = repo.state.value

        repo.send(AmpCommand.SetAmpType(AmpType.METAL)); runCurrent()

        assertEquals(before, repo.state.value)
        assertEquals(0, fake.userFrames().size)
    }

    @Test
    fun userDisconnectDoesNotAutoReconnect() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()

        repo.disconnect(); runCurrent()
        advanceTimeBy(5_000); runCurrent()

        assertEquals(1, fake.connectCalls)
        assertEquals(ConnectionState.Disconnected, repo.connection.value)
    }

    @Test
    fun reconnectBacksOffThenGivesUpAndExposesIt() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        assertEquals(1, fake.connectCalls)

        fake.failConnects = true
        fake.dropConnection(); runCurrent()
        advanceTimeBy(1_001); runCurrent()   // attempt 1 after 1 s
        assertEquals(2, fake.connectCalls)
        advanceTimeBy(2_000); runCurrent()   // attempt 2 after a further 2 s
        assertEquals(3, fake.connectCalls)
        advanceTimeBy(4_000); runCurrent()   // attempt 3 after a further 4 s
        assertEquals(4, fake.connectCalls)

        advanceTimeBy(60_000); runCurrent()  // gave up: nothing more is tried
        assertEquals(4, fake.connectCalls)
        assertEquals(true, repo.gaveUp.value)
    }

    @Test
    fun userRetryAfterGivingUpWorksAndClearsTheFlag() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        fake.failConnects = true
        fake.dropConnection(); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(true, repo.gaveUp.value)

        fake.failConnects = false
        repo.reconnect(); runCurrent()

        assertEquals(false, repo.gaveUp.value)
        assertEquals(ConnectionState.Connected, repo.connection.value)
    }

    @Test
    fun aCancelledOrFailedWriteDoesNotStopLaterCommands() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()

        fake.failNextWrite = CancellationException("write not acknowledged") // what a timed-out/aborted GATT write throws
        repo.send(AmpCommand.SetAmpType(AmpType.METAL)); runCurrent()
        fake.failNextWrite = RuntimeException("stack rejected the write")
        repo.send(AmpCommand.SetGain(5)); runCurrent()
        repo.send(AmpCommand.SetAmpType(AmpType.CLEAN)); runCurrent()

        assertEquals(1, fake.userFrames().size)
        assertArrayEquals(frame(AmpCommand.SetAmpType(AmpType.CLEAN)), fake.userFrames()[0])
        assertEquals(AmpType.CLEAN, repo.state.value.ampType) // failed writes did not change state
    }

    // ---- amp -> app (real frames captured 2026-11-15) ----

    private fun hex(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun liveFrame(group: Int, item: Int, sub: Int, value: Int) = ByteArray(16).also {
        it[0] = 0xFE.toByte(); it[1] = 0x0F
        it[2] = group.toByte(); it[3] = item.toByte(); it[4] = sub.toByte(); it[5] = value.toByte()
        it[15] = 0xFF.toByte()
    }

    private val bbDump = hex("FE0FBB0C0004190D0A0A0A0A0A0A0AFF")
    private val bcDump = hex("FE0FBC00141C1412180B1123140000FF")
    private val c0Dump = hex("FE0FC0000A080A0B0E100C640D000DFF")

    @Test
    fun connectDumpFillsTheState() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()

        fake.emitFromAmp(bbDump); fake.emitFromAmp(bcDump); fake.emitFromAmp(c0Dump); runCurrent()

        val s = repo.state.value
        assertEquals(12, s.volume)
        assertEquals(AmpType.METAL, s.ampType)
        assertEquals(13, s.gain)
        assertEquals(List(7) { 10 }, s.eq)
        assertEquals(dev.blaze.controller.protocol.GuitarEffect.BYPASS, s.effect)
        assertEquals(0, s.masterDelay)
        assertEquals(dev.blaze.controller.protocol.ReverbType.PLATE, s.reverbType)
        assertEquals(listOf(10, 8, 10), listOf(s.reverbLevel, s.reverbTone, s.reverbTime))
        assertEquals(listOf(11, 14, 16), listOf(s.delayLevel, s.delayFeedback, s.delayTime))
    }

    @Test
    fun aKnobSweepEndsOnTheLastValueInOrder() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()

        repeat(200) { fake.emitFromAmp(liveFrame(0x02, 0x01, 0x00, it % 51)) }
        runCurrent()

        assertEquals(199 % 51, repo.state.value.volume)
    }

    @Test
    fun incomingFramesNeverCauseWrites() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        val before = fake.written.size

        listOf(bbDump, bcDump, c0Dump, liveFrame(0x02, 0x03, 0x00, 1), liveFrame(0x02, 0x01, 0x00, 7))
            .forEach { fake.emitFromAmp(it) }
        runCurrent()

        assertEquals(before, fake.written.size)
    }

    @Test
    fun aChangeMadeOnTheAmpIsNotOverwrittenByTheAppsOlderValueAfterReconnect() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        repo.send(AmpCommand.SetAmpType(AmpType.METAL)); runCurrent()

        fake.emitFromAmp(liveFrame(0x02, 0x03, 0x00, AmpType.CLEAN.code)); runCurrent()
        assertEquals(AmpType.CLEAN, repo.state.value.ampType)

        fake.dropConnection(); runCurrent()
        advanceTimeBy(1_001); runCurrent()

        assertEquals(2, fake.connectCalls)
        assertEquals(1, fake.userFrames().size) // only the original METAL write; nothing re-sent over the amp's CLEAN
        assertEquals(AmpType.CLEAN, repo.state.value.ampType)
    }

    @Test
    fun garbageFramesAreIgnoredAndLaterValidOnesStillApply() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        val before = repo.state.value

        listOf(
            ByteArray(0), bbDump.copyOf(15), bbDump + byteArrayOf(0),
            bbDump.copyOf().also { it[1] = 0x0D }, bbDump.copyOf().also { it[15] = 0 },
            ByteArray(16) { (it * 37).toByte() },
        ).forEach { fake.emitFromAmp(it) }
        runCurrent()
        assertEquals(before, repo.state.value)

        fake.emitFromAmp(liveFrame(0x02, 0x01, 0x00, 33)); runCurrent()
        assertEquals(33, repo.state.value.volume)
    }

    @Test
    fun refreshWhenConnectedAsksTheAmpForAFreshDump() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        assertEquals(1, fake.syncCount())

        repo.refresh(); runCurrent()

        assertEquals(2, fake.syncCount())
        assertEquals(0, fake.userFrames().size) // refresh is not a user command
    }

    @Test
    fun refreshWhenDisconnectedDoesNothingAndDoesNotThrow() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)

        repo.refresh(); runCurrent()

        assertEquals(0, fake.written.size)
    }

    @Test
    fun valuesChangedOnTheAmpDuringAnOutageAreNotOverwrittenAfterReconnect() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        repo.send(AmpCommand.SetGain(20)); runCurrent()
        assertEquals(1, fake.userFrames().size)

        fake.dropConnection(); runCurrent()          // the user turns the Gain pot on the amp during the outage
        advanceTimeBy(1_001); runCurrent()           // reconnected; the app sent only the sync
        fake.emitFromAmp(hex("FE0FBB0C000419050A0A0A0A0A0A0AFF")) // the amp's dump: gain 5
        runCurrent()

        assertEquals(1, fake.userFrames().size)      // nothing pushed back over the amp's value
        assertEquals(5, repo.state.value.gain)
    }

    @Test
    fun theConnectDumpAlsoRestoresTheAuxVolume() = runTest {
        val fake = FakeAmpTransport()
        val repo = repo(fake)
        repo.connect(device); runCurrent()
        fake.emitFromAmp(hex("FE0FBB0F000119080A0A0A0A0A0A0AFF")); fake.emitFromAmp(hex("FE0FBF100001000A0A0A0A0A0A0A00FF")); runCurrent()
        assertEquals(15, repo.state.value.volume)
        assertEquals(16, repo.state.value.auxVolume)
    }
}
