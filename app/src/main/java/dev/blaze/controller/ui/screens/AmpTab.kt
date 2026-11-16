package dev.blaze.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.Ranges
import dev.blaze.controller.protocol.ReverbType
import dev.blaze.controller.ui.AmpActions
import dev.blaze.controller.ui.HomeUiState
import dev.blaze.controller.ui.components.ChipRow
import dev.blaze.controller.ui.components.Knob
import dev.blaze.controller.ui.components.KnobSize
import dev.blaze.controller.ui.components.Panel
import dev.blaze.controller.ui.components.PowerButton
import dev.blaze.controller.ui.theme.Pb

/** Gain + effect, Delay and Reverb on one screen. The middle area scrolls only as a fallback on very small screens. */
@Composable
fun AmpTab(ui: HomeUiState, enabled: Boolean, actions: AmpActions, modifier: Modifier = Modifier) {
    val amp = ui.amp
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Knob(amp.gain, Ranges.GAIN, "GAIN", actions::setGain, KnobSize.Large, enabled)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("EFEKT", color = Pb.TextDim, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 5.dp))
                    ChipRow(GuitarEffect.entries.map { it to effectLabel(it) }, amp.effect, actions::setEffect, enabled)
                }
            }
        }

        Panel(title = "DELAY", trailing = { PowerButton(ui.delayOn, "Delay", actions::toggleDelay, enabled) }) {
            // switched-off panels fade but stay adjustable
            Row(Modifier.fillMaxWidth().alpha(if (ui.delayOn) 1f else 0.45f), horizontalArrangement = Arrangement.SpaceAround) {
                Knob(amp.masterDelay, Ranges.MASTER_FX, "ANA", actions::setMasterDelay, enabled = enabled, description = "Delay ANA")
                Knob(amp.delayLevel, Ranges.EFFECT_KNOB, "SEVİYE", actions::setDelayLevel, enabled = enabled, description = "Delay SEVİYE")
                Knob(amp.delayFeedback, Ranges.EFFECT_KNOB, "FEEDBACK", actions::setDelayFeedback, enabled = enabled, description = "Delay FEEDBACK")
                Knob(amp.delayTime, Ranges.EFFECT_KNOB, "SÜRE", actions::setDelayTime, enabled = enabled, description = "Delay SÜRE")
            }
        }

        Panel(title = "REVERB", trailing = { PowerButton(ui.reverbOn, "Reverb", actions::toggleReverb, enabled) }) {
            Column(Modifier.alpha(if (ui.reverbOn) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(ReverbType.entries.map { it to it.name }, amp.reverbType, actions::setReverbType, enabled)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Knob(amp.masterReverb, Ranges.MASTER_FX, "ANA", actions::setMasterReverb, enabled = enabled, description = "Reverb ANA")
                    Knob(amp.reverbLevel, Ranges.EFFECT_KNOB, "SEVİYE", actions::setReverbLevel, enabled = enabled, description = "Reverb SEVİYE")
                    Knob(amp.reverbTone, Ranges.EFFECT_KNOB, "TON", actions::setReverbTone, enabled = enabled, description = "Reverb TON")
                    Knob(amp.reverbTime, Ranges.EFFECT_KNOB, "SÜRE", actions::setReverbTime, enabled = enabled, description = "Reverb SÜRE")
                }
            }
        }
    }
}

private fun effectLabel(e: GuitarEffect) = when (e) {
    GuitarEffect.BYPASS -> "OFF"
    else -> e.name
}
