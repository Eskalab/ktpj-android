package co.eskala.bktpj_notification.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Capture
import co.eskala.bktpj_notification.CaptureSource
import co.eskala.bktpj_notification.CaptureStatus
import co.eskala.bktpj_notification.Money
import co.eskala.bktpj_notification.NuTransaction
import co.eskala.bktpj_notification.Shortcut
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.ui.components.CaptureCard
import co.eskala.bktpj_notification.ui.components.OutlinePill
import co.eskala.bktpj_notification.ui.components.ServiceState
import co.eskala.bktpj_notification.ui.components.StatusChip
import co.eskala.bktpj_notification.ui.components.TaskRow
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import java.util.Calendar

/**
 * Inicio (dashboard): atajos de gasto arriba, "+ Gasto manual", resumen de tareas y gastos de hoy,
 * las próximas tareas y las últimas capturas. Cada resumen abre su pestaña.
 */
@Composable
fun HomeScreen(
    state: ServiceState,
    captures: List<Capture>,
    retrying: Set<String>,
    onRetry: (String) -> Unit,
    onAddManual: () -> Unit,
    onFixState: () -> Unit,
    modifier: Modifier = Modifier,
    shortcuts: List<Shortcut> = emptyList(),
    runningShortcuts: Set<String> = emptySet(),
    onRunShortcut: (Shortcut) -> Unit = {},
    onEditShortcut: (Shortcut) -> Unit = {},
    onAddShortcut: () -> Unit = {},
    tasks: List<SpurTask> = emptyList(),
    completingTasks: Set<String> = emptySet(),
    onTaskDone: (SpurTask) -> Unit = {},
    onTaskPriority: (SpurTask) -> Unit = {},
    onTaskEdit: (SpurTask) -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenGastos: () -> Unit = {},
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val today = captures.filter { isToday(it.at) }
    val errors = today.count { it.status == CaptureStatus.ERROR }
    val todayTotal = today.filter { it.status == CaptureStatus.OK }.sumOf { Money.signedValue(it.tx.amount, it.tx.type) }

    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spur", style = t.brand, color = c.fg, modifier = Modifier.weight(1f).semantics { heading() })
            StatusChip(state)
        }
        if (state != ServiceState.ACTIVE) {
            StateHint(state, onFixState)
        }

        Shortcuts(shortcuts, runningShortcuts, onRunShortcut, onEditShortcut, onAddShortcut)

        // Separado de los atajos por el espacio entre bloques: un toque en un atajo ya crea la tarjeta.
        OutlinePill("+ Gasto manual", onClick = onAddManual, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap)) {
            SummaryTile(
                overline = "TAREAS",
                value = "${tasks.size}",
                caption = if (tasks.isEmpty()) "Nada pendiente" else "pendiente${if (tasks.size == 1) "" else "s"}",
                bg = c.accentSoft,
                fg = c.accentText,
                onClick = onOpenTasks,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                overline = "GASTOS HOY",
                value = "${today.size}",
                caption = if (errors > 0) "$errors con error" else Money.signedTotal(todayTotal),
                bg = c.card,
                fg = c.muted,
                onClick = onOpenGastos,
                modifier = Modifier.weight(1f),
            )
        }

        if (tasks.isNotEmpty()) {
            Section("PRÓXIMAS TAREAS", action = if (tasks.size > 3) "Ver todas" else null, onAction = onOpenTasks) {
                tasks.take(3).forEach { task ->
                    TaskRow(task, busy = task.id in completingTasks, onDone = { onTaskDone(task) }, onTogglePriority = { onTaskPriority(task) }, onEdit = { onTaskEdit(task) })
                }
            }
        }

        Section("ÚLTIMAS CAPTURAS", action = if (captures.isNotEmpty()) "Ver gastos" else null, onAction = onOpenGastos) {
            val recent = captures.take(2)
            if (recent.isEmpty()) {
                Text(
                    "Aún no hay capturas. Cuando llegue una notificación de Nu aparecerá aquí.",
                    style = t.body,
                    color = c.muted,
                )
            }
            recent.forEach { cap ->
                CaptureCard(cap, onRetry = { onRetry(cap.id) }, retrying = cap.id in retrying)
            }
        }
    }
}

/** Título de sección (overline) con acción opcional a la derecha. */
@Composable
private fun Section(title: String, action: String?, onAction: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = KtpjTheme.type.overline, color = KtpjTheme.colors.muted, modifier = Modifier.weight(1f))
            if (action != null) OutlinePill(action, onClick = onAction)
        }
        content()
    }
}

/** Atajos en dos columnas, grandes: un toque crea la tarjeta, mantener presionado lo edita. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shortcuts(
    shortcuts: List<Shortcut>,
    running: Set<String>,
    onRun: (Shortcut) -> Unit,
    onEdit: (Shortcut) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACCESO RÁPIDO", style = KtpjTheme.type.overline, color = KtpjTheme.colors.muted, modifier = Modifier.weight(1f))
            OutlinePill("+ Atajo", onClick = onAdd)
        }
        if (shortcuts.isEmpty()) {
            Text(
                "Guarda gastos que se repiten (peajes, parqueadero) para crearlos con un toque.",
                style = KtpjTheme.type.secondary,
                color = KtpjTheme.colors.muted,
            )
            return@Column
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap),
            maxItemsInEachRow = 2,
        ) {
            shortcuts.forEach { s ->
                ShortcutButton(s, s.id in running, { onRun(s) }, { onEdit(s) }, Modifier.weight(1f))
            }
            // Con un número impar, el último botón no se estira a todo el ancho.
            if (shortcuts.size % 2 == 1) Box(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutButton(s: Shortcut, running: Boolean, onRun: () -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(KtpjShapes.card)
            .background(c.card)
            .combinedClickable(
                enabled = !running,
                role = Role.Button,
                onClickLabel = "Crear tarjeta",
                onLongClickLabel = "Editar atajo",
                onClick = onRun,
                onLongClick = onEdit,
            )
            .semantics { contentDescription = "Atajo ${s.name}, ${s.signedAmount ?: ""} pesos" }
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(s.name, style = t.rowTitle, color = c.fg, maxLines = 1)
        Text(
            if (running) "Guardando…" else "${s.signedAmount ?: ""}",
            style = t.amountList,
            color = if (s.type == "abono" && !running) c.green else c.fg,
            maxLines = 1,
        )
    }
}

/** Resumen tocable: overline, valor 26sp/700 y una línea de detalle. */
@Composable
private fun SummaryTile(
    overline: String,
    value: String,
    caption: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val t = KtpjTheme.type
    Column(
        modifier
            .clip(KtpjShapes.card)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(overline, style = t.overline, color = fg)
        Text(value, style = t.tileCount, color = KtpjTheme.colors.fg)
        Text(caption, style = t.caption, color = KtpjTheme.colors.muted, maxLines = 1)
    }
}

@Composable
private fun StateHint(state: ServiceState, onFix: () -> Unit) {
    val c = KtpjTheme.colors
    val (text, action) = when (state) {
        ServiceState.NO_ACCESS -> "Spur no tiene acceso a las notificaciones: no se están capturando movimientos." to "Dar acceso"
        else -> "No se pudo conectar con Neon. Las capturas quedan en el historial para reintentar." to "Revisar"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.accentSoft, KtpjShapes.card)
            .padding(KtpjDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, style = KtpjTheme.type.secondary, color = c.accentText, modifier = Modifier.weight(1f))
        OutlinePill(action, onClick = onFix)
    }
}

private fun isToday(at: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = at }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

internal fun sampleShortcuts() = listOf(
    Shortcut(name = "Peaje Andes", amount = 16100, merchant = "Peaje Andes", type = "cargo"),
    Shortcut(name = "Parqueadero", amount = 8000, merchant = null, type = "cargo"),
    Shortcut(name = "Almuerzo", amount = 22000, merchant = null, type = "cargo"),
)

internal fun sampleCaptures(): List<Capture> {
    val now = System.currentTimeMillis()
    return listOf(
        Capture("1", now, CaptureSource.NU, NuTransaction("Nu: \$45.900 en Rappi", "45.900", "Rappi", "cargo", ""), CaptureStatus.OK),
        Capture("2", now - 3_600_000, CaptureSource.NU, NuTransaction("Nu: +\$1.200.000 recibido", "1.200.000", null, "abono", ""), CaptureStatus.OK),
        Capture("3", now - 7_200_000, CaptureSource.NU, NuTransaction("Notificación Nu", null, null, "desconocido", ""), CaptureStatus.ERROR, "Timeout al conectar con Neon"),
    )
}

@Preview(name = "Inicio — Claro", widthDp = 360, heightDp = 900)
@Composable
private fun HomePreview() = KtpjTheme(ThemeMode.LIGHT) {
    HomeScreen(
        ServiceState.ACTIVE, sampleCaptures(), emptySet(), {}, {}, {},
        shortcuts = sampleShortcuts(),
        tasks = listOf(SpurTask("t1", "Revisar Gmail: qué pasó con la entrevista de Tata", null, null)),
    )
}

@Preview(name = "Inicio — Oscuro", widthDp = 360, heightDp = 900)
@Composable
private fun HomeDarkPreview() = KtpjTheme(ThemeMode.DARK) {
    HomeScreen(ServiceState.ACTIVE, sampleCaptures(), emptySet(), {}, {}, {})
}
