package dev.blaze.controller.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.theme.Pb

/** Top tabs: the selected tab is red with a glowing underline, the rest are dim grey. */
@Composable
fun TabBar(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Pb.Border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            },
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag("tab-$title")
                    .selectable(
                        selected = isSelected, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { onSelect(index) },
                    )
                    .drawBehind {
                        if (isSelected) {
                            val y = size.height - 1.5.dp.toPx()
                            val x0 = size.width * 0.14f
                            val x1 = size.width * 0.86f
                            drawLine(Pb.Red.copy(alpha = 0.25f), Offset(x0, y), Offset(x1, y), 9.dp.toPx(), StrokeCap.Round)
                            drawLine(Pb.Red, Offset(x0, y), Offset(x1, y), 3.dp.toPx(), StrokeCap.Round)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = TextStyle(
                        color = if (isSelected) Color(0xFFFF3B4A) else Color(0xFF8A8A93),
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp,
                        shadow = if (isSelected) Shadow(Pb.Red.copy(alpha = 0.6f), Offset.Zero, 16f) else null,
                    ),
                )
            }
        }
    }
}
