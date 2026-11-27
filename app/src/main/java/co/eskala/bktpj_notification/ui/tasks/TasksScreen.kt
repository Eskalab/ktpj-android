package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.EpicRow
import co.eskala.bktpj_notification.ui.components.OutlinePill
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.DoneTaskRow
import co.eskala.bktpj_notification.ui.components.TaskDatePicker
import co.eskala.bktpj_notification.ui.components.TaskRow
import co.eskala.bktpj_notification.ui.components.isOverdue
import co.eskala.bktpj_notification.ui.components.isToday
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import androidx.compose.runtime.setValue
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/**
 * Tareas en acordeón: Hoy (abierto, ★ primero) · Anteriores (cerrado) · Terminadas hoy (abierto, tachadas)
 * · Próximos días (cerrado) · Sin fecha (cerrado). Tocar el título abre/cierra; tocar la fecha abre el calendario.
 */
@Composable
fun TasksScreen(
    tasks: List<SpurTask>,
    completing: Set<String>,
    refreshing: Boolean,
    onDone: (SpurTask) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePriority: (SpurTask) -> Unit = {},
    onAdd: () -> Unit = {},
    doneToday: List<SpurTask> = emptyList(),
    onSetDue: (SpurTask, String?) -> Unit = { _, _ -> },
    onEdit: (SpurTask) -> Unit = {},
    onToggleStarted: (SpurTask) -> Unit = {},
    epics: List<TaskProject> = emptyList(),
    onOpenEpic: (TaskProject) -> Unit = {},
    habits: List<co.eskala.bktpj_notification.Habit> = emptyList(),
    habitsError: String? = null,
    onOpenHabit: (co.eskala.bktpj_notification.Habit) -> Unit = {},
    onAddHabit: () -> Unit = {},
    onStartHabit: (co.eskala.bktpj_notification.Habit) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    // KTP-13: cambia la fecha y dice si se pudo (para confirmar con "Deshacer"). Sin él, usa onSetDue.
    onReschedule: (suspend (SpurTask, String?) -> Boolean)? = null,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var editingDate by remember { mutableStateOf<SpurTask?>(null) }

    val overdue = overdueTasks(tasks, LocalDate.now())
    val today = tasks.filter { it.due != null && isToday(it.due) }.sortedByDescending { it.priority }
    val upcoming = tasks.filter { it.due != null && !isOverdue(it.due) && !isToday(it.due) }.sortedBy { it.due }
    val noDate = tasks.filter { it.due == null }
    // Qué secciones están abiertas (se recuerda al girar o volver a la pestaña).
    var openToday by rememberSaveable { mutableStateOf(true) }
    var openOverdue by rememberSaveable { mutableStateOf(false) }
    var openDone by rememberSaveable { mutableStateOf(true) }
    var openUpcoming by rememberSaveable { mutableStateOf(false) }
    var openNoDate by rememberSaveable { mutableStateOf(false) }
    var openEpics by rememberSaveable { mutableStateOf(false) }
    var openHabits by rememberSaveable { mutableStateOf(true) }
    // Los que tocan hoy primero (pendientes antes que hechos); los que no tocan, al final y tenues.
    val habitList = habits.sortedWith(compareBy({ !it.dueToday }, { it.doneToday }))

    // KTP-13: reprogramar atrasadas con un toque y confirmar "LAV-3 → Mañana" con Deshacer.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var moving by remember { mutableStateOf(emptySet<String>()) }
    suspend fun applyDue(task: SpurTask, due: String?): Boolean =
        onReschedule?.invoke(task, due) ?: run { onSetDue(task, due); true }
    fun reschedule(list: List<SpurTask>, due: String?) {
        val todo = list.filter { it.id !in moving && it.due != due }
        if (todo.isEmpty()) return
        moving = moving + todo.map { it.id }
        scope.launch {
            val moved = mutableListOf<SpurTask>()
            for (task in todo) if (applyDue(task, due)) moved += task else break   // si falla una, no seguir
            moving = moving - todo.map { it.id }.toSet()
            if (moved.isEmpty()) return@launch
            val today = LocalDate.now()
            val msg = if (todo.size == 1) rescheduleMessage(moved[0], due, today) else bulkRescheduleMessage(moved.size, due, today)
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(msg, actionLabel = "Deshacer", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                moved.forEach { applyDue(it, it.due) }   // it.due es la fecha anterior
            }
        }
    }

    Box(modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tareas", style = t.screenTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
                OutlinePill("Calendario", onClick = onOpenCalendar)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                OutlinePill(if (refreshing) "…" else "Actualizar", onClick = onRefresh, enabled = !refreshing)
            }
            PrimaryButton("+ Nueva tarea", onClick = onAdd, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (tasks.isEmpty() && doneToday.isEmpty()) {
            item {
                Text(
                    "Crea tareas con \"+ Nueva tarea\" o desde Claude Code con /spur: aparecen aquí y te llega una notificación.",
                    style = t.body,
                    color = c.muted,
                )
            }
        }

        fun header(title: String, count: Int, open: Boolean, accent: Boolean = false, action: (@Composable () -> Unit)? = null, onToggle: () -> Unit) {
            item(key = "h:$title") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(KtpjShapes.row)
                        .clickable(role = Role.Button, onClickLabel = if (open) "Cerrar" else "Abrir", onClick = onToggle)
                        .semantics { heading(); stateDescription = if (open) "Abierta" else "Cerrada" }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (count == 0) title else "$title · $count",
                        style = t.overline,
                        color = if (accent && count > 0) c.accent else c.muted,
                        modifier = Modifier.weight(1f),
                    )
                    action?.let { it(); androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp)) }
                    Text(if (open) "▾" else "▸", style = t.rowTitle, color = c.muted)
                }
            }
        }

        fun section(
            title: String, list: List<SpurTask>, open: Boolean, onToggle: () -> Unit, accent: Boolean = false, emptyText: String? = null,
            reschedulable: Boolean = false, action: (@Composable () -> Unit)? = null,
        ) {
            if (list.isEmpty() && emptyText == null) return
            header(title, list.size, open, accent, action, onToggle)
            if (!open) return
            if (list.isEmpty()) {
                item(key = "e:$title") { Text(emptyText!!, style = t.secondary, color = c.muted) }
            }
            items(list, key = { it.id }) { task ->
                TaskRow(
                    task,
                    busy = task.id in completing || task.id in moving,
                    onDone = { onDone(task) },
                    onTogglePriority = { onTogglePriority(task) },
                    onEditDate = { editingDate = task },
                    onEdit = { onEdit(task) },
                    onToggleStarted = { onToggleStarted(task) },
                    onOpenEpic = onOpenEpic,
                    footer = if (!reschedulable) null else {
                        {
                            RescheduleChips(
                                task,
                                enabled = task.id !in completing && task.id !in moving,
                                onQuick = { reschedule(listOf(task), quickDueDate(it, LocalDate.now())) },
                                onOther = { editingDate = task },
                            )
                        }
                    },
                )
            }
        }

        if (habits.isNotEmpty() || habitsError == null) {
            val dueHabits = habits.filter { it.dueToday }
            header("HÁBITOS" + if (dueHabits.isNotEmpty()) " · ${dueHabits.count { it.doneToday }}/${dueHabits.size} hoy" else "", 0, openHabits) { openHabits = !openHabits }
            if (openHabits) {
                items(habitList, key = { "habit:" + it.id }) { h -> co.eskala.bktpj_notification.ui.components.HabitRow(h, onOpen = { onOpenHabit(h) }, onStart = { onStartHabit(h) }) }
                item(key = "habit:add") {
                    Text(
                        if (habits.isEmpty()) "+ Crear un hábito (ej. leer 10 páginas diarias)" else "+ Hábito",
                        style = t.captionStrong,
                        color = c.accent,
                        modifier = Modifier.clip(KtpjShapes.pill).clickable(role = Role.Button, onClick = onAddHabit).padding(vertical = 8.dp),
                    )
                }
            }
        } else {
            item(key = "habit:err") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(KtpjShapes.card).background(c.accentSoft).padding(KtpjDimens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("HÁBITOS", style = t.overline, color = c.accentText)
                    Text("No se pueden leer los hábitos: falta aplicar la migración habit_checks en la base.", style = t.secondary, color = c.fg)
                    Text(habitsError.orEmpty(), style = t.caption, color = c.muted, maxLines = 2)
                }
            }
        }

        section("HOY", today, openToday, { openToday = !openToday },
            emptyText = if (tasks.isNotEmpty() || doneToday.isNotEmpty()) "Nada pendiente para hoy." else null)
        section(
            "ANTERIORES", overdue, openOverdue, { openOverdue = !openOverdue }, accent = true, reschedulable = true,
            action = if (openOverdue && overdue.size >= 2) {
                { SmallChip("Todas a hoy", "Pasar todas las anteriores a hoy", enabled = moving.isEmpty()) { reschedule(overdue, quickDueDate(QuickDue.HOY, LocalDate.now())) } }
            } else null,
        )
        if (doneToday.isNotEmpty()) {
            header("TERMINADAS HOY", doneToday.size, openDone) { openDone = !openDone }
            if (openDone) items(doneToday, key = { "done:" + it.id }) { DoneTaskRow(it) }
        }
        section("PRÓXIMOS DÍAS", upcoming, openUpcoming, { openUpcoming = !openUpcoming })
        section("SIN FECHA", noDate, openNoDate, { openNoDate = !openNoDate })
        if (epics.isNotEmpty()) {
            header("ÉPICAS", epics.size, openEpics) { openEpics = !openEpics }
            if (openEpics) items(epics, key = { "epic:" + it.id }) { e -> EpicRow(e, onOpen = { onOpenEpic(e) }) }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp)) { data ->
        Snackbar(data, shape = KtpjShapes.card, containerColor = c.invBg, contentColor = c.invFg, actionColor = c.accent)
    }
    }

    editingDate?.let { task ->
        TaskDatePicker(
            initial = task.due,
            onPick = { due ->
                editingDate = null
                if (due != task.due) {
                    if (overdue.any { it.id == task.id }) reschedule(listOf(task), due) else onSetDue(task, due)
                }
            },
            onDismiss = { editingDate = null },
        )
    }
}

internal fun sampleTasks() = listOf(
    SpurTask("t1", "Revisar Gmail: qué pasó con la entrevista de Tata", "Nadie se conectó a la entrevista técnica", null),
    SpurTask("t2", "Pagar la administración", null, "2026-11-05"),
)

@Preview(name = "Tareas — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun TasksPreview() = KtpjTheme(ThemeMode.LIGHT) {
    TasksScreen(sampleTasks(), emptySet(), false, {}, {})
}

@Preview(name = "Tareas vacío — Oscuro", widthDp = 360, heightDp = 760)
@Composable
private fun TasksEmptyPreview() = KtpjTheme(ThemeMode.DARK) {
    TasksScreen(emptyList(), emptySet(), false, {}, {})
}
