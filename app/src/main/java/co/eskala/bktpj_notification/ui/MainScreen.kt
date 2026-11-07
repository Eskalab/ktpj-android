package co.eskala.bktpj_notification.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import co.eskala.bktpj_notification.CaptureLog
import co.eskala.bktpj_notification.CaptureSource
import co.eskala.bktpj_notification.CaptureStatus
import co.eskala.bktpj_notification.CardSaver
import co.eskala.bktpj_notification.NeonRepository
import co.eskala.bktpj_notification.Prefs
import co.eskala.bktpj_notification.Shortcut
import co.eskala.bktpj_notification.ShortcutStore
import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.DailySummary
import co.eskala.bktpj_notification.ui.summary.DaySummaryScreen
import co.eskala.bktpj_notification.ui.components.isToday
import co.eskala.bktpj_notification.ui.components.isOverdue
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.SpurTasks
import co.eskala.bktpj_notification.ui.columns.ColumnPickerScreen
import co.eskala.bktpj_notification.ui.columns.ColumnsState
import co.eskala.bktpj_notification.ui.columns.DestinationPicks
import co.eskala.bktpj_notification.ui.components.ServiceState
import co.eskala.bktpj_notification.ui.gastos.DestinationCounts
import co.eskala.bktpj_notification.ui.gastos.GastosScreen
import co.eskala.bktpj_notification.ui.home.HomeScreen
import co.eskala.bktpj_notification.ui.manual.ManualEntryScreen
import co.eskala.bktpj_notification.ui.manual.ManualMode
import co.eskala.bktpj_notification.ui.onboarding.ConnectionStatus
import co.eskala.bktpj_notification.ui.onboarding.NeonConnectScreen
import co.eskala.bktpj_notification.ui.settings.SettingsScreen
import co.eskala.bktpj_notification.ui.tasks.NewTaskScreen
import co.eskala.bktpj_notification.ui.tasks.NextTaskDialog
import co.eskala.bktpj_notification.ui.tasks.TasksScreen
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { INICIO("Inicio"), TAREAS("Tareas"), GASTOS("Gastos"), AJUSTES("Ajustes") }

/** Pantallas que se abren encima de las pestañas. */
private enum class Overlay { MANUAL, SHORTCUT, NEW_TASK, EDIT_TASK, SUMMARY, DESTINATIONS, NEON }

/** App ya configurada: Inicio (dashboard) / Tareas / Gastos / Ajustes con navegación inferior. */
@Composable
fun MainScreen(
    hasListenerAccess: Boolean,
    onOpenAccessSettings: () -> Unit,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onLegacyConfig: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = KtpjTheme.colors

    var tab by rememberSaveable { mutableStateOf(Tab.INICIO) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // Resumen diario: se abre al tocar su notificación o el aviso de Inicio.
    var summaryKind by rememberSaveable { mutableStateOf<String?>(null) }
    var summarySeenTick by remember { mutableStateOf(0) }
    fun openSummary(kind: DailySummary.Kind) {
        summaryKind = kind.name
        overlay = Overlay.SUMMARY
        DailySummary.markSeen(ctx, kind)
        summarySeenTick++
    }
    val summaryRequest by DailySummary.openRequest.collectAsState()
    LaunchedEffect(summaryRequest) {
        summaryRequest?.let { openSummary(it); DailySummary.openRequest.value = null }
    }
    var editingShortcutId by rememberSaveable { mutableStateOf<String?>(null) }   // null = atajo nuevo
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { CaptureLog.load(ctx) }
    val captures by CaptureLog.captures.collectAsState()
    val retrying = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { ShortcutStore.load(ctx) }
    // Tareas de Claude (/spur): se leen al abrir; el worker las revisa cada ~15 min en segundo plano.
    LaunchedEffect(Unit) { SpurTasks.sync(ctx) }
    val tasks by SpurTasks.tasks.collectAsState()
    val doneToday by SpurTasks.doneToday.collectAsState()
    val completingTasks = remember { mutableStateListOf<String>() }
    var refreshingTasks by remember { mutableStateOf(false) }
    val shortcuts by ShortcutStore.shortcuts.collectAsState()
    val runningShortcuts = remember { mutableStateListOf<String>() }

    var inboxId by remember { mutableStateOf(Prefs.getColumnInbox(ctx)) }
    var hoyId by remember { mutableStateOf(Prefs.getColumnHoy(ctx)) }
    val hoyConfigured = hoyId.isNotEmpty() && hoyId != inboxId
    var counts by remember { mutableStateOf(DestinationCounts(null, null, hoyConfigured)) }

    var neonStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    var neonHost by remember { mutableStateOf(Prefs.getNeonHost(ctx)) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    fun testNeon() {
        testJob?.cancel()
        neonStatus = ConnectionStatus.Testing
        testJob = scope.launch {
            neonStatus = NeonRepository.checkConnection(Prefs.getNeonConfig(ctx)).fold(
                onSuccess = { ConnectionStatus.Ok(it.columnCount, it.millis) },
                onFailure = { ConnectionStatus.Error(it.message ?: "Error desconocido") },
            )
        }
    }

    fun loadCounts() {
        scope.launch {
            val r = NeonRepository.fetchColumnCounts(ctx, inboxId, hoyId)
            val map = r.getOrNull().orEmpty()
            counts = DestinationCounts(map[inboxId], if (hoyConfigured) map[hoyId] else null, hoyConfigured)
        }
    }

    fun retry(id: String) {
        if (id in retrying) return
        retrying += id
        scope.launch {
            val r = CardSaver.retry(ctx, id)
            retrying -= id
            if (r?.status == CaptureStatus.OK) loadCounts()
        }
    }

    // Tarea recién completada: abre "¿Qué sigue?" para continuar el hilo.
    var followUpOf by remember { mutableStateOf<SpurTask?>(null) }
    var creatingFollowUp by remember { mutableStateOf(false) }
    // Proyectos de KTPJ para el formulario y "¿Qué sigue?"; se recargan al abrir cualquiera de los dos.
    var projects by remember { mutableStateOf<List<TaskProject>>(emptyList()) }
    LaunchedEffect(overlay == Overlay.NEW_TASK || overlay == Overlay.EDIT_TASK || followUpOf != null) {
        NeonRepository.fetchProjects(ctx).getOrNull()?.let { projects = it }
    }

    fun completeTask(task: SpurTask) {
        if (task.id in completingTasks) return
        completingTasks += task.id
        scope.launch {
            val r = SpurTasks.markDone(ctx, task.id)
            completingTasks -= task.id
            if (r.isFailure) Toast.makeText(ctx, "No se pudo marcar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            else followUpOf = task
        }
    }

    fun createFollowUp(title: String, priority: Boolean, due: String?, projectId: String?) {
        if (creatingFollowUp) return
        creatingFollowUp = true
        scope.launch {
            val r = SpurTasks.create(ctx, title, null, due, priority, projectId = projectId)
            creatingFollowUp = false
            if (r.isSuccess) {
                followUpOf = null
                Toast.makeText(ctx, if (priority) "★ Siguiente tarea creada" else "Siguiente tarea creada", Toast.LENGTH_SHORT).show()
            } else {
                // El diálogo sigue abierto para no perder lo escrito.
                Toast.makeText(ctx, "No se pudo crear: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun togglePriority(task: SpurTask) {
        if (task.id in completingTasks) return
        completingTasks += task.id
        scope.launch {
            val r = SpurTasks.setPriority(ctx, task.id, !task.priority)
            completingTasks -= task.id
            when {
                r.isFailure -> Toast.makeText(ctx, "No se pudo cambiar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                !task.priority -> Toast.makeText(ctx, "★ Prioritaria: te la recuerdo cada hora", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshTasks() {
        if (refreshingTasks) return
        refreshingTasks = true
        scope.launch {
            val r = SpurTasks.sync(ctx)
            refreshingTasks = false
            if (r.isFailure) Toast.makeText(ctx, "No se pudieron leer las tareas: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun runShortcut(s: Shortcut) {
        if (s.id in runningShortcuts) return
        runningShortcuts += s.id
        scope.launch {
            val r = CardSaver.save(ctx, s.toTransaction(), CaptureSource.MANUAL, notify = false)
            runningShortcuts -= s.id
            if (r.status == CaptureStatus.OK) {
                Toast.makeText(ctx, "${s.name} · ${r.signedAmount ?: ""} → tarjeta creada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "No se pudo guardar: ${r.error}. Quedó en el historial.", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) { testNeon() }
    // Recarga los conteos cuando entra una captura nueva o cambian los destinos.
    LaunchedEffect(captures.firstOrNull()?.id, inboxId, hoyId) { loadCounts() }

    val state = when {
        !hasListenerAccess -> ServiceState.NO_ACCESS
        neonStatus is ConnectionStatus.Error -> ServiceState.OFFLINE
        else -> ServiceState.ACTIVE
    }

    BackHandler(enabled = overlay != null || tab != Tab.INICIO) {
        if (overlay != null) overlay = null else tab = Tab.INICIO
    }

    when (overlay) {
        Overlay.SUMMARY -> {
            val kind = summaryKind?.let { runCatching { DailySummary.Kind.valueOf(it) }.getOrNull() }
            if (kind == null) { overlay = null; return }
            var postponing by remember { mutableStateOf(false) }
            LaunchedEffect(kind) { SpurTasks.sync(ctx) }
            DaySummaryScreen(
                kind = kind,
                pending = tasks.filter { it.due != null && isToday(it.due) }.sortedByDescending { it.priority },
                done = doneToday,
                overdue = tasks.count { it.due != null && isOverdue(it.due) },
                completing = completingTasks.toSet(),
                postponing = postponing,
                onDone = ::completeTask,
                onPostpone = {
                    postponing = true
                    scope.launch {
                        val n = DailySummary.postponeToday(ctx)
                        postponing = false
                        Toast.makeText(ctx, "Listo: ${if (n == 1) "1 tarea pasó" else "$n tareas pasaron"} a mañana. A descansar 🌙", Toast.LENGTH_LONG).show()
                        overlay = null
                    }
                },
                onOpenTasks = { overlay = null; tab = Tab.TAREAS },
                onClose = { overlay = null },
            )
            followUpOf?.let { done ->
                NextTaskDialog(done = done, saving = creatingFollowUp, onCreate = ::createFollowUp, onDismiss = { followUpOf = null }, projects = projects)
            }
            return
        }

        Overlay.NEW_TASK -> {
            var saving by remember { mutableStateOf(false) }
            NewTaskScreen(
                projects = projects,
                saving = saving,
                onSave = { task ->
                    saving = true
                    scope.launch {
                        val r = SpurTasks.create(ctx, task.title, task.description, task.due, task.priority, task.app, task.projectId)
                        saving = false
                        if (r.isSuccess) {
                            Toast.makeText(ctx, if (task.priority) "★ Tarea creada: te la recuerdo cada hora" else "Tarea creada", Toast.LENGTH_SHORT).show()
                            overlay = null
                            tab = Tab.TAREAS
                        } else {
                            // Se queda en el formulario para no perder lo escrito.
                            Toast.makeText(ctx, "No se pudo crear: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onCancel = { overlay = null },
            )
            return
        }

        Overlay.EDIT_TASK -> {
            val original = tasks.firstOrNull { it.id == editingTaskId }
            if (original == null) { overlay = null; return }
            var saving by remember { mutableStateOf(false) }
            NewTaskScreen(
                initial = original,
                projects = projects,
                saving = saving,
                onSave = { task ->
                    saving = true
                    scope.launch {
                        val r = SpurTasks.update(
                            ctx, original.id, task.title, task.description, task.due, task.priority, task.app,
                            task.projectId?.let { id -> projects.firstOrNull { it.id == id } ?: original.project?.takeIf { it.id == id } },
                        )
                        saving = false
                        if (r.isSuccess) {
                            Toast.makeText(ctx, "Tarea actualizada", Toast.LENGTH_SHORT).show()
                            overlay = null
                        } else {
                            Toast.makeText(ctx, "No se pudo guardar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onCancel = { overlay = null },
            )
            return
        }

        Overlay.MANUAL -> {
            var saving by remember { mutableStateOf(false) }
            ManualEntryScreen(
                saving = saving,
                hoyConfigured = hoyConfigured,
                onSave = { tx, alsoShortcut ->
                    alsoShortcut?.let { ShortcutStore.put(ctx, it) }
                    saving = true
                    scope.launch {
                        val r = CardSaver.save(ctx, tx, CaptureSource.MANUAL, notify = false)
                        saving = false
                        if (r.status == CaptureStatus.OK) {
                            Toast.makeText(ctx, "Tarjeta creada · ${r.signedAmount ?: ""}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "No se pudo guardar: ${r.error}. Quedó en el historial.", Toast.LENGTH_LONG).show()
                        }
                        overlay = null
                        tab = Tab.INICIO
                    }
                },
                onCancel = { overlay = null },
            )
            return
        }

        Overlay.SHORTCUT -> {
            val existing = editingShortcutId?.let { ShortcutStore.get(ctx, it) }
            ManualEntryScreen(
                saving = false,
                hoyConfigured = hoyConfigured,
                onSave = { _, _ -> },
                onCancel = { overlay = null },
                mode = ManualMode.EditShortcut(existing),
                onSaveShortcut = {
                    ShortcutStore.put(ctx, it)
                    overlay = null
                },
                onDeleteShortcut = existing?.let { s ->
                    {
                        ShortcutStore.remove(ctx, s.id)
                        Toast.makeText(ctx, "Atajo «${s.name}» eliminado", Toast.LENGTH_SHORT).show()
                        overlay = null
                    }
                },
            )
            return
        }

        Overlay.DESTINATIONS -> {
            var columns by remember { mutableStateOf<ColumnsState>(ColumnsState.Loading) }
            val picks = remember { DestinationPicks(inboxId.ifEmpty { null }, hoyId.ifEmpty { null }) }
            fun loadColumns() {
                columns = ColumnsState.Loading
                scope.launch {
                    columns = NeonRepository.fetchColumnsWithCounts(ctx).fold(
                        onSuccess = { list ->
                            picks.retainOnly(list.map { it.id }.toSet())
                            ColumnsState.Loaded(list)
                        },
                        onFailure = { ColumnsState.Error(it.message ?: "Error desconocido") },
                    )
                }
            }
            LaunchedEffect(Unit) { loadColumns() }
            ColumnPickerScreen(
                step = null,
                columns = columns,
                picks = picks,
                confirmLabel = "Guardar",
                onRetry = ::loadColumns,
                onConfirm = {
                    val inbox = picks.inbox ?: return@ColumnPickerScreen
                    Prefs.setColumnInbox(ctx, inbox)
                    Prefs.setColumnHoy(ctx, picks.hoy ?: "")
                    inboxId = inbox
                    hoyId = picks.hoy ?: ""
                    overlay = null
                },
            )
            return
        }

        Overlay.NEON -> {
            var connectionString by remember { mutableStateOf(Prefs.getNeonConfig(ctx).connectionString) }
            var status by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
            var job by remember { mutableStateOf<Job?>(null) }
            fun test() {
                job?.cancel()
                val cfg = Prefs.parseConnectionString(connectionString)
                if (cfg == null) {
                    status = ConnectionStatus.Error("URL inválida — debe ser postgresql://usuario:contraseña@host/db")
                    return
                }
                status = ConnectionStatus.Testing
                job = scope.launch {
                    status = NeonRepository.checkConnection(cfg).fold(
                        onSuccess = { ConnectionStatus.Ok(it.columnCount, it.millis) },
                        onFailure = { ConnectionStatus.Error(it.message ?: "Error desconocido") },
                    )
                }
            }
            NeonConnectScreen(
                connectionString = connectionString,
                status = status,
                onConnectionStringChange = {
                    connectionString = it
                    job?.cancel()
                    status = ConnectionStatus.Idle
                },
                onTest = ::test,
                onContinue = {
                    Prefs.parseConnectionString(connectionString)?.let { Prefs.saveNeonConfig(ctx, it) }
                    neonHost = Prefs.getNeonHost(ctx)
                    neonStatus = status
                    overlay = null
                    loadCounts()
                },
                step = null,
                continueLabel = "Guardar",
            )
            return
        }

        null -> Unit
    }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.INICIO -> HomeScreen(
                    state = state,
                    captures = captures,
                    retrying = retrying.toSet(),
                    onRetry = ::retry,
                    onAddManual = { overlay = Overlay.MANUAL },
                    shortcuts = shortcuts,
                    runningShortcuts = runningShortcuts.toSet(),
                    onRunShortcut = ::runShortcut,
                    onEditShortcut = {
                        editingShortcutId = it.id
                        overlay = Overlay.SHORTCUT
                    },
                    onAddShortcut = {
                        editingShortcutId = null
                        overlay = Overlay.SHORTCUT
                    },
                    onFixState = { if (state == ServiceState.NO_ACCESS) onOpenAccessSettings() else tab = Tab.AJUSTES },
                    tasks = tasks,
                    completingTasks = completingTasks.toSet(),
                    onTaskDone = ::completeTask,
                    onTaskPriority = ::togglePriority,
                    onTaskEdit = { editingTaskId = it.id; overlay = Overlay.EDIT_TASK },
                    onOpenTasks = { tab = Tab.TAREAS },
                    onOpenGastos = { tab = Tab.GASTOS },
                    summaryBanner = remember(summarySeenTick, tab, overlay) {
                        DailySummary.currentKind()?.takeIf { !DailySummary.seenToday(ctx, it) }
                    },
                    onOpenSummary = ::openSummary,
                )

                Tab.TAREAS -> TasksScreen(
                    tasks = tasks,
                    completing = completingTasks.toSet(),
                    refreshing = refreshingTasks,
                    onDone = ::completeTask,
                    onRefresh = ::refreshTasks,
                    onTogglePriority = ::togglePriority,
                    onAdd = { overlay = Overlay.NEW_TASK },
                    onEdit = { editingTaskId = it.id; overlay = Overlay.EDIT_TASK },
                    doneToday = doneToday,
                    onSetDue = { task, due ->
                        scope.launch {
                            val r = SpurTasks.setDue(ctx, task.id, due)
                            if (r.isFailure) Toast.makeText(ctx, "No se pudo cambiar la fecha: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                )

                Tab.GASTOS -> GastosScreen(
                    captures = captures,
                    counts = counts,
                    retrying = retrying.toSet(),
                    onRetry = ::retry,
                    onAddManual = { overlay = Overlay.MANUAL },
                )

                Tab.AJUSTES -> SettingsScreen(
                    neonHost = neonHost,
                    neonStatus = neonStatus,
                    onTestNeon = ::testNeon,
                    onEditNeon = { overlay = Overlay.NEON },
                    inboxTitle = counts.inbox?.title ?: inboxId.ifEmpty { null },
                    hoyTitle = if (hoyConfigured) counts.hoy?.title ?: hoyId else null,
                    onEditDestinations = { overlay = Overlay.DESTINATIONS },
                    sourcePackage = Prefs.getNuPackage(ctx),
                    onSavePackage = { Prefs.setNuPackage(ctx, it) },
                    hasListenerAccess = hasListenerAccess,
                    onOpenAccessSettings = onOpenAccessSettings,
                    theme = theme,
                    onThemeChange = onThemeChange,
                    onLegacyConfig = onLegacyConfig,
                )
            }
        }
        BottomNav(tab, onSelect = { tab = it })
    }

    followUpOf?.let { done ->
        NextTaskDialog(
            done = done,
            saving = creatingFollowUp,
            onCreate = ::createFollowUp,
            onDismiss = { followUpOf = null },
            projects = projects,
        )
    }
}

/** Bottom nav: 64dp, 3 ítems de texto 14sp/700, activo fg, inactivo muted, borde superior `line`. */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val c = KtpjTheme.colors
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(KtpjDimens.divider).background(c.line))
        Row(Modifier.fillMaxWidth().height(KtpjDimens.bottomNavHeight)) {
            Tab.entries.forEach { t ->
                val active = t == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = active }
                        .clickable(role = Role.Tab, onClick = { onSelect(t) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.label, style = KtpjTheme.type.navItem, color = if (active) c.fg else c.muted)
                }
            }
        }
    }
}
