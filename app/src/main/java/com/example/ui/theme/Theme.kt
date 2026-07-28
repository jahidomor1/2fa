package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color.Black,
    primaryContainer = RoyalIndigo,
    onPrimaryContainer = CyberCyan,
    secondary = ElectricBlue,
    onSecondary = Color.Black,
    tertiary = NeonPurple,
    background = CyberDarkBg,
    onBackground = Color.White,
    surface = CyberDarkSurface,
    onSurface = Color.White,
    surfaceVariant = CyberDarkCard,
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = RoyalIndigo,
    secondary = CyberCyan,
    onSecondary = Color.Black,
    tertiary = NeonPurple,
    background = CyberLightBg,
    onBackground = CyberLightText,
    surface = CyberLightSurface,
    onSurface = CyberLightText,
    surfaceVariant = CyberLightCard,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun TwoFAGenerateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
