package co.eskala.bktpj_notification.ui.balance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Balance
import co.eskala.bktpj_notification.Capture
import co.eskala.bktpj_notification.Money
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Balance del mes. Lectura de arriba abajo:
 *  1. Cifra héroe: lo gastado en variables (lo que de verdad "gastaste").
 *  2. Barra segmentada alta: cómo se reparte el gasto entre variables y fijos (2 partes → no dona).
 *  3. Las tres cifras, separadas y nunca sumadas: variables, fijos, ingresos.
 *  4. Lo que más pesa en cada grupo (barras horizontales ordenadas, un tono por grupo).
 *  5. El neto, pequeño y al final.
 */
@Composable
fun BalanceScreen(captures: List<Capture>, fixed: Set<String>, onBack: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var offset by rememberSaveable { mutableStateOf(0) }
    val b = Balance.of(Balance.month(captures, offset), fixed)
    val monthName = SimpleDateFormat("MMMM yyyy", Locale("es", "CO"))
        .format(Calendar.getInstance().apply { add(Calendar.MONTH, offset) }.time)
        .replaceFirstChar { it.uppercase() }
    val spent = b.variable + b.fixed

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            Text(
                "‹ Volver",
                style = t.captionStrong,
                color = c.muted,
                modifier = Modifier.clip(KtpjShapes.pill).clickable(role = Role.Button, onClick = onBack).padding(vertical = 8.dp),
            )

            // Título + selector de mes
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Balance", style = t.screenTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
                MonthArrow("‹", "Mes anterior", enabled = true) { offset-- }
                Text(monthName, style = t.captionStrong, color = c.fg, modifier = Modifier.padding(horizontal = 4.dp))
                MonthArrow("›", "Mes siguiente", enabled = offset < 0) { offset++ }
            }

            if (b.count == 0) {
                Text("No hay movimientos capturados en $monthName.", style = t.body, color = c.muted)
                return@Column
            }

            // 1. Héroe
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GASTASTE EN VARIABLES", style = t.overline, color = c.muted)
                Text(Money.format(b.variable), style = t.hero, color = c.fg)
                Text(
                    if (b.fixed > 0) "sin contar ${Money.format(b.fixed)} de gastos fijos" else "aún no hay gastos marcados como fijos",
                    style = t.secondary,
                    color = c.muted,
                )
            }

            // 2. Reparto del gasto
            if (spent > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SegmentedBar(
                        listOf(
                            Segment("Variables", b.variable, c.chartVariable),
                            Segment("Fijos", b.fixed, c.chartFixed),
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem("Variables", c.chartVariable)
                        LegendItem("Fijos", c.chartFixed)
                    }
                }
            }

            // 3. Las tres cifras
            Column(
                Modifier.fillMaxWidth().clip(KtpjShapes.card).background(c.card).padding(KtpjDimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FigureRow("Gastos variables", "−" + Money.format(b.variable), c.chartVariable)
                FigureRow("Gastos fijos", "−" + Money.format(b.fixed), c.chartFixed)
                Box(Modifier.fillMaxWidth().height(KtpjDimens.divider).background(c.line))
                FigureRow("Ingresos", "+" + Money.format(b.income), null)
                if (b.fixedIncome > 0) {
                    Text("Incluye ${Money.format(b.fixedIncome)} de ingresos fijos (arriendos…)", style = t.caption, color = c.muted)
                }
            }

            // 4. Lo que más pesa
            if (b.topVariable.isNotEmpty()) RankedBars("LO QUE MÁS PESA EN VARIABLES", b.topVariable, c.chartVariable)
            if (b.topFixed.isNotEmpty()) RankedBars("TUS GASTOS FIJOS", b.topFixed, c.chartFixed)

            // 5. Neto, discreto
            Text(
                when {
                    b.left > 0 -> "Te sobraron ${Money.format(b.left)} en $monthName."
                    b.left < 0 -> "En $monthName salió ${Money.format(b.left)} más de lo que entró."
                    else -> "En $monthName entró lo mismo que salió."
                },
                style = t.secondary,
                color = c.muted,
            )
            Text(
                "Para separar un gasto fijo, marca \"Fijo\" en el movimiento (pestaña Gastos): se aplica a todos los de ese comercio. " +
                    "Cuenta solo lo capturado en este celular.",
                style = t.caption,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun MonthArrow(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KtpjTheme.colors
    Box(
        Modifier
            .size(KtpjDimens.minTouchTarget)
            .clip(KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = KtpjTheme.type.amount, color = if (enabled) c.fg else c.line)
    }
}

private data class Segment(val name: String, val value: Double, val color: Color)

/** Barra segmentada alta (28dp): cada parte su bloque, 4dp de esquina, 2dp de separación, % adentro si cabe. */
@Composable
private fun SegmentedBar(parts: List<Segment>) {
    val shown = parts.filter { it.value > 0 }
    val total = shown.sumOf { it.value }
    val c = KtpjTheme.colors
    val desc = shown.joinToString(", ") { "${it.name} ${Money.format(it.value)}, ${pct(it.value, total)}" }
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = "Reparto del gasto: $desc" }) {
        val gap = 2.dp
        val usable = maxWidth - gap * (shown.size - 1).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            shown.forEach { s ->
                val w = usable * (s.value / total).toFloat()
                Box(
                    Modifier.width(w).height(28.dp).clip(RoundedCornerShape(4.dp)).background(s.color),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (w >= 44.dp) {
                        Text(pct(s.value, total), style = KtpjTheme.type.captionStrong, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

private fun pct(v: Double, total: Double) = "${(v / total * 100).roundToInt()}%"

@Composable
private fun LegendItem(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(name, style = KtpjTheme.type.caption, color = KtpjTheme.colors.muted)
    }
}

/** Fila de cifra: muestra de color (identidad) + nombre + monto, en tinta de texto. */
@Composable
private fun FigureRow(name: String, value: String, swatch: Color?) {
    val c = KtpjTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (swatch != null) Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(swatch))
        else Spacer(Modifier.width(10.dp))
        Text(name, style = KtpjTheme.type.body, color = c.fg, modifier = Modifier.weight(1f))
        Text(value, style = KtpjTheme.type.amountList, color = c.fg)
    }
}

/** Barras horizontales ordenadas (máx. 5), un solo tono, extremo redondeado; valor en texto al lado. */
@Composable
private fun RankedBars(title: String, rows: List<Pair<String, Double>>, color: Color) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val max = rows.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = t.overline, color = c.muted)
        rows.forEach { (name, value) ->
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = t.secondary, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Money.format(value), style = t.captionStrong, color = c.fg)
                }
                Box(Modifier.fillMaxWidth().height(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth((value / max).toFloat().coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .clip(KtpjShapes.pill)
                            .background(color)
                    )
                }
            }
        }
    }
}
