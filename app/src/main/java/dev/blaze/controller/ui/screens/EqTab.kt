package dev.blaze.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.protocol.Ranges
import dev.blaze.controller.ui.AmpActions
import dev.blaze.controller.ui.HomeUiState
import dev.blaze.controller.ui.components.EqFader
import dev.blaze.controller.ui.components.Panel
import dev.blaze.controller.ui.theme.Pb

private val bandLabels = listOf("100", "200", "400", "800", "1.6k", "3.2k", "6.4k")

/** Seven tall faders and a RESET button that returns every band to the centre. */
@Composable
fun EqTab(ui: HomeUiState, enabled: Boolean, actions: AmpActions, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Panel(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                bandLabels.forEachIndexed { index, label ->
                    EqFader(label, ui.amp.eq[index], Ranges.EQ_LEVEL, { actions.setEq(index, it) }, enabled, Modifier.fillMaxHeight())
                }
            }
        }
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A30), Color(0xFF141417))))
                .border(1.dp, Color(0xFF3C3C44), shape)
                .clickable(enabled = enabled) { actions.resetEq() },
            contentAlignment = Alignment.Center,
        ) {
            Text("SIFIRLA", color = Color(0xFFFF5A67), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.4.sp)
        }
    }
}
