package com.privategallery.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Dynamic color as the default theming strategy on Android 12+, with a static fallback below
 * that, per android-design-guidelines R1.1/R1.7. Colors are always referenced through
 * MaterialTheme.colorScheme at call sites — no hardcoded hex in screens (R1.2).
 */
@Composable
fun PrivateGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(primary = SeedPrimaryDark, tertiary = SeedTertiaryDark, surface = SurfaceDark)
        else -> lightColorScheme(primary = SeedPrimaryLight, tertiary = SeedTertiaryLight, surface = SurfaceLight)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
