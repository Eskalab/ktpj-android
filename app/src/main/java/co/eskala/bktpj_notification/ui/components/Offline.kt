package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.CaptureLog
import co.eskala.bktpj_notification.Habits
import co.eskala.bktpj_notification.OfflineSync
import co.eskala.bktpj_notification.Outbox
import co.eskala.bktpj_notification.OutboxOp
import co.eskala.bktpj_notification.PendingCaptures
import co.eskala.bktpj_notification.SpurTasks
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/** "1 cambio por subir" / "3 cambios por subir". */
fun pendingLabel(n: Int) = if (n == 1) "1 cambio por subir" else "$n cambios por subir"

/** Texto corto de un cambio de la cola, para la lista de lo que no se pudo subir. */
fun describeOp(op: OutboxOp, taskTitle: (String) -> String?, habitTitle: (String) -> String?): String {
    fun t(id: String) = "«${taskTitle(id) ?: "tarea"}»"
    fun h(id: String) = "«${habitTitle(id) ?: "hábito"}»"
    return when (op) {
        is OutboxOp.CreateTask -> "Crear «${op.title}»"
        is OutboxOp.UpdateTask -> "Editar «${op.title}»"
        is OutboxOp.CompleteTask -> "Completar ${t(op.id)}"
        is OutboxOp.SetStarted -> (if (op.started) "Empezar " else "Volver a pendiente ") + t(op.id)
        is OutboxOp.SetDue -> "Fecha de ${t(op.id)}"
        is OutboxOp.SetPriority -> (if (op.high) "★ a " else "Quitar ★ a ") + t(op.id)
        is OutboxOp.CheckHabit -> "Marcar ${h(op.habitId)} · ${op.day}"
        is OutboxOp.UncheckHabit -> "Desmarcar ${h(op.habitId)} · ${op.day}"
    }
}

/**
 * KTP-8 · Franja discreta bajo el contenido: "Sin conexión · mostrando lo último guardado", cuántos cambios
 * faltan por subir y, si algo falló varias veces, el aviso con Reintentar (y Ver para descartar uno a uno).
 * No ocupa espacio cuando todo está al día.
 */
@Composable
fun OfflineBar() {
    val ctx = LocalContext.current
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    LaunchedEffect(Unit) { Outbox.load(ctx); PendingCaptures.load(ctx); OfflineSync.start(ctx) }

    val online by OfflineSync.online.collectAsState()
    val readFailed by OfflineSync.readFailed.collectAsState()
    val flushing by OfflineSync.flushing.collectAsState()
    val entries by Outbox.entries.collectAsState()
    val pendingCaptures by PendingCaptures.list.collectAsState()
    val tasks by SpurTasks.tasks.collectAsState()
    val done by SpurTasks.doneToday.collectAsState()
    val habits by Habits.habits.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val offline = online == false || readFailed
    val pending = entries.count { !it.parked } + pendingCaptures.count { !it.parked }
    val failedOps = entries.filter { it.parked }
    val failedCaptures = pendingCaptures.filter { it.parked }
    val failed = failedOps.size + failedCaptures.size
    // Con red, lo que se está subiendo en este momento no se anuncia (sería un parpadeo en cada toque).
    val showPending = pending > 0 && (offline || !flushing)
    if (!offline && !showPending && failed == 0) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(horizontal = KtpjDimens.screenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val status = listOfNotNull(
            "Sin conexión · mostrando lo último guardado".takeIf { offline },
            pendingLabel(pending).takeIf { showPending },
        ).joinToString(" · ")
        if (status.isNotEmpty()) {
            Text(
                status,
                style = t.caption,
                color = c.muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (failed > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (failed == 1) "1 cambio no se pudo subir" else "$failed cambios no se pudieron subir",
                    style = t.captionStrong,
                    color = c.accentText,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                Box(
                    Modifier
                        .heightIn(min = 32.dp)
                        .clip(KtpjShapes.pill)
                        .clickable(role = Role.Button, onClickLabel = if (expanded) "Ocultar detalle" else "Ver detalle") { expanded = !expanded }
                        .semantics { stateDescription = if (expanded) "Abierto" else "Cerrado" }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (expanded) "Ocultar" else "Ver", style = t.captionStrong, color = c.fg)
                }
                RetryPill(onClick = { OfflineSync.retryFailed(ctx) }, busy = flushing, onAccentBackground = false)
            }
            if (expanded) {
                val title: (String) -> String? = { id -> (tasks + done).firstOrNull { it.id == id }?.title }
                val habitTitle: (String) -> String? = { id -> habits.firstOrNull { it.id == id }?.title }
                failedOps.forEach { e ->
                    FailedRow(
                        text = describeOp(e.op, title, habitTitle),
                        error = e.lastError,
                        onDiscard = { OfflineSync.discard(ctx, e.seq) },
                    )
                }
                failedCaptures.forEach { p ->
                    val cap = CaptureLog.get(ctx, p.id)
                    FailedRow(
                        text = "Gasto «${cap?.label ?: "captura"}»" + (cap?.signedAmount?.let { " $it" } ?: ""),
                        error = cap?.error,
                        // El gasto queda en el historial (con su Reintentar); solo deja de reintentarse solo.
                        onDiscard = { OfflineSync.discardCapture(ctx, p.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedRow(text: String, error: String?, onDiscard: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(text, style = t.caption, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            error?.let { Text(it, style = t.monoSmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Box(
            Modifier
                .heightIn(min = KtpjDimens.minTouchTarget)
                .clip(KtpjShapes.pill)
                .clickable(role = Role.Button, onClickLabel = "Descartar $text", onClick = onDiscard)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Descartar", style = t.captionStrong, color = c.accentText)
        }
    }
}
