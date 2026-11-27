package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import co.eskala.bktpj_notification.ui.components.appLabel
import co.eskala.bktpj_notification.ui.components.dueLabel
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.EpicField
import co.eskala.bktpj_notification.ui.components.PomosField
import co.eskala.bktpj_notification.ui.components.Chip
import co.eskala.bktpj_notification.ui.components.AppField
import co.eskala.bktpj_notification.ui.components.DueChips
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/** Datos del formulario "Nueva tarea"; [due] en formato YYYY-MM-DD. */
data class NewTask(val title: String, val description: String?, val due: String?, val priority: Boolean, val app: String? = null, val projectId: String? = null, val pomos: Int? = null)

/** Nueva tarea desde el celular: queda en el tablero Spur igual que las que llegan desde Claude (/spur). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTaskScreen(
    saving: Boolean,
    onSave: (NewTask) -> Unit,
    onCancel: () -> Unit,
    initial: SpurTask? = null,
    projects: List<TaskProject> = emptyList(),
    onCreateProject: (suspend (String, String) -> TaskProject?)? = null,
    initialDue: String? = null,
    onSwitchToHabit: (() -> Unit)? = null,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var due by rememberSaveable { mutableStateOf(initial?.due ?: initialDue) }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: false) }
    var app by rememberSaveable { mutableStateOf(initial?.app) }
    var projectId by rememberSaveable { mutableStateOf(initial?.project?.id) }
    var pomos by rememberSaveable { mutableStateOf(initial?.pomos ?: 0) }
    val canSave = !saving && title.isNotBlank()

    fun build() = NewTask(
        title = title.trim(),
        description = description.trim().ifEmpty { null },
        due = due,
        priority = priority,
        app = app,
        projectId = projectId,
        pomos = pomos.takeIf { it > 0 },
    )

    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    // Qué editor está abierto debajo de los chips (uno a la vez); null = ninguno.
    var open by rememberSaveable { mutableStateOf<TaskOption?>(null) }
    // La épica recién creada desde el buscador aún no está en [projects]: se recuerda para el chip.
    var createdProject by remember { mutableStateOf<TaskProject?>(null) }
    val createProject: (suspend (String, String) -> TaskProject?)? = onCreateProject?.let { create ->
        { name, prefix -> create(name, prefix).also { createdProject = it } }
    }
    val epic = projectId?.let { id ->
        projects.firstOrNull { it.id == id } ?: initial?.project?.takeIf { it.id == id } ?: createdProject?.takeIf { it.id == id }
    }
    val appName = app?.let { remember(it) { appLabel(ctx, it) ?: it } }
    val chips = taskOptionChips(due?.let { dueLabel(it) }, priority, epicShortName(epic), pomos, appName, description)

    // KTP-11 opción C: título + chips con cada opción y su valor; el editor se abre debajo
    // y el botón grande queda justo después, sin tener que desplazarse en el caso común.
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
    ) {
        Text("TABLERO SPUR", style = t.overlineLarge, color = c.muted)
        Text(if (initial == null) "Nueva tarea" else "Editar tarea", style = t.onboardingTitle, color = c.fg)
        if (initial == null && onSwitchToHabit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Tarea", true, {})
                Chip("↻ Hábito", false, onSwitchToHabit)
            }
        }

        Field("Qué hay que hacer") {
            TextBox(title, { title = it.take(200) }, "Ej. Llamar al notario", "Título de la tarea", ImeAction.Done)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { (option, text) ->
                OptionChip(text, expanded = open == option, toggle = option == TaskOption.PRIORIDAD) {
                    focus.clearFocus()
                    if (option == TaskOption.PRIORIDAD) priority = !priority
                    else open = if (open == option) null else option
                }
            }
        }
        if (priority) {
            Text(
                "★ Te la recuerdo cada hora (6:00 a.m. a 8:00 p.m.) el día de su fecha o si se atrasa, hasta que la marques como Hecha.",
                style = t.caption,
                color = c.muted,
            )
        }
        open?.let { option ->
            OptionEditor(option, onClose = { open = null }) {
                when (option) {
                    TaskOption.FECHA -> DueChips(due, { due = it })
                    TaskOption.EPICA -> EpicField(projects, projectId, { projectId = it }, createProject, initial?.project)
                    TaskOption.POMOS -> PomosField(pomos, { pomos = it })
                    TaskOption.APP -> AppField(app, { app = it })
                    TaskOption.DETALLE -> TextBox(description, { description = it.take(500) }, "Ej. Preguntar por la escritura", "Detalle de la tarea", ImeAction.Done)
                    TaskOption.PRIORIDAD -> Unit
                }
            }
        }
        Column(
            Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
        ) {
            PrimaryButton(if (saving) "Guardando…" else if (initial == null) "Crear tarea" else "Guardar cambios", onClick = { onSave(build()) }, enabled = canSave)
            SecondaryButton("Cancelar", onClick = onCancel, enabled = !saving)
        }
        initial?.let { co.eskala.bktpj_notification.ui.components.PomoSessionsList(it.id, todayOnly = false) }
    }
}

/** Chip de opción: muestra el valor actual; relleno si tiene valor, borde grueso si su editor está abierto. */
@Composable
private fun OptionChip(text: OptionChipText, expanded: Boolean, toggle: Boolean, onClick: () -> Unit) {
    val c = KtpjTheme.colors
    Box(
        Modifier
            .heightIn(min = KtpjDimens.minTouchTarget)
            .clip(KtpjShapes.pill)
            .then(if (text.set) Modifier.background(c.invBg) else Modifier)
            .border(if (expanded) KtpjDimens.borderField * 1.5f else KtpjDimens.borderCard, if (expanded) c.fg else c.border, KtpjShapes.pill)
            .clickable(role = if (toggle) Role.Switch else Role.Button, onClick = onClick)
            .semantics {
                contentDescription = text.spoken
                stateDescription = when {
                    toggle -> if (text.set) "activada" else "desactivada"
                    expanded -> "abierto, toca para cerrar"
                    else -> "toca para cambiar"
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.label, style = KtpjTheme.type.captionStrong, color = if (text.set) c.invFg else c.fg)
    }
}

/** Panel en línea con el editor de una opción y un "Listo" para cerrarlo. */
@Composable
private fun OptionEditor(option: TaskOption, onClose: () -> Unit, content: @Composable () -> Unit) {
    val c = KtpjTheme.colors
    val title = when (option) {
        TaskOption.FECHA -> "Fecha"
        TaskOption.EPICA -> "Épica"
        TaskOption.POMOS -> "Pomodoros estimados"
        TaskOption.APP -> "App"
        TaskOption.DETALLE -> "Detalle"
        TaskOption.PRIORIDAD -> "Prioritaria"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(KtpjDimens.borderCard, c.border, KtpjShapes.row)
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = KtpjTheme.type.rowTitle, color = c.fg, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .heightIn(min = KtpjDimens.minTouchTarget)
                    .clip(KtpjShapes.pill)
                    .clickable(role = Role.Button, onClickLabel = "Cerrar $title", onClick = onClose)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Listo", style = KtpjTheme.type.buttonSecondary, color = c.fg)
            }
        }
        content()
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        Text(label, style = KtpjTheme.type.rowTitle, color = KtpjTheme.colors.fg)
        content()
    }
}

@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, placeholder: String, description: String, ime: ImeAction) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = t.body.copy(color = c.fg),
        cursorBrush = SolidColor(c.fg),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ime),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(KtpjDimens.borderField, c.fg, KtpjShapes.row)
                    .padding(KtpjDimens.cardPadding),
            ) {
                if (value.isEmpty()) Text(placeholder, style = t.body, color = c.muted)
                inner()
            }
        },
    )
}

@Preview(name = "Nueva tarea — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun NewTaskPreview() = KtpjTheme(ThemeMode.LIGHT) {
    NewTaskScreen(saving = false, onSave = {}, onCancel = {})
}

@Preview(name = "Nueva tarea — Oscuro, fuente grande", widthDp = 360, heightDp = 760, fontScale = 1.6f)
@Composable
private fun NewTaskDarkPreview() = KtpjTheme(ThemeMode.DARK) {
    NewTaskScreen(saving = false, onSave = {}, onCancel = {})
}
