package dev.blaze.controller.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.tuner.NoteReading
import dev.blaze.controller.tuner.TunerUi
import dev.blaze.controller.ui.SystemActions
import dev.blaze.controller.ui.components.PrimaryButton
import dev.blaze.controller.ui.theme.Pb
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val IN_TUNE_CENTS = 5.0
private val PANEL_HEIGHT = 300.dp
private val InTuneGreen = Color(0xFF2FD45A)
private val NearAmber = Color(0xFFF2A900)

/** The microphone tuner. The panel always occupies the same space: dimmed placeholders when silent, live values when a string rings. */
@Composable
fun TunerTab(ui: TunerUi, micGranted: Boolean, micNeedsSettings: Boolean, system: SystemActions, modifier: Modifier = Modifier) {
    // the screen stays on while tuning; cleared as soon as this tab leaves the composition
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            !micGranted -> {
                Text(
                    "Akort için telefonun mikrofonuna ihtiyaç var. Ses yalnızca telefonda işlenir, kaydedilmez ve gönderilmez.",
                    color = Color(0xFFE4E4E8), fontSize = 13.sp,
                )
                if (micNeedsSettings) PrimaryButton("Ayarlardan mikrofon iznini ver", system.onOpenAppSettings)
                else PrimaryButton("Mikrofon iznini ver", system.onRequestMic)
            }
            ui is TunerUi.Error -> Text(ui.message, color = Pb.LedRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            else -> TunerPanel((ui as? TunerUi.Reading)?.note)
        }
        Text(
            "En iyi sonuç için amfiyi Clean tonda, düşük sesle kullan ve telefonu amfiye yakın tut. Distortion'da algılama zorlaşabilir.",
            color = Color(0xFF85858D), fontSize = 11.sp,
        )
    }
}

@Composable
private fun TunerPanel(note: NoteReading?) {
    val active = note != null
    val cents = note?.cents ?: 0.0
    val inTune = active && abs(cents) <= IN_TUNE_CENTS
    val color = when {
        !active -> Pb.Text.copy(alpha = 0.3f)
        inTune -> InTuneGreen
        abs(cents) <= 15 -> NearAmber
        else -> Pb.LedRed
    }
    val dim = Pb.Text.copy(alpha = if (active) 0.92f else 0.3f)
    Column(
        Modifier.fillMaxWidth().height(PANEL_HEIGHT).testTag("tunerPanel"),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            note?.let { "${it.name}${it.octave}" } ?: "—", fontSize = 72.sp,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Thin, color = color,
        )
        NeedleBar(if (active) cents else null, color)
        Text(note?.let { String.format(Locale.ROOT, "%+d cent", cents.roundToInt()) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = dim)
        // one status slot: prompt while silent, "Akortlu" when in tune, blank otherwise (space stays reserved)
        Text(
            when {
                !active -> "Bir tel çal"
                inTune -> "Akortlu"
                else -> " "
            },
            color = if (inTune) InTuneGreen else dim, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
        )
        Text(note?.let { String.format(Locale.ROOT, "%.1f Hz", it.frequency) } ?: "—", color = dim, fontSize = 13.sp)
        Text(note?.let { n -> n.string?.let { "$it. tel" } ?: "—" } ?: "—", color = dim, fontSize = 13.sp)
    }
}

/** Scale -50..+50 cents with a centre mark and a needle (needle omitted when inactive). */
@Composable
private fun NeedleBar(cents: Double?, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val mid = size.width / 2
        val y = size.height / 2
        val track = Color(0xFF3A3A42)
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
        drawLine(Color(0xFF777777), Offset(mid, y - 18f), Offset(mid, y + 18f), strokeWidth = 4f)
        if (cents != null) {
            val x = mid + (cents.coerceIn(-50.0, 50.0) / 50.0).toFloat() * mid
            drawLine(color.copy(alpha = 0.35f), Offset(x, y - 26f), Offset(x, y + 26f), strokeWidth = 22f, cap = StrokeCap.Round)
            drawLine(color, Offset(x, y - 22f), Offset(x, y + 22f), strokeWidth = 10f, cap = StrokeCap.Round)
        }
    }
}
