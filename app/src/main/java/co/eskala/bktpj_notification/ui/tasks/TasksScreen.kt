package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.SpurTask
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
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var editingDate by remember { mutableStateOf<SpurTask?>(null) }

    val overdue = tasks.filter { it.due != null && isOverdue(it.due) }.sortedBy { it.due }
    val today = tasks.filter { it.due != null && isToday(it.due) }.sortedByDescending { it.priority }
    val upcoming = tasks.filter { it.due != null && !isOverdue(it.due) && !isToday(it.due) }.sortedBy { it.due }
    val noDate = tasks.filter { it.due == null }
    // Qué secciones están abiertas (se recuerda al girar o volver a la pestaña).
    var openToday by rememberSaveable { mutableStateOf(true) }
    var openOverdue by rememberSaveable { mutableStateOf(false) }
    var openDone by rememberSaveable { mutableStateOf(true) }
    var openUpcoming by rememberSaveable { mutableStateOf(false) }
    var openNoDate by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tareas", style = t.screenTitle, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
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

        fun header(title: String, count: Int, open: Boolean, accent: Boolean = false, onToggle: () -> Unit) {
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
                    Text(if (open) "▾" else "▸", style = t.rowTitle, color = c.muted)
                }
            }
        }

        fun section(title: String, list: List<SpurTask>, open: Boolean, onToggle: () -> Unit, accent: Boolean = false, emptyText: String? = null) {
            if (list.isEmpty() && emptyText == null) return
            header(title, list.size, open, accent, onToggle)
            if (!open) return
            if (list.isEmpty()) {
                item(key = "e:$title") { Text(emptyText!!, style = t.secondary, color = c.muted) }
            }
            items(list, key = { it.id }) { task ->
                TaskRow(
                    task,
                    busy = task.id in completing,
                    onDone = { onDone(task) },
                    onTogglePriority = { onTogglePriority(task) },
                    onEditDate = { editingDate = task },
                    onEdit = { onEdit(task) },
                )
            }
        }

        section("HOY", today, openToday, { openToday = !openToday },
            emptyText = if (tasks.isNotEmpty() || doneToday.isNotEmpty()) "Nada pendiente para hoy." else null)
        section("ANTERIORES", overdue, openOverdue, { openOverdue = !openOverdue }, accent = true)
        if (doneToday.isNotEmpty()) {
            header("TERMINADAS HOY", doneToday.size, openDone) { openDone = !openDone }
            if (openDone) items(doneToday, key = { "done:" + it.id }) { DoneTaskRow(it) }
        }
        section("PRÓXIMOS DÍAS", upcoming, openUpcoming, { openUpcoming = !openUpcoming })
        section("SIN FECHA", noDate, openNoDate, { openNoDate = !openNoDate })
    }

    editingDate?.let { task ->
        TaskDatePicker(
            initial = task.due,
            onPick = { due ->
                editingDate = null
                if (due != task.due) onSetDue(task, due)
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
