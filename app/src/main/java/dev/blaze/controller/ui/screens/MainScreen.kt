package dev.blaze.controller.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import dev.blaze.controller.R
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.Ranges
import dev.blaze.controller.tuner.TunerUi
import dev.blaze.controller.ui.AmpActions
import dev.blaze.controller.ui.HomeStatus
import dev.blaze.controller.ui.HomeUiState
import dev.blaze.controller.ui.SystemActions
import dev.blaze.controller.ui.components.Knob
import dev.blaze.controller.ui.components.KnobSize
import dev.blaze.controller.ui.components.PedalSwitch
import dev.blaze.controller.ui.components.StatusLed
import dev.blaze.controller.ui.components.TabBar
import dev.blaze.controller.ui.theme.Pb
import dev.blaze.controller.ui.theme.brushedBackground
import dev.blaze.controller.ui.theme.presentation

const val TAB_AMP = 0
const val TAB_EQ = 1
const val TAB_TUNER = 2

/**
 * The whole app: brand + LED + master volume on top, tabs, the selected tab's content, and the footswitch row that
 * stays at the bottom on every tab. When the amp is not connected the AMP/EQ content and the pedals fade and ignore
 * input and a card explains why; the tuner tab stays usable (it only needs the microphone).
 */
@Composable
fun MainScreen(
    ui: HomeUiState,
    tuner: TunerUi,
    micGranted: Boolean,
    micNeedsSettings: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    actions: AmpActions,
    system: SystemActions,
) {
    val status = ui.status.presentation()
    val enabled = status.controlsEnabled

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = androidx.compose.ui.graphics.Color(0xFF121215)) { AboutPanel(system.onOpenUrl, Modifier.statusBarsPadding()) }
        },
    ) {
    BoxWithConstraints(Modifier.fillMaxSize().brushedBackground()) {
    // sideways the tabs move up into the header row to give the content more height
    val landscape = maxWidth > maxHeight
    Column(
        Modifier
            .fillMaxSize()
            // keep clear of the system navigation / camera cutout at the sides
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (landscape) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.logo), "Hakkında",
                Modifier.size(40.dp).clip(CircleShape).testTag("logo").clickable { scope.launch { drawer.open() } },
            )
            Column(Modifier.padding(start = 10.dp).then(if (landscape) Modifier else Modifier.weight(1f))) {
                StatusLed(status.led, status.label)
            }
            if (landscape) TabBar(listOf("AMP", "EQ", "AKORT"), tab, onTab, Modifier.weight(1f).padding(horizontal = 16.dp))
            Knob(ui.amp.volume, Ranges.VOLUME, "ANA SES", actions::setVolume, KnobSize.Medium, enabled)
            Knob(
                ui.amp.auxVolume, Ranges.AUX_VOLUME, "MÜZİK", actions::setAuxVolume, KnobSize.Medium, enabled,
                modifier = Modifier.padding(start = 6.dp), description = "Müzik sesi",
            )
        }
        if (!landscape) TabBar(listOf("AMP", "EQ", "AKORT"), tab, onTab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                TAB_AMP -> AmpTab(ui, enabled, actions)
                TAB_EQ -> EqTab(ui, enabled, actions)
                else -> TunerTab(tuner, micGranted, micNeedsSettings, system)
            }
            if (tab != TAB_TUNER && ui.status != HomeStatus.Connected) {
                ConnectionCard(ui, actions, system, Modifier.align(Alignment.TopCenter).padding(12.dp))
            }
        }
        PedalDock(ui.amp.ampType, enabled, actions::onTone)
    }
    }
    }
}

private val tones = listOf(
    AmpType.CLEAN to "CLEAN",
    AmpType.ACOUSTIC to "ACOUSTIC",
    AmpType.DRIVE to "DRIVE",
    AmpType.DISTORTION to "DISTORTION",
    AmpType.METAL to "METAL",
)

/** Five footswitches along the bottom edge; the lit one is the amp's current tone. */
@Composable
private fun PedalDock(selected: AmpType, enabled: Boolean, onTone: (AmpType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .brushedBackground()
            .background(Color.Black.copy(alpha = 0.35f))
            .drawBehind { drawLine(Color(0xFF34343B), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        tones.forEach { (type, name) ->
            PedalSwitch(name, selected == type, enabled, { onTone(type) }, Modifier.weight(1f))
        }
    }
}
