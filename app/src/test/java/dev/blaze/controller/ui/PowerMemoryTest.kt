package dev.blaze.controller.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerMemoryTest {

    private var clock = 0L
    private fun memory() = PowerMemory(defaultLevel = 20, pendingMs = 1_000, now = { clock })

    @Test
    fun neverNonZeroTogglesToTheDefault() {
        assertEquals(20, memory().levelAfterToggle(0))
    }

    @Test
    fun remembersTheLastNonZeroLevelAndRestoresIt() {
        val m = memory()
        m.observe(30)
        assertEquals(0, m.levelAfterToggle(30))
        m.observe(0)
        clock += 5_000
        assertEquals(30, m.levelAfterToggle(0))
    }

    @Test
    fun aLevelSetOnTheAmpIsRememberedToo() {
        val m = memory()
        m.observe(12)   // knob turned on the amp
        m.observe(0)    // then turned down to zero on the amp
        assertEquals(12, m.levelAfterToggle(0))
    }

    @Test
    fun observingZeroNeverForgetsTheLastNonZero() {
        val m = memory()
        m.observe(25); m.observe(0); m.observe(0)
        assertEquals(25, m.levelAfterToggle(0))
    }

    @Test
    fun aRapidSecondToggleUndoesTheFirstEvenBeforeTheAmpStateUpdates() {
        val m = memory()
        m.observe(30)
        val first = m.levelAfterToggle(30)    // state still says 30: the write is in flight
        val second = m.levelAfterToggle(30)   // second tap arrives before the state changed
        assertEquals(0, first)
        assertEquals(30, second)
    }

    @Test
    fun anUnconfirmedRequestExpiresSoAFailedWriteDoesNotLeaveTheButtonOutOfStep() {
        val m = memory()
        m.observe(30)
        assertEquals(0, m.levelAfterToggle(30))   // the write never arrived: state stays 30
        clock += 2_000
        assertEquals(0, m.levelAfterToggle(30))   // trusts the observed state again
    }
}
