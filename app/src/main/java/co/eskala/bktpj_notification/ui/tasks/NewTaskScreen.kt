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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import co.eskala.bktpj_notification.ui.components.appLabel
import co.eskala.bktpj_notification.ui.components.dueLabel
import kotlinx.coroutines.launch
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
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/** Datos del formulario "Nueva tarea"; [due] en formato YYYY-MM-DD. */
data class NewTask(val title: String, val description: String?, val due: String?, val priority: Boolean, val app: String? = null, val projectId: String? = null, val pomos: Int? = null)

/** Nueva tarea desde el celular: queda en el tablero Spur igual que las que llegan desde Claude (/spur). */
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
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // La épica recién creada desde el buscador aún no está en [projects]: se recuerda para el resumen.
    var createdProject by remember { mutableStateOf<TaskProject?>(null) }
    val createProject: (suspend (String, String) -> TaskProject?)? = onCreateProject?.let { create ->
        { name, prefix -> create(name, prefix).also { createdProject = it } }
    }
    val epic = projectId?.let { id ->
        projects.firstOrNull { it.id == id } ?: initial?.project?.takeIf { it.id == id } ?: createdProject?.takeIf { it.id == id }
    }
    val appName = app?.let { remember(it) { appLabel(ctx, it) ?: it } }
    val dueText = due?.let { dueLabel(it) }
    val summary = taskSummaryLine(dueText, priority, epicShortName(epic), pomos, appName)
    val spoken = taskSummarySpoken(dueText, priority, epicShortName(epic), pomos, appName)

    // KTP-11 opción B: barra fija compacta (resumen + "Crear") que nunca tapa el formulario;
    // un degradado avisa que hay más opciones debajo.
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TABLERO SPUR", style = t.overlineLarge, color = c.muted, modifier = Modifier.weight(1f))
                    HeaderAction("Cancelar", enabled = !saving, onClick = onCancel)
                }
                Text(if (initial == null) "Nueva tarea" else "Editar tarea", style = t.onboardingTitle, color = c.fg)
                if (initial == null && onSwitchToHabit != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Tarea", true, {})
                        Chip("↻ Hábito", false, onSwitchToHabit)
                    }
                }

                Field("Qué hay que hacer") {
                    TextBox(title, { title = it.take(200) }, "Ej. Llamar al notario", "Título de la tarea", ImeAction.Next)
                }
                Field("Detalle (opcional)") {
                    // "Listo" cierra el teclado y baja por las opciones.
                    TextBox(description, { description = it.take(500) }, "Ej. Preguntar por la escritura", "Detalle de la tarea", ImeAction.Done) {
                        focus.clearFocus()
                        scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                    }
                }
                Field("Fecha") {
                    DueChips(due, { due = it })
                }
                Field("Épica (opcional)") { EpicField(projects, projectId, { projectId = it }, createProject, initial?.project) }
                Field("Pomodoros estimados") { PomosField(pomos, { pomos = it }) }
                initial?.let { co.eskala.bktpj_notification.ui.components.PomoSessionsList(it.id, todayOnly = false) }
                Field("App (opcional)") {
                    AppField(app, { app = it })
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("★ Prioritaria", priority, { priority = !priority })
                    Text(
                        if (priority) "El día de su fecha (o si se atrasa) te la recuerdo cada hora, de 6:00 a.m. a 8:00 p.m., hasta que la marques como Hecha."
                        else "Sin estrella solo aparece en la lista, sin recordatorios.",
                        style = t.caption,
                        color = c.muted,
                    )
                }
            }
            // Degradado sobre el borde inferior mientras quede contenido por ver.
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg))),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(KtpjDimens.divider).background(if (scroll.canScrollForward) c.border else c.line))
        Row(
            Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tocar el resumen baja a las opciones para cambiarlas.
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = KtpjDimens.minTouchTarget)
                    .clip(KtpjShapes.row)
                    .clickable(role = Role.Button, onClickLabel = "Ver opciones") {
                        focus.clearFocus()
                        scope.launch { scroll.animateScrollTo(scroll.maxValue) }
                    }
                    .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("SE GUARDA", style = t.overline, color = c.muted)
                Text(summary, style = t.captionStrong, color = c.fg)
            }
            PrimaryButton(
                if (saving) "Guardando…" else if (initial == null) "Crear" else "Guardar",
                onClick = { onSave(build()) },
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Acción de texto en la cabecera (Cancelar), con área táctil mínima. */
@Composable
private fun HeaderAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = KtpjDimens.minTouchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KtpjTheme.type.buttonSecondary, color = KtpjTheme.colors.fg)
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
private fun TextBox(value: String, onChange: (String) -> Unit, placeholder: String, description: String, ime: ImeAction, onDone: (() -> Unit)? = null) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = t.body.copy(color = c.fg),
        cursorBrush = SolidColor(c.fg),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ime),
        keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
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
