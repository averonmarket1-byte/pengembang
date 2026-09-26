package com.jendelamengambang.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Palet Warna (mengikuti spesifikasi fintech modern) ----
val BgSoft = Color(0xFFF7F8FA)
val CardDark = Color(0xFF1C1C1E)
val AccentPrimary = Color(0xFF5B6CFF)
val StatusGreen = Color(0xFF22C55E)
val StatusRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFF1C1C1E)
val TextSecondary = Color(0xFF8A8F98)

private val LightColors = lightColorScheme(
    primary = AccentPrimary,
    background = BgSoft,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun JendelaMengambangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
