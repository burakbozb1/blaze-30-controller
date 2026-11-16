package dev.blaze.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.theme.LedState
import dev.blaze.controller.ui.theme.Pb

/** A small glowing LED followed by a status label, like the CONNECTED light on a real unit. */
@Composable
fun StatusLed(state: LedState, label: String, modifier: Modifier = Modifier) {
    val (light, base, dark) = when (state) {
        LedState.Connected -> Triple(Color(0xFFB6FFC7), Pb.LedGreen, Color(0xFF0C6B27))
        LedState.Searching -> Triple(Color(0xFFFFE9A8), Pb.LedAmber, Color(0xFF7A5200))
        LedState.Disconnected -> Triple(Color(0xFFFFB3BA), Pb.LedRed, Color(0xFF6B0A14))
    }
    Row(modifier.testTag("led"), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp)) {
            val c = center
            val r = 5.5.dp.toPx()
            drawCircle(base.copy(alpha = 0.18f), r + 4.dp.toPx(), c)
            drawCircle(base.copy(alpha = 0.40f), r + 2.dp.toPx(), c)
            drawCircle(
                Brush.radialGradient(0f to light, 0.55f to base, 1f to dark, center = c + Offset(-0.25f * r, -0.3f * r), radius = r * 1.5f),
                r, c,
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color(0xFFD8D8DC), fontSize = 10.sp, letterSpacing = 1.2.sp)
    }
}
