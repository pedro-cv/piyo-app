package bo.piyo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from docs/02-ux.md ("Palette light" / "Palette dark") and the canvas artboards.
 * Exposed through [LocalPiyo] so components use the exact canvas colours, not Material's derived ones.
 */
data class PiyoTokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val accent: Color,
    val danger: Color,
    val offline: Color,
    val offlineInk: Color,
    val mapBase: Color,
    val mapRoad: Color,
    val mapMajor: Color,
    val mapWater: Color,
    val mapPark: Color,
    val mapLabel: Color,
    val userDot: Color = Color(0xFF2F7BF6),
    val dark: Boolean,
)

val LightTokens = PiyoTokens(
    bg = Color(0xFFF7F8F5), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF0EA),
    ink = Color(0xFF15191C), muted = Color(0xFF5C636A), line = Color(0xFFDCDFD7),
    primary = Color(0xFF2A6A47), onPrimary = Color(0xFFFFFFFF), primarySoft = Color(0xFFDCEEE3),
    accent = Color(0xFFF2A33A), danger = Color(0xFFD63B3B),
    offline = Color(0xFFFFF2D1), offlineInk = Color(0xFF5A4300),
    mapBase = Color(0xFFE6EBE2), mapRoad = Color(0xFFFFFFFF), mapMajor = Color(0xFFF3DFA6),
    mapWater = Color(0xFFC9DFEA), mapPark = Color(0xFFCFE6C3), mapLabel = Color(0xFF5F6A66),
    dark = false,
)

val DarkTokens = PiyoTokens(
    bg = Color(0xFF0E1012), surface = Color(0xFF181B1E), surface2 = Color(0xFF22262A),
    ink = Color(0xFFF2F3F1), muted = Color(0xFFA3A9AF), line = Color(0xFF2D3236),
    primary = Color(0xFF5FBF8A), onPrimary = Color(0xFF07261B), primarySoft = Color(0xFF163B2A),
    accent = Color(0xFFF6B45A), danger = Color(0xFFFF6B6B),
    offline = Color(0xFF3B2F12), offlineInk = Color(0xFFFFE4A3),
    mapBase = Color(0xFF1B2024), mapRoad = Color(0xFF2B3237), mapMajor = Color(0xFF3C3A2A),
    mapWater = Color(0xFF1E2E38), mapPark = Color(0xFF1E2C22), mapLabel = Color(0xFF8A939B),
    dark = true,
)

val LocalPiyo = staticCompositionLocalOf { LightTokens }

private fun scheme(t: PiyoTokens): ColorScheme = (if (t.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = t.primary, onPrimary = t.onPrimary,
    primaryContainer = t.primarySoft, onPrimaryContainer = t.primary,
    secondary = t.accent, error = t.danger,
    background = t.bg, onBackground = t.ink,
    surface = t.surface, onSurface = t.ink,
    surfaceVariant = t.surface2, onSurfaceVariant = t.muted,
    outline = t.line, outlineVariant = t.line,
)

/** @param darkOverride "system" | "on" | "off" (Datos y conexión switch). */
@Composable
fun PiyoTheme(darkOverride: String = "system", content: @Composable () -> Unit) {
    val dark = when (darkOverride) { "on" -> true; "off" -> false; else -> isSystemInDarkTheme() }
    val tokens = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalPiyo provides tokens) {
        MaterialTheme(colorScheme = scheme(tokens), typography = PiyoTypography(), content = content)
    }
}
