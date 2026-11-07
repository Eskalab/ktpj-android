package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.Dialog
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

fun projectColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback

/** "● Sucesión papá" con el color del proyecto en la web. */
@Composable
fun ProjectTag(project: TaskProject, textColor: Color = KtpjTheme.colors.accentText) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(projectColor(project.color, KtpjTheme.colors.muted), KtpjShapes.pill))
        Text(project.title, style = KtpjTheme.type.caption, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Épica de la tarea: Ninguno · Lavadora · Sucesión papá · … · "+ Nuevo".
 * ("Épica" en la app; en la base y en la web de KTPJ es un Project.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectChips(
    projects: List<TaskProject>,
    selectedId: String?,
    onChange: (String?) -> Unit,
    onCreate: (suspend (name: String, prefix: String) -> TaskProject?)? = null,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Ninguno", selectedId == null, { onChange(null) })
        projects.forEach { p -> Chip(p.title, selectedId == p.id, { onChange(p.id) }) }
        if (onCreate != null) Chip("+ Nueva", false, { adding = true })
    }
    if (adding && onCreate != null) {
        NewProjectDialog(
            used = projects.mapNotNull { it.prefix }.toSet() + UsedPrefixes.boards,
            onCreate = onCreate,
            onCreated = { adding = false; onChange(it.id) },
            onDismiss = { adding = false },
        )
    }
}

/** Crear épica: solo el nombre. Queda seleccionada al crearlo. */
@Composable
private fun NewProjectDialog(
    used: Set<String>,
    onCreate: suspend (name: String, prefix: String) -> TaskProject?,
    onCreated: (TaskProject) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var prefix by rememberSaveable { mutableStateOf("") }
    var prefixEdited by rememberSaveable { mutableStateOf(false) }   // si lo tocó, ya no se sugiere solo
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val prefixOk = prefix.length in 2..5 && prefix !in used
    val canSave = !saving && name.isNotBlank() && prefixOk
    fun save() {
        if (!canSave) return
        saving = true
        scope.launch {
            val p = onCreate(name.trim(), prefix)
            saving = false
            if (p != null) onCreated(p)
        }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().background(c.bg, KtpjShapes.notification).padding(KtpjDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
        ) {
            Text("Nueva épica", style = t.screenTitle, color = c.fg)
            Text("Agrupa los pasos de algo que estás sacando adelante, por ejemplo \"Reparar la lavadora\".", style = t.secondary, color = c.muted)
            BasicTextField(
                value = name,
                onValueChange = {
                    name = it.take(60)
                    if (!prefixEdited) prefix = suggestPrefix(name, used)
                },
                singleLine = true,
                textStyle = t.body.copy(color = c.fg),
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Nombre de la épica" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().border(KtpjDimens.borderField, c.fg, KtpjShapes.row).padding(KtpjDimens.cardPadding)) {
                        if (name.isEmpty()) Text("Ej. Reparar la lavadora", style = t.body, color = c.muted)
                        inner()
                    }
                },
            )
            Text("Prefijo de los IDs", style = t.rowTitle, color = c.fg)
            BasicTextField(
                value = prefix,
                onValueChange = { v -> prefixEdited = true; prefix = cleanPrefix(v) },
                singleLine = true,
                textStyle = t.mono.copy(color = c.fg),
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Prefijo de la épica" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().border(KtpjDimens.borderField, c.fg, KtpjShapes.row).padding(KtpjDimens.cardPadding)) {
                        if (prefix.isEmpty()) Text("LAV", style = t.mono, color = c.muted)
                        inner()
                    }
                },
            )
            Text(
                when {
                    prefix.isEmpty() -> "Se sugiere con las 3 primeras letras del nombre."
                    prefix in used -> "$prefix ya lo usa otra épica."
                    prefix.length < 2 -> "Mínimo 2 letras."
                    else -> "Las tareas quedarán como $prefix-1, $prefix-2…"
                },
                style = t.caption,
                color = if (prefix in used) c.accent else c.muted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
                PrimaryButton(if (saving) "Creando…" else "Crear épica", onClick = { save() }, enabled = canSave)
                SecondaryButton("Cancelar", onClick = onDismiss, enabled = !saving)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Solo letras A-Z y números, en mayúscula y sin tildes; máximo 5. */
fun cleanPrefix(v: String): String =
    java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(5)

/** Las 3 primeras letras del nombre; si ya existe, prueba con la inicial + letras de la siguiente palabra, o un número. */
fun suggestPrefix(name: String, used: Set<String>): String {
    val words = name.split(Regex("\\s+")).map(::cleanPrefix).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    val base = words.joinToString("").take(3)
    if (base !in used) return base
    if (words.size > 1) {
        val alt = (words[0].take(1) + words[1]).take(3)
        if (alt.length == 3 && alt !in used) return alt
    }
    return (2..9).map { base.take(2) + it }.firstOrNull { it !in used } ?: base
}

/** Prefijos tomados por tableros de KTPJ (KD, NU…) y épicas; MainScreen los carga de la base. */
object UsedPrefixes {
    var boards by mutableStateOf(setOf("SP"))
}

/** Avance de la épica: "3/5" con barra. */
@Composable
fun EpicRow(project: TaskProject, onOpen: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KtpjShapes.card)
            .background(if (c.isDark) c.bg else c.card)
            .then(if (c.isDark) Modifier.border(1.dp, c.line, KtpjShapes.card) else Modifier)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Ver épica", onClick = onOpen)
            .padding(KtpjDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).background(projectColor(project.color, c.muted), KtpjShapes.pill))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(project.title, style = t.rowTitle, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                project.prefix?.let { Text(it, style = t.monoSmall, color = c.muted) }
            }
            ProgressBar(project.done, project.total)
        }
        Text("${project.done}/${project.total}", style = t.captionStrong, color = c.muted)
        Text("›", style = t.amount, color = c.muted)
    }
}

@Composable
fun ProgressBar(done: Int, total: Int, height: androidx.compose.ui.unit.Dp = 6.dp) {
    val c = KtpjTheme.colors
    Box(Modifier.fillMaxWidth().height(height).clip(KtpjShapes.pill).background(c.line)) {
        if (total > 0 && done > 0) {
            Box(Modifier.fillMaxWidth(done / total.toFloat()).fillMaxHeight().clip(KtpjShapes.pill).background(c.green))
        }
    }
}
