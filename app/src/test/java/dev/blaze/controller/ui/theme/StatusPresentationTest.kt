package dev.blaze.controller.ui.theme

import dev.blaze.controller.ui.HomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPresentationTest {

    private val expected = mapOf(
        HomeStatus.Connected to StatusPresentation(LedState.Connected, "BAĞLI", true),
        HomeStatus.Connecting to StatusPresentation(LedState.Searching, "BAĞLANILIYOR…", false),
        HomeStatus.Reconnecting to StatusPresentation(LedState.Searching, "YENİDEN BAĞLANILIYOR…", false),
        HomeStatus.Starting to StatusPresentation(LedState.Searching, "ARANIYOR…", false),
        HomeStatus.Scanning to StatusPresentation(LedState.Searching, "ARANIYOR…", false),
        HomeStatus.NotFound to StatusPresentation(LedState.Searching, "ARANIYOR…", false),
        HomeStatus.GaveUp to StatusPresentation(LedState.Disconnected, "BAĞLI DEĞİL", false),
        HomeStatus.LinkLost to StatusPresentation(LedState.Disconnected, "BAĞLI DEĞİL", false),
        HomeStatus.NeedsPermission to StatusPresentation(LedState.Disconnected, "BAĞLI DEĞİL", false),
        HomeStatus.BluetoothOff to StatusPresentation(LedState.Disconnected, "BAĞLI DEĞİL", false),
        HomeStatus.PickDevice to StatusPresentation(LedState.Disconnected, "BAĞLI DEĞİL", false),
    )

    @Test
    fun everyStatusHasAnExpectedPresentation() {
        for (status in HomeStatus.entries) {
            val want = expected[status] ?: error("no expectation for $status; add one when adding a status")
            assertEquals("presentation of $status", want, status.presentation())
        }
    }

    @Test
    fun onlyConnectedEnablesControls() {
        val enabled = HomeStatus.entries.filter { it.presentation().controlsEnabled }
        assertEquals(listOf(HomeStatus.Connected), enabled)
        assertTrue(HomeStatus.entries.all { it.presentation().label.isNotBlank() })
    }
}
