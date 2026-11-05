package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.ProjectChips
import co.eskala.bktpj_notification.ui.components.Chip
import co.eskala.bktpj_notification.ui.components.DueChips
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/**
 * "¿Qué sigue?": aparece al completar una tarea en la app, para capturar el siguiente paso del hilo
 * mientras está fresco. Cerrarlo ("Nada por ahora", atrás o tocar fuera) no crea nada.
 */
@Composable
fun NextTaskDialog(
    done: SpurTask,
    saving: Boolean,
    onCreate: (title: String, priority: Boolean, due: String?, projectId: String?) -> Unit,
    onDismiss: () -> Unit,
    projects: List<TaskProject> = emptyList(),
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var title by rememberSaveable(done.id) { mutableStateOf("") }
    var priority by rememberSaveable(done.id) { mutableStateOf(false) }
    var due by rememberSaveable(done.id) { mutableStateOf<String?>(null) }
    // Sigue el mismo hilo: hereda el proyecto de la tarea que se acaba de terminar.
    var projectId by rememberSaveable(done.id) { mutableStateOf(done.project?.id) }
    val focus = remember { FocusRequester() }
    val canCreate = !saving && title.isNotBlank()

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bg, KtpjShapes.notification)
                .padding(KtpjDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
        ) {
            Text("✓ COMPLETADA", style = t.overline, color = c.green)
            Text(done.title, style = t.secondary, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("¿Qué sigue?", style = t.screenTitle, color = c.fg)

            BasicTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                textStyle = t.body.copy(color = c.fg),
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate(title.trim(), priority, due, projectId) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = "Siguiente tarea" },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(KtpjDimens.borderField, c.fg, KtpjShapes.row)
                            .padding(KtpjDimens.cardPadding),
                    ) {
                        if (title.isEmpty()) Text("El siguiente paso de este hilo", style = t.body, color = c.muted)
                        inner()
                    }
                },
            )
            DueChips(due, { due = it })
            if (projects.isNotEmpty()) ProjectChips(projects, projectId, { projectId = it })
            Chip("★ Prioritaria", priority, { priority = !priority })

            Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
                PrimaryButton(if (saving) "Creando…" else "Crear tarea", onClick = { onCreate(title.trim(), priority, due, projectId) }, enabled = canCreate)
                SecondaryButton("Nada por ahora", onClick = onDismiss, enabled = !saving)
            }
        }
    }

    // El teclado se abre de una: si hay algo que seguir, se escribe sin un toque extra.
    LaunchedEffect(done.id) { runCatching { focus.requestFocus() } }
}
