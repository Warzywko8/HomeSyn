package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DomSyncLightColorScheme = lightColorScheme(
    primary = SagePrimaryLight,
    onPrimary = SageOnPrimaryLight,
    primaryContainer = SageContainerLight,
    onPrimaryContainer = SageOnContainerLight,
    secondary = TerracottaSecondaryLight,
    onSecondary = TerracottaOnSecondaryLight,
    secondaryContainer = TerracottaContainerLight,
    onSecondaryContainer = TerracottaOnContainerLight,
    tertiary = WarmAmberTertiaryLight,
    onTertiary = WarmAmberOnTertiaryLight,
    tertiaryContainer = WarmAmberContainerLight,
    onTertiaryContainer = WarmAmberOnContainerLight,
    background = CreamBackgroundLight,
    onBackground = DeepGraphiteTextLight,
    surface = CreamSurfaceLight,
    onSurface = DeepGraphiteTextLight,
    surfaceVariant = LinenSurfaceVariantLight,
    onSurfaceVariant = MutedOliveTextLight,
    surfaceContainerLow = LinenSurfaceContainerLowLight,
    surfaceContainer = LinenSurfaceVariantLight,
    surfaceContainerHigh = LinenSurfaceContainerHighLight,
    outline = WarmOutlineLight,
    outlineVariant = WarmOutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

private val DomSyncDarkColorScheme = darkColorScheme(
    primary = SagePrimaryDark,
    onPrimary = SageOnPrimaryDark,
    primaryContainer = SageContainerDark,
    onPrimaryContainer = SageOnContainerDark,
    secondary = TerracottaSecondaryDark,
    onSecondary = TerracottaOnSecondaryDark,
    secondaryContainer = TerracottaContainerDark,
    onSecondaryContainer = TerracottaOnContainerDark,
    tertiary = WarmAmberTertiaryDark,
    onTertiary = WarmAmberOnTertiaryDark,
    tertiaryContainer = WarmAmberContainerDark,
    onTertiaryContainer = WarmAmberOnContainerDark,
    background = DeepMossBackgroundDark,
    onBackground = CreamTextDark,
    surface = BasaltSurfaceDark,
    onSurface = CreamTextDark,
    surfaceVariant = BasaltSurfaceVariantDark,
    onSurfaceVariant = MutedSageTextDark,
    surfaceContainerLow = BasaltSurfaceContainerLowDark,
    surfaceContainer = BasaltSurfaceDark,
    surfaceContainerHigh = BasaltSurfaceContainerHighDark,
    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

val DomSyncShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DomSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DomSyncDarkColorScheme
        else -> DomSyncLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DomSyncTypography,
        shapes = DomSyncShapes,
        content = content
    )
}
