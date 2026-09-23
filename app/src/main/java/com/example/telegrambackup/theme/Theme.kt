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
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = EmeraldSecondary,
    secondary = EmeraldSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = EmeraldSecondary,
    tertiary = EmeraldTertiary,
    background = EmeraldBackground,
    onBackground = EmeraldTextPrimary,
    surface = EmeraldSurface,
    onSurface = EmeraldTextPrimary,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldTextSecondary,
    surfaceContainerLowest = Color(0xFF031A12),
    surfaceContainerLow = Color(0xFF08271E),
    surfaceContainer = EmeraldSurface,
    surfaceContainerHigh = EmeraldSurfaceVariant,
    surfaceContainerHighest = Color(0xFF174235),
    outline = EmeraldOutline,
    outlineVariant = Color(0xFF1E4E3F)
)

// 5: Cyberpunk Neon
private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A044E),
    onPrimaryContainer = CyberpunkSecondary,
    secondary = CyberpunkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A044E),
    onSecondaryContainer = CyberpunkSecondary,
    tertiary = CyberpunkTertiary,
    background = CyberpunkBackground,
    onBackground = CyberpunkTextPrimary,
    surface = CyberpunkSurface,
    onSurface = CyberpunkTextPrimary,
    surfaceVariant = CyberpunkSurfaceVariant,
    onSurfaceVariant = CyberpunkTextSecondary,
    surfaceContainerLowest = Color(0xFF090014),
    surfaceContainerLow = Color(0xFF130129),
    surfaceContainer = CyberpunkSurface,
    surfaceContainerHigh = CyberpunkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF3B0764),
    outline = CyberpunkOutline,
    outlineVariant = Color(0xFF581C87)
)

// 6: Sunset Amber & Gold
private val SunsetAmberColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF451A03),
    onPrimaryContainer = SunsetSecondary,
    secondary = SunsetSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = SunsetSecondary,
    tertiary = SunsetTertiary,
    background = SunsetBackground,
    onBackground = SunsetTextPrimary,
    surface = SunsetSurface,
    onSurface = SunsetTextPrimary,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetTextSecondary,
    surfaceContainerLowest = Color(0xFF110700),
    surfaceContainerLow = Color(0xFF1F0E02),
    surfaceContainer = SunsetSurface,
    surfaceContainerHigh = SunsetSurfaceVariant,
    surfaceContainerHighest = Color(0xFF451A03),
    outline = SunsetOutline,
    outlineVariant = Color(0xFF78350F)
)

// 7: Crimson Rose
private val CrimsonRoseColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4C0519),
    onPrimaryContainer = CrimsonSecondary,
    secondary = CrimsonSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4C0519),
    onSecondaryContainer = CrimsonSecondary,
    tertiary = CrimsonTertiary,
    background = CrimsonBackground,
    onBackground = CrimsonTextPrimary,
    surface = CrimsonSurface,
    onSurface = CrimsonTextPrimary,
    surfaceVariant = CrimsonSurfaceVariant,
    onSurfaceVariant = CrimsonTextSecondary,
    surfaceContainerLowest = Color(0xFF140206),
    surfaceContainerLow = Color(0xFF22050E),
    surfaceContainer = CrimsonSurface,
    surfaceContainerHigh = CrimsonSurfaceVariant,
    surfaceContainerHighest = Color(0xFF4C0519),
    outline = CrimsonOutline,
    outlineVariant = Color(0xFF881337)
)

// 8: Arctic Frost
private val ArcticFrostColorScheme = darkColorScheme(
    primary = ArcticPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = ArcticSecondary,
    secondary = ArcticSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = ArcticSecondary,
    tertiary = ArcticTertiary,
    background = ArcticBackground,
    onBackground = ArcticTextPrimary,
    surface = ArcticSurface,
    onSurface = ArcticTextPrimary,
    surfaceVariant = ArcticSurfaceVariant,
    onSurfaceVariant = ArcticTextSecondary,
    surfaceContainerLowest = Color(0xFF02131B),
    surfaceContainerLow = Color(0xFF06202C),
    surfaceContainer = ArcticSurface,
    surfaceContainerHigh = ArcticSurfaceVariant,
    surfaceContainerHighest = Color(0xFF083344),
    outline = ArcticOutline,
    outlineVariant = Color(0xFF155E75)
)

// 9: Royal Lavender
private val RoyalLavenderColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2E1065),
    onPrimaryContainer = LavenderSecondary,
    secondary = LavenderSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = LavenderSecondary,
    tertiary = LavenderTertiary,
    background = LavenderBackground,
    onBackground = LavenderTextPrimary,
    surface = LavenderSurface,
    onSurface = LavenderTextPrimary,
    surfaceVariant = LavenderSurfaceVariant,
    onSurfaceVariant = LavenderTextSecondary,
    surfaceContainerLowest = Color(0xFF0B0418),
    surfaceContainerLow = Color(0xFF170830),
    surfaceContainer = LavenderSurface,
    surfaceContainerHigh = LavenderSurfaceVariant,
    surfaceContainerHighest = Color(0xFF2E1065),
    outline = LavenderOutline,
    outlineVariant = Color(0xFF581C87)
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
    CYBERPUNK(5, "Cyberpunk Neon", "Synthwave violet with electric magenta", CyberpunkPrimary, CyberpunkSecondary, CyberpunkBackground, CyberpunkSurface),
    SUNSET(6, "Sunset Amber", "Obsidian with warm golden amber & copper", SunsetPrimary, SunsetSecondary, SunsetBackground, SunsetSurface),
    CRIMSON(7, "Crimson Rose", "Dark ruby with intense scarlet rose", CrimsonPrimary, CrimsonSecondary, CrimsonBackground, CrimsonSurface),
    ARCTIC(8, "Arctic Frost", "Subzero glacier slate with glowing ice cyan", ArcticPrimary, ArcticSecondary, ArcticBackground, ArcticSurface),
    LAVENDER(9, "Royal Lavender", "Royal velvet with radiant lilac & violet", LavenderPrimary, LavenderSecondary, LavenderBackground, LavenderSurface);

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
        AppThemeMode.CYBERPUNK -> CyberpunkColorScheme
        AppThemeMode.SUNSET -> SunsetAmberColorScheme
        AppThemeMode.CRIMSON -> CrimsonRoseColorScheme
        AppThemeMode.ARCTIC -> ArcticFrostColorScheme
        AppThemeMode.LAVENDER -> RoyalLavenderColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
