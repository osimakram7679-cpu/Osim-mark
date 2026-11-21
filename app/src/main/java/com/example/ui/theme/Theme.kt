package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6DD59B),
    onPrimary = Color(0xFF00381F),
    primaryContainer = Color(0xFF0D5232),
    onPrimaryContainer = Color(0xFFD2F3DF),
    secondary = Color(0xFFFFB68B),
    onSecondary = Color(0xFF532200),
    secondaryContainer = Color(0xFF763408),
    onSecondaryContainer = Color(0xFFFFE3D1),
    tertiary = Color(0xFFF3C668),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4100),
    onTertiaryContainer = Color(0xFFFFEBB8),
    background = DarkBackground,
    onBackground = LightInk,
    surface = DarkSurface,
    onSurface = LightInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    outline = DarkOutline,
    error = Color(0xFFF87171)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = SaffronSecondary,
    onSecondary = Color.White,
    secondaryContainer = SaffronContainer,
    onSecondaryContainer = OnSaffronContainer,
    tertiary = HarvestTertiary,
    onTertiary = Color.White,
    tertiaryContainer = HarvestContainer,
    onTertiaryContainer = OnHarvestContainer,
    background = CreamBackground,
    onBackground = InkDark,
    surface = CreamSurface,
    onSurface = InkDark,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = CreamOutline,
    error = StatusDanger
)

val StoreShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun DebiganjMartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = StoreShapes,
        content = content
    )
}
