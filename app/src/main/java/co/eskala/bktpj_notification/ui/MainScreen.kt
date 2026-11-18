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
import co.eskala.bktpj_notification.active
import co.eskala.bktpj_notification.FixedStore
import co.eskala.bktpj_notification.Capture
import co.eskala.bktpj_notification.ui.balance.BalanceScreen
import co.eskala.bktpj_notification.EpicCard
import co.eskala.bktpj_notification.ui.tasks.EpicScreen
import co.eskala.bktpj_notification.ui.components.TaskDatePicker
import co.eskala.bktpj_notification.ui.components.UsedPrefixes
import co.eskala.bktpj_notification.DailySummary
import co.eskala.bktpj_notification.ui.summary.DaySummaryScreen
import co.eskala.bktpj_notification.ui.components.isToday
import co.eskala.bktpj_notification.ui.components.isOverdue
import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.Pomodoro
import co.eskala.bktpj_notification.ui.tasks.PomodoroScreen
import co.eskala.bktpj_notification.ui.tasks.PomodoroBar
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
private enum class Overlay { MANUAL, SHORTCUT, NEW_TASK, EDIT_TASK, SUMMARY, EPIC, BALANCE, POMODORO, NEW_HABIT, EDIT_HABIT, HABIT, DESTINATIONS, NEON }

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
    // Pomodoro de la tarea en curso (▶ Empezar). Se abre también desde su notificación.
    LaunchedEffect(Unit) { Pomodoro.load(ctx); co.eskala.bktpj_notification.PomoLog.load(ctx) }
    val pomo by Pomodoro.state.collectAsState()
    val pomoRequest by Pomodoro.openRequest.collectAsState()
    LaunchedEffect(pomoRequest) {
        if (pomoRequest) { Pomodoro.openRequest.value = false; if (Pomodoro.load(ctx) != null) overlay = Overlay.POMODORO }
    }
    var editingShortcutId by rememberSaveable { mutableStateOf<String?>(null) }   // null = atajo nuevo
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { CaptureLog.load(ctx); FixedStore.load(ctx) }
    val fixed by FixedStore.fixed.collectAsState()
    var newTaskDue by rememberSaveable { mutableStateOf<String?>(null) }   // "+ Tarea" de Inicio = para hoy
    fun toggleFixed(cap: Capture) {
        val on = FixedStore.keyOf(cap) !in fixed
        FixedStore.setFixed(ctx, cap, on)
        Toast.makeText(ctx, if (on) "${cap.label}: fijo (se aplica a todos sus movimientos)" else "${cap.label}: ya no es fijo", Toast.LENGTH_SHORT).show()
    }
    val captures by CaptureLog.captures.collectAsState()
    val retrying = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) { ShortcutStore.load(ctx) }
    // Tareas de Claude (/spur): se leen al abrir; el worker las revisa cada ~15 min en segundo plano.
    LaunchedEffect(Unit) { SpurTasks.sync(ctx) }
    val tasks by SpurTasks.tasks.collectAsState()
    // Hábitos: se leen junto con las tareas; sin los de épicas en pausa.
    val habits by co.eskala.bktpj_notification.Habits.habits.collectAsState()
    val habitsError by co.eskala.bktpj_notification.Habits.error.collectAsState()
    val activeHabits = habits.filter { it.active }
    var habitId by rememberSaveable { mutableStateOf<String?>(null) }
    fun startHabit(h: co.eskala.bktpj_notification.Habit) {
        Pomodoro.begin(ctx, h)
        overlay = Overlay.POMODORO
    }
    var habitDoneAsk by remember { mutableStateOf<co.eskala.bktpj_notification.Habit?>(null) }
    // Día a día (Inicio, Tareas, resumen): sin las tareas de épicas en pausa.
    val activeTasks = tasks.filter { it.active }
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
    // Épicas (Projects de KTPJ) para el formulario y "¿Qué sigue?"; se recargan al abrir cualquiera de los dos.
    var projects by remember { mutableStateOf<List<TaskProject>>(emptyList()) }
    val createProject: suspend (String, String) -> TaskProject? = { name, prefix ->
        val r = NeonRepository.insertProject(ctx, name, prefix)
        r.getOrNull()?.also { p -> projects = (projects + p).sortedBy { it.title.lowercase() } }
            ?: run {
                val msg = r.exceptionOrNull()?.message.orEmpty()
                val why = if ("projects_prefix_key" in msg || "duplicate" in msg) "el prefijo $prefix ya existe" else msg
                Toast.makeText(ctx, "No se pudo crear la épica: $why", Toast.LENGTH_LONG).show(); null
            }
    }
    fun loadProjects() {
        scope.launch {
            NeonRepository.fetchProjects(ctx).getOrNull()?.let { projects = it }
            NeonRepository.fetchUsedPrefixes(ctx).getOrNull()?.let { UsedPrefixes.boards = it }
        }
    }
    LaunchedEffect(overlay == Overlay.NEW_TASK || overlay == Overlay.EDIT_TASK || followUpOf != null || tab == Tab.TAREAS) {
        loadProjects()
    }

    // Épica abierta (pantalla con sus tareas). Se recarga cuando cambian las tareas.
    var epicId by rememberSaveable { mutableStateOf<String?>(null) }
    var epicCards by remember { mutableStateOf<List<EpicCard>?>(null) }
    fun openEpic(p: TaskProject) { epicId = p.id; epicCards = null; overlay = Overlay.EPIC }

    fun toggleStarted(task: SpurTask) {
        if (task.id in completingTasks) return
        completingTasks += task.id
        scope.launch {
            val starting = !task.started
            val r = SpurTasks.setStarted(ctx, task.id, starting)
            completingTasks -= task.id
            if (r.isSuccess) {
                if (starting) { Pomodoro.begin(ctx, task); overlay = Overlay.POMODORO }
                else Pomodoro.stop(ctx, task.id)
            }
            if (r.isFailure) Toast.makeText(ctx, "No se pudo cambiar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun completeTask(task: SpurTask) {
        if (task.id in completingTasks) return
        completingTasks += task.id
        scope.launch {
            val r = SpurTasks.markDone(ctx, task.id)
            completingTasks -= task.id
            if (r.isSuccess) Pomodoro.stop(ctx, task.id)
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
                !task.priority -> Toast.makeText(ctx, "★ Prioritaria: el día de su fecha te la recuerdo cada hora", Toast.LENGTH_SHORT).show()
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
        Overlay.HABIT -> {
            val h = habits.firstOrNull { it.id == habitId }
            if (h == null) { overlay = null; return }
            co.eskala.bktpj_notification.ui.tasks.HabitScreen(h, onBack = { overlay = null }, onEdit = { overlay = Overlay.EDIT_HABIT }, onStart = { startHabit(h) })
            return
        }

        Overlay.NEW_HABIT, Overlay.EDIT_HABIT -> {
            val original = if (overlay == Overlay.EDIT_HABIT) habits.firstOrNull { it.id == habitId } else null
            if (overlay == Overlay.EDIT_HABIT && original == null) { overlay = null; return }
            var saving by remember { mutableStateOf(false) }
            co.eskala.bktpj_notification.ui.tasks.NewHabitScreen(
                initial = original,
                projects = projects,
                onCreateProject = createProject,
                saving = saving,
                onSwitchToTask = if (original == null) ({ overlay = Overlay.NEW_TASK }) else null,
                onSave = { d ->
                    saving = true
                    scope.launch {
                        val r = if (original == null) co.eskala.bktpj_notification.Habits.create(ctx, d).map { }
                        else co.eskala.bktpj_notification.Habits.update(ctx, original.id, d)
                        saving = false
                        if (r.isSuccess) {
                            val err = co.eskala.bktpj_notification.Habits.error.value
                            Toast.makeText(
                                ctx,
                                if (err != null) "Hábito guardado, pero no se pueden leer los hábitos: falta aplicar la migración habit_checks en la base."
                                else if (original == null) "↻ Hábito creado" else "Hábito actualizado",
                                if (err != null) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                            ).show()
                            overlay = if (original == null) null else Overlay.HABIT
                            if (original == null) tab = Tab.TAREAS
                        } else Toast.makeText(ctx, "No se pudo guardar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                },
                onCancel = { overlay = if (original == null) null else Overlay.HABIT },
            )
            return
        }

        Overlay.POMODORO -> {
            val p = pomo
            if (p == null) { overlay = null; return }
            PomodoroScreen(
                state = p,
                onBack = { overlay = null },
                onTaskDone = {
                    if (p.habit) {
                        // Hábito: marca el día (pregunta cuánto si tiene meta con unidad) y cierra el pomodoro.
                        val h = habits.firstOrNull { it.id == p.taskId }
                        if (h != null && h.goal != null && !h.unit.isNullOrBlank()) habitDoneAsk = h
                        else {
                            Pomodoro.stop(ctx); overlay = null
                            if (h != null) scope.launch {
                                val r = co.eskala.bktpj_notification.Habits.check(ctx, h.id, h.goal)
                                Toast.makeText(ctx, if (r.isSuccess) "✓ ${h.title}: hecho por hoy" else "No se pudo marcar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val task = tasks.firstOrNull { it.id == p.taskId }
                        overlay = null
                        if (task != null) completeTask(task) else Pomodoro.stop(ctx)
                    }
                },
            )
            habitDoneAsk?.let { h ->
                co.eskala.bktpj_notification.ui.components.AmountDialog(
                    habit = h,
                    onSave = { amount ->
                        habitDoneAsk = null
                        Pomodoro.stop(ctx); overlay = null
                        scope.launch {
                            val r = co.eskala.bktpj_notification.Habits.check(ctx, h.id, amount)
                            Toast.makeText(ctx, if (r.isSuccess) "✓ ${h.title}: $amount ${h.unit} hoy" else "No se pudo marcar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { habitDoneAsk = null },
                )
            }
            return
        }

        Overlay.BALANCE -> {
            BalanceScreen(captures = captures, fixed = fixed, onBack = { overlay = null })
            return
        }

        Overlay.EPIC -> {
            val project = projects.firstOrNull { it.id == epicId }
                ?: (tasks + doneToday).firstNotNullOfOrNull { t -> t.project?.takeIf { it.id == epicId } }
            if (project == null) { overlay = null; return }
            LaunchedEffect(epicId, tasks, doneToday) {
                NeonRepository.fetchEpicCards(ctx, project.id).getOrNull()?.let { epicCards = it }
            }
            var pausing by remember { mutableStateOf(false) }
            var askResume by remember { mutableStateOf(false) }
            fun setPaused(paused: Boolean, shift: Int = 0) {
                pausing = true
                scope.launch {
                    val r = SpurTasks.setEpicPaused(ctx, project.id, paused, shift)
                    pausing = false
                    if (r.isSuccess) {
                        projects = projects.map { if (it.id == project.id) it.copy(paused = paused) else it }
                        Toast.makeText(
                            ctx,
                            if (paused) "⏸ ${project.title} en pausa: sus tareas no salen en Hoy ni te recuerdan"
                            else "▶ ${project.title} reanudada" + if (shift != 0) " (fechas corridas $shift días)" else "",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else Toast.makeText(ctx, "No se pudo: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
            if (askResume) {
                // Retomar: la tarea pendiente más próxima pasa a la fecha elegida y las demás se corren igual.
                val first = epicCards.orEmpty().filter { !it.done && it.fromSpur }.mapNotNull { it.task.due }.minOrNull()
                TaskDatePicker(
                    initial = null,
                    onPick = { picked ->
                        askResume = false
                        val shift = if (picked != null && first != null) daysBetween(first, picked) else 0
                        setPaused(false, shift)
                    },
                    onDismiss = { askResume = false },
                )
            }
            EpicScreen(
                pausing = pausing,
                onPause = { setPaused(true) },
                onResume = { askResume = true },
                project = project.copy(paused = projects.firstOrNull { it.id == project.id }?.paused ?: project.paused),
                cards = epicCards,
                completing = completingTasks.toSet(),
                onDone = ::completeTask,
                onToggleStarted = ::toggleStarted,
                onTogglePriority = ::togglePriority,
                onEdit = { editingTaskId = it.id; overlay = Overlay.EDIT_TASK },
                onBack = { overlay = null; loadProjects() },
            )
            followUpOf?.let { done ->
                NextTaskDialog(done = done, saving = creatingFollowUp, onCreate = ::createFollowUp, onDismiss = { followUpOf = null }, projects = projects, onCreateProject = createProject)
            }
            return
        }

        Overlay.SUMMARY -> {
            val kind = summaryKind?.let { runCatching { DailySummary.Kind.valueOf(it) }.getOrNull() }
            if (kind == null) { overlay = null; return }
            var postponing by remember { mutableStateOf(false) }
            LaunchedEffect(kind) { SpurTasks.sync(ctx) }
            DaySummaryScreen(
                kind = kind,
                pending = activeTasks.filter { it.due != null && isToday(it.due) }.sortedByDescending { it.priority },
                done = doneToday,
                overdue = activeTasks.count { it.due != null && isOverdue(it.due) },
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
                NextTaskDialog(done = done, saving = creatingFollowUp, onCreate = ::createFollowUp, onDismiss = { followUpOf = null }, projects = projects, onCreateProject = createProject)
            }
            return
        }

        Overlay.NEW_TASK -> {
            var saving by remember { mutableStateOf(false) }
            NewTaskScreen(
                onSwitchToHabit = { overlay = Overlay.NEW_HABIT },
                initialDue = newTaskDue,
                projects = projects,
                onCreateProject = createProject,
                saving = saving,
                onSave = { task ->
                    saving = true
                    scope.launch {
                        val r = SpurTasks.create(ctx, task.title, task.description, task.due, task.priority, task.app, task.projectId, task.pomos)
                        saving = false
                        if (r.isSuccess) {
                            Toast.makeText(ctx, if (task.priority) "★ Tarea creada: el día de su fecha te la recuerdo cada hora" else "Tarea creada", Toast.LENGTH_SHORT).show()
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
                onCreateProject = createProject,
                saving = saving,
                onSave = { task ->
                    saving = true
                    scope.launch {
                        val r = SpurTasks.update(
                            ctx, original.id, task.title, task.description, task.due, task.priority, task.app,
                            task.projectId?.let { id -> projects.firstOrNull { it.id == id } ?: original.project?.takeIf { it.id == id } },
                            task.pomos,
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
                    tasks = activeTasks,
                    completingTasks = completingTasks.toSet(),
                    onTaskDone = ::completeTask,
                    onTaskPriority = ::togglePriority,
                    onTaskEdit = { editingTaskId = it.id; overlay = Overlay.EDIT_TASK },
                    onTaskStarted = ::toggleStarted,
                    onOpenEpic = ::openEpic,
                    onAddTask = {
                        newTaskDue = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        overlay = Overlay.NEW_TASK
                    },
                    onOpenBalance = { overlay = Overlay.BALANCE },
                    fixed = fixed,
                    onOpenTasks = { tab = Tab.TAREAS },
                    onOpenGastos = { tab = Tab.GASTOS },
                    summaryBanner = remember(summarySeenTick, tab, overlay) {
                        DailySummary.currentKind()?.takeIf { !DailySummary.seenToday(ctx, it) }
                    },
                    onOpenSummary = ::openSummary,
                    habits = activeHabits,
                    onOpenHabit = { habitId = it.id; overlay = Overlay.HABIT },
                    onStartHabit = ::startHabit,
                )

                Tab.TAREAS -> TasksScreen(
                    tasks = activeTasks,
                    completing = completingTasks.toSet(),
                    refreshing = refreshingTasks,
                    onDone = ::completeTask,
                    onRefresh = ::refreshTasks,
                    onTogglePriority = ::togglePriority,
                    onAdd = { newTaskDue = null; overlay = Overlay.NEW_TASK },
                    onEdit = { editingTaskId = it.id; overlay = Overlay.EDIT_TASK },
                    onToggleStarted = ::toggleStarted,
                    epics = projects,
                    onOpenEpic = ::openEpic,
                    doneToday = doneToday,
                    habits = activeHabits,
                    habitsError = habitsError,
                    onOpenHabit = { habitId = it.id; overlay = Overlay.HABIT },
                    onAddHabit = { overlay = Overlay.NEW_HABIT },
                    onStartHabit = ::startHabit,
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
                    fixed = fixed,
                    onToggleFixed = ::toggleFixed,
                    onOpenBalance = { overlay = Overlay.BALANCE },
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
        pomo?.let { PomodoroBar(it, onOpen = { overlay = Overlay.POMODORO }) }
        BottomNav(tab, onSelect = { tab = it })
    }

    followUpOf?.let { done ->
        NextTaskDialog(
            done = done,
            saving = creatingFollowUp,
            onCreate = ::createFollowUp,
            onDismiss = { followUpOf = null },
            projects = projects,
                onCreateProject = createProject,
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

/** Días de [from] a [to] (YYYY-MM-DD). */
private fun daysBetween(from: String, to: String): Int {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    return Math.round((f.parse(to)!!.time - f.parse(from)!!.time) / 86_400_000.0).toInt()
}
