package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.TaskProject
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextDecoration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/**
 * Tarea enviada desde Claude Code (/spur). "Hecha" la marca como hecha en el tablero;
 * la ★ la vuelve prioritaria (recordatorio cada hora).
 */
@Composable
fun TaskRow(
    task: SpurTask,
    busy: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePriority: (() -> Unit)? = null,
    onEditDate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onToggleStarted: (() -> Unit)? = null,
    onOpenEpic: ((TaskProject) -> Unit)? = null,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    // ★ resalta (fondo cálido + franja de acento). En curso: franja azul (y fondo azul suave si no es ★).
    // Normales: gris neutro en claro; en oscuro, negras como la pantalla con un borde sutil.
    val bg = when {
        task.priority -> c.accentSoft
        task.started -> c.blueSoft
        c.isDark -> c.bg
        else -> c.card
    }
    val secondary = if (task.priority) c.accentText else c.muted
    val stripe = if (task.started) c.blue else c.accent
    val hasStripe = task.priority || task.started
    Row(
        modifier
            .fillMaxWidth()
            .clip(KtpjShapes.card)
            .background(bg)
            .then(
                if (hasStripe) Modifier.drawBehind { drawRect(stripe, size = Size(5.dp.toPx(), size.height)) }
                else if (c.isDark) Modifier.border(1.dp, c.line, KtpjShapes.card)
                else Modifier
            )
            .padding(KtpjDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(
                    if (onEdit != null) Modifier.clickable(enabled = !busy, onClickLabel = "Editar tarea", onClick = onEdit)
                    else Modifier
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            task.key?.let { Text(it, style = t.monoSmall, color = c.muted) }
            Text(task.title, style = t.rowTitle, color = c.fg)
            val date = task.due?.let(::dueLabel)
            val overdue = task.due?.let(::isOverdue) == true
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onEditDate != null) {
                    Text(
                        (date ?: "+ Fecha") + if (overdue) " · atrasada" else "",
                        style = t.captionStrong,
                        color = if (overdue) c.accent else if (date != null) secondary else c.muted,
                        modifier = Modifier
                            .clip(KtpjShapes.pill)
                            .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Cambiar fecha", onClick = onEditDate)
                            .padding(vertical = 4.dp),
                    )
                } else if (date != null) {
                    Text(date, style = t.captionStrong, color = if (overdue) c.accent else secondary)
                }
                if (onToggleStarted != null) {
                    Text(
                        if (task.started) "● En curso" else "▶ Empezar",
                        style = t.captionStrong,
                        color = if (task.started) c.blue else c.muted,
                        modifier = Modifier
                            .clip(KtpjShapes.pill)
                            .clickable(
                                enabled = !busy,
                                role = Role.Button,
                                onClickLabel = if (task.started) "Volver a pendiente" else "Marcar en curso",
                                onClick = onToggleStarted,
                            )
                            .padding(vertical = 4.dp),
                    )
                } else if (task.started) {
                    Text("● En curso", style = t.captionStrong, color = c.blue)
                }
            }
            task.description?.let { Text(it, style = t.caption, color = secondary, maxLines = 2) }
            task.project?.let { p ->
                if (onOpenEpic != null) {
                    Box(
                        Modifier
                            .clip(KtpjShapes.pill)
                            .clickable(role = Role.Button, onClickLabel = "Ver épica ${p.title}") { onOpenEpic(p) }
                            .padding(vertical = 4.dp)
                    ) { ProjectTag(p, secondary) }
                } else ProjectTag(p, secondary)
            }
            task.app?.let { AppLink(it, enabled = !busy) }
        }
        if (onTogglePriority != null) {
            Box(
                Modifier
                    .size(KtpjDimens.minTouchTarget)
                    .clip(KtpjShapes.pill)
                    .clickable(
                        enabled = !busy,
                        role = Role.Checkbox,
                        onClickLabel = if (task.priority) "Quitar prioridad" else "Marcar como prioritaria",
                        onClick = onTogglePriority,
                    )
                    .semantics { stateDescription = if (task.priority) "Prioritaria" else "Normal" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (task.priority) "★" else "☆",
                    style = t.amount,
                    color = if (task.priority) c.accent else c.muted,
                )
            }
        }
        OutlinePill(if (busy) "…" else "Hecha", onClick = onDone, enabled = !busy)
    }
}

/** Terminada hoy: tachada, sin acciones. */
@Composable
fun DoneTaskRow(task: SpurTask, modifier: Modifier = Modifier, titleColor: androidx.compose.ui.graphics.Color? = null) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = KtpjDimens.cardPadding, vertical = 10.dp)
            .semantics(mergeDescendants = true) { stateDescription = "Terminada" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("✓", style = t.rowTitle, color = c.green)
        Column(Modifier.weight(1f)) {
            task.key?.let { Text(it, style = t.monoSmall, color = c.muted) }
            Text(
                task.title,
                style = t.body.copy(textDecoration = TextDecoration.LineThrough),
                color = titleColor ?: c.muted,
            )
        }
    }
}

private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun dayOffset(due: String): Int? {
    val d = runCatching { ISO.parse(due) }.getOrNull() ?: return null
    val today = ISO.parse(ISO.format(Date()))!!
    return Math.round((d.time - today.time) / 86_400_000.0).toInt()
}

/** "Hoy", "Mañana", "Ayer" o "5 nov". */
fun dueLabel(due: String): String = when (dayOffset(due)) {
    0 -> "Hoy"
    1 -> "Mañana"
    -1 -> "Ayer"
    null -> due
    else -> SimpleDateFormat("d MMM", Locale("es", "CO")).format(ISO.parse(due)!!).replace(".", "")
}

fun isOverdue(due: String): Boolean = (dayOffset(due) ?: 0) < 0
fun isToday(due: String): Boolean = dayOffset(due) == 0

/** Calendario para elegir la fecha de la tarea; "Quitar fecha" la deja sin fecha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDatePicker(initial: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val utc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: ISO.format(Date())).let { runCatching { utc.parse(it)?.time }.getOrNull() },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.let { utc.format(Date(it)) }) }) { Text("Listo") }
        },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("Quitar fecha") }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/**
 * Selector de fecha de una tarea: Sin fecha · Hoy · Mañana · Otra… (abre el calendario).
 * [value] en formato YYYY-MM-DD o null. FlowRow para que "Otra…" no se salga en pantallas angostas.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DueChips(value: String?, onChange: (String?) -> Unit) {
    var picking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val today = ISO.format(Date())
    val tomorrow = ISO.format(java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }.time)
    val isOther = value != null && value != today && value != tomorrow
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("Sin fecha", value == null, { onChange(null) })
        Chip("Hoy", value == today, { onChange(today) })
        Chip("Mañana", value == tomorrow, { onChange(tomorrow) })
        Chip(if (isOther) dueLabel(value!!) else "Otra…", isOther, { picking = true })
    }
    if (picking) {
        TaskDatePicker(
            initial = value,
            onPick = { picking = false; onChange(it) },
            onDismiss = { picking = false },
        )
    }
}
