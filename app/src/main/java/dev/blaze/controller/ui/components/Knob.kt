package dev.blaze.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.SliderModel
import dev.blaze.controller.ui.SliderThrottle
import dev.blaze.controller.ui.theme.Pb
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class KnobSize(val dp: Int) { Small(46), Medium(62), Large(84) }

/**
 * Rotary-style control drawn on a Canvas: a dark cap inside a glowing red arc that wraps it concentrically.
 * Drag vertically to change the value (up = more); double-tap opens [ValuePopup] for fine adjustment.
 * Values go through [SliderModel] so amp echoes never snap the knob back while it is being dragged.
 */
@Composable
fun Knob(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit,
    size: KnobSize = KnobSize.Small,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    /** Accessible name, test tag suffix and popup title; defaults to [label] (use it when labels repeat, e.g. delay/reverb "ANA"). */
    description: String = label,
) {
    val density = LocalDensity.current
    val model = remember { SliderModel(value, SliderThrottle(60) { System.nanoTime() / 1_000_000 }) }
    LaunchedEffect(value) { model.onExternal(value) }
    var showPopup by remember { mutableStateOf(false) }
    // a popup left open while the link drops would keep "setting" values the amp never gets
    LaunchedEffect(enabled) { if (!enabled) showPopup = false }
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentRange by rememberUpdatedState(range)
    val shown = model.local.roundToInt().coerceIn(range)

    Column(modifier.alpha(if (enabled) 1f else 0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("knob-$description")
                .semantics {
                    contentDescription = description
                    stateDescription = shown.toString()
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        shown.toFloat(), range.first.toFloat()..range.last.toFloat(), (range.last - range.first - 1).coerceAtLeast(0),
                    )
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
                    if (enabled) detectTapGestures(onDoubleTap = { showPopup = true })
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var start = 0
                    var travelPx = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start = model.local.roundToInt(); travelPx = 0f },
                        onDragEnd = { model.onFinish()?.let(currentOnChange) },
                        onDragCancel = { model.onFinish()?.let(currentOnChange) },
                    ) { change, dragAmount ->
                        change.consume()
                        travelPx -= dragAmount // finger moving up is a negative y delta
                        val target = KnobMath.valueAfterDrag(start, travelPx / density.density, currentRange)
                        model.onDrag(target.toFloat())?.let(currentOnChange)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            KnobFace(shown, range, size.dp.dp)
        }
        Text(
            label, fontSize = 9.sp, letterSpacing = 1.sp, color = Pb.TextDim, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    if (showPopup) {
        ValuePopup(
            title = description.uppercase(java.util.Locale.forLanguageTag("tr-TR")), value = value, range = range,
            onValueChange = currentOnChange, onDismiss = { showPopup = false },
        )
    }
}

/** The drawing: track + glowing value arc around the outside, dark cap inside, red pointer. */
@Composable
private fun KnobFace(value: Int, range: IntRange, size: Dp) {
    Canvas(Modifier.size(size)) {
        val d = this.size.minDimension
        val c = center
        val ringStroke = d * 0.11f
        val ringRadius = (d - ringStroke) / 2f
        val arcTopLeft = Offset(c.x - ringRadius, c.y - ringRadius)
        val arcSize = Size(ringRadius * 2, ringRadius * 2)
        val sweep = KnobMath.sweepDegrees(value, range)

        // empty track (270 degrees from bottom-left, clockwise)
        drawArc(Pb.RedTrack, 135f, KnobMath.SWEEP_DEGREES, false, arcTopLeft, arcSize, style = Stroke(ringStroke))
        if (sweep > 0.5f) {
            // soft glow: three wider, fainter passes under the bright arc
            for ((width, alpha) in listOf(ringStroke * 2.2f to 0.10f, ringStroke * 1.7f to 0.18f, ringStroke * 1.3f to 0.30f)) {
                drawArc(Pb.Red.copy(alpha = alpha), 135f, sweep, false, arcTopLeft, arcSize, style = Stroke(width))
            }
            rotate(135f, pivot = c) {
                drawArc(
                    Brush.sweepGradient(listOf(Pb.Red, Pb.RedLight, Pb.RedLight), center = c),
                    0f, sweep, false, arcTopLeft, arcSize, style = Stroke(ringStroke),
                )
            }
        }

        // cap
        val capR = d * 0.34f
        drawCircle(Color.Black.copy(alpha = 0.6f), capR, c + Offset(0f, d * 0.035f))
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFF3B3B43), 0.72f to Color(0xFF101014),
                center = Offset(c.x - capR * 0.24f, c.y - capR * 0.4f), radius = capR * 1.7f,
            ),
            capR, c,
        )
        drawCircle(Color(0xFF4A4A52).copy(alpha = 0.55f), capR, c, style = Stroke(d * 0.012f))

        // pointer: angle 0 = straight up, same 270-degree sweep as the arc
        val angle = Math.toRadians((sweep - 135f).toDouble())
        val dir = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
        val from = c + dir * (d * 0.07f)
        val to = c + dir * (d * 0.30f)
        val w = max(2.5.dp.toPx(), d * 0.05f)
        drawLine(Pb.Red.copy(alpha = 0.35f), from, to, w * 2.2f, StrokeCap.Round)
        drawLine(Color(0xFFFF3B4A), from, to, w, StrokeCap.Round)
    }
}
