package dev.blaze.controller.ui

import dev.blaze.controller.amp.AmpRepository
import dev.blaze.controller.ble.FakeAmpTransport
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.ProtocolCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val blaze = FoundDevice("BLAZE-30 APP", "AA:BB:CC:00:00:01")
    private val blaze2 = FoundDevice("BLAZE-30 APP", "AA:BB:CC:DD:EE:FF")

    private fun TestScope.vm(fake: FakeAmpTransport): HomeViewModel {
        val repo = AmpRepository(fake, backgroundScope, reconnectDelayMs = 1_000L, syncSettleMs = 0L)
        return HomeViewModel(repo, fake, backgroundScope, rescanDelayMs = 3_000L, maxScanWindows = 3)
    }

    @Test
    fun tappingAToneSendsItsFrame() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)
        vm.onPermissionResult(granted = true); vm.onBluetoothEnabled(true); runCurrent()

        vm.onTone(AmpType.METAL); runCurrent()

        assertArrayEquals(ProtocolCodec.encode(AmpCommand.SetAmpType(AmpType.METAL)), fake.written.last())
        assertEquals(AmpType.METAL, vm.ui.value.amp.ampType)
    }

    @Test
    fun singleDeviceFoundConnectsAutomatically() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        assertEquals(HomeStatus.Connected, vm.ui.value.status)
        assertTrue(vm.ui.value.connected)
    }

    @Test
    fun noDeviceFoundShowsNotFoundAndRetryRescans() = runTest {
        val fake = FakeAmpTransport(emptyList())
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        assertEquals(HomeStatus.NotFound, vm.ui.value.status)
        assertFalse(vm.ui.value.connected)

        vm.retry(); runCurrent()
        assertEquals(HomeStatus.NotFound, vm.ui.value.status) // no crash, still offers retry
    }

    @Test
    fun severalBlazesShowAPickerAndSelectingOneConnects() = runTest {
        val fake = FakeAmpTransport(listOf(blaze, blaze2))
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        assertEquals(HomeStatus.PickDevice, vm.ui.value.status)
        assertEquals(listOf(blaze, blaze2), vm.ui.value.devices)

        vm.onDevicePicked(blaze2); runCurrent()
        assertEquals(HomeStatus.Connected, vm.ui.value.status)
    }

    @Test
    fun permissionDeniedAndBluetoothOffAreDistinctStatesThatRecover() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)

        vm.onPermissionResult(false); runCurrent()
        assertEquals(HomeStatus.NeedsPermission, vm.ui.value.status)

        vm.onPermissionResult(true); vm.onBluetoothEnabled(false); runCurrent()
        assertEquals(HomeStatus.BluetoothOff, vm.ui.value.status)

        vm.onBluetoothEnabled(true); runCurrent()
        assertEquals(HomeStatus.Connected, vm.ui.value.status)
    }

    @Test
    fun droppedLinkShowsReconnectingNotConnected() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()

        fake.dropConnection(); runCurrent()

        assertEquals(HomeStatus.Reconnecting, vm.ui.value.status)
        assertFalse(vm.ui.value.connected)
    }

    @Test
    fun whenTheAmpIsNotFoundTheAppKeepsLookingAndConnectsWhenItAppears() = runTest {
        val fake = FakeAmpTransport(emptyList())
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        assertEquals(HomeStatus.NotFound, vm.ui.value.status)

        fake.devices = listOf(blaze)          // the amp starts advertising again (e.g. stale link timed out)
        advanceTimeBy(3_001); runCurrent()

        assertEquals(HomeStatus.Connected, vm.ui.value.status)
    }

    @Test
    fun backgroundRescanStopsWhenBluetoothIsTurnedOff() = runTest {
        val fake = FakeAmpTransport(emptyList())
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()

        vm.onBluetoothEnabled(false); runCurrent()
        fake.devices = listOf(blaze)
        advanceTimeBy(10_000); runCurrent()

        assertEquals(HomeStatus.BluetoothOff, vm.ui.value.status) // no sneaky connect while off
        assertEquals(0, fake.connectCalls)
    }

    @Test
    fun turningBluetoothOffStopsTheRepositoryReconnectLoopSoItCannotRaceTheNextScan() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        assertEquals(1, fake.connectCalls)

        vm.onBluetoothEnabled(false); fake.dropConnection(); runCurrent()
        advanceTimeBy(5_000); runCurrent()

        assertEquals(1, fake.connectCalls) // no reconnect attempts while Bluetooth is off
    }

    @Test
    fun scanningStopsAfterTheBudgetAndOffersAManualRetry() = runTest {
        val fake = FakeAmpTransport(emptyList())
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        advanceTimeBy(60_000); runCurrent()

        assertEquals(3, fake.scanCalls)                       // gave up after maxScanWindows
        assertEquals(HomeStatus.GaveUp, vm.ui.value.status)

        fake.devices = listOf(blaze)
        vm.retry(); runCurrent()                                // manual retry works again
        assertEquals(HomeStatus.Connected, vm.ui.value.status)
    }

    @Test
    fun lostLinkThatCannotBeRestoredEndsInLinkLostWithRetry() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()

        fake.failConnects = true
        fake.dropConnection(); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(HomeStatus.LinkLost, vm.ui.value.status)

        fake.failConnects = false
        vm.retry(); runCurrent()
        assertEquals(HomeStatus.Connected, vm.ui.value.status)
    }

    @Test
    fun permanentlyDeniedPermissionIsSurfacedSoTheUiCanSendTheUserToSettings() = runTest {
        val vm = vm(FakeAmpTransport(listOf(blaze)))
        vm.onPermissionResult(false, permanentlyDenied = true); runCurrent()
        assertEquals(HomeStatus.NeedsPermission, vm.ui.value.status)
        assertTrue(vm.ui.value.permissionNeedsSettings)

        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        assertFalse(vm.ui.value.permissionNeedsSettings)
        assertEquals(HomeStatus.Connected, vm.ui.value.status)
    }

    @Test
    fun returningToTheForegroundRequestsAFreshDump() = runTest {
        val fake = FakeAmpTransport(listOf(blaze))
        val vm = vm(fake)
        vm.onPermissionResult(true); vm.onBluetoothEnabled(true); runCurrent()
        val sync = ProtocolCodec.encode(AmpCommand.Sync)
        assertEquals(1, fake.written.count { it.contentEquals(sync) })

        vm.onForeground(); runCurrent()

        assertEquals(2, fake.written.count { it.contentEquals(sync) })
    }
}
