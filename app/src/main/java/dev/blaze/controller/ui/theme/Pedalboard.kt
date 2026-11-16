package dev.blaze.controller.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import dev.blaze.controller.ui.HomeStatus
import java.util.Random

/** Design tokens of the pedalboard look (values from docs/superpowers/specs/ui-redesign-mockups/5-final-all-screens.html). */
object Pb {
    val Red = Color(0xFFFF2D3D)
    val RedLight = Color(0xFFFF6A3D)
    val RedTrack = Color(0xFF2B1316)
    val Text = Color(0xFFF2F2F2)
    val TextDim = Color(0xFFB9B9C0)
    val Border = Color(0xFF2A2A30)
    val PanelTop = Color(0xC71E1E22)     // #1E1E22 @ 0.78
    val PanelBottom = Color(0xD10E0E11)  // #0E0E11 @ 0.82
    val LedGreen = Color(0xFF25D85A)
    val LedAmber = Color(0xFFF2A900)
    val LedRed = Color(0xFFE0192B)
    val Background = Color(0xFF0D0D0F)
}

/** Dark-only Material theme in the pedalboard colors (also fixes dark text on the dark window of the old screens). */
@Composable
fun PedalboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pb.Red,
            onPrimary = Pb.Text,
            secondary = Pb.RedLight,
            background = Pb.Background,
            onBackground = Pb.Text,
            surface = Color(0xFF17171B),
            onSurface = Pb.Text,
            surfaceVariant = Color(0xFF202025),
            onSurfaceVariant = Pb.TextDim,
            outline = Pb.Border,
            error = Pb.LedRed,
        ),
        content = content,
    )
}

/** Horizontal brushed-metal streaks, generated once (no asset file) and tiled. */
private val brushedTexture: ShaderBrush by lazy {
    val w = 256
    val h = 256
    val pixels = IntArray(w * h)
    val rnd = Random(7)
    var rowBase = 0f
    for (y in 0 until h) {
        // each scan line has its own brightness (long horizontal streaks) with a little grain along x
        rowBase = (rowBase * 0.55f + rnd.nextFloat() * 0.45f)
        var grain = 0f
        for (x in 0 until w) {
            grain = grain * 0.9f + (rnd.nextFloat() - 0.5f) * 0.12f
            val a = ((rowBase * 0.075f + grain * 0.05f).coerceIn(0f, 0.12f) * 255).toInt()
            pixels[y * w + x] = (a shl 24) or 0x00FFFFFF
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Brushed black: dark vertical gradient with a horizontal metal-streak texture on top. */
fun Modifier.brushedBackground(): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF1A1A1D), 0.45f to Color(0xFF0D0D0F), 1f to Color(0xFF151518),
            startY = 0f, endY = size.height,
        ),
    )
    drawRect(brushedTexture, topLeft = Offset.Zero, size = size)
}

enum class LedState { Connected, Searching, Disconnected }

data class StatusPresentation(val led: LedState, val label: String, val controlsEnabled: Boolean)

/** How the header LED and the controls behave for each connection status. Only a connected amp enables controls. */
fun HomeStatus.presentation(): StatusPresentation = when (this) {
    HomeStatus.Connected -> StatusPresentation(LedState.Connected, "BAĞLI", true)
    HomeStatus.Connecting -> StatusPresentation(LedState.Searching, "BAĞLANILIYOR…", false)
    HomeStatus.Reconnecting -> StatusPresentation(LedState.Searching, "YENİDEN BAĞLANILIYOR…", false)
    HomeStatus.Starting, HomeStatus.Scanning, HomeStatus.NotFound -> StatusPresentation(LedState.Searching, "ARANIYOR…", false)
    HomeStatus.GaveUp, HomeStatus.LinkLost, HomeStatus.NeedsPermission, HomeStatus.BluetoothOff, HomeStatus.PickDevice ->
        StatusPresentation(LedState.Disconnected, "BAĞLI DEĞİL", false)
}
