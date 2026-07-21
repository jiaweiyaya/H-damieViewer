// File: app/src/main/java/com/jiaweiya/hdamieviewer/ui/theme/Theme.kt
package com.jiaweiya.hdamieviewer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = MyCustomPurple,
    onPrimary = Color.White,
    primaryContainer = MyCustomPurpleContainer,
    onPrimaryContainer = MyCustomPurpleDark,
    secondary = MyCustomPurpleDark,
    tertiary = MyCustomPurple
)

@Composable
fun HDAmieViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeColor: Long = 0xFF9E77ED,
    content: @Composable () -> Unit
) {
    val baseColor = Color(themeColor)

    // 动态浅色配置
    val dynamicLightScheme = lightColorScheme(
        primary = baseColor,
        onPrimary = Color.White,
        primaryContainer = baseColor.copy(alpha = 0.15f),
        onPrimaryContainer = baseColor,
        secondary = baseColor,
        tertiary = baseColor
    )

    // 动态深色配置
    val dynamicDarkScheme = darkColorScheme(
        primary = baseColor,
        onPrimary = Color.Black,
        primaryContainer = baseColor.copy(alpha = 0.3f),
        onPrimaryContainer = Color.White,
        secondary = baseColor,
        tertiary = baseColor
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> dynamicDarkScheme
        else -> dynamicLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}