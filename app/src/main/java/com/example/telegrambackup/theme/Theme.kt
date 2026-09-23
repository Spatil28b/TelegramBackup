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

// 5: Cyberpunk Neon
private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = Color(0xFF1A052A),
    primaryContainer = Color(0xFF381454),
    onPrimaryContainer = CyberpunkSecondary,
    secondary = CyberpunkSecondary,
    onSecondary = Color(0xFF1A052A),
    secondaryContainer = Color(0xFF381454),
    onSecondaryContainer = CyberpunkSecondary,
    tertiary = CyberpunkTertiary,
    onTertiary = Color.Black,
    background = CyberpunkBackground,
    onBackground = CyberpunkTextPrimary,
    surface = CyberpunkSurface,
    onSurface = CyberpunkTextPrimary,
    surfaceVariant = CyberpunkSurfaceVariant,
    onSurfaceVariant = CyberpunkTextSecondary,
    surfaceContainerLowest = Color(0xFF080410),
    surfaceContainerLow = Color(0xFF110A1E),
    surfaceContainer = CyberpunkSurface,
    surfaceContainerHigh = CyberpunkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF332054),
    outline = CyberpunkOutline,
    outlineVariant = Color(0xFF5B21B6)
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

// 7: Crimson Rose
private val CrimsonRoseColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color(0xFF26050E),
    primaryContainer = Color(0xFF4D0C1E),
    onPrimaryContainer = CrimsonSecondary,
    secondary = CrimsonSecondary,
    onSecondary = Color(0xFF26050E),
    secondaryContainer = Color(0xFF4D0C1E),
    onSecondaryContainer = CrimsonSecondary,
    tertiary = CrimsonTertiary,
    onTertiary = Color.Black,
    background = CrimsonBackground,
    onBackground = CrimsonTextPrimary,
    surface = CrimsonSurface,
    onSurface = CrimsonTextPrimary,
    surfaceVariant = CrimsonSurfaceVariant,
    onSurfaceVariant = CrimsonTextSecondary,
    surfaceContainerLowest = Color(0xFF0F0407),
    surfaceContainerLow = Color(0xFF1B080D),
    surfaceContainer = CrimsonSurface,
    surfaceContainerHigh = CrimsonSurfaceVariant,
    surfaceContainerHighest = Color(0xFF4C1B2A),
    outline = CrimsonOutline,
    outlineVariant = Color(0xFF6B2132)
)

// 8: Arctic Frost
private val ArcticFrostColorScheme = darkColorScheme(
    primary = ArcticPrimary,
    onPrimary = Color(0xFF031824),
    primaryContainer = Color(0xFF093750),
    onPrimaryContainer = ArcticSecondary,
    secondary = ArcticSecondary,
    onSecondary = Color(0xFF031824),
    secondaryContainer = Color(0xFF093750),
    onSecondaryContainer = ArcticSecondary,
    tertiary = ArcticTertiary,
    onTertiary = Color(0xFF031824),
    background = ArcticBackground,
    onBackground = ArcticTextPrimary,
    surface = ArcticSurface,
    onSurface = ArcticTextPrimary,
    surfaceVariant = ArcticSurfaceVariant,
    onSurfaceVariant = ArcticTextSecondary,
    surfaceContainerLowest = Color(0xFF030D14),
    surfaceContainerLow = Color(0xFF071926),
    surfaceContainer = ArcticSurface,
    surfaceContainerHigh = ArcticSurfaceVariant,
    surfaceContainerHighest = Color(0xFF19425E),
    outline = ArcticOutline,
    outlineVariant = Color(0xFF1F4E6E)
)

// 9: Royal Lavender
private val RoyalLavenderColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color(0xFF1C0E36),
    primaryContainer = Color(0xFF381D6B),
    onPrimaryContainer = LavenderSecondary,
    secondary = LavenderSecondary,
    onSecondary = Color(0xFF1C0E36),
    secondaryContainer = Color(0xFF381D6B),
    onSecondaryContainer = LavenderSecondary,
    tertiary = LavenderTertiary,
    onTertiary = Color(0xFF1C0E36),
    background = LavenderBackground,
    onBackground = LavenderTextPrimary,
    surface = LavenderSurface,
    onSurface = LavenderTextPrimary,
    surfaceVariant = LavenderSurfaceVariant,
    onSurfaceVariant = LavenderTextSecondary,
    surfaceContainerLowest = Color(0xFF0B0716),
    surfaceContainerLow = Color(0xFF140D27),
    surfaceContainer = LavenderSurface,
    surfaceContainerHigh = LavenderSurfaceVariant,
    surfaceContainerHighest = Color(0xFF372763),
    outline = LavenderOutline,
    outlineVariant = Color(0xFF50388C)
)

// 10: Nordic Glacier (Nord Frost)
private val NordColorScheme = darkColorScheme(
    primary = NordPrimary,
    onPrimary = Color(0xFF1E232A),
    primaryContainer = Color(0xFF3B4252),
    onPrimaryContainer = NordPrimary,
    secondary = NordSecondary,
    onSecondary = Color(0xFF1E232A),
    secondaryContainer = Color(0xFF3B4252),
    onSecondaryContainer = NordSecondary,
    tertiary = NordTertiary,
    onTertiary = Color(0xFF1E232A),
    background = NordBackground,
    onBackground = NordTextPrimary,
    surface = NordSurface,
    onSurface = NordTextPrimary,
    surfaceVariant = NordSurfaceVariant,
    onSurfaceVariant = NordTextSecondary,
    surfaceContainerLowest = Color(0xFF1E222A),
    surfaceContainerLow = Color(0xFF282D37),
    surfaceContainer = NordSurface,
    surfaceContainerHigh = NordSurfaceVariant,
    surfaceContainerHighest = Color(0xFF434C5E),
    outline = NordOutline,
    outlineVariant = Color(0xFF4C566A)
)

// 11: Sakura Blossom (Japanese Cherry)
private val SakuraColorScheme = darkColorScheme(
    primary = SakuraPrimary,
    onPrimary = Color(0xFF220A1E),
    primaryContainer = Color(0xFF4A1A40),
    onPrimaryContainer = SakuraPrimary,
    secondary = SakuraSecondary,
    onSecondary = Color(0xFF220A1E),
    secondaryContainer = Color(0xFF4A1A40),
    onSecondaryContainer = SakuraSecondary,
    tertiary = SakuraTertiary,
    onTertiary = Color(0xFF220A1E),
    background = SakuraBackground,
    onBackground = SakuraTextPrimary,
    surface = SakuraSurface,
    onSurface = SakuraTextPrimary,
    surfaceVariant = SakuraSurfaceVariant,
    onSurfaceVariant = SakuraTextSecondary,
    surfaceContainerLowest = Color(0xFF120B13),
    surfaceContainerLow = Color(0xFF1C131D),
    surfaceContainer = SakuraSurface,
    surfaceContainerHigh = SakuraSurfaceVariant,
    surfaceContainerHighest = Color(0xFF4A2B48),
    outline = SakuraOutline,
    outlineVariant = Color(0xFF5E365C)
)

// 12: Matrix Terminal (Cyber Hacker)
private val MatrixColorScheme = darkColorScheme(
    primary = MatrixPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0D3318),
    onPrimaryContainer = MatrixSecondary,
    secondary = MatrixSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0D3318),
    onSecondaryContainer = MatrixSecondary,
    tertiary = MatrixTertiary,
    onTertiary = Color.Black,
    background = MatrixBackground,
    onBackground = MatrixTextPrimary,
    surface = MatrixSurface,
    onSurface = MatrixTextPrimary,
    surfaceVariant = MatrixSurfaceVariant,
    onSurfaceVariant = MatrixTextSecondary,
    surfaceContainerLowest = Color(0xFF010603),
    surfaceContainerLow = Color(0xFF051108),
    surfaceContainer = MatrixSurface,
    surfaceContainerHigh = MatrixSurfaceVariant,
    surfaceContainerHighest = Color(0xFF163B20),
    outline = MatrixOutline,
    outlineVariant = Color(0xFF184223)
)

// 13: Mocha Espresso (Warm Walnut)
private val MochaColorScheme = darkColorScheme(
    primary = MochaPrimary,
    onPrimary = Color(0xFF211406),
    primaryContainer = Color(0xFF3F270B),
    onPrimaryContainer = MochaPrimary,
    secondary = MochaSecondary,
    onSecondary = Color(0xFF211406),
    secondaryContainer = Color(0xFF3F270B),
    onSecondaryContainer = MochaSecondary,
    tertiary = MochaTertiary,
    onTertiary = Color.White,
    background = MochaBackground,
    onBackground = MochaTextPrimary,
    surface = MochaSurface,
    onSurface = MochaTextPrimary,
    surfaceVariant = MochaSurfaceVariant,
    onSurfaceVariant = MochaTextSecondary,
    surfaceContainerLowest = Color(0xFF0D0A08),
    surfaceContainerLow = Color(0xFF17120F),
    surfaceContainer = MochaSurface,
    surfaceContainerHigh = MochaSurfaceVariant,
    surfaceContainerHighest = Color(0xFF42312A),
    outline = MochaOutline,
    outlineVariant = Color(0xFF523D35)
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
    LAVENDER(9, "Royal Lavender", "Royal velvet with radiant lilac & violet", LavenderPrimary, LavenderSecondary, LavenderBackground, LavenderSurface),
    NORD(10, "Nordic Glacier", "Nord polar slate with glacial frost teal", NordPrimary, NordSecondary, NordBackground, NordSurface),
    SAKURA(11, "Sakura Blossom", "Japanese midnight plum with petal rose", SakuraPrimary, SakuraSecondary, SakuraBackground, SakuraSurface),
    MATRIX(12, "Matrix Terminal", "Obsidian hacker terminal with phosphor green", MatrixPrimary, MatrixSecondary, MatrixBackground, MatrixSurface),
    MOCHA(13, "Mocha Espresso", "Warm roasted cocoa with golden caramel", MochaPrimary, MochaSecondary, MochaBackground, MochaSurface);

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
        AppThemeMode.NORD -> NordColorScheme
        AppThemeMode.SAKURA -> SakuraColorScheme
        AppThemeMode.MATRIX -> MatrixColorScheme
        AppThemeMode.MOCHA -> MochaColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
