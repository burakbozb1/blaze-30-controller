package dev.blaze.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.theme.Pb

/**
 * Footswitch: bevelled hex nut, dark well and brushed chrome cap (drawn). When [selected] a thick red ring glows
 * around the cap and the label turns red. The whole column (label + switch) is the touch target.
 */
@Composable
fun PedalSwitch(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .testTag("pedal-$label")
            .alpha(if (enabled) 1f else 0.4f)
            .selectable(
                selected = selected, enabled = enabled, role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            maxLines = 1, softWrap = false,
            style = TextStyle(
                color = if (selected) Color(0xFFFF3B4A) else Color(0xFFECECF0),
                fontSize = with(LocalDensity.current) { 10.dp.toSp() }, // fixed size: it is a label printed on the hardware
                fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp,
                shadow = if (selected) Shadow(Pb.Red.copy(alpha = 0.95f), Offset.Zero, 16f) else null,
            ),
        )
        Spacer(Modifier.height(6.dp))
        PedalFace(selected)
    }
}

@Composable
private fun PedalFace(selected: Boolean) {
    Canvas(Modifier.size(64.dp)) {
        val w = size.width
        val h = size.height
        val c = center

        /** Pointy-sided hexagon centred on the canvas; [scale] 1 = the full nut. */
        fun hex(scale: Float): Path = Path().apply {
            val sx = w / 2f * scale
            val sy = h * 0.45f * scale
            moveTo(c.x - sx * 0.5f, c.y - sy); lineTo(c.x + sx * 0.5f, c.y - sy)
            lineTo(c.x + sx, c.y)
            lineTo(c.x + sx * 0.5f, c.y + sy); lineTo(c.x - sx * 0.5f, c.y + sy)
            lineTo(c.x - sx, c.y)
            close()
        }

        // nut: soft drop shadow, then three bevel layers (light from the top-left)
        translate(0f, 3.dp.toPx()) { drawPath(hex(1f), Color.Black.copy(alpha = 0.55f)) }
        drawPath(
            hex(1f),
            Brush.linearGradient(
                0f to Color(0xFFB9B9C0), 0.22f to Color(0xFF6D6D75), 0.55f to Color(0xFF2C2C31), 0.8f to Color(0xFF1A1A1E), 1f to Color(0xFF3A3A40),
                start = Offset.Zero, end = Offset(w, h),
            ),
        )
        drawPath(
            hex(1f - 6.dp.toPx() / w),
            Brush.linearGradient(
                0f to Color(0xFF8D8D95), 0.28f to Color(0xFF4B4B52), 0.6f to Color(0xFF1E1E22), 1f to Color(0xFF101013),
                start = Offset(w, h), end = Offset.Zero,
            ),
        )
        drawPath(
            hex(1f - 14.dp.toPx() / w),
            Brush.linearGradient(0f to Color(0xFF3B3B42), 0.6f to Color(0xFF151518), 1f to Color(0xFF26262B), start = Offset.Zero, end = Offset(w, h)),
        )

        // dark well the cap sits in
        val wellR = 23.dp.toPx()
        drawCircle(
            Brush.radialGradient(0f to Color(0xFF0B0B0D), 0.55f to Color(0xFF0B0B0D), 1f to Color(0xFF2A2A30), center = c, radius = wellR),
            wellR, c,
        )
        drawCircle(Color.White.copy(alpha = 0.12f), wellR, c + Offset(0f, 1.dp.toPx()), style = Stroke(1.dp.toPx()))

        // brushed chrome cap
        val capR = 19.dp.toPx()
        drawCircle(Color.Black.copy(alpha = 0.6f), capR, c + Offset(0f, 2.dp.toPx()))
        rotate(20f, pivot = c) {
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color(0xFFE6E6EA), Color(0xFF9A9AA2), Color(0xFFF2F2F5), Color(0xFF7A7A82), Color(0xFFDCDCE0), Color(0xFF8E8E96), Color(0xFFE6E6EA)),
                    center = c,
                ),
                capR, c,
            )
        }
        for (i in 3..18 step 2) drawCircle(Color.Black.copy(alpha = 0.10f), i.dp.toPx(), c, style = Stroke(0.6.dp.toPx()))
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.25f),
                center = c + Offset(-0.2f * capR, -0.3f * capR), radius = capR * 1.1f,
            ),
            capR * 0.74f, c,
        )
        drawCircle(Color.Black.copy(alpha = 0.35f), capR - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        drawCircle(Color(0xFF4A4A52), capR, c, style = Stroke(1.dp.toPx()))
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.85f), 1f to Color.Transparent,
                center = c + Offset(-0.32f * capR, -0.46f * capR), radius = capR * 0.55f,
            ),
            capR, c,
        )

        // red ring when selected
        if (selected) {
            val ringR = 26.dp.toPx()
            drawCircle(Pb.Red.copy(alpha = 0.10f), ringR, c, style = Stroke(9.dp.toPx()))
            drawCircle(Pb.Red.copy(alpha = 0.18f), ringR, c, style = Stroke(6.5.dp.toPx()))
            drawCircle(Pb.Red.copy(alpha = 0.32f), ringR, c, style = Stroke(5.dp.toPx()))
            drawCircle(Pb.Red, ringR, c, style = Stroke(3.5.dp.toPx()))
            drawCircle(Pb.Red.copy(alpha = 0.45f), ringR - 3.dp.toPx(), c, style = Stroke(2.dp.toPx()))
        }
    }
}
