package dev.blaze.controller.ui.screens

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class RecordingActions : AmpActions {
    val calls = mutableListOf<String>()
    override fun onTone(type: AmpType) { calls += "tone:${type.name}" }
    override fun setGain(v: Int) { calls += "gain:$v" }
    override fun setVolume(v: Int) { calls += "volume:$v" }
    override fun setAuxVolume(v: Int) { calls += "auxVolume:$v" }
    override fun setEffect(effect: GuitarEffect) { calls += "effect:${effect.name}" }
    override fun setEq(index: Int, level: Int) { calls += "eq:$index:$level" }
    override fun resetEq() { calls += "resetEq" }
    override fun setMasterDelay(v: Int) { calls += "masterDelay:$v" }
    override fun setDelayLevel(v: Int) { calls += "delayLevel:$v" }
    override fun setDelayFeedback(v: Int) { calls += "delayFeedback:$v" }
    override fun setDelayTime(v: Int) { calls += "delayTime:$v" }
    override fun toggleDelay() { calls += "toggleDelay" }
    override fun setMasterReverb(v: Int) { calls += "masterReverb:$v" }
    override fun setReverbType(type: ReverbType) { calls += "reverbType:${type.name}" }
    override fun setReverbLevel(v: Int) { calls += "reverbLevel:$v" }
    override fun setReverbTone(v: Int) { calls += "reverbTone:$v" }
    override fun setReverbTime(v: Int) { calls += "reverbTime:$v" }
    override fun toggleReverb() { calls += "toggleReverb" }
    override fun retry() { calls += "retry" }
    override fun onDevicePicked(device: FoundDevice) { calls += "pick:${device.address}" }
}

class MainScreenTest {
    @get:Rule val rule = createComposeRule()

    private val actions = RecordingActions()
    private val system = mutableListOf<String>()
    private val systemActions = SystemActions(
        onGrantPermission = { system += "grant" },
        onOpenBluetoothSettings = { system += "btSettings" },
        onOpenAppSettings = { system += "appSettings" },
        onRequestMic = { system += "mic" },
    )

    private fun ui(status: HomeStatus = HomeStatus.Connected, amp: AmpState = AmpState.DEFAULT, devices: List<FoundDevice> = emptyList(), needsSettings: Boolean = false) =
        HomeUiState(status, devices, amp, permissionNeedsSettings = needsSettings)

    private fun show(
        ui: HomeUiState = ui(),
        tuner: TunerUi = TunerUi.NoSignal,
        micGranted: Boolean = true,
        micNeedsSettings: Boolean = false,
        startTab: Int = 0,
    ) {
        rule.setContent {
            PedalboardTheme {
                var tab by remember { mutableIntStateOf(startTab) }
                MainScreen(ui, tuner, micGranted, micNeedsSettings, tab, { tab = it }, actions, systemActions)
            }
        }
    }

    @Test fun pedalTapSendsTheTone() {
        show()
        rule.onNodeWithTag("pedal-METAL").performClick()
        assertEquals(listOf("tone:METAL"), actions.calls)
    }

    @Test fun theSelectedPedalFollowsTheAmpType() {
        show(ui(amp = AmpState.DEFAULT.copy(ampType = AmpType.DISTORTION)))
        rule.onNodeWithTag("pedal-DISTORTION").assertIsSelected()
    }

    @Test fun tabsSwitchTheContent() {
        show()
        rule.onNodeWithTag("tab-EQ").performClick()
        rule.onNodeWithText("SIFIRLA").assertExists()
        rule.onNodeWithTag("tab-AKORT").performClick()
        rule.onNodeWithText("Bir tel çal").assertExists()
        rule.onNodeWithTag("tab-AMP").performClick()
        rule.onNodeWithTag("knob-GAIN").assertExists()
    }

    @Test fun eqResetCallsResetEq() {
        show(startTab = 1)
        rule.onNodeWithText("SIFIRLA").performClick()
        assertEquals(listOf("resetEq"), actions.calls)
    }

    @Test fun eqFaderSetProgressReachesTheActions() {
        show(startTab = 1)
        rule.onNodeWithTag("fader-400").performSemanticsAction(SemanticsActions.SetProgress) { it(17f) }
        assertEquals(listOf("eq:2:17"), actions.calls)
    }

    @Test fun delayPowerButtonReflectsTheStateAndToggles() {
        show(ui(amp = AmpState.DEFAULT.copy(masterDelay = 30)))
        rule.onNodeWithTag("power-Delay").assertIsOn().performClick()
        assertEquals(listOf("toggleDelay"), actions.calls)
    }

    @Test fun powerButtonsAreOffAtLevelZero() {
        show(ui(amp = AmpState.DEFAULT.copy(masterDelay = 0, masterReverb = 0)))
        rule.onNodeWithTag("power-Delay").assertIsOff()
        rule.onNodeWithTag("power-Reverb").assertIsOff().performClick()
        assertEquals(listOf("toggleReverb"), actions.calls)
    }

    @Test fun effectAndReverbTypeChipsReachTheActions() {
        show()
        rule.onNodeWithTag("chip-CHORUS").performClick()
        rule.onNodeWithTag("chip-SPRING").performClick()
        assertEquals(listOf("effect:CHORUS", "reverbType:SPRING"), actions.calls)
    }

    @Test fun knobsReachTheirActions() {
        show()
        rule.onNodeWithTag("knob-GAIN").performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        rule.onNodeWithTag("knob-ANA SES").performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        rule.onNodeWithTag("knob-Delay ANA").performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
        rule.onNodeWithTag("knob-Reverb ANA").performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        assertEquals(listOf("gain:20", "volume:30", "masterDelay:25", "masterReverb:15"), actions.calls)
    }

    @Test fun connectedShowsTheGreenStatusLabel() {
        show()
        rule.onNodeWithText("BAĞLI").assertExists()
        rule.onNodeWithText("Tekrar ara").assertDoesNotExist()
    }

    @Test fun notConnectedDisablesControlsButKeepsTheTunerTab() {
        show(ui(HomeStatus.NotFound))
        rule.onNodeWithText("ARANIYOR…").assertExists()
        rule.onNodeWithTag("pedal-METAL").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("knob-ANA SES").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 80.dp.toPx()), durationMillis = 300)
        }
        assertTrue("sent ${actions.calls}", actions.calls.isEmpty())

        rule.onNodeWithTag("tab-AKORT").performClick()
        rule.onNodeWithText("Bir tel çal").assertExists()
        rule.onNodeWithText("Tekrar ara").assertDoesNotExist() // the connection card belongs to AMP/EQ only
    }

    @Test fun retryCardCallsRetry() {
        show(ui(HomeStatus.GaveUp))
        rule.onNodeWithTag("btn-Tekrar ara").performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test fun linkLostOffersReconnect() {
        show(ui(HomeStatus.LinkLost))
        rule.onNodeWithTag("btn-Tekrar bağlan").performClick()
        assertEquals(listOf("retry"), actions.calls)
    }

    @Test fun devicePickerListsTheDevicesAndPicks() {
        val devices = listOf(FoundDevice("BLAZE-30 APP", "AA:01"), FoundDevice("BLAZE-30 APP", "BB:02"))
        show(ui(HomeStatus.PickDevice, devices = devices))
        rule.onNodeWithText("Birden fazla Blaze bulundu, birini seç:").assertExists()
        rule.onNodeWithTag("device-BB:02").performClick()
        assertEquals(listOf("pick:BB:02"), actions.calls)
    }

    @Test fun bluetoothPermissionCardUsesTheRightButton() {
        show(ui(HomeStatus.NeedsPermission))
        rule.onNodeWithTag("btn-Bluetooth iznini ver").performClick()
        assertEquals(listOf("grant"), system)
    }

    @Test fun permanentlyDeniedBluetoothPermissionOpensAppSettings() {
        show(ui(HomeStatus.NeedsPermission, needsSettings = true))
        rule.onNodeWithTag("btn-Ayarlardan Bluetooth iznini ver").performClick()
        assertEquals(listOf("appSettings"), system)
    }

    @Test fun bluetoothOffCardOpensBluetoothSettings() {
        show(ui(HomeStatus.BluetoothOff))
        rule.onNodeWithTag("btn-Bluetooth ayarlarını aç").performClick()
        assertEquals(listOf("btSettings"), system)
    }

    @Test fun tunerShowsTheReadingAndTheMicrophoneButtons() {
        show(tuner = TunerUi.Reading(NoteReading("E", 2, 3.0, 82.4, 6)), startTab = 2)
        rule.onNodeWithText("E2").assertExists()
        rule.onNodeWithText("6. tel").assertExists()
        rule.onNodeWithText("+3 cent").assertExists()
        rule.onNodeWithText("Akortlu").assertExists()
    }

    @Test fun microphonePermissionButtonsOnTheTunerTab() {
        show(micGranted = false, startTab = 2)
        rule.onNodeWithTag("btn-Mikrofon iznini ver").performClick()
        assertEquals(listOf("mic"), system)
    }

    @Test fun microphonePermanentlyDeniedOpensAppSettings() {
        show(micGranted = false, micNeedsSettings = true, startTab = 2)
        rule.onNodeWithTag("btn-Ayarlardan mikrofon iznini ver").performClick()
        assertEquals(listOf("appSettings"), system)
    }

    @Test fun tunerErrorShowsItsMessage() {
        show(tuner = TunerUi.Error("Mikrofon kullanılamıyor"), startTab = 2)
        rule.onNodeWithText("Mikrofon kullanılamıyor").assertExists()
    }

    // ---- carried over from the old tuner screen ----

    @Test fun silenceShowsDimPlaceholdersInTheSameSlotsInsteadOfHidingThem() {
        show(tuner = TunerUi.NoSignal, startTab = 2)
        rule.onNodeWithTag("tunerPanel").assertExists()
        rule.onNodeWithText("E2").assertDoesNotExist()
        assertTrue(rule.onAllNodesWithText("—").fetchSemanticsNodes().size >= 4)
    }

    @Test fun theTunerPanelKeepsItsSizeWhenSoundComesAndGoes() {
        var tuner by mutableStateOf<TunerUi>(TunerUi.NoSignal)
        rule.setContent {
            PedalboardTheme { MainScreen(ui(), tuner, true, false, 2, {}, actions, systemActions) }
        }
        val quiet = rule.onNodeWithTag("tunerPanel").getUnclippedBoundsInRoot()
        tuner = TunerUi.Reading(NoteReading("E", 2, 12.4, 82.4, 6)); rule.waitForIdle()
        assertEquals("height changed when sound started", quiet.height, rule.onNodeWithTag("tunerPanel").getUnclippedBoundsInRoot().height)
        tuner = TunerUi.Reading(NoteReading("E", 2, -3.0, 82.4, 6)); rule.waitForIdle()
        assertEquals("height changed when in tune", quiet.height, rule.onNodeWithTag("tunerPanel").getUnclippedBoundsInRoot().height)
        tuner = TunerUi.NoSignal; rule.waitForIdle()
        assertEquals("height changed when sound stopped", quiet.height, rule.onNodeWithTag("tunerPanel").getUnclippedBoundsInRoot().height)
    }

    // ---- Review Focus 5: large font on a normal-height screen ----

    @Test fun largeFontScaleKeepsAllControlsReachable() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                PedalboardTheme {
                    Box(Modifier.size(width = 393.dp, height = 780.dp)) {
                        var tab by remember { mutableIntStateOf(0) }
                        MainScreen(ui(), TunerUi.NoSignal, true, false, tab, { tab = it }, actions, systemActions)
                    }
                }
            }
        }
        listOf("CLEAN", "ACOUSTIC", "DRIVE", "DISTORTION", "METAL").forEach { rule.onNodeWithTag("pedal-$it").assertIsDisplayed() }
        listOf("AMP", "EQ", "AKORT").forEach { rule.onNodeWithTag("tab-$it").assertIsDisplayed() }
        rule.onNodeWithTag("knob-GAIN").assertIsDisplayed()
        rule.onNodeWithTag("power-Delay").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("power-Reverb").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("knob-Reverb SÜRE").performScrollTo().assertIsDisplayed()
    }

    @Test fun pedalLabelsFitEvenAtDoubleFontScale() {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2.0f)) {
                PedalboardTheme {
                    Box(Modifier.size(width = 393.dp, height = 780.dp)) {
                        MainScreen(ui(), TunerUi.NoSignal, true, false, 0, {}, actions, systemActions)
                    }
                }
            }
        }
        for (name in listOf("CLEAN", "ACOUSTIC", "DRIVE", "DISTORTION", "METAL")) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText(name, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            // the text's natural width must fit the width its column offers (hasVisualOverflow is unreliable for single-line softWrap=false text)
            assertTrue(
                "$name needs ${layout.multiParagraph.maxIntrinsicWidth}px but only ${layout.layoutInput.constraints.maxWidth}px are available at font scale 2",
                layout.multiParagraph.maxIntrinsicWidth <= layout.layoutInput.constraints.maxWidth,
            )
        }
    }

    @Test fun inLandscapeTheTabsSitInTheHeaderRowNextToTheBrand() {
        rule.setContent {
            PedalboardTheme {
                Box(Modifier.requiredSize(width = 851.dp, height = 393.dp)) {
                    MainScreen(ui(), TunerUi.NoSignal, true, false, 0, {}, actions, systemActions)
                }
            }
        }
        val brandBottom = rule.onNodeWithTag("logo").fetchSemanticsNode().boundsInRoot.bottom
        val tabTop = rule.onNodeWithTag("tab-AMP").fetchSemanticsNode().boundsInRoot.top
        assertTrue("tabs ($tabTop) should start above the brand's bottom ($brandBottom)", tabTop < brandBottom)
    }

    @Test fun tappingTheLogoOpensTheAboutPanelWithTheLinkedInButton() {
        val opened = mutableListOf<String>()
        rule.setContent {
            PedalboardTheme {
                Box(Modifier.size(width = 393.dp, height = 780.dp)) {
                    MainScreen(ui(), TunerUi.NoSignal, true, false, 0, {}, actions, SystemActions({}, {}, {}, {}, { opened += it }))
                }
            }
        }
        rule.onNodeWithTag("logo").performClick()
        rule.onNodeWithTag("btn-LINKEDIN'DE BURAK BOZ").performClick()
        assertEquals(listOf("https://www.linkedin.com/in/burakbozb1"), opened)
    }

    @Test fun musicKnobShowsTheAmpValueAndReachesItsAction() {
        show(ui(amp = AmpState.DEFAULT.copy(auxVolume = 31)))
        rule.onNodeWithTag("knob-Müzik sesi").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(31f, 0f..50f, 49),
            ),
        )
        rule.onNodeWithTag("knob-Müzik sesi").performSemanticsAction(SemanticsActions.SetProgress) { it(22f) }
        assertEquals(listOf("auxVolume:22"), actions.calls)
    }

    @Test fun musicKnobIsDisabledWhenNotConnected() {
        show(ui(HomeStatus.NotFound))
        rule.onNodeWithTag("knob-Müzik sesi").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 80.dp.toPx()), durationMillis = 300)
        }
        assertTrue("sent ${actions.calls}", actions.calls.isEmpty())
    }

    @Test fun doubleTapOnTheMusicKnobOpensItsPopup() {
        show()
        rule.onNodeWithTag("knob-Müzik sesi").performTouchInput { doubleClick() }
        rule.onNodeWithTag("popup-title").assertTextEquals("MÜZİK SESİ") // Turkish upper-casing: i -> İ
    }

    private fun overlaps(a: androidx.compose.ui.geometry.Rect, b: androidx.compose.ui.geometry.Rect) = a.overlaps(b)

    @Test fun twoHeaderKnobsDoNotOverlapEachOtherOrTheLogoInPortrait() {
        rule.setContent {
            PedalboardTheme {
                Box(Modifier.size(width = 393.dp, height = 780.dp)) {
                    MainScreen(ui(), TunerUi.NoSignal, true, false, 0, {}, actions, systemActions)
                }
            }
        }
        val main = rule.onNodeWithTag("knob-ANA SES").fetchSemanticsNode().boundsInRoot
        val music = rule.onNodeWithTag("knob-Müzik sesi").fetchSemanticsNode().boundsInRoot
        val logo = rule.onNodeWithTag("logo").fetchSemanticsNode().boundsInRoot
        assertTrue("knobs overlap: $main $music", !overlaps(main, music))
        assertTrue("music knob overlaps the logo", !overlaps(music, logo))
        assertTrue("music knob leaves the screen: $music", music.right <= with(rule.density) { 393.dp.toPx() } + 0.5f)
    }

    @Test fun twoHeaderKnobsDoNotOverlapTheTabsInLandscape() {
        rule.setContent {
            PedalboardTheme {
                Box(Modifier.requiredSize(width = 851.dp, height = 393.dp)) {
                    MainScreen(ui(), TunerUi.NoSignal, true, false, 0, {}, actions, systemActions)
                }
            }
        }
        val main = rule.onNodeWithTag("knob-ANA SES").fetchSemanticsNode().boundsInRoot
        val music = rule.onNodeWithTag("knob-Müzik sesi").fetchSemanticsNode().boundsInRoot
        val tabs = rule.onNodeWithTag("tab-AKORT").fetchSemanticsNode().boundsInRoot
        assertTrue("knobs overlap", !overlaps(main, music))
        assertTrue("music knob overlaps the tabs", !overlaps(music, tabs))
    }
}
