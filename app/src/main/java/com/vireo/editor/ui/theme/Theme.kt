package com.vireo.editor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Purple = Color(0xFF8B5CF6)
val Cyan = Color(0xFF22D3EE)
val Bg = Color(0xFF0A0A0C)
val Surface1 = Color(0xFF141418)
val Surface2 = Color(0xFF1C1C22)
val Stroke = Color(0xFF2A2A33)
val TextHi = Color(0xFFE9E9F0)
val TextLo = Color(0xFF8E94A8)
val Danger = Color(0xFFEF4444)
val Accent = Color(0xFFF59E0B)

val BrandGradient = Brush.horizontalGradient(listOf(Purple, Cyan))

private val VireoColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = Cyan,
    background = Bg,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextLo,
    outline = Stroke,
    error = Danger
)

private val VireoType = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
)

@Composable
fun VireoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VireoColors, typography = VireoType, content = content)
}
