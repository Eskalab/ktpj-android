package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.EpicCard
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.DoneTaskRow
import co.eskala.bktpj_notification.ui.components.ProgressBar
import co.eskala.bktpj_notification.ui.components.TaskRow
import co.eskala.bktpj_notification.ui.components.dueLabel
import co.eskala.bktpj_notification.ui.components.projectColor
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/**
 * Épica: encabezado con color, prefijo y avance; luego En curso (azul) · Pendientes · Hechas (verde, tachadas).
 * Las tareas de Spur tienen acciones; las tarjetas de la web de KTPJ son solo para ver.
 */
@Composable
fun EpicScreen(
    project: TaskProject,
    cards: List<EpicCard>?,          // null = cargando
    completing: Set<String>,
    onDone: (SpurTask) -> Unit,
    onToggleStarted: (SpurTask) -> Unit,
    onTogglePriority: (SpurTask) -> Unit,
    onEdit: (SpurTask) -> Unit,
    onBack: () -> Unit,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val list = cards.orEmpty()
    val inProgress = list.filter { !it.done && it.task.started }
    val pending = list.filter { !it.done && !it.task.started }
    val done = list.filter { it.done }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "‹ Tareas",
                            style = t.captionStrong,
                            color = c.muted,
                            modifier = Modifier
                                .clip(KtpjShapes.pill)
                                .clickable(role = Role.Button, onClick = onBack)
                                .padding(vertical = 8.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(14.dp).background(projectColor(project.color, c.muted), KtpjShapes.pill))
                        Text(
                            "ÉPICA" + (project.prefix?.let { " · $it" } ?: ""),
                            style = t.overlineLarge,
                            color = c.muted,
                        )
                    }
                    Text(project.title, style = t.screenTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                    if (cards != null && list.isNotEmpty()) {
                        Text(
                            "${done.size} de ${list.size} hechas" + if (inProgress.isNotEmpty()) " · ${inProgress.size} en curso" else "",
                            style = t.secondary,
                            color = c.muted,
                        )
                        ProgressBar(done.size, list.size, height = 8.dp)
                    }
                }
            }

            when {
                cards == null -> item { Text("Cargando…", style = t.secondary, color = c.muted) }
                list.isEmpty() -> item {
                    Text("Esta épica aún no tiene tareas. Asígnala al crear o editar una tarea.", style = t.body, color = c.muted)
                }
            }

            fun header(title: String, n: Int, color: androidx.compose.ui.graphics.Color) {
                item(key = "h:$title") {
                    Text("$title · $n", style = t.overline, color = color, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                }
            }

            if (inProgress.isNotEmpty()) {
                header("EN CURSO", inProgress.size, c.blue)
                items(inProgress, key = { it.task.id }) { EpicCardItem(it, completing, onDone, onToggleStarted, onTogglePriority, onEdit) }
            }
            if (pending.isNotEmpty()) {
                header("PENDIENTES", pending.size, c.muted)
                items(pending, key = { it.task.id }) { EpicCardItem(it, completing, onDone, onToggleStarted, onTogglePriority, onEdit) }
            }
            if (done.isNotEmpty()) {
                header("HECHAS", done.size, c.green)
                items(done, key = { it.task.id }) { DoneTaskRow(it.task, titleColor = c.green) }
            }
        }
    }
}

@Composable
private fun EpicCardItem(
    card: EpicCard,
    completing: Set<String>,
    onDone: (SpurTask) -> Unit,
    onToggleStarted: (SpurTask) -> Unit,
    onTogglePriority: (SpurTask) -> Unit,
    onEdit: (SpurTask) -> Unit,
) {
    val task = card.task
    if (card.fromSpur) {
        TaskRow(
            task,
            busy = task.id in completing,
            onDone = { onDone(task) },
            onTogglePriority = { onTogglePriority(task) },
            onEdit = { onEdit(task) },
            onToggleStarted = { onToggleStarted(task) },
        )
    } else {
        WebCardRow(card)
    }
}

/** Tarjeta de la web de KTPJ: solo lectura, con el tablero y la columna donde está. */
@Composable
private fun WebCardRow(card: EpicCard) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val task = card.task
    val blue = c.blue
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KtpjShapes.card)
            .background(if (task.started) c.blueSoft else if (c.isDark) c.bg else c.card)
            .then(
                if (task.started) Modifier.drawBehind { drawRect(blue, size = Size(5.dp.toPx(), size.height)) }
                else if (c.isDark) Modifier.border(1.dp, c.line, KtpjShapes.card)
                else Modifier
            )
            .padding(KtpjDimens.cardPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            task.key?.let { Text(it, style = t.monoSmall, color = c.muted) }
            Text(task.title, style = t.rowTitle, color = c.fg)
            val meta = listOfNotNull(task.due?.let(::dueLabel), card.where?.let { "En la web: $it" }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = t.caption, color = c.muted)
        }
    }
}
