package dev.blaze.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.SliderModel
import dev.blaze.controller.ui.SliderThrottle
import dev.blaze.controller.ui.theme.Pb
import kotlin.math.roundToInt

/**
 * Vertical fader: value above, band label below, red track with a silver thumb. Touching or dragging anywhere on the
 * track sets the value (top = max). Uses [SliderModel] so amp echoes never fight the finger.
 */
@Composable
fun EqFader(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val model = remember { SliderModel(value, SliderThrottle(60) { System.nanoTime() / 1_000_000 }) }
    LaunchedEffect(value) { model.onExternal(value) }
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentRange by rememberUpdatedState(range)
    val shown = model.local.roundToInt().coerceIn(range)
    val span = (range.last - range.first).coerceAtLeast(1)

    Column(modifier.width(34.dp).alpha(if (enabled) 1f else 0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            shown.toString(), Modifier.testTag("fader-value-$label"),
            color = Color(0xFFFF5A67), fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .width(34.dp)
                .testTag("fader-$label")
                .semantics {
                    contentDescription = label
                    stateDescription = shown.toString()
                    progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), range.first.toFloat()..range.last.toFloat(), (span - 1).coerceAtLeast(0))
                    if (enabled) {
                        setProgress { target ->
                            currentOnChange(target.roundToInt().coerceIn(currentRange))
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val thumbHalf = 7.5.dp.toPx()
                    fun valueAt(y: Float): Float {
                        val usable = (size.height - 2 * thumbHalf).coerceAtLeast(1f)
                        val fraction = 1f - ((y - thumbHalf) / usable).coerceIn(0f, 1f)
                        return currentRange.first + fraction * span
                    }
                    detectTapGestures(onTap = { offset ->
                        model.onDrag(valueAt(offset.y))?.let(currentOnChange)
                        model.onFinish()?.let(currentOnChange)
                    })
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val thumbHalf = 7.5.dp.toPx()
                    fun valueAt(y: Float): Float {
                        val usable = (size.height - 2 * thumbHalf).coerceAtLeast(1f)
                        val fraction = 1f - ((y - thumbHalf) / usable).coerceIn(0f, 1f)
                        return currentRange.first + fraction * span
                    }
                    detectVerticalDragGestures(
                        onDragStart = { offset -> model.onDrag(valueAt(offset.y))?.let(currentOnChange) },
                        onDragEnd = { model.onFinish()?.let(currentOnChange) },
                        onDragCancel = { model.onFinish()?.let(currentOnChange) },
                    ) { change, _ ->
                        change.consume()
                        model.onDrag(valueAt(change.position.y))?.let(currentOnChange)
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val trackW = 7.dp.toPx()
                val thumbW = 28.dp.toPx()
                val thumbH = 15.dp.toPx()
                val usable = (size.height - thumbH).coerceAtLeast(1f)
                val fraction = ((model.local - range.first) / span).coerceIn(0f, 1f)
                val thumbCenterY = thumbH / 2 + (1f - fraction) * usable
                val x = (size.width - trackW) / 2f
                val radius = CornerRadius(trackW / 2, trackW / 2)

                drawRoundRect(Pb.RedTrack, Offset(x, thumbH / 2), Size(trackW, usable), radius)
                // glow + fill from the bottom up to the thumb
                val fillTop = thumbCenterY
                val fillH = (thumbH / 2 + usable) - fillTop
                if (fillH > 0f) {
                    drawRoundRect(Pb.Red.copy(alpha = 0.20f), Offset(x - 3.dp.toPx(), fillTop), Size(trackW + 6.dp.toPx(), fillH), CornerRadius(trackW))
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Pb.Red, Color(0xFFA8101C)), startY = fillTop, endY = fillTop + fillH),
                        Offset(x, fillTop), Size(trackW, fillH), radius,
                    )
                }
                // thumb
                val tx = (size.width - thumbW) / 2f
                val ty = thumbCenterY - thumbH / 2
                drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(tx, ty + 2.dp.toPx()), Size(thumbW, thumbH), CornerRadius(4.dp.toPx()))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFD4D4DA), Color(0xFF6A6A72)), startY = ty, endY = ty + thumbH),
                    Offset(tx, ty), Size(thumbW, thumbH), CornerRadius(4.dp.toPx()),
                )
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(tx + 4.dp.toPx(), thumbCenterY), Offset(tx + thumbW - 4.dp.toPx(), thumbCenterY), 1.dp.toPx())
            }
        }
        Text(label, color = Pb.TextDim, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
