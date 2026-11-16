package dev.blaze.controller.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.blaze.controller.ui.theme.LedState
import dev.blaze.controller.ui.theme.PedalboardTheme
import dev.blaze.controller.ui.theme.brushedBackground
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

/**
 * Not an assertion test: renders every component once and writes `gallery.png` to the app's external files dir so the
 * look can be reviewed on a real screenshot (see the "visual acceptance" task). Run it with
 * `adb shell am instrument -w -e class ...GalleryShotTest dev.blaze.controller.test/androidx.test.runner.AndroidJUnitRunner`
 * (without Gradle's uninstall step) and `adb pull /sdcard/Android/data/dev.blaze.controller/files/gallery.png`.
 */
class GalleryShotTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun renderComponentGallery() {
        rule.setContent {
            PedalboardTheme {
                Column(
                    Modifier.width(393.dp).brushedBackground().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        StatusLed(LedState.Connected, "BAĞLI")
                        StatusLed(LedState.Searching, "ARANIYOR…")
                        StatusLed(LedState.Disconnected, "BAĞLI DEĞİL")
                    }
                    TabBar(listOf("AMP", "EQ", "AKORT"), selected = 0, onSelect = {})
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Knob(0, 0..50, "MIN", {}, KnobSize.Medium)
                        Knob(15, 0..30, "GAIN", {}, KnobSize.Large)
                        Knob(25, 0..50, "ANA", {}, KnobSize.Medium)
                        Knob(50, 0..50, "MAX", {}, KnobSize.Small)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        PowerButton(on = true, contentDescription = "Delay", onToggle = {})
                        PowerButton(on = false, contentDescription = "Reverb", onToggle = {})
                    }
                    ChipRow(listOf("a" to "PLATE", "b" to "ROOM", "c" to "SPRING"), selected = "a", onSelect = {})
                    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(10, 12, 9, 10, 14, 11, 7).forEachIndexed { i, v ->
                            EqFader(listOf("100", "200", "400", "800", "1.6k", "3.2k", "6.4k")[i], v, 0..20, {})
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("CLEAN", "ACOUSTIC", "DRIVE", "DISTORTION", "METAL").forEachIndexed { i, name ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                PedalSwitch(name, selected = i == 3, enabled = true, onClick = {})
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        FileOutputStream(File(dir, "gallery.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
