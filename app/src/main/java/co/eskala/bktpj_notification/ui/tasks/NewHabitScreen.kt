package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Habit
import co.eskala.bktpj_notification.HabitDates
import co.eskala.bktpj_notification.HabitDraft
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.AppField
import co.eskala.bktpj_notification.ui.components.Chip
import co.eskala.bktpj_notification.ui.components.EpicField
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.components.Stepper
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/** Nuevo / editar hábito: qué, qué días, meta del día (opcional) y hora del aviso (opcional). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewHabitScreen(
    saving: Boolean,
    onSave: (HabitDraft) -> Unit,
    onCancel: () -> Unit,
    initial: Habit? = null,
    projects: List<TaskProject> = emptyList(),
    onCreateProject: (suspend (String, String) -> TaskProject?)? = null,
    onSwitchToTask: (() -> Unit)? = null,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var days by rememberSaveable { mutableStateOf(initial?.days?.toList().orEmpty()) }   // vacío = todos
    var goal by rememberSaveable { mutableStateOf(initial?.goal ?: 0) }
    var unit by rememberSaveable { mutableStateOf(initial?.unit.orEmpty()) }
    var remindAt by rememberSaveable { mutableStateOf(initial?.remindAt) }
    var projectId by rememberSaveable { mutableStateOf(initial?.project?.id) }
    var app by rememberSaveable { mutableStateOf(initial?.app) }
    val canSave = !saving && title.isNotBlank()

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            Text("TABLERO SPUR", style = t.overlineLarge, color = c.muted)
            Text(if (initial == null) "Nuevo hábito" else "Editar hábito", style = t.onboardingTitle, color = c.fg)
            if (onSwitchToTask != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Tarea", false, onSwitchToTask)
                    Chip("↻ Hábito", true, {})
                }
            }
            Text(
                "Algo que repites (leer, ejercicio, agua). No se termina: cada día lo marcas y llevas la racha 🔥.",
                style = t.secondary,
                color = c.muted,
            )

            Block("Qué hábito") {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    textStyle = t.body.copy(color = c.fg),
                    cursorBrush = SolidColor(c.fg),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nombre del hábito" },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().border(KtpjDimens.borderField, c.fg, KtpjShapes.row).padding(KtpjDimens.cardPadding)) {
                            if (title.isEmpty()) Text("Ej. Leer", style = t.body, color = c.muted)
                            inner()
                        }
                    },
                )
            }

            Block("Qué días") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Todos", days.isEmpty() || days.size == 7, { days = emptyList() })
                    Chip("Entre semana", days.toSet() == setOf(1, 2, 3, 4, 5), { days = listOf(1, 2, 3, 4, 5) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4, 5, 6, 0).forEach { d ->
                        val on = days.isEmpty() || d in days
                        Chip(HabitDates.LETTERS[d], on, {
                            val cur = if (days.isEmpty()) (0..6).toList() else days
                            val next = if (d in cur) cur - d else cur + d
                            days = if (next.size == 7) emptyList() else next.ifEmpty { cur }   // al menos un día
                        })
                    }
                }
                Text(HabitDates.label(days.toSet()), style = t.caption, color = c.muted)
            }

            Block("Meta del día (opcional)") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stepper(goal, if (goal == 0) "Sin meta" else "$goal", { goal = it }, 0..999, "Meta")
                    if (goal > 0) {
                        BasicTextField(
                            value = unit,
                            onValueChange = { unit = it.take(20) },
                            singleLine = true,
                            textStyle = t.body.copy(color = c.fg),
                            cursorBrush = SolidColor(c.fg),
                            modifier = Modifier.width(130.dp).semantics { contentDescription = "Unidad de la meta" },
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().border(KtpjDimens.borderField, c.fg, KtpjShapes.row).padding(KtpjDimens.cardPadding)) {
                                    if (unit.isEmpty()) Text("páginas", style = t.body, color = c.muted)
                                    inner()
                                }
                            },
                        )
                    }
                }
                if (goal > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("páginas", "min", "km", "vasos", "veces").forEach { u -> Chip(u, unit == u, { unit = u }) }
                    }
                }
                Text(
                    if (goal == 0) "Sin meta solo marcas si lo hiciste."
                    else if (unit.isBlank()) "Con unidad, al marcar te pregunta cuánto hiciste."
                    else "Al marcar te pregunta cuánto hiciste (ya viene con $goal $unit).",
                    style = t.caption,
                    color = c.muted,
                )
            }

            Block("Aviso si no lo has hecho") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Sin aviso", remindAt == null, { remindAt = null })
                    listOf(7, 12, 18, 21).forEach { h -> Chip(hourLabel(h), remindAt == h, { remindAt = h }) }
                }
                if (remindAt != null) {
                    Stepper(remindAt!!, hourLabel(remindAt!!), { remindAt = it }, 5..23, "Hora del aviso")
                }
                Text(
                    if (remindAt == null) "No te avisa; lo ves en Hoy."
                    else "Los días que toca, a partir de las ${hourLabel(remindAt!!)}, una vez, solo si no lo has marcado.",
                    style = t.caption,
                    color = c.muted,
                )
            }

            Block("Épica (opcional)") { EpicField(projects, projectId, { projectId = it }, onCreateProject, initial?.project) }
            Block("App (opcional)") { AppField(app, { app = it }) }
        }
        Column(
            Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
        ) {
            PrimaryButton(
                if (saving) "Guardando…" else if (initial == null) "Crear hábito" else "Guardar cambios",
                onClick = {
                    onSave(
                        HabitDraft(
                            title = title.trim(),
                            days = days.toSet(),
                            goal = goal.takeIf { it > 0 },
                            unit = unit.trim().takeIf { goal > 0 && it.isNotEmpty() },
                            remindAt = remindAt,
                            projectId = projectId,
                            app = app,
                        )
                    )
                },
                enabled = canSave,
            )
            SecondaryButton("Cancelar", onClick = onCancel, enabled = !saving)
        }
    }
}

@Composable
private fun Block(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        Text(label, style = KtpjTheme.type.rowTitle, color = KtpjTheme.colors.fg)
        content()
    }
}
