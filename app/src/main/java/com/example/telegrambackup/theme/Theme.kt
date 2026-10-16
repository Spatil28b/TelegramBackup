package com.example.telegrambackup.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 0 & 2: Midnight Dark (Linear/Spotify/Google Files Midnight Slate Style)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), // Sky 400 - Crisp, high contrast
    onPrimary = Color(0xFF003554),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF2DD4BF), // Teal 400
    onSecondary = Color(0xFF003831),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = Color(0xFFA78BFA), // Lavender
    onTertiary = Color(0xFF2E1065),
    background = Color(0xFF0B0F19), // Deep slate obsidian navy
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111827), // Slate 900
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF070A12),
    surfaceContainerLow = Color(0xFF151D2E),
    surfaceContainer = Color(0xFF1E293B), // Slate 800
    surfaceContainerHigh = Color(0xFF27354D),
    surfaceContainerHighest = Color(0xFF334155),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF1E293B)
)

// 1: Clean Light (Apple/Google Files Clean Aesthetic)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7), // Vibrant electric sky azure
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0284C7),
    tertiary = Color(0xFF10B981),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC), // Soft modern slate white
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0)
)

// 3: Pure AMOLED Black (True 0-Watt OLED with Neon Cyan Accent)
private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF), // Electric Cyber Cyan
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003840),
    onPrimaryContainer = Color(0xFF80FAFF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF10B981),
    onTertiary = Color.Black,
    background = Color(0xFF000000), // True pitch black
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0F0F12),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF09090B),
    surfaceContainer = Color(0xFF121215), // Deep zinc card
    surfaceContainerHigh = Color(0xFF18181B),
    surfaceContainerHighest = Color(0xFF27272A),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A)
)

// 4: Emerald Forest (Lush Nordic Pine & Jade Mint)
private val EmeraldColorScheme = darkColorScheme(
    primary = Color(0xFF10B981), // Emerald 500
    onPrimary = Color(0xFF02170F),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = Color(0xFF34D399), // Mint 400
    onSecondary = Color(0xFF02170F),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF02170F),
    background = Color(0xFF03140D), // Deep botanical black-green
    onBackground = Color(0xFFF0FDF4),
    surface = Color(0xFF062116),
    onSurface = Color(0xFFF0FDF4),
    surfaceVariant = Color(0xFF0A2B1D),
    onSurfaceVariant = Color(0xFFA7F3D0),
    surfaceContainerLowest = Color(0xFF020E09),
    surfaceContainerLow = Color(0xFF0A2B1D),
    surfaceContainer = Color(0xFF0F3A27),
    surfaceContainerHigh = Color(0xFF164C34),
    surfaceContainerHighest = Color(0xFF1E5E41),
    outline = Color(0xFF236B4E),
    outlineVariant = Color(0xFF123F2C)
)

// 5: Sunset Amber (Obsidian Roast Espresso & Golden Amber Copper)
private val SunsetAmberColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B), // Luminous Amber 500
    onPrimary = Color(0xFF261602),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = Color(0xFFFB923C), // Sunset Orange 400
    onSecondary = Color(0xFF261602),
    secondaryContainer = Color(0xFF7C2D12),
    onSecondaryContainer = Color(0xFFFED7AA),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color.Black,
    background = Color(0xFF100A05), // Dark roast espresso
    onBackground = Color(0xFFFFFBEB),
    surface = Color(0xFF1A1108),
    onSurface = Color(0xFFFFFBEB),
    surfaceVariant = Color(0xFF24170C),
    onSurfaceVariant = Color(0xFFFDE68A),
    surfaceContainerLowest = Color(0xFF0B0603),
    surfaceContainerLow = Color(0xFF24170C),
    surfaceContainer = Color(0xFF301F10),
    surfaceContainerHigh = Color(0xFF3E2916),
    surfaceContainerHighest = Color(0xFF4E341C),
    outline = Color(0xFF784D23),
    outlineVariant = Color(0xFF3D2612)
)

// 7: Liquid Glass (iOS 26 / VisionOS Spatial Glassmorphism)
private val LiquidGlassColorScheme = darkColorScheme(
    primary = LiquidGlassPrimary,
    onPrimary = LiquidGlassOnPrimary,
    primaryContainer = LiquidGlassPrimaryContainer,
    onPrimaryContainer = LiquidGlassOnPrimaryContainer,
    secondary = LiquidGlassSecondary,
    onSecondary = LiquidGlassOnSecondary,
    secondaryContainer = LiquidGlassSecondaryContainer,
    onSecondaryContainer = LiquidGlassOnSecondaryContainer,
    tertiary = LiquidGlassTertiary,
    onTertiary = LiquidGlassOnTertiary,
    tertiaryContainer = LiquidGlassTertiaryContainer,
    onTertiaryContainer = LiquidGlassOnTertiaryContainer,
    background = Color.Transparent, // Transparent background allows the ambient glowing canvas underneath to shine through!
    onBackground = LiquidGlassOnBackground,
    surface = LiquidGlassSurface,
    onSurface = LiquidGlassOnSurface,
    surfaceVariant = LiquidGlassSurfaceVariant,
    onSurfaceVariant = LiquidGlassOnSurfaceVariant,
    surfaceContainerLowest = LiquidGlassSurfaceContainerLowest,
    surfaceContainerLow = LiquidGlassSurfaceContainerLow,
    surfaceContainer = LiquidGlassSurfaceContainer,
    surfaceContainerHigh = LiquidGlassSurfaceContainerHigh,
    surfaceContainerHighest = LiquidGlassSurfaceContainerHighest,
    outline = LiquidGlassOutline,
    outlineVariant = LiquidGlassOutlineVariant,
    error = LiquidGlassError,
    onError = LiquidGlassOnError,
    errorContainer = LiquidGlassErrorContainer,
    onErrorContainer = LiquidGlassOnErrorContainer,
    inverseSurface = LiquidGlassInverseSurface,
    inverseOnSurface = LiquidGlassInverseOnSurface,
    inversePrimary = LiquidGlassInversePrimary
)

enum class AppThemeMode(
    val id: Int,
    val title: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color
) {
    SYSTEM(0, "System Default", "Follows device dark or light mode dynamically", Color(0xFF38BDF8), Color(0xFF2DD4BF), Color(0xFF0B0F19), Color(0xFF111827)),
    LIGHT(1, "Clean Light", "Crisp Google Files light aesthetic with soft slate surfaces", Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFFF8FAFC), Color(0xFFFFFFFF)),
    DARK(2, "Midnight Dark", "Slate navy with modern Spotify/Discord midnight styling", Color(0xFF38BDF8), Color(0xFF2DD4BF), Color(0xFF0B0F19), Color(0xFF111827)),
    AMOLED(3, "Pure AMOLED Black", "100% pitch black with electric cyber accents for OLED", Color(0xFF00F0FF), Color(0xFF38BDF8), Color(0xFF000000), Color(0xFF121215)),
    EMERALD(4, "Emerald Forest", "Deep jade slate with lush mint & botanical emerald", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF03140D), Color(0xFF0F3A27)),
    SUNSET(5, "Sunset Amber", "Obsidian espresso with warm golden amber & copper", Color(0xFFF59E0B), Color(0xFFFB923C), Color(0xFF100A05), Color(0xFF301F10)),
    DYNAMIC_WALLPAPER(6, "Material You Dynamic", "Adaptive palette extracted live from your device wallpaper (Android 12+)", Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF0F172A), Color(0xFF1E293B)),
    LIQUID_GLASS(7, "Liquid Glass", "Frosted spatial glass with soft cyan luminescence, crystalline borders & translucent depth layers", Color(0xFF7DD3FC), Color(0xFFC4B5FD), Color(0xFF060B18), Color(0x4D1E3461));

    companion object {
        fun fromId(id: Int): AppThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

@Composable
fun TelegramBackupTheme(
    themeMode: Int = 0,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val mode = AppThemeMode.fromId(themeMode)
    val colorScheme: ColorScheme = when (mode) {
        AppThemeMode.SYSTEM -> if (darkTheme) DarkColorScheme else LightColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.EMERALD -> EmeraldColorScheme
        AppThemeMode.SUNSET -> SunsetAmberColorScheme
        AppThemeMode.DYNAMIC_WALLPAPER -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        AppThemeMode.LIQUID_GLASS -> LiquidGlassColorScheme
    }

    val isLiquidGlass = mode == AppThemeMode.LIQUID_GLASS

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsLiquidGlass provides isLiquidGlass
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = {
                LiquidGlassAmbientCanvas {
                    content()
                }
            }
        )
    }
}
