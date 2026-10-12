package com.gamedeals.watchlist.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BC4),
    secondary = Color(0xFFE0A100),
    tertiary = Color(0xFF1B8A5A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    secondary = Color(0xFFFFC83D),
    tertiary = Color(0xFF7DDBA8),
)

/** Green used for "target reached" highlights. */
val DealGreen = Color(0xFF1B8A5A)

@Composable
fun DealsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
