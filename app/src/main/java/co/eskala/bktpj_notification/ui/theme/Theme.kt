package co.eskala.bktpj_notification.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Preferencia de tema de Ajustes › Tema (segmentado Sistema / Claro / Oscuro). */
enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private val DefaultKtpjType = KtpjType()

val LocalKtpjColors = staticCompositionLocalOf { LightKtpjColors }
val LocalKtpjType = staticCompositionLocalOf { DefaultKtpjType }

/** Acceso a los tokens: `KtpjTheme.colors.accent`, `KtpjTheme.type.hero`. */
object KtpjTheme {
    val colors: KtpjColors
        @Composable @ReadOnlyComposable get() = LocalKtpjColors.current

    val type: KtpjType
        @Composable @ReadOnlyComposable get() = LocalKtpjType.current
}

@Composable
fun KtpjTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkKtpjColors else LightKtpjColors
    val type = DefaultKtpjType

    MaterialTheme(
        colorScheme = colors.toColorScheme(),
        typography = type.toMaterialTypography(),
    ) {
        CompositionLocalProvider(
            LocalKtpjColors provides colors,
            LocalKtpjType provides type,
            content = content,
        )
    }
}

/** Para que los componentes de Material 3 que usemos (Switch, ripple, selección) usen los tokens. */
private fun KtpjColors.toColorScheme() = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = invBg,
    onPrimary = invFg,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = accentSoft,
    onSecondaryContainer = accentText,
    error = accent,
    onError = onAccent,
    background = bg,
    onBackground = fg,
    surface = bg,
    onSurface = fg,
    surfaceVariant = card,
    onSurfaceVariant = muted,
    surfaceContainer = card,
    outline = border,
    outlineVariant = line,
)
