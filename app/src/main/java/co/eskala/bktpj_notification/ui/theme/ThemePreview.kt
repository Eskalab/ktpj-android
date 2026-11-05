package co.eskala.bktpj_notification.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Hoja de tokens para revisar el tema en Android Studio (Split / Design).

@Composable
private fun TokenSheet() {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Column(
        Modifier
            .background(c.bg)
            .padding(KtpjDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
    ) {
        Text("Spur", style = t.brand, color = c.fg)
        Text("Capturado hoy", style = t.secondary, color = c.muted)
        Text("3", style = t.hero, color = c.fg)
        Text("Conecta tu base Neon", style = t.screenTitle, color = c.fg)
        Text("PASO 1 DE 3", style = t.overline, color = c.muted)
        Text("La app solo lee las notificaciones de Nu Colombia.", style = t.body, color = c.muted)
        Text("Rappi", style = t.rowTitle, color = c.fg)
        Text("−45.900", style = t.amount, color = c.fg)
        Text("+1.2M", style = t.amount, color = c.green)
        Text("com.nu.production", style = t.mono, color = c.fg)
        Spacer(Modifier.height(KtpjDimens.cardGapSmall))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                c.fg, c.muted, c.card, c.line, c.border, c.accent, c.accentSoft,
                c.accentText, c.green, c.invBg, c.dot, c.shade,
            ).forEach { Swatch(it) }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(20.dp)
            .background(color, KtpjShapes.progress)
            .border(1.dp, KtpjTheme.colors.line, KtpjShapes.progress),
    )
}

@Preview(name = "Tema — Claro", widthDp = 360)
@Composable
private fun ThemeLightPreview() = KtpjTheme(ThemeMode.LIGHT) { TokenSheet() }

@Preview(name = "Tema — Oscuro", widthDp = 360)
@Composable
private fun ThemeDarkPreview() = KtpjTheme(ThemeMode.DARK) { TokenSheet() }
