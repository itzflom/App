package com.lastreel.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Ring colours: calm while you have time, warm as the limit nears, mint for the break. */
object RingColors {
    val Calm = Color(0xFF7C8CFF)
    val Warm = Color(0xFFFF8A5B)
    val Rest = Color(0xFF3DD6B0)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8E9BFF),
    onPrimary = Color(0xFF0B0F33),
    background = Color(0xFF0D0E13),
    onBackground = Color(0xFFEDEEF5),
    surface = Color(0xFF16181F),
    onSurface = Color(0xFFEDEEF5),
    surfaceVariant = Color(0xFF232632),
    onSurfaceVariant = Color(0xFFA3A7B8),
    secondaryContainer = Color(0xFF2B2F3F),
    onSecondaryContainer = Color(0xFFEDEEF5),
    outline = Color(0xFF343848),
    secondary = Color(0xFF3DD6B0),
    error = Color(0xFFFF8A5B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F5FE8),
    onPrimary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF14151C),
    surface = Color.White,
    onSurface = Color(0xFF14151C),
    surfaceVariant = Color(0xFFE9EBF4),
    onSurfaceVariant = Color(0xFF5B5F72),
    secondaryContainer = Color(0xFFDDE1F2),
    onSecondaryContainer = Color(0xFF14151C),
    outline = Color(0xFFD3D6E3),
    secondary = Color(0xFF0FA583),
    error = Color(0xFFD8531F),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
        // Tabular figures so a ticking countdown doesn't jitter sideways.
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun LastReelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
