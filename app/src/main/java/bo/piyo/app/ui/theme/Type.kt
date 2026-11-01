package bo.piyo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import bo.piyo.app.R

/**
 * Sora 600/700 for titles, line numbers and durations; Roboto (system sans) for everything else.
 * Sora is loaded through Downloadable Fonts (needs Google Play services); devices without it fall back
 * to the system sans-serif, which keeps the layout identical.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val Sora: FontFamily = FontFamily(
    Font(googleFont = GoogleFont("Sora"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Sora"), fontProvider = provider, weight = FontWeight.Bold),
)

val Roboto: FontFamily = FontFamily.SansSerif

@Composable
fun PiyoTypography() = Typography(
    displayLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.7.sp),
)

/** Line number in chips / durations: Sora 700. */
val LineNumberStyle = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp)
val DurationStyle = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp)
val BrandStyle = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp)
