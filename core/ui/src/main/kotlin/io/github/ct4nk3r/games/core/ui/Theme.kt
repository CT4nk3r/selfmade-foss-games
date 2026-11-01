package io.github.ct4nk3r.games.core.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Settings shared by every game in this repository. */
@Serializable
data class CommonSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val showTimer: Boolean = true,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB1F1BA),
    onPrimaryContainer = Color(0xFF002109),
    secondary = Color(0xFF8C5A1E),
    onSecondary = Color.White,
    tertiary = Color(0xFF1D4E89),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D59F),
    onPrimary = Color(0xFF003914),
    primaryContainer = Color(0xFF145224),
    onPrimaryContainer = Color(0xFFB1F1BA),
    secondary = Color(0xFFFFB872),
    onSecondary = Color(0xFF4A2800),
    tertiary = Color(0xFFA5C8FF),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun GamesTheme(activity: ComponentActivity, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    // Keep status bar icons readable when the in-app theme differs from the system theme.
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
