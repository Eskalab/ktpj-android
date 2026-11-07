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
import co.eskala.bktpj_notification.ui.components.ProjectChips
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
data class NewTask(val title: String, val description: String?, val due: String?, val priority: Boolean, val app: String? = null, val projectId: String? = null)

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
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var due by rememberSaveable { mutableStateOf(initial?.due ?: initialDue) }
    var priority by rememberSaveable { mutableStateOf(initial?.priority ?: false) }
    var app by rememberSaveable { mutableStateOf(initial?.app) }
    var projectId by rememberSaveable { mutableStateOf(initial?.project?.id) }
    val canSave = !saving && title.isNotBlank()

    fun build() = NewTask(
        title = title.trim(),
        description = description.trim().ifEmpty { null },
        due = due,
        priority = priority,
        app = app,
        projectId = projectId,
    )

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            Text("TABLERO SPUR", style = t.overlineLarge, color = c.muted)
            Text(if (initial == null) "Nueva tarea" else "Editar tarea", style = t.onboardingTitle, color = c.fg)

            Field("Qué hay que hacer") {
                TextBox(title, { title = it.take(200) }, "Ej. Llamar al notario", "Título de la tarea", ImeAction.Next)
            }
            Field("Detalle (opcional)") {
                TextBox(description, { description = it.take(500) }, "Ej. Preguntar por la escritura", "Detalle de la tarea", ImeAction.Done)
            }
            Field("Fecha") {
                DueChips(due, { due = it })
            }
            Field("Épica (opcional)") { ProjectChips(projects, projectId, { projectId = it }, onCreateProject) }
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
        Column(
            Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
        ) {
            PrimaryButton(if (saving) "Guardando…" else if (initial == null) "Crear tarea" else "Guardar cambios", onClick = { onSave(build()) }, enabled = canSave)
            SecondaryButton("Cancelar", onClick = onCancel, enabled = !saving)
        }
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
