package com.vireo.editor.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vireo.editor.ui.theme.*

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "ga")
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(BrandGradient)
            .alpha(alpha)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ts")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(46.dp)
                .scale(scale)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) Purple.copy(alpha = 0.22f) else Surface2)
                .border(1.dp, if (selected) Purple else Stroke, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (selected) Purple else TextHi, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = if (selected) Purple else TextLo, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Purple else Surface2)
                    .border(1.dp, if (sel) Purple else Stroke, RoundedCornerShape(18.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(label, color = if (sel) Color.White else TextLo, fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String = String.format("%.2f", value),
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextLo, fontSize = 13.sp)
            Text(valueText, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Purple, activeTrackColor = Purple,
                inactiveTrackColor = Stroke
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Stroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Color.Black.copy(alpha = 0.65f)) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun PulsingDot(color: Color = Danger) {
    val t = rememberInfiniteTransition(label = "p")
    val s by t.animateFloat(1f, 1.5f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ps")
    Box(Modifier.size(10.dp).scale(s).clip(CircleShape).background(color))
}
