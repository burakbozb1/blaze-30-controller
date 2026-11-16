package dev.blaze.controller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blaze.controller.ui.SliderModel
import dev.blaze.controller.ui.SliderThrottle
import dev.blaze.controller.ui.theme.Pb
import kotlin.math.roundToInt

/**
 * Fine-adjustment window opened by double-tapping a knob. Changes apply live (throttled); TAMAM, back and a tap
 * outside only close it, the value stays where it is.
 */
@Composable
fun ValuePopup(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val model = remember { SliderModel(value, SliderThrottle(60) { System.nanoTime() / 1_000_000 }) }
    LaunchedEffect(value) { model.onExternal(value) }
    val shape = RoundedCornerShape(18.dp)

    /** One step from the value on screen (not the amp's, which may not have echoed the previous tap yet); always sent. */
    fun step(delta: Int) {
        val next = KnobMath.step(model.local.roundToInt(), delta, range)
        model.onDrag(next.toFloat())?.let(onValueChange)
        model.onFinish()?.let(onValueChange)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1D1D22), Color(0xFF121215))))
                .border(1.dp, Color(0xFF3A3A42), shape)
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, Modifier.testTag("popup-title"),
                color = Pb.TextDim, fontSize = 11.sp, letterSpacing = 1.4.sp, textAlign = TextAlign.Center,
            )
            Text(
                model.local.roundToInt().toString(), Modifier.testTag("popup-value").padding(top = 4.dp, bottom = 8.dp),
                style = TextStyle(
                    color = Color(0xFFFF4655), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold,
                    shadow = Shadow(Pb.Red.copy(alpha = 0.6f), Offset.Zero, 28f),
                ),
            )
            Slider(
                value = model.local,
                onValueChange = { model.onDrag(it)?.let(onValueChange) },
                onValueChangeFinished = { model.onFinish()?.let(onValueChange) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD8D8DE),
                    activeTrackColor = Pb.Red,
                    inactiveTrackColor = Pb.RedTrack,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StepButton("−", "popup-minus") { step(-1) }
                StepButton("+", "popup-plus") { step(+1) }
            }
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF3B4A), Color(0xFFB3101D))))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("TAMAM", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .width(96.dp)
            .testTag(tag)
            .clip(shape)
            .background(Color(0xFF1A1A1F))
            .border(1.dp, Color(0xFF3A3A42), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color(0xFFDDDDE2), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}
