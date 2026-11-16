package dev.blaze.controller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaze.controller.ui.theme.Pb

/** Choice chips, three per row, equal width. The chosen one has a red outline and glow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(
    items: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    FlowRow(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        maxItemsInEachRow = 3,
    ) {
        items.forEach { (key, text) ->
            val isSelected = key == selected
            Box(
                Modifier
                    .weight(1f)
                    .testTag("chip-$text")
                    .heightIn(min = 40.dp)
                    .drawBehind {
                        // glow for the chosen chip (drawn, not a shadow: a shadow shows through the translucent fill)
                        if (isSelected) {
                            drawRoundRect(Pb.Red.copy(alpha = 0.16f), Offset(-4.dp.toPx(), -4.dp.toPx()), Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()), CornerRadius(12.dp.toPx()))
                            drawRoundRect(Pb.Red.copy(alpha = 0.28f), Offset(-2.dp.toPx(), -2.dp.toPx()), Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()), CornerRadius(10.dp.toPx()))
                        }
                    }
                    .background(Color(0xCC141418), shape)
                    .border(1.dp, if (isSelected) Pb.Red else Color(0xFF34343C), shape)
                    .selectable(
                        selected = isSelected, enabled = enabled, role = Role.RadioButton,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { onSelect(key) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (isSelected) Color(0xFFFF5A67) else Color(0xFFBBBBC2),
                    fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, maxLines = 1,
                )
            }
        }
    }
}
