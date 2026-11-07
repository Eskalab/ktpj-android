package co.eskala.bktpj_notification.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.eskala.bktpj_notification.DailySummary
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.ui.components.DoneTaskRow
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.components.TaskRow
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resumen del día, a pantalla completa. Jerarquía: fecha → saludo → frase → números → lista → una acción.
 * Mañana ("Tu día"): lo de hoy con ★ primero y botón "Empezar el día".
 * Noche ("Cierre del día"): progreso, lo terminado tachado, lo pendiente y "Pasar a mañana".
 */
@Composable
fun DaySummaryScreen(
    kind: DailySummary.Kind,
    pending: List<SpurTask>,
    done: List<SpurTask>,
    overdue: Int,
    completing: Set<String>,
    postponing: Boolean,
    onDone: (SpurTask) -> Unit,
    onPostpone: () -> Unit,
    onOpenTasks: () -> Unit,
    onClose: () -> Unit,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val morning = kind == DailySummary.Kind.MORNING
    val phrase = DailySummary.phrase(kind, pending.size, done.size)
    val date = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "CO")).format(Date()).uppercase()

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            // Fecha + cerrar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date, style = t.overlineLarge, color = c.muted, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(KtpjDimens.minTouchTarget)
                        .clip(KtpjShapes.pill)
                        .clickable(role = Role.Button, onClickLabel = "Cerrar resumen", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", style = t.rowTitle, color = c.muted) }
            }

            // Saludo
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (morning) "☀️" else "🌙", fontSize = 44.sp)
                Text(
                    if (morning) "Buenos días" else "Cierre del día",
                    style = t.onboardingTitle,
                    color = c.fg,
                    modifier = Modifier.semantics { heading() },
                )
            }

            // Frase: el corazón emocional de la pantalla, con una franja de acento.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(c.accent, KtpjShapes.pill))
                Text(
                    phrase,
                    style = t.zoneTitle.copy(fontStyle = FontStyle.Italic, lineHeight = 28.sp),
                    color = c.fg,
                )
            }

            if (morning) MorningStats(pending, overdue, onOpenTasks) else EveningProgress(done.size, pending.size)

            // Listas
            if (morning) {
                if (pending.isNotEmpty()) {
                    Block("PARA HOY") {
                        pending.forEach { TaskRow(it, busy = it.id in completing, onDone = { onDone(it) }) }
                    }
                } else {
                    Text("Sin tareas con fecha para hoy. Si quieres, agenda algo desde Tareas.", style = t.body, color = c.muted)
                }
            } else {
                if (done.isNotEmpty()) Block("TERMINASTE") { done.forEach { DoneTaskRow(it) } }
                if (pending.isNotEmpty()) {
                    Block("QUEDÓ PENDIENTE") {
                        pending.forEach { TaskRow(it, busy = it.id in completing || postponing, onDone = { onDone(it) }) }
                    }
                }
            }
        }

        // Una sola acción principal, fija abajo.
        Column(
            Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 12.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
        ) {
            when {
                morning -> {
                    PrimaryButton("Empezar el día", onClick = onOpenTasks)
                    SecondaryButton("Cerrar", onClick = onClose)
                }
                pending.isNotEmpty() -> {
                    PrimaryButton(
                        if (postponing) "Moviendo…" else "Pasar ${if (pending.size == 1) "la pendiente" else "las ${pending.size}"} a mañana",
                        onClick = onPostpone,
                        enabled = !postponing,
                    )
                    SecondaryButton("Dejarlas así", onClick = onClose, enabled = !postponing)
                }
                else -> PrimaryButton("Buenas noches", onClick = onClose)
            }
        }
    }
}

@Composable
private fun MorningStats(pending: List<SpurTask>, overdue: Int, onOpenTasks: () -> Unit) {
    val c = KtpjTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap)) {
        Stat("HOY", "${pending.size}", c.card, c.fg, Modifier.weight(1f))
        Stat("★ PRIORITARIAS", "${pending.count { it.priority }}", c.accentSoft, c.accentText, Modifier.weight(1f))
        if (overdue > 0) Stat("ATRASADAS", "$overdue", c.card, c.accent, Modifier.weight(1f), onOpenTasks)
    }
}

@Composable
private fun Stat(label: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier
            .clip(KtpjShapes.card)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = KtpjTheme.type.overline, color = KtpjTheme.colors.muted, maxLines = 1)
        Text(value, style = KtpjTheme.type.tileCount, color = fg)
    }
}

/** "3 de 5" grande y una barra verde: el progreso se entiende sin leer. */
@Composable
private fun EveningProgress(done: Int, pending: Int) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val total = done + pending
    if (total == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$done", style = t.hero, color = if (pending == 0) c.green else c.fg)
            Text("de $total terminadas", style = t.body, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(KtpjShapes.pill).background(c.line)) {
            Box(Modifier.fillMaxWidth(done / total.toFloat()).fillMaxHeight().clip(KtpjShapes.pill).background(c.green))
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        Text(title, style = KtpjTheme.type.overline, color = KtpjTheme.colors.muted)
        content()
    }
}

/** Aviso de una línea en Inicio, solo mientras el resumen está vigente y no se ha abierto. */
@Composable
fun SummaryBanner(kind: DailySummary.Kind, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .clip(KtpjShapes.card)
            .background(c.invBg)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = KtpjDimens.cardPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (kind == DailySummary.Kind.MORNING) "☀️" else "🌙", fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(
                if (kind == DailySummary.Kind.MORNING) "Tu día está listo" else "Tu cierre del día está listo",
                style = t.rowTitle,
                color = c.invFg,
            )
            Text("Toca para verlo", style = t.caption, color = c.invFg.copy(alpha = 0.7f))
        }
        Text("›", style = t.amount, color = c.invFg)
    }
}

@Preview(name = "Tu día — Oscuro", widthDp = 360, heightDp = 800)
@Composable
private fun MorningPreview() = KtpjTheme(ThemeMode.DARK) {
    DaySummaryScreen(
        DailySummary.Kind.MORNING,
        listOf(
            SpurTask("1", "Pagar a Bety gastos notariales", null, "2026-11-06", priority = true),
            SpurTask("2", "Responder HV a nueva empresa", null, "2026-11-06"),
        ),
        emptyList(), 2, emptySet(), false, {}, {}, {}, {},
    )
}
