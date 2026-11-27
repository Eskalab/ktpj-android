package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * KTP-13 — Reprogramar tareas atrasadas desde la tarjeta: Hoy · Mañana · 📅 Otra.
 * La lógica es pura (recibe "hoy") para probarla en JVM.
 */

/** Atajos de un toque para las tareas atrasadas. */
enum class QuickDue { HOY, MANANA }

/** Fecha destino YYYY-MM-DD del atajo, contada desde [today]. */
fun quickDueDate(option: QuickDue, today: LocalDate): String = when (option) {
    QuickDue.HOY -> today
    QuickDue.MANANA -> today.plusDays(1)
}.toString()

private fun parseDue(due: String?): LocalDate? = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** ¿Cuenta como "anterior"? Fecha válida antes de [today]. Sin fecha o fecha mala: no. */
fun isOverdueOn(due: String?, today: LocalDate): Boolean = parseDue(due)?.isBefore(today) == true

/** Sección Anteriores: tareas pendientes con fecha pasada, la más vieja primero. */
fun overdueTasks(tasks: List<SpurTask>, today: LocalDate): List<SpurTask> =
    tasks.filter { isOverdueOn(it.due, today) }.sortedBy { it.due }

private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale("es", "CO"))

/** "Hoy", "Mañana", "5 dic" o "Sin fecha" para el aviso de confirmación. */
fun dueTargetLabel(due: String?, today: LocalDate): String {
    val d = parseDue(due) ?: return if (due == null) "Sin fecha" else due
    return when (d) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> d.format(SHORT).replace(".", "")
    }
}

/** Cómo se nombra la tarea en avisos y lectores de pantalla: su ID (LAV-3) o el título recortado. */
fun taskRef(task: SpurTask): String =
    task.key ?: task.title.let { if (it.length > 24) it.take(23).trimEnd() + "…" else it }

/** "LAV-3 → Mañana". */
fun rescheduleMessage(task: SpurTask, due: String?, today: LocalDate): String =
    "${taskRef(task)} → ${dueTargetLabel(due, today)}"

/** "1 tarea → Hoy" / "3 tareas → Hoy". */
fun bulkRescheduleMessage(count: Int, due: String?, today: LocalDate): String =
    "${if (count == 1) "1 tarea" else "$count tareas"} → ${dueTargetLabel(due, today)}"

/**
 * Fila compacta dentro de una tarjeta atrasada: Hoy · Mañana · 📅 Otra. Cada botón se anuncia
 * con la tarea ("Pasar a hoy LAV-3"). FlowRow: en pantallas angostas "Otra" baja de línea.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RescheduleChips(
    task: SpurTask,
    enabled: Boolean,
    onQuick: (QuickDue) -> Unit,
    onOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref = taskRef(task)
    FlowRow(
        modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallChip("Hoy", "Pasar a hoy $ref", enabled) { onQuick(QuickDue.HOY) }
        SmallChip("Mañana", "Pasar a mañana $ref", enabled) { onQuick(QuickDue.MANANA) }
        SmallChip("📅 Otra", "Elegir otra fecha para $ref", enabled, onOther)
    }
}

/** Píldora pequeña con borde, como los chips de fecha pero más compacta para ir dentro de la tarjeta. */
@Composable
internal fun SmallChip(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KtpjTheme.colors
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(KtpjShapes.pill)
            .border(1.dp, c.border, KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KtpjTheme.type.captionStrong, color = c.fg)
    }
}
