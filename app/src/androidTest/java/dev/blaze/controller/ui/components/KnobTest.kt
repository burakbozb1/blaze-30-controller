package dev.blaze.controller.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KnobTest {
    @get:Rule val rule = createComposeRule()

    private val reported = mutableListOf<Int>()

    @Composable
    private fun Harness(initial: Int, range: IntRange = 0..50, label: String = "GAIN", enabled: Boolean = true) {
        var v by remember { mutableIntStateOf(initial) }
        Knob(value = v, range = range, label = label, onValueChange = { v = it; reported += it }, enabled = enabled, size = KnobSize.Large)
    }

    private fun dragBy(dyDp: Float) {
        rule.onNodeWithTag("knob-GAIN").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - dyDp.dp.toPx()), durationMillis = 400)
        }
    }

    @Test fun draggingUpIncreasesAndReportsTheValue() {
        rule.setContent { Harness(initial = 10) }
        dragBy(110f)
        rule.waitForIdle()
        assertTrue("reported $reported", reported.isNotEmpty() && reported.last() >= 30)
        assertTrue("never above the range", reported.all { it in 0..50 })
    }

    @Test fun draggingDownDecreasesTheValue() {
        rule.setContent { Harness(initial = 40) }
        dragBy(-110f)
        rule.waitForIdle()
        assertTrue("reported $reported", reported.isNotEmpty() && reported.last() <= 20)
    }

    @Test fun doubleTapOpensThePopupWithTitleAndValue() {
        rule.setContent { Harness(initial = 15) }
        rule.onNodeWithTag("knob-GAIN").performTouchInput { doubleClick() }
        rule.onNodeWithTag("popup-title").assertTextEquals("GAIN")
        rule.onNodeWithTag("popup-value").assertTextEquals("15")
    }

    @Test fun popupPlusAndMinusStepByOneAndApplyLive() {
        rule.setContent { Harness(initial = 15) }
        rule.onNodeWithTag("knob-GAIN").performTouchInput { doubleClick() }
        rule.onNodeWithTag("popup-plus").performClick()
        rule.onNodeWithTag("popup-value").assertTextEquals("16")
        rule.onNodeWithTag("popup-minus").performClick()
        rule.onNodeWithTag("popup-minus").performClick()
        rule.onNodeWithTag("popup-value").assertTextEquals("14")
        assertEquals(listOf(16, 15, 14), reported)
    }

    @Test fun tamamClosesThePopupAndKeepsTheValue() {
        rule.setContent { Harness(initial = 15) }
        rule.onNodeWithTag("knob-GAIN").performTouchInput { doubleClick() }
        rule.onNodeWithTag("popup-plus").performClick()
        rule.onNodeWithText("TAMAM").performClick()
        rule.onNodeWithTag("popup-value").assertDoesNotExist()
        assertEquals(listOf(16), reported)
    }

    @Test fun semanticsExposeTheRangeAndAllowSetProgress() {
        rule.setContent { Harness(initial = 15) }
        rule.onNodeWithTag("knob-GAIN").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(15f, 0f..50f, 49)),
        )
        rule.onNodeWithTag("knob-GAIN").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        rule.waitForIdle()
        assertEquals(listOf(20), reported)
    }

    @Test fun aDisabledKnobIgnoresDragAndDoubleTap() {
        rule.setContent { Harness(initial = 15, enabled = false) }
        dragBy(110f)
        rule.onNodeWithTag("knob-GAIN").performTouchInput { doubleClick() }
        rule.waitForIdle()
        assertTrue("reported $reported", reported.isEmpty())
        rule.onNodeWithTag("popup-value").assertDoesNotExist()
    }

    @Test fun thePopupClosesWhenTheKnobBecomesDisabled() {
        var enabled by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            var v by remember { mutableIntStateOf(15) }
            Knob(value = v, range = 0..50, label = "GAIN", onValueChange = { v = it }, enabled = enabled)
        }
        rule.onNodeWithTag("knob-GAIN").performTouchInput { doubleClick() }
        rule.onNodeWithTag("popup-value").assertTextEquals("15")
        enabled = false
        rule.waitForIdle()
        rule.onNodeWithTag("popup-value").assertDoesNotExist()
    }

    @Test fun popupPlusTwiceStepsTwiceEvenBeforeTheAmpEchoes() {
        // the parent never updates `value` (a slow echo): two quick taps must still give 16 then 17
        rule.setContent { Knob(value = 15, range = 0..50, label = "GAIN", onValueChange = { reported += it }) }
        rule.onNodeWithTag("knob-GAIN").performTouchInput { doubleClick() }
        rule.onNodeWithTag("popup-plus").performClick()
        rule.onNodeWithTag("popup-plus").performClick()
        assertEquals(listOf(16, 17), reported)
    }
}
