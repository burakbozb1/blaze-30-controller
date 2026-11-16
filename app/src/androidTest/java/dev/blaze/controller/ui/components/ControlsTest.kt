package dev.blaze.controller.ui.components

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.blaze.controller.ui.theme.LedState
import dev.blaze.controller.ui.theme.PedalboardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pedalClickCallsBackAndExposesSelectedState() {
        var clicks = 0
        rule.setContent { PedalboardTheme { PedalSwitch("DRIVE", selected = false, enabled = true, onClick = { clicks++ }) } }
        rule.onNodeWithTag("pedal-DRIVE").assertIsNotSelected().performClick()
        assertEquals(1, clicks)
    }

    @Test fun selectedPedalIsExposedAsSelected() {
        rule.setContent { PedalboardTheme { PedalSwitch("METAL", selected = true, enabled = true, onClick = {}) } }
        rule.onNodeWithTag("pedal-METAL").assertIsSelected()
    }

    @Test fun disabledPedalDoesNotCallBack() {
        var clicks = 0
        rule.setContent { PedalboardTheme { PedalSwitch("CLEAN", selected = false, enabled = false, onClick = { clicks++ }) } }
        rule.onNodeWithTag("pedal-CLEAN").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test fun powerButtonTogglesAndExposesItsState() {
        rule.setContent {
            PedalboardTheme {
                var on by remember { mutableStateOf(true) }
                PowerButton(on = on, contentDescription = "Delay", onToggle = { on = !on })
            }
        }
        rule.onNodeWithTag("power-Delay").assertIsOn().performClick()
        rule.onNodeWithTag("power-Delay").assertIsOff().performClick()
        rule.onNodeWithTag("power-Delay").assertIsOn()
    }

    @Test fun powerButtonHasNoOnOffText() {
        rule.setContent { PedalboardTheme { PowerButton(on = true, contentDescription = "Delay", onToggle = {}) } }
        rule.onNodeWithText("AÇIK").assertDoesNotExist()
        rule.onNodeWithText("KAPALI").assertDoesNotExist()
    }

    @Test fun statusLedShowsItsLabel() {
        rule.setContent {
            PedalboardTheme {
                androidx.compose.foundation.layout.Column {
                    StatusLed(LedState.Connected, "BAĞLI")
                    StatusLed(LedState.Searching, "ARANIYOR…")
                    StatusLed(LedState.Disconnected, "BAĞLI DEĞİL")
                }
            }
        }
        rule.onNodeWithText("BAĞLI").assertExists()
        rule.onNodeWithText("ARANIYOR…").assertExists()
        rule.onNodeWithText("BAĞLI DEĞİL").assertExists()
    }

    @Test fun tabSelectionCallsBackAndExposesSelected() {
        var picked = -1
        rule.setContent { PedalboardTheme { TabBar(listOf("AMP", "EQ", "AKORT"), selected = 0, onSelect = { picked = it }) } }
        rule.onNodeWithTag("tab-AMP").assertIsSelected()
        rule.onNodeWithTag("tab-EQ").assertIsNotSelected().performClick()
        assertEquals(1, picked)
    }

    @Test fun chipRowSelectsAndExposesSelected() {
        var picked: String? = null
        rule.setContent {
            PedalboardTheme {
                ChipRow(listOf("a" to "PLATE", "b" to "ROOM", "c" to "SPRING"), selected = "a", onSelect = { picked = it })
            }
        }
        rule.onNodeWithTag("chip-PLATE").assertIsSelected()
        rule.onNodeWithTag("chip-ROOM").assertIsNotSelected().performClick()
        assertEquals("b", picked)
    }

    @Test fun disabledChipRowDoesNotSelect() {
        var picked: String? = null
        rule.setContent {
            PedalboardTheme {
                ChipRow(listOf("a" to "PLATE", "b" to "ROOM"), selected = "a", onSelect = { picked = it }, enabled = false)
            }
        }
        rule.onNodeWithTag("chip-ROOM").assertIsNotEnabled().performClick()
        assertEquals(null, picked)
    }

    @Test fun faderDragUpIncreasesTheValue() {
        val reported = mutableListOf<Int>()
        rule.setContent {
            PedalboardTheme {
                var v by remember { mutableIntStateOf(0) }
                EqFader("100", v, 0..20, onValueChange = { v = it; reported += it }, modifier = Modifier.height(260.dp))
            }
        }
        rule.onNodeWithTag("fader-100").performTouchInput {
            swipe(start = Offset(center.x, height * 0.95f), end = Offset(center.x, height * 0.25f), durationMillis = 400)
        }
        rule.waitForIdle()
        assertTrue("reported $reported", reported.isNotEmpty() && reported.last() >= 12)
        assertTrue(reported.all { it in 0..20 })
    }

    @Test fun faderSetProgressReportsTheValue() {
        val reported = mutableListOf<Int>()
        rule.setContent {
            PedalboardTheme { EqFader("200", 10, 0..20, onValueChange = { reported += it }, modifier = Modifier.height(260.dp)) }
        }
        rule.onNodeWithTag("fader-200").performSemanticsAction(SemanticsActions.SetProgress) { it(14f) }
        assertEquals(listOf(14), reported)
    }

    @Test fun aDisabledFaderIgnoresInput() {
        val reported = mutableListOf<Int>()
        rule.setContent {
            PedalboardTheme { EqFader("400", 10, 0..20, onValueChange = { reported += it }, enabled = false, modifier = Modifier.height(260.dp)) }
        }
        rule.onNodeWithTag("fader-400").performTouchInput {
            swipe(start = Offset(center.x, height * 0.9f), end = Offset(center.x, height * 0.1f), durationMillis = 300)
        }
        assertTrue(reported.isEmpty())
    }

    @Test fun theFaderShowsExactlyTheValueItSent() {
        val reported = mutableListOf<Int>()
        rule.setContent {
            PedalboardTheme {
                var v by remember { mutableIntStateOf(0) }
                EqFader("100", v, 0..20, onValueChange = { v = it; reported += it }, modifier = Modifier.height(260.dp))
            }
        }
        // end the swipe where the position maps to 14.7 of 20 (not a whole step)
        rule.onNodeWithTag("fader-100").performTouchInput {
            val half = 7.5.dp.toPx()
            val usable = height - 2 * half
            swipe(start = Offset(center.x, height - half), end = Offset(center.x, half + (1f - 0.735f) * usable), durationMillis = 500)
        }
        rule.waitForIdle()
        assertTrue("reported $reported", reported.isNotEmpty())
        rule.onNodeWithTag("fader-value-100").assertTextEquals(reported.last().toString())
    }
}
