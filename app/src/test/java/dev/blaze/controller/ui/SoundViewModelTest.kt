package dev.blaze.controller.ui

import dev.blaze.controller.amp.AmpRepository
import dev.blaze.controller.ble.FakeAmpTransport
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.ProtocolCodec
import dev.blaze.controller.protocol.ReverbType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoundViewModelTest {
    private val blaze = FoundDevice("BLAZE-30 APP", "AA:BB:CC:00:00:01")

    private fun TestScope.connectedVm(fake: FakeAmpTransport): HomeViewModel {
        val vm = HomeViewModel(AmpRepository(fake, backgroundScope, syncSettleMs = 0L), fake, backgroundScope)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        return vm
    }

    private fun frame(c: AmpCommand) = ProtocolCodec.encode(c)

    @Test fun eqBandSendsThatBandAndClampsToTheProtocolRange() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setEq(3, 99); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetEqBand(3, 20)), fake.written.last())
        assertEquals(20, vm.ui.value.amp.eq[3])
    }

    @Test fun choosingWahSendsTheWahEffect() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setEffect(GuitarEffect.WAH); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetEffect(GuitarEffect.WAH)), fake.written.last())
    }

    @Test fun reverbAndDelayControlsSendTheirFrames() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setReverbType(ReverbType.SPRING); vm.setReverbLevel(7); vm.setReverbTone(8); vm.setReverbTime(9)
        vm.setDelayLevel(1); vm.setDelayFeedback(2); vm.setDelayTime(3); runCurrent()
        assertEquals(8, fake.written.size) // sync + 7 commands
        assertArrayEquals(frame(AmpCommand.SetReverbType(ReverbType.SPRING)), fake.written[1])
        assertArrayEquals(frame(AmpCommand.SetDelayTime(3)), fake.written[7])
    }

    @Test fun masterDelayAndReverbSendTheGuitarTabFrames() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setMasterDelay(20); vm.setMasterReverb(30); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetMasterDelay(20)), fake.written[fake.written.size - 2])
        assertArrayEquals(frame(AmpCommand.SetMasterReverb(30)), fake.written.last())
        assertEquals(20, vm.ui.value.amp.masterDelay)
    }

    @Test fun auxVolumeSendsItsFrameAndUpdatesTheState() = runTest {
        val fake = FakeAmpTransport(listOf(blaze)); val vm = connectedVm(fake)
        vm.setAuxVolume(12); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetAuxVolume(12)), fake.written.last())
        assertEquals(12, vm.ui.value.amp.auxVolume)
        vm.setAuxVolume(0); runCurrent()
        assertArrayEquals(frame(AmpCommand.SetAuxVolume(0)), fake.written.last())
        assertEquals(0, vm.ui.value.amp.auxVolume)
    }
}
