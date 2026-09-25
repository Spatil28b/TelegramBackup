package com.example.telegrambackup.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 0 & 2: Midnight Dark (Files by Google Dark Style)
private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlueLight,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFC2E7FF),
    secondary = TelegramCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = TelegramEmerald,
    onTertiary = Color.Black,
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF191C20),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF2B2F37),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF1D2026),
    surfaceContainer = Color(0xFF22252C),
    surfaceContainerHigh = Color(0xFF2B2F37),
    surfaceContainerHighest = Color(0xFF343942),
    outline = Color(0xFF8E9192),
    outlineVariant = Color(0xFF44474F)
)

// 1: Clean Light (Files by Google Light Style)
private val LightColorScheme = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = TelegramCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E3FD),
    onSecondaryContainer = Color(0xFF041E49),
    tertiary = TelegramEmerald,
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFF444746),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFE9EEF6),
    surfaceContainerHigh = Color(0xFFE1E8F2),
    surfaceContainerHighest = Color(0xFFD8E1EE),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFC4C7C5)
)

// 3: Pure AMOLED Black
private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = AmoledPrimary,
    secondary = AmoledSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = AmoledPrimary,
    tertiary = TelegramEmerald,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0A0A0A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFF3F3F46)
)

// 4: Emerald Forest
private val EmeraldColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF02170F),
    primaryContainer = Color(0xFF083827),
    onPrimaryContainer = EmeraldSecondary,
    secondary = EmeraldSecondary,
    onSecondary = Color(0xFF02170F),
    secondaryContainer = Color(0xFF083827),
    onSecondaryContainer = EmeraldSecondary,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    background = EmeraldBackground,
    onBackground = EmeraldTextPrimary,
    surface = EmeraldSurface,
    onSurface = EmeraldTextPrimary,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldTextSecondary,
    surfaceContainerLowest = Color(0xFF02140D),
    surfaceContainerLow = Color(0xFF072118),
    surfaceContainer = EmeraldSurface,
    surfaceContainerHigh = EmeraldSurfaceVariant,
    surfaceContainerHighest = Color(0xFF174C3A),
    outline = EmeraldOutline,
    outlineVariant = Color(0xFF1E5641)
)


// 6: Sunset Amber & Gold
private val SunsetAmberColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = Color(0xFF261602),
    primaryContainer = Color(0xFF482704),
    onPrimaryContainer = SunsetSecondary,
    secondary = SunsetSecondary,
    onSecondary = Color(0xFF261602),
    secondaryContainer = Color(0xFF482704),
    onSecondaryContainer = SunsetSecondary,
    tertiary = SunsetTertiary,
    onTertiary = Color.Black,
    background = SunsetBackground,
    onBackground = SunsetTextPrimary,
    surface = SunsetSurface,
    onSurface = SunsetTextPrimary,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetTextSecondary,
    surfaceContainerLowest = Color(0xFF0E0804),
    surfaceContainerLow = Color(0xFF191007),
    surfaceContainer = SunsetSurface,
    surfaceContainerHigh = SunsetSurfaceVariant,
    surfaceContainerHighest = Color(0xFF452D18),
    outline = SunsetOutline,
    outlineVariant = Color(0xFF5D3E21)
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
    SYSTEM(0, "System Default", "Follows device dark or light mode", TelegramBlue, TelegramCyan, DarkBackground, DarkSurface),
    LIGHT(1, "Clean Light", "Crisp Google Files light aesthetic", TelegramBlue, TelegramCyan, Color(0xFFF8F9FA), Color.White),
    DARK(2, "Midnight Dark", "Slate navy with Google Files dark styling", TelegramBlueLight, TelegramCyan, Color(0xFF111318), Color(0xFF191C20)),
    AMOLED(3, "Pure AMOLED Black", "100% pitch black for maximum OLED battery saving", AmoledPrimary, AmoledSecondary, Color.Black, Color(0xFF0A0A0A)),
    EMERALD(4, "Emerald Forest", "Deep jade slate with lush mint & emerald", EmeraldPrimary, EmeraldSecondary, EmeraldBackground, EmeraldSurface),
    SUNSET(5, "Sunset Amber", "Obsidian with warm golden amber & copper", SunsetPrimary, SunsetSecondary, SunsetBackground, SunsetSurface);

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
    val mode = AppThemeMode.fromId(themeMode)
    val colorScheme: ColorScheme = when (mode) {
        AppThemeMode.SYSTEM -> if (darkTheme) DarkColorScheme else LightColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.EMERALD -> EmeraldColorScheme
        AppThemeMode.SUNSET -> SunsetAmberColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
