package co.eskala.bktpj_notification.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Capture
import co.eskala.bktpj_notification.CaptureSource
import co.eskala.bktpj_notification.CaptureStatus
import co.eskala.bktpj_notification.Money
import co.eskala.bktpj_notification.ui.components.Chip
import co.eskala.bktpj_notification.ui.components.RetryPill
import co.eskala.bktpj_notification.ui.components.amountColor
import co.eskala.bktpj_notification.ui.components.hour
import co.eskala.bktpj_notification.ui.home.sampleCaptures
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class Filter { ALL, OK, ERROR }

/**
 * 05 Historial: chips Todo / Guardado / Error · N, agrupado por día con la suma del día.
 * [header] va entre el título y los chips (Gastos pone ahí los destinos y "+ Gasto manual").
 */
@Composable
fun HistoryScreen(
    captures: List<Capture>,
    retrying: Set<String>,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Historial",
    header: (@Composable () -> Unit)? = null,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val errorCount = captures.count { it.status == CaptureStatus.ERROR }
    val shown = when (filter) {
        Filter.ALL -> captures
        Filter.OK -> captures.filter { it.status == CaptureStatus.OK }
        Filter.ERROR -> captures.filter { it.status == CaptureStatus.ERROR }
    }
    val byDay = shown.groupBy { dayKey(it.at) }

    LazyColumn(
        modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
    ) {
        item {
            Text(title, style = t.screenTitle, color = c.fg)
            header?.let {
                Column(Modifier.padding(top = KtpjDimens.blockGapSmall)) { it() }
            }
            Row(
                Modifier.padding(top = KtpjDimens.blockGapSmall, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip("Todo", filter == Filter.ALL, { filter = Filter.ALL })
                Chip("Guardado", filter == Filter.OK, { filter = Filter.OK })
                Chip("Error · $errorCount", filter == Filter.ERROR, { filter = Filter.ERROR })
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (filter == Filter.ERROR) "No hay capturas con error." else "Todavía no hay capturas.",
                    style = t.body,
                    color = c.muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "day:$day") {
                val total = list.sumOf { Money.signedValue(it.tx.amount, it.tx.type) }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
                    Text(dayLabel(list.first().at), style = t.captionStrong, color = c.muted, modifier = Modifier.weight(1f))
                    Text(Money.signedTotal(total), style = t.captionStrong, color = c.muted)
                }
            }
            items(list, key = { it.id }) { cap ->
                HistoryRow(cap, retrying = cap.id in retrying, onRetry = { onRetry(cap.id) })
            }
        }
    }
}

/** Fila: comercio | monto; "HH:mm · Inbox + Hoy" | "✓ Guardado" o pill "Reintentar". Divisor inferior `line`. */
@Composable
private fun HistoryRow(cap: Capture, retrying: Boolean, onRetry: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val line = c.line
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), KtpjDimens.divider.toPx())
            }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cap.label + if (cap.source == CaptureSource.MANUAL) " · manual" else "",
                style = t.rowTitle,
                color = c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            cap.signedAmount?.let { Text(it, style = t.amountList, color = amountColor(cap)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (cap.status == CaptureStatus.ERROR) "${cap.hour()} · ${cap.error ?: "Error"}" else "${cap.hour()} · ${cap.destinations}",
                style = t.caption,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (cap.status == CaptureStatus.OK) {
                Text("✓ Guardado", style = t.captionStrong, color = c.green)
            } else {
                RetryPill(onRetry, retrying, onAccentBackground = false)
            }
        }
    }
}

private fun dayKey(at: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(at)

/** "HOY" / "AYER" / "LUN 27 OCT". */
private fun dayLabel(at: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(cal, today) -> "HOY"
        same(cal, yesterday) -> "AYER"
        else -> SimpleDateFormat("EEE d MMM", Locale("es", "CO")).format(at).replace(".", "").uppercase()
    }
}

@Preview(name = "05 Historial — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun HistoryPreview() = KtpjTheme(ThemeMode.LIGHT) {
    HistoryScreen(sampleCaptures(), emptySet(), {})
}
