package dev.blaze.controller.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.blaze.controller.amp.AmpState
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.ReverbType
import dev.blaze.controller.tuner.NoteReading
import dev.blaze.controller.tuner.TunerUi
import dev.blaze.controller.ui.AmpActions
import dev.blaze.controller.ui.HomeStatus
import dev.blaze.controller.ui.HomeUiState
import dev.blaze.controller.ui.SystemActions
import dev.blaze.controller.ui.theme.PedalboardTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot tool (asserts nothing): renders every tab/state of [MainScreen] at phone size and writes `screen-*.png` to the
 * app's external files dir for the visual acceptance step. Run through `am instrument` without Gradle's uninstall step.
 */
class ScreensShotTest {
    @get:Rule val rule = createComposeRule()

    private object NoActions : AmpActions {
        override fun onTone(type: AmpType) {}
        override fun setGain(v: Int) {}
        override fun setVolume(v: Int) {}
        override fun setAuxVolume(v: Int) {}
        override fun setEffect(effect: GuitarEffect) {}
        override fun setEq(index: Int, level: Int) {}
        override fun resetEq() {}
        override fun setMasterDelay(v: Int) {}
        override fun setDelayLevel(v: Int) {}
        override fun setDelayFeedback(v: Int) {}
        override fun setDelayTime(v: Int) {}
        override fun toggleDelay() {}
        override fun setMasterReverb(v: Int) {}
        override fun setReverbType(type: ReverbType) {}
        override fun setReverbLevel(v: Int) {}
        override fun setReverbTone(v: Int) {}
        override fun setReverbTime(v: Int) {}
        override fun toggleReverb() {}
        override fun retry() {}
        override fun onDevicePicked(device: FoundDevice) {}
    }
    private val system = SystemActions({}, {}, {}, {})

    private val playing = AmpState.DEFAULT.copy(
        ampType = AmpType.DISTORTION, gain = 18, volume = 25, effect = GuitarEffect.BYPASS,
        masterDelay = 22, delayLevel = 12, delayFeedback = 8, delayTime = 15,
        masterReverb = 18, reverbType = ReverbType.PLATE, reverbLevel = 10, reverbTone = 9, reverbTime = 14,
        eq = listOf(10, 12, 9, 10, 14, 11, 7),
    )

    private fun shot(name: String, tab: Int, ui: HomeUiState, tuner: TunerUi = TunerUi.NoSignal, micGranted: Boolean = true) {
        rule.setContent {
            PedalboardTheme {
                Box(Modifier.size(width = 393.dp, height = 851.dp)) {
                    var t by remember { mutableIntStateOf(tab) }
                    MainScreen(ui, tuner, micGranted, false, t, { t = it }, NoActions, system)
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        FileOutputStream(File(dir, "screen-$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }


    @Test fun ampConnected() = shot("amp", 0, HomeUiState(HomeStatus.Connected, emptyList(), playing))
    @Test fun ampDelayOff() = shot("amp-delay-off", 0, HomeUiState(HomeStatus.Connected, emptyList(), playing.copy(masterDelay = 0)))
    @Test fun eq() = shot("eq", 1, HomeUiState(HomeStatus.Connected, emptyList(), playing))
    @Test fun tunerActive() = shot("tuner-active", 2, HomeUiState(HomeStatus.Connected, emptyList(), playing), TunerUi.Reading(NoteReading("E", 2, 3.0, 82.4, 6)))
    @Test fun tunerIdle() = shot("tuner-idle", 2, HomeUiState(HomeStatus.Connected, emptyList(), playing))
    @Test fun tunerNoMic() = shot("tuner-nomic", 2, HomeUiState(HomeStatus.Connected, emptyList(), playing), micGranted = false)
    @Test fun notConnected() = shot("not-connected", 0, HomeUiState(HomeStatus.NotFound, emptyList(), AmpState.DEFAULT))
    @Test fun pickDevice() = shot("pick-device", 0, HomeUiState(HomeStatus.PickDevice, listOf(FoundDevice("BLAZE-30 APP", "AA:BB:CC:00:00:01"), FoundDevice("BLAZE-30 APP", "AA:BB:CC:DD:EE:FF")), AmpState.DEFAULT))
}
