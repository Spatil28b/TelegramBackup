package com.example.telegrambackup.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 0 & 2: Midnight Dark (Telegram Default Dark)
private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlueLight,
    onPrimary = Color.Black,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = TelegramBlueLight,
    secondary = TelegramCyan,
    onSecondary = Color.Black,
    tertiary = TelegramEmerald,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkTextSecondary
)

// 1: Clean Light
private val LightColorScheme = lightColorScheme(
    primary = TelegramBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = TelegramBlueDark,
    secondary = TelegramCyan,
    onSecondary = Color.White,
    tertiary = TelegramEmerald,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFF94A3B8)
)

// 3: Pure AMOLED Black
private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    primaryContainer = AmoledSurfaceVariant,
    onPrimaryContainer = AmoledPrimary,
    secondary = AmoledSecondary,
    onSecondary = Color.Black,
    tertiary = TelegramEmerald,
    background = AmoledBackground,
    onBackground = Color.White,
    surface = AmoledSurface,
    onSurface = Color.White,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = AmoledOutline
)

// 4: Emerald Forest
private val EmeraldColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.Black,
    primaryContainer = EmeraldSurfaceVariant,
    onPrimaryContainer = EmeraldSecondary,
    secondary = EmeraldSecondary,
    onSecondary = Color.Black,
    tertiary = EmeraldTertiary,
    background = EmeraldBackground,
    onBackground = EmeraldTextPrimary,
    surface = EmeraldSurface,
    onSurface = EmeraldTextPrimary,
    surfaceVariant = EmeraldSurfaceVariant,
    onSurfaceVariant = EmeraldTextSecondary,
    outline = EmeraldOutline
)

// 5: Cyberpunk Neon
private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = Color.White,
    primaryContainer = CyberpunkSurfaceVariant,
    onPrimaryContainer = CyberpunkSecondary,
    secondary = CyberpunkSecondary,
    onSecondary = Color.Black,
    tertiary = CyberpunkTertiary,
    background = CyberpunkBackground,
    onBackground = CyberpunkTextPrimary,
    surface = CyberpunkSurface,
    onSurface = CyberpunkTextPrimary,
    surfaceVariant = CyberpunkSurfaceVariant,
    onSurfaceVariant = CyberpunkTextSecondary,
    outline = CyberpunkOutline
)

// 6: Sunset Amber & Gold
private val SunsetAmberColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = Color.Black,
    primaryContainer = SunsetSurfaceVariant,
    onPrimaryContainer = SunsetSecondary,
    secondary = SunsetSecondary,
    onSecondary = Color.Black,
    tertiary = SunsetTertiary,
    background = SunsetBackground,
    onBackground = SunsetTextPrimary,
    surface = SunsetSurface,
    onSurface = SunsetTextPrimary,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = SunsetTextSecondary,
    outline = SunsetOutline
)

// 7: Crimson Rose
private val CrimsonRoseColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonSurfaceVariant,
    onPrimaryContainer = CrimsonSecondary,
    secondary = CrimsonSecondary,
    onSecondary = Color.Black,
    tertiary = CrimsonTertiary,
    background = CrimsonBackground,
    onBackground = CrimsonTextPrimary,
    surface = CrimsonSurface,
    onSurface = CrimsonTextPrimary,
    surfaceVariant = CrimsonSurfaceVariant,
    onSurfaceVariant = CrimsonTextSecondary,
    outline = CrimsonOutline
)

// 8: Arctic Frost
private val ArcticFrostColorScheme = darkColorScheme(
    primary = ArcticPrimary,
    onPrimary = Color.Black,
    primaryContainer = ArcticSurfaceVariant,
    onPrimaryContainer = ArcticSecondary,
    secondary = ArcticSecondary,
    onSecondary = Color.Black,
    tertiary = ArcticTertiary,
    background = ArcticBackground,
    onBackground = ArcticTextPrimary,
    surface = ArcticSurface,
    onSurface = ArcticTextPrimary,
    surfaceVariant = ArcticSurfaceVariant,
    onSurfaceVariant = ArcticTextSecondary,
    outline = ArcticOutline
)

// 9: Royal Lavender
private val RoyalLavenderColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.Black,
    primaryContainer = LavenderSurfaceVariant,
    onPrimaryContainer = LavenderSecondary,
    secondary = LavenderSecondary,
    onSecondary = Color.Black,
    tertiary = LavenderTertiary,
    background = LavenderBackground,
    onBackground = LavenderTextPrimary,
    surface = LavenderSurface,
    onSurface = LavenderTextPrimary,
    surfaceVariant = LavenderSurfaceVariant,
    onSurfaceVariant = LavenderTextSecondary,
    outline = LavenderOutline
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
    LIGHT(1, "Clean Light", "Crisp Telegram light aesthetic", TelegramBlue, TelegramCyan, Color(0xFFF8FAFC), Color.White),
    DARK(2, "Midnight Dark", "Slate navy with vibrant sky accents", TelegramBlueLight, TelegramCyan, DarkBackground, DarkSurface),
    AMOLED(3, "Pure AMOLED Black", "100% pitch black for maximum OLED battery saving", AmoledPrimary, AmoledSecondary, AmoledBackground, AmoledSurfaceVariant),
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
