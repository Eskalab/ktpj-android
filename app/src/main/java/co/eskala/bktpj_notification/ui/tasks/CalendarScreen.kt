package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.HabitDates
import co.eskala.bktpj_notification.Habits
import co.eskala.bktpj_notification.NeonRepository
import co.eskala.bktpj_notification.PomoEnd
import co.eskala.bktpj_notification.PomoLog
import co.eskala.bktpj_notification.PomoSession
import co.eskala.bktpj_notification.ui.components.clockLabel
import co.eskala.bktpj_notification.ui.components.durationLabel
import co.eskala.bktpj_notification.ui.components.rangeLabel
import co.eskala.bktpj_notification.ui.components.startOfDay
import co.eskala.bktpj_notification.ui.components.statusMark
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ES = Locale("es", "CO")
private val HOUR_H: Dp = 44.dp

/**
 * KTP-5: calendario de lo hecho. Arriba el mes (punto = hubo pomodoros o tareas terminadas ese día);
 * abajo el día elegido en una línea de tiempo con cada enfoque del pomodoro (inicio → fin) y cada tarea
 * terminada a su hora, más los hábitos cumplidos. Los pomodoros vienen del celular; las tareas, de la base.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val ctx = LocalContext.current
    var monthOffset by rememberSaveable { mutableStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(startOfDay()) }
    val sessions by PomoLog.sessions.collectAsState()
    val habits by Habits.habits.collectAsState()

    val monthStart = remember(monthOffset) {
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(); set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, monthOffset)
        }
    }
    val monthEnd = remember(monthStart) { (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) } }

    // Tareas terminadas del mes (una consulta por mes)
    var done by remember { mutableStateOf<List<NeonRepository.DoneAt>?>(null) }
    var doneError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(monthStart.timeInMillis) {
        done = null
        NeonRepository.fetchDoneBetween(ctx, monthStart.timeInMillis, monthEnd.timeInMillis)
            .onSuccess { done = it; doneError = null }
            .onFailure { done = emptyList(); doneError = it.message }
    }

    val dayEnd = selected + 86_400_000L
    val daySessions = sessions.filter { it.start in selected until dayEnd }.sortedBy { it.start }
    val dayDone = done.orEmpty().filter { it.at in selected until dayEnd }
    val dayIso = HabitDates.iso(Calendar.getInstance().apply { timeInMillis = selected })
    val dayHabits = habits.filter { dayIso in it.checks }
    val activeDays: Set<Long> = remember(sessions, done) {
        (sessions.map { startOfDay(it.start) } + done.orEmpty().map { startOfDay(it.at) }).toSet()
    }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            Text(
                "‹ Tareas",
                style = t.captionStrong,
                color = c.muted,
                modifier = Modifier.clip(KtpjShapes.pill).clickable(role = Role.Button, onClick = onBack).padding(vertical = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Calendario", style = t.screenTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
                Arrow("‹", "Mes anterior", true) { monthOffset-- }
                Text(
                    SimpleDateFormat("MMM yyyy", ES).format(monthStart.time).replaceFirstChar { it.uppercase() },
                    style = t.captionStrong,
                    color = c.fg,
                )
                Arrow("›", "Mes siguiente", monthOffset < 0) { monthOffset++ }
            }

            MonthGrid(monthStart, selected, activeDays) { selected = it }

            // Resumen del día
            val focusMin = daySessions.sumOf { it.minutes }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    SimpleDateFormat("EEEE d 'de' MMMM", ES).format(Date(selected)).replaceFirstChar { it.uppercase() },
                    style = t.zoneTitle,
                    color = c.fg,
                )
                Text(
                    listOf(
                        "🍅 ${daySessions.count { it.status == PomoEnd.COMPLETED }} pomodoros · ${durationLabel(focusMin)} de enfoque",
                        "✓ ${dayDone.size} tarea${if (dayDone.size == 1) "" else "s"}",
                    ).plus(if (dayHabits.isNotEmpty()) listOf("↻ ${dayHabits.size} hábito${if (dayHabits.size == 1) "" else "s"}") else emptyList())
                        .joinToString(" · "),
                    style = t.secondary,
                    color = c.muted,
                )
                if (done == null) Text("Cargando tareas…", style = t.caption, color = c.muted)
                doneError?.let { Text("No se pudieron leer las tareas: $it", style = t.caption, color = c.accent) }
            }

            if (daySessions.isEmpty() && dayDone.isEmpty() && dayHabits.isEmpty()) {
                Text("Nada registrado este día.", style = t.body, color = c.muted)
            } else {
                Timeline(daySessions, dayDone, selected)
                DayList(daySessions, dayDone, dayHabits.map { h ->
                    val amount = h.checks[dayIso]
                    h.title + (if (amount != null && h.unit != null) " · $amount ${h.unit}" else "")
                })
            }
            Text(
                "Los pomodoros se anotan en este celular desde que se instaló el registro; las tareas terminadas vienen de la base.",
                style = t.caption,
                color = c.muted,
            )
        }
    }
}

@Composable
private fun Arrow(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KtpjTheme.colors
    Box(
        Modifier.size(KtpjDimens.minTouchTarget).clip(KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = KtpjTheme.type.amount, color = if (enabled) c.fg else c.line) }
}

/** Mes en cuadrícula lunes → domingo. Punto bajo el número si hubo actividad. */
@Composable
private fun MonthGrid(monthStart: Calendar, selectedDay: Long, active: Set<Long>, onPick: (Long) -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val today = startOfDay()
    val lead = (monthStart.get(Calendar.DAY_OF_WEEK) + 5) % 7   // lunes = 0
    val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = lead + daysInMonth
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, style = t.overline, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        repeat((cells + 6) / 7) { w ->
            Row {
                repeat(7) { d ->
                    val idx = w * 7 + d - lead
                    if (idx < 0 || idx >= daysInMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = (monthStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, idx) }.timeInMillis
                        val isSel = day == selectedDay
                        val future = day > today
                        Column(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(KtpjShapes.row)
                                .background(if (isSel) c.invBg else Color.Transparent)
                                .then(if (day == today && !isSel) Modifier.border(1.5.dp, c.fg, KtpjShapes.row) else Modifier)
                                .semantics { selected = isSel }
                                .clickable(enabled = !future, role = Role.Button) { onPick(day) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "${idx + 1}",
                                style = t.captionStrong,
                                color = when { isSel -> c.invFg; future -> c.line; else -> c.fg },
                            )
                            Box(
                                Modifier.padding(top = 2.dp).size(5.dp).clip(KtpjShapes.pill)
                                    .background(if (day in active) (if (isSel) c.invFg else c.accent) else Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Línea de tiempo del día: bloques de enfoque a su hora y marcas de tareas terminadas. */
@Composable
private fun Timeline(sessions: List<PomoSession>, done: List<NeonRepository.DoneAt>, day: Long) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    fun hourOf(ms: Long) = ((ms - day) / 3_600_000f)
    val points = sessions.flatMap { listOf(hourOf(it.start), hourOf(it.end)) } + done.map { hourOf(it.at) }
    val from = minOf(7, (points.minOrNull() ?: 7f).toInt()).coerceAtLeast(0)
    val to = maxOf(20, ((points.maxOrNull() ?: 20f) + 1).toInt()).coerceAtMost(24)
    val hours = to - from

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("LÍNEA DEL DÍA", style = t.overline, color = c.muted)
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(HOUR_H * hours)
                .semantics { contentDescription = "Línea de tiempo del día con ${sessions.size} enfoques y ${done.size} tareas terminadas" },
        ) {
            val labelW = 52.dp
            val lane = maxWidth - labelW
            // horas
            repeat(hours + 1) { i ->
                val y = HOUR_H * i
                Row(Modifier.offset(y = y - 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(hourText(from + i), style = t.monoSmall, color = c.muted, modifier = Modifier.width(labelW))
                    Box(Modifier.width(lane).height(1.dp).background(c.line))
                }
            }
            // enfoques
            sessions.forEach { s ->
                val top = HOUR_H * (hourOf(s.start) - from)
                val h = (HOUR_H * ((s.end - s.start) / 3_600_000f)).coerceAtLeast(14.dp)
                val color = if (s.habit) c.green else c.accent
                Box(
                    Modifier
                        .offset(x = labelW, y = top)
                        .width(lane * 0.62f)
                        .height(h)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = if (s.status == PomoEnd.COMPLETED) 1f else 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        statusMark(s.status) + " " + (s.key?.let { "$it " } ?: "") + s.title,
                        style = t.caption,
                        color = Color.White,
                        maxLines = if (h > 30.dp) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // tareas terminadas
            done.forEach { d ->
                val y = HOUR_H * (hourOf(d.at) - from)
                Row(
                    Modifier.offset(x = labelW + lane * 0.64f, y = y - 8.dp).width(lane * 0.36f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(KtpjShapes.pill).background(c.green))
                    Text("✓ " + d.task.title, style = t.caption, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Lista legible del día, en orden de hora. */
@Composable
private fun DayList(sessions: List<PomoSession>, done: List<NeonRepository.DoneAt>, habits: List<String>) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    data class Item(val at: Long, val mark: String, val text: String, val right: String)
    val items = sessions.map { Item(it.start, statusMark(it.status), (it.key?.let { k -> "$k · " } ?: "") + it.title, rangeLabel(it) + " · ${it.minutes} min") } +
        done.map { Item(it.at, "✓", (it.task.key?.let { k -> "$k · " } ?: "") + it.task.title, "terminada " + clockLabel(it.at)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DETALLE", style = t.overline, color = c.muted)
        items.sortedBy { it.at }.forEach { i ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(i.mark, style = t.secondary, color = c.fg, modifier = Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(i.text, style = t.secondary, color = c.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(i.right, style = t.caption, color = c.muted)
                }
            }
        }
        habits.forEach { h ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("↻", style = t.secondary, color = c.green, modifier = Modifier.width(20.dp))
                Text(h, style = t.secondary, color = c.fg)
            }
        }
    }
}

private fun hourText(h: Int) = when {
    h == 0 || h == 24 -> "12 a.m."
    h < 12 -> "$h a.m."
    h == 12 -> "12 p.m."
    else -> "${h - 12} p.m."
}
