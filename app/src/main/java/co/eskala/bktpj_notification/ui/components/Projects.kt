package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

private fun projectColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback

/** "● Sucesión papá" con el color del proyecto en la web. */
@Composable
fun ProjectTag(project: TaskProject) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(projectColor(project.color, KtpjTheme.colors.muted), KtpjShapes.pill))
        Text(project.title, style = KtpjTheme.type.caption, color = KtpjTheme.colors.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ninguno · Buscar trabajo · KTPJ · … */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectChips(projects: List<TaskProject>, selectedId: String?, onChange: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Ninguno", selectedId == null, { onChange(null) })
        projects.forEach { p -> Chip(p.title, selectedId == p.id, { onChange(p.id) }) }
    }
}
