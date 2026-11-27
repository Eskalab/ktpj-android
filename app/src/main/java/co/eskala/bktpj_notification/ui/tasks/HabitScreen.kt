package co.eskala.bktpj_notification.ui.tasks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Habit
import co.eskala.bktpj_notification.HabitDates
import co.eskala.bktpj_notification.Habits
import co.eskala.bktpj_notification.ui.components.AmountDialog
import co.eskala.bktpj_notification.ui.components.AppLink
import co.eskala.bktpj_notification.ui.components.HabitRow
import co.eskala.bktpj_notification.ui.components.OutlinePill
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.ProjectTag
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val WEEKS = 15

/**
 * Hábito: racha actual y mejor, este mes, y una cuadrícula de las últimas semanas (un cuadro por día:
 * verde = hecho, borde = tocaba y no se hizo, tenue = no tocaba). Tocar un día pasado lo marca o desmarca,
 * por si se olvidó registrarlo. "⏸ Pausar" (viaje, enfermo…) lo saca de Inicio y de los avisos sin romper la
 * racha; los días en pausa salen rayados en la cuadrícula.
 */
@Composable
fun HabitScreen(habit: Habit, onBack: () -> Unit, onEdit: () -> Unit, onStart: () -> Unit = {}) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmArchive by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<String?>(null) }
    var askingPause by remember { mutableStateOf(false) }
    var pauseBusy by remember { mutableStateOf(false) }
    val pause = habit.currentPause

    fun changePause(action: suspend () -> Result<Unit>) {
        pauseBusy = true
        scope.launch {
            val r = action()
            pauseBusy = false
            if (r.isFailure) Toast.makeText(ctx, "No se pudo: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }
    val streak = habit.streak()
    val best = maxOf(habit.bestStreak(), streak)
    val (monthDone, monthDue) = habit.monthRate()

    fun toggleDay(day: String) {
        if (habit.doneOn(day)) scope.launch { Habits.uncheck(ctx, habit.id, day) }
        else if (habit.goal != null && !habit.unit.isNullOrBlank()) editingDay = day
        else scope.launch { Habits.check(ctx, habit.id, habit.goal, day) }
    }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹ Tareas",
                    style = t.captionStrong,
                    color = c.muted,
                    modifier = Modifier.clip(KtpjShapes.pill).clickable(role = Role.Button, onClick = onBack).padding(vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (pause == null) {
                    OutlinePill(if (pauseBusy) "…" else "⏸ Pausar", onClick = { askingPause = true }, enabled = !pauseBusy)
                    Spacer(Modifier.width(8.dp))
                }
                OutlinePill("Editar", onClick = onEdit)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("↻ HÁBITO" + (habit.key?.let { " · $it" } ?: ""), style = t.overlineLarge, color = c.muted)
                Text(habit.title, style = t.screenTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                Text(
                    listOfNotNull(
                        HabitDates.label(habit.days),
                        habit.goalLabel?.let { "meta $it" },
                        habit.remindAt?.let { "aviso ${hourLabel(it)}" },
                    ).joinToString(" · "),
                    style = t.secondary,
                    color = c.muted,
                )
                habit.project?.let { ProjectTag(it) }
                habit.app?.let { AppLink(it) }
            }

            // En pausa: hasta cuándo, motivo y reanudar (desde hoy vuelve a tocar).
            if (pause != null) {
                Column(
                    Modifier.fillMaxWidth().clip(KtpjShapes.card).background(c.card).padding(KtpjDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(pause.label(), style = t.rowTitle, color = c.fg)
                    Text(
                        (pause.end?.let { "Vuelve el ${HabitDates.short(HabitDates.plusDays(it, 1))}. " } ?: "Sin fecha fija. ") +
                            "No sale en Inicio, no te avisa y no rompe la racha; si lo haces igual, cuenta.",
                        style = t.secondary,
                        color = c.muted,
                    )
                    OutlinePill(
                        if (pauseBusy) "…" else "▶ Reanudar",
                        onClick = { changePause { Habits.resume(ctx, habit.id) } },
                        enabled = !pauseBusy,
                    )
                }
            }

            // Hoy: marcar y, aparte, un botón grande para el pomodoro (siempre, aunque ya esté hecho:
            // leer de más también vale).
            HabitRow(habit, onOpen = {})
            val pomo by co.eskala.bktpj_notification.Pomodoro.state.collectAsState()
            val running = pomo?.taskId == habit.id
            if (pause == null || running) PrimaryButton(
                if (running) "● Volver al pomodoro" else "▶ Empezar pomodoro",
                onClick = onStart,
            )

            co.eskala.bktpj_notification.ui.components.PomoSessionsList(habit.id, todayOnly = false, max = 10)

            // Cifras
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("🔥 $streak", "racha actual", Modifier.weight(1f))
                Stat("$best", "mejor racha", Modifier.weight(1f))
                Stat(if (monthDue == 0) "—" else "${monthDone * 100 / monthDue}%", "este mes · $monthDone/$monthDue", Modifier.weight(1f))
            }

            // Cuadrícula
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ÚLTIMAS $WEEKS SEMANAS", style = t.overline, color = c.muted)
                HabitGrid(habit, onTapDay = ::toggleDay)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(c.green, null, "hecho")
                    Legend(Color.Transparent, c.muted, "tocaba")
                    Legend(c.line, null, "no tocaba")
                    if (habit.pauses.isNotEmpty()) Legend(Color.Transparent, null, "pausa", striped = true)
                }
                Text("Toca un día pasado para marcarlo si se te olvidó registrarlo.", style = t.caption, color = c.muted)
            }

            // Últimos registros con cantidad
            if (habit.unit != null) {
                val recent = habit.checks.entries.sortedByDescending { it.key }.take(7)
                if (recent.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ÚLTIMOS DÍAS", style = t.overline, color = c.muted)
                        val total = habit.checks.values.sumOf { it ?: 0 }
                        recent.forEach { (day, amount) ->
                            Row {
                                Text(dayLabel(day), style = t.secondary, color = c.fg, modifier = Modifier.weight(1f))
                                Text(amount?.let { "$it ${habit.unit}" } ?: "✓", style = t.captionStrong, color = c.fg)
                            }
                        }
                        Text("En total: $total ${habit.unit}", style = t.caption, color = c.muted)
                    }
                }
            }

            if (!confirmArchive) {
                SecondaryButton("Dejar este hábito", onClick = { confirmArchive = true })
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(KtpjShapes.card).background(c.card).padding(KtpjDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("¿Dejar \"${habit.title}\"?", style = t.rowTitle, color = c.fg)
                    Text("Sale de tus listas y no te avisa más. Lo registrado queda guardado.", style = t.secondary, color = c.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinePill("Sí, dejarlo", onClick = {
                            scope.launch {
                                val r = Habits.archive(ctx, habit.id)
                                if (r.isSuccess) onBack()
                                else Toast.makeText(ctx, "No se pudo: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        })
                        OutlinePill("Cancelar", onClick = { confirmArchive = false })
                    }
                }
            }
        }
    }

    if (askingPause) {
        HabitPauseDialog(
            habit = habit,
            onPause = { end, reason -> askingPause = false; changePause { Habits.pause(ctx, habit.id, end, reason) } },
            onDismiss = { askingPause = false },
        )
    }

    editingDay?.let { day ->
        AmountDialog(
            habit = habit,
            onSave = { amount -> editingDay = null; scope.launch { Habits.check(ctx, habit.id, amount, day) } },
            onDismiss = { editingDay = null },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = KtpjTheme.colors
    Column(
        modifier.clip(KtpjShapes.card).background(c.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = KtpjTheme.type.tileCount, color = c.fg)
        Text(label, style = KtpjTheme.type.caption, color = c.muted)
    }
}

@Composable
private fun Legend(fill: Color, border: Color?, label: String, striped: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(fill)
                .then(if (striped) Modifier.pauseStripes(KtpjTheme.colors.muted) else Modifier)
                .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(2.dp)) else Modifier)
        )
        Text(label, style = KtpjTheme.type.caption, color = KtpjTheme.colors.muted)
    }
}

/** Rayas diagonales tenues: día en pausa. */
private fun Modifier.pauseStripes(color: Color): Modifier = drawBehind {
    drawRect(color.copy(alpha = 0.12f))
    val step = size.minDimension / 3f
    var x = -size.height
    while (x < size.width) {
        drawLine(color.copy(alpha = 0.45f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
        x += step
    }
}

/** Columnas = semanas (la última es la actual), filas = lunes … domingo. Días en pausa: rayados. */
@Composable
private fun HabitGrid(habit: Habit, onTapDay: (String) -> Unit) {
    val c = KtpjTheme.colors
    val today = Calendar.getInstance()
    val todayIso = HabitDates.iso(today)
    // Lunes de la semana actual, retrocediendo WEEKS-1 semanas
    val start = (today.clone() as Calendar).apply {
        val dow = (get(Calendar.DAY_OF_WEEK) + 5) % 7   // lunes = 0
        add(Calendar.DAY_OF_YEAR, -dow - 7 * (WEEKS - 1))
    }
    val labels = listOf("L", "", "X", "", "V", "", "D")
    val gridDesc = "Cuadrícula de días cumplidos" +
        if (habit.pauses.isNotEmpty()) "; los días en pausa salen rayados" else ""
    BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = gridDesc }) {
        val gap = 3.dp
        val labelW = 14.dp
        val cell = ((maxWidth - labelW - gap * WEEKS) / WEEKS).coerceAtMost(22.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.width(labelW)) {
                labels.forEach { Box(Modifier.size(labelW, cell), contentAlignment = Alignment.CenterStart) { Text(it, style = KtpjTheme.type.overline, color = c.muted) } }
            }
            repeat(WEEKS) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(7) { d ->
                        val cal = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, w * 7 + d) }
                        val iso = HabitDates.iso(cal)
                        val future = iso > todayIso
                        val done = habit.doneOn(iso)
                        val paused = !future && habit.isPausedOn(iso)
                        val scheduled = habit.scheduledOn(cal)
                        val shape = RoundedCornerShape(3.dp)
                        val state = when {
                            future -> ""
                            done -> "hecho"
                            paused -> "en pausa"
                            scheduled -> "no hecho"
                            else -> "no tocaba"
                        }
                        Box(
                            Modifier
                                .size(cell)
                                .clip(shape)
                                .background(
                                    when {
                                        future -> Color.Transparent
                                        done -> c.green
                                        paused -> Color.Transparent
                                        scheduled -> Color.Transparent
                                        else -> c.line
                                    }
                                )
                                .then(if (paused && !done) Modifier.pauseStripes(c.muted) else Modifier)
                                .then(
                                    when {
                                        iso == todayIso -> Modifier.border(2.dp, c.fg, shape)
                                        !future && !done && !paused && scheduled -> Modifier.border(1.dp, c.muted, shape)
                                        else -> Modifier
                                    }
                                )
                                .then(
                                    if (!future) Modifier
                                        .clickable(role = Role.Checkbox, onClickLabel = dayLabel(iso)) { onTapDay(iso) }
                                        .semantics { stateDescription = state }
                                    else Modifier
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(iso: String): String {
    if (iso == HabitDates.today()) return "Hoy"
    if (iso == HabitDates.daysAgo(1)) return "Ayer"
    return SimpleDateFormat("EEEE d MMM", Locale("es", "CO")).format(HabitDates.parse(iso).time).replaceFirstChar { it.uppercase() }
}

fun hourLabel(h: Int): String = when {
    h == 0 -> "12:00 a.m."
    h < 12 -> "$h:00 a.m."
    h == 12 -> "12:00 p.m."
    else -> "${h - 12}:00 p.m."
}
