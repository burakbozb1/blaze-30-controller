package dev.blaze.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.blaze.controller.ui.theme.Pb

/**
 * Round power switch: bevelled steel bezel, dark dome and a **drawn** power symbol (no font glyph, so it looks the same
 * on every phone). On: red LED ring around the bezel and a glowing red symbol. Off: dull steel and a grey symbol.
 * There is deliberately no on/off text.
 */
@Composable
fun PowerButton(
    on: Boolean,
    contentDescription: String,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Canvas(
        modifier
            .size(48.dp)
            .testTag("power-$contentDescription")
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { this.contentDescription = contentDescription }
            .toggleable(
                value = on, enabled = enabled, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
            ),
    ) {
        val c = center
        val bezelR = 20.dp.toPx()

        if (on) {
            val ringR = bezelR + 1.25.dp.toPx()
            drawCircle(Pb.Red.copy(alpha = 0.14f), ringR, c, style = Stroke(9.dp.toPx()))
            drawCircle(Pb.Red.copy(alpha = 0.28f), ringR, c, style = Stroke(5.5.dp.toPx()))
            drawCircle(Pb.Red, ringR, c, style = Stroke(2.5.dp.toPx()))
        }
        drawCircle(Color.Black.copy(alpha = 0.7f), bezelR, c + Offset(0f, 2.5.dp.toPx()))
        rotate(30f, pivot = c) {
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color(0xFFD4D4DA), Color(0xFF6A6A72), Color(0xFFECECF0), Color(0xFF55555D), Color(0xFFD4D4DA)),
                    center = c,
                ),
                bezelR, c,
            )
        }
        val domeR = bezelR - 3.dp.toPx()
        drawCircle(
            Brush.radialGradient(
                0f to (if (on) Color(0xFF44242A) else Color(0xFF3A3A42)),
                0.78f to (if (on) Color(0xFF12090B) else Color(0xFF0E0E11)),
                center = c + Offset(-0.15f * domeR, -0.4f * domeR), radius = domeR * 1.6f,
            ),
            domeR, c,
        )
        drawCircle(Color.Black.copy(alpha = 0.7f), domeR - 1.5.dp.toPx(), c + Offset(0f, 1.dp.toPx()), style = Stroke(3.dp.toPx()))

        drawPowerSymbol(c, 7.4.dp.toPx(), if (on) Color(0xFFFF5563) else Color(0xFF70707A), glow = on)
    }
}

/** The IEC power symbol: an open ring (gap at the top) with a vertical bar. */
private fun DrawScope.drawPowerSymbol(c: Offset, r: Float, color: Color, glow: Boolean) {
    val topLeft = Offset(c.x - r, c.y - r + 0.8.dp.toPx())
    val arcSize = Size(r * 2, r * 2)
    val barFrom = Offset(c.x, c.y - r * 1.25f)
    val barTo = Offset(c.x, c.y - r * 0.1f)
    fun symbol(width: Float, tint: Color) {
        drawArc(tint, startAngle = 300f, sweepAngle = 300f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width, cap = StrokeCap.Round))
        drawLine(tint, barFrom, barTo, width, StrokeCap.Round)
    }
    if (glow) {
        symbol(5.5.dp.toPx(), Pb.Red.copy(alpha = 0.22f))
        symbol(4.dp.toPx(), Pb.Red.copy(alpha = 0.35f))
    }
    symbol(2.4.dp.toPx(), color)
}
