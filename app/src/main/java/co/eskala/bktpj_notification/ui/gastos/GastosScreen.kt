package co.eskala.bktpj_notification.ui.gastos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Capture
import co.eskala.bktpj_notification.NeonRepository.ColumnCount
import co.eskala.bktpj_notification.ui.components.OutlinePill
import co.eskala.bktpj_notification.ui.history.HistoryScreen
import co.eskala.bktpj_notification.ui.home.sampleCaptures
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/** Conteos de las columnas destino; null mientras carga o si no se pudo leer Neon. */
data class DestinationCounts(val inbox: ColumnCount?, val hoy: ColumnCount?, val hoyConfigured: Boolean)

/** Gastos: tiles de Inbox / Hoy, "+ Gasto manual" y el historial completo de capturas. */
@Composable
fun GastosScreen(
    captures: List<Capture>,
    counts: DestinationCounts,
    retrying: Set<String>,
    onRetry: (String) -> Unit,
    onAddManual: () -> Unit,
    modifier: Modifier = Modifier,
    fixed: Set<String> = emptySet(),
    onToggleFixed: ((co.eskala.bktpj_notification.Capture) -> Unit)? = null,
    onOpenBalance: () -> Unit = {},
) {
    val c = KtpjTheme.colors
    HistoryScreen(
        captures = captures,
        retrying = retrying,
        onRetry = onRetry,
        modifier = modifier,
        title = "Gastos",
        fixed = fixed,
        onToggleFixed = onToggleFixed,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap)) {
                    Tile(
                        overline = "INBOX",
                        count = counts.inbox?.count,
                        caption = counts.inbox?.title,
                        bg = c.accentSoft,
                        fg = c.accentText,
                        modifier = Modifier.weight(1f),
                    )
                    Tile(
                        overline = "HOY",
                        count = if (counts.hoyConfigured) counts.hoy?.count else null,
                        caption = if (counts.hoyConfigured) counts.hoy?.title else "Sin columna",
                        bg = c.card,
                        fg = c.muted,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap)) {
                    OutlinePill("+ Gasto manual", onClick = onAddManual, modifier = Modifier.weight(1f))
                    OutlinePill("Balance del mes", onClick = onOpenBalance, modifier = Modifier.weight(1f))
                }
            }
        },
    )
}

/** Tile 26sp/700 con overline; el conteo es "—" mientras carga o si no hay columna. */
@Composable
private fun Tile(overline: String, count: Int?, caption: String?, bg: Color, fg: Color, modifier: Modifier) {
    val t = KtpjTheme.type
    Column(
        modifier
            .background(bg, KtpjShapes.card)
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(overline, style = t.overline, color = fg)
        Text(count?.toString() ?: "—", style = t.tileCount, color = KtpjTheme.colors.fg)
        if (caption != null) Text(caption, style = t.caption, color = KtpjTheme.colors.muted, maxLines = 1)
    }
}

@Preview(name = "Gastos — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun GastosPreview() = KtpjTheme(ThemeMode.LIGHT) {
    GastosScreen(sampleCaptures(), DestinationCounts(ColumnCount("Inbox", 14), ColumnCount("Hoy", 6), true), emptySet(), {}, {})
}
