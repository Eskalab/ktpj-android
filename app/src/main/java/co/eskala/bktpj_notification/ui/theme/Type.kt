package co.eskala.bktpj_notification.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.eskala.bktpj_notification.R

// Bricolage Grotesque es una fuente variable (ejes wght, opsz, wdth). Fijamos el peso
// y el tamaño óptico por estilo, igual que hace el navegador con font-optical-sizing: auto.
@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: FontWeight, opticalSize: Float = 16f) = Font(
    R.font.bricolage_grotesque,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize.coerceIn(12f, 96f)),
    ),
)

private fun bricolageAt(size: Float, weight: FontWeight) = FontFamily(bricolage(weight, size))

/** Familia completa (500–800) para usos genéricos y la tipografía de Material. */
val Bricolage = FontFamily(
    bricolage(FontWeight.Medium),
    bricolage(FontWeight.SemiBold),
    bricolage(FontWeight.Bold),
    bricolage(FontWeight.ExtraBold),
)

/** Datos técnicos: connection string, package name, host. */
val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))

private fun style(size: Float, weight: FontWeight, lineHeight: Float? = null, tracking: Float = 0f) =
    TextStyle(
        fontFamily = bricolageAt(size, weight),
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
        letterSpacing = tracking.em,
    )

/** Roles tipográficos del handoff. El color se aplica en cada pantalla (fg / muted / …). */
@Immutable
data class KtpjType(
    val hero: TextStyle = style(52f, FontWeight.ExtraBold, lineHeight = 1f, tracking = -0.03f),
    val screenTitle: TextStyle = style(32f, FontWeight.ExtraBold, lineHeight = 1.05f, tracking = -0.03f),
    val onboardingTitle: TextStyle = style(34f, FontWeight.ExtraBold, lineHeight = 1.05f, tracking = -0.03f),
    val brand: TextStyle = style(20f, FontWeight.ExtraBold, tracking = -0.02f),
    val amount: TextStyle = style(20f, FontWeight.ExtraBold),
    val amountList: TextStyle = style(17f, FontWeight.ExtraBold),
    val tileCount: TextStyle = style(26f, FontWeight.Bold),
    val zoneTitle: TextStyle = style(20f, FontWeight.ExtraBold),
    val rowTitle: TextStyle = style(16f, FontWeight.Bold),
    val body: TextStyle = style(16f, FontWeight.Medium, lineHeight = 1.45f),
    val secondary: TextStyle = style(14f, FontWeight.Medium),
    val caption: TextStyle = style(13f, FontWeight.Medium),
    val captionSemi: TextStyle = style(13f, FontWeight.SemiBold),
    val captionStrong: TextStyle = style(13f, FontWeight.Bold),
    val overline: TextStyle = style(12f, FontWeight.Bold, tracking = 0.06f),
    val overlineLarge: TextStyle = style(13f, FontWeight.Bold, tracking = 0.06f),
    val button: TextStyle = style(16f, FontWeight.Bold),
    val buttonSecondary: TextStyle = style(15f, FontWeight.Bold),
    val navItem: TextStyle = style(14f, FontWeight.Bold),
    val mono: TextStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    val monoSmall: TextStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

/** Mapeo a Material 3 para que los componentes estándar (Switch, TextField…) hereden la fuente. */
internal fun KtpjType.toMaterialTypography() = Typography(
    displayLarge = hero,
    headlineLarge = screenTitle,
    titleLarge = brand,
    titleMedium = rowTitle,
    bodyLarge = body,
    bodyMedium = secondary,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = navItem,
    labelSmall = overline,
)
