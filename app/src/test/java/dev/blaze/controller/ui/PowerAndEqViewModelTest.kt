package dev.blaze.controller.ui

import dev.blaze.controller.amp.AmpRepository
import dev.blaze.controller.ble.FakeAmpTransport
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.ProtocolCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerAndEqViewModelTest {
    private val blaze = FoundDevice("BLAZE-30 APP", "AA:BB:CC:00:00:01")

    private fun TestScope.connectedVm(fake: FakeAmpTransport): HomeViewModel {
        val vm = HomeViewModel(AmpRepository(fake, backgroundScope, syncSettleMs = 0L), fake, backgroundScope)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        return vm
    }

    private fun frame(c: AmpCommand) = ProtocolCodec.encode(c)

    private fun live(group: Int, item: Int, sub: Int, value: Int) = ByteArray(16).also {
        it[0] = 0xFE.toByte(); it[1] = 0x0F
        it[2] = group.toByte(); it[3] = item.toByte(); it[4] = sub.toByte(); it[5] = value.toByte()
        it[15] = 0xFF.toByte()
    }

    @Test
    fun delayOffWritesZeroAndOnRestoresThePreviousLevel() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setMasterDelay(30); runCurrent()
        assertTrue(vm.ui.value.delayOn)

        vm.toggleDelay(); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetMasterDelay(0)), fake.written.last())
        assertFalse(vm.ui.value.delayOn)

        vm.toggleDelay(); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetMasterDelay(30)), fake.written.last())
        assertTrue(vm.ui.value.delayOn)
    }

    @Test
    fun zeroSetOnTheAmpThenToggleRestoresTheLastNonZeroLevel() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        fake.emitFromAmp(live(0x02, 0x02, 0x00, 12)); runCurrent()
        fake.emitFromAmp(live(0x02, 0x02, 0x00, 0)); runCurrent()
        assertFalse(vm.ui.value.delayOn)

        vm.toggleDelay(); runCurrent()

        assertArrayEquals(frame(AmpCommand.SetMasterDelay(12)), fake.written.last())
    }

    @Test
    fun neverSetLevelTogglesOnToTheDefault() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        assertFalse(vm.ui.value.delayOn)
        vm.toggleDelay(); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetMasterDelay(20)), fake.written.last())
    }

    @Test
    fun twoQuickTapsEndWhereTheyStarted() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setMasterDelay(30); runCurrent()

        vm.toggleDelay(); vm.toggleDelay(); runCurrent()   // no state update in between

        assertArrayEquals(frame(AmpCommand.SetMasterDelay(0)), fake.written[fake.written.size - 2])
        assertArrayEquals(frame(AmpCommand.SetMasterDelay(30)), fake.written.last())
        assertTrue(vm.ui.value.delayOn)
    }

    @Test
    fun reverbPowerMirrorsDelayPower() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setMasterReverb(40); runCurrent()
        vm.toggleReverb(); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetMasterReverb(0)), fake.written.last())
        assertFalse(vm.ui.value.reverbOn)
        vm.toggleReverb(); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetMasterReverb(40)), fake.written.last())
        assertTrue(vm.ui.value.reverbOn)
    }

    @Test
    fun resetEqWritesSevenFlatBandsInOrder() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setEq(2, 3); vm.setEq(6, 18); runCurrent()
        assertEquals(3, vm.ui.value.amp.eq[2])

        vm.resetEq(); runCurrent()

        val last7 = fake.written.takeLast(7)
        for (i in 0..6) assertArrayEquals("band $i", frame(AmpCommand.SetEqBand(i, 10)), last7[i])
        assertEquals(List(7) { 10 }, vm.ui.value.amp.eq)
    }

    @Test
    fun theMasterPotMovesGuiVolumeAndAuxVolumeTogetherWithoutAnyWrites() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        val before = fake.written.size
        for (v in listOf(5, 17, 33)) {
            fake.emitFromAmp(live(0x02, 0x01, 0x00, v)); fake.emitFromAmp(live(0x04, 0x01, 0x00, v))
        }
        runCurrent()
        assertEquals(33, vm.ui.value.amp.volume)
        assertEquals(33, vm.ui.value.amp.auxVolume)
        assertEquals(before, fake.written.size)
    }
}
