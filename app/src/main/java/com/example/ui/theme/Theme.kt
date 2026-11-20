package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VoxLectDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = LightCyanTint,
    secondary = WarmCoral,
    onSecondary = OnWarmCoral,
    secondaryContainer = DeepCoralContainer,
    onSecondaryContainer = LightCoralTint,
    tertiary = StudioMint,
    onTertiary = OnStudioMint,
    tertiaryContainer = DeepMintContainer,
    onTertiaryContainer = LightMintTint,
    background = ObsidianBackground,
    onBackground = StudioTextPrimary,
    surface = ObsidianSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = ErrorCrimson,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val VoxLectLightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = Color(0xFF083344),
    secondary = CoralSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CoralContainerLight,
    onSecondaryContainer = Color(0xFF4C0519),
    tertiary = EmeraldTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF022C22),
    background = AlabasterBackground,
    onBackground = LightTextPrimary,
    surface = AlabasterSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val VoxLectShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxLectTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VoxLectDarkColorScheme else VoxLectLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoxLectTypography,
        shapes = VoxLectShapes,
        content = content
    )
}
