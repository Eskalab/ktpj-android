package co.eskala.bktpj_notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Tarea enviada desde Claude Code con el skill /spur (tarjeta del tablero "Spur" en Neon). */
data class SpurTask(
    val id: String,
    val title: String,
    val description: String?,
    val due: String?,
    val priority: Boolean = false,   // ★ prioritaria: recordatorio cada hora hasta completarla
    val app: String? = null,         // paquete de la app que abre la tarea (etiqueta "app:<paquete>")
    val project: TaskProject? = null, // épica (Project de KTPJ), opcional
    val key: String? = null,          // ID tipo Jira: LAV-3, o SP-12 sin épica
    val started: Boolean = false,     // "en curso"
    val pomos: Int? = null,           // pomodoros estimados (etiqueta "pomos:N")
)

/**
 * Épica (Project de KTPJ). [color] "#RRGGBB"; [prefix] de las IDs (LAV); [total]/[done] = tarjetas
 * no archivadas de la épica y cuántas están hechas (solo vienen al listar épicas).
 */
data class TaskProject(
    val id: String,
    val title: String,
    val color: String?,
    val prefix: String? = null,
    val total: Int = 0,
    val done: Int = 0,
    val paused: Boolean = false,   // en pausa: sus tareas no salen en Hoy ni recuerdan
)

/** Tarea visible en el día a día: su épica no está en pausa. */
val SpurTask.active: Boolean get() = project?.paused != true

/** Tarjeta de una épica: tarea de Spur (se puede actuar) o tarjeta de la web (solo lectura). */
data class EpicCard(
    val task: SpurTask,
    val done: Boolean,
    val fromSpur: Boolean,
    val where: String?,   // "KTPJ — Desarrollo · Hecho" para las de la web
)

/**
 * Tareas pendientes de Claude: se leen de Neon al abrir la app y cada ~15 min en segundo plano.
 * Se avisa con una notificación por cada tarea nueva, y las prioritarias (★) se recuerdan
 * cada hora (de 6:00 a.m. a 8:00 p.m.) hasta que se completan.
 */
object SpurTasks {

    private const val WORK_NAME = "spur_tasks_sync"
    private const val SEEN_FILE = "spur_tasks"
    private const val SEEN_KEY = "seen_ids"
    private const val REMIND_PREFIX = "remind_"
    // El worker corre cada ~15 min: con 55 min el recordatorio sale cada hora, no cada hora y cuarto.
    private const val REMIND_EVERY_MS = 55 * 60 * 1000L
    // Franja de recordatorios (hora del teléfono): de 6:00 a.m. a 8:00 p.m.; de noche no se insiste.
    private const val REMIND_FROM_HOUR = 6
    private const val REMIND_UNTIL_HOUR = 20

    /** ★ con fecha de hoy o ya vencida. Sin fecha o con fecha futura: no insiste. */
    private fun remindsToday(task: SpurTask): Boolean =
        task.due != null && (co.eskala.bktpj_notification.ui.components.isToday(task.due) ||
            co.eskala.bktpj_notification.ui.components.isOverdue(task.due))

    private fun inReminderHours(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h in REMIND_FROM_HOUR until REMIND_UNTIL_HOUR
    }

    private val _tasks = MutableStateFlow<List<SpurTask>>(emptyList())
    val tasks: StateFlow<List<SpurTask>> = _tasks.asStateFlow()

    /** Completadas hoy (sección "Terminadas hoy", tachadas). */
    private val _doneToday = MutableStateFlow<List<SpurTask>>(emptyList())
    val doneToday: StateFlow<List<SpurTask>> = _doneToday.asStateFlow()

    /** Inicio del día local expresado en UTC ("yyyy-MM-dd HH:mm:ss"), que es como la base guarda las fechas. */
    private fun startOfTodayUtc(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(cal.time)
    }

    suspend fun refreshDoneToday(ctx: Context) {
        NeonRepository.fetchTasksDoneSince(ctx, startOfTodayUtc()).getOrNull()?.let {
            OfflineCache.saveDoneToday(ctx, it)
            show(ctx, _tasks.value, it)
        }
    }

    // ── KTP-8: sin conexión. Los cambios van a la cola (Outbox) y se ven al instante; la cola los sube. ──

    @Volatile private var restored = false

    /** Épica para mostrar una tarea creada o movida sin red. */
    private fun projectOf(ctx: Context, id: String): TaskProject? =
        (_tasks.value + _doneToday.value).firstNotNullOfOrNull { t -> t.project?.takeIf { it.id == id } }
            ?: OfflineCache.project(ctx, id)

    /** Muestra [tasks]/[done] con los cambios aún sin subir aplicados encima. */
    private fun show(ctx: Context, tasks: List<SpurTask>, done: List<SpurTask>, ops: List<OutboxOp> = Outbox.pendingOps(ctx)) {
        val (t, d) = OutboxPolicy.applyToTasks(tasks, done, ops) { projectOf(ctx, it) }
        _tasks.value = t
        _doneToday.value = d
    }

    /** Abre con lo último guardado si todavía no se ha leído nada de la base. */
    fun restoreCache(ctx: Context) {
        if (restored) return
        restored = true
        if (_tasks.value.isEmpty() && _doneToday.value.isEmpty()) {
            show(ctx, OfflineCache.get(ctx).tasks.orEmpty(), OfflineCache.doneToday(ctx).orEmpty())
        }
        Habits.restoreCache(ctx)
    }

    /** Encola el cambio, lo aplica en pantalla y trata de subirlo ya. */
    private fun local(ctx: Context, op: OutboxOp) {
        Outbox.enqueue(ctx, op)
        show(ctx, _tasks.value, _doneToday.value, listOf(op))
        OfflineSync.kick(ctx)
    }

    suspend fun setDue(ctx: Context, id: String, due: String?): Result<Unit> {
        local(ctx, OutboxOp.SetDue(id, due))
        return Result.success(Unit)
    }

    /** Revisión periódica (15 min es el mínimo de Android) con red disponible. */
    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun sync(ctx: Context): Result<List<SpurTask>> {
        restoreCache(ctx)
        OfflineSync.flush(ctx)   // primero sube lo pendiente; así la lectura ya lo trae
        val r = NeonRepository.fetchClaudeTasks(ctx)
        if (r.isSuccess) Habits.sync(ctx)   // aparte: si falla (falta la migración), las tareas siguen
        OfflineSync.readFailed.value = OfflineSync.isNetworkError(r.exceptionOrNull())
        r.getOrNull()?.let { list ->
            OfflineCache.saveTasks(ctx, list)
            show(ctx, list, _doneToday.value)
            refreshDoneToday(ctx)
            val sp = ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE)
            val seen = sp.getStringSet(SEEN_KEY, emptySet()).orEmpty()
            val now = System.currentTimeMillis()
            val edit = sp.edit()
            list.forEach { task ->
                val last = sp.getLong(REMIND_PREFIX + task.id, 0L)
                when {
                    task.id !in seen -> {
                        SpurNotifications.showTask(ctx, task, reminder = false)
                        edit.putLong(REMIND_PREFIX + task.id, now)
                    }
                    // Recordatorio cada hora solo para ★ de hoy (o atrasadas): las de días siguientes esperan su día.
                    task.priority && task.active && remindsToday(task) && now - last >= REMIND_EVERY_MS && inReminderHours() -> {
                        SpurNotifications.showTask(ctx, task, reminder = true)
                        edit.putLong(REMIND_PREFIX + task.id, now)
                    }
                }
            }
            // Solo guardamos las pendientes: así los datos no crecen sin límite.
            val pending = list.map { it.id }.toSet()
            sp.all.keys.filter { it.startsWith(REMIND_PREFIX) && it.removePrefix(REMIND_PREFIX) !in pending }
                .forEach { edit.remove(it) }
            edit.putStringSet(SEEN_KEY, pending).apply()
        }
        return r
    }

    suspend fun markDone(ctx: Context, id: String): Result<Unit> {
        // Aparece tachada de inmediato; la cola la sube y la próxima lectura de la base la confirma.
        local(ctx, OutboxOp.CompleteTask(id, at = System.currentTimeMillis()))
        SpurNotifications.cancelTask(ctx, id)
        return Result.success(Unit)
    }

    /**
     * Crea una tarea desde el celular. Se marca como vista para no notificar algo que uno mismo
     * acaba de escribir; si es prioritaria, el primer recordatorio llega en una hora.
     */
    suspend fun create(ctx: Context, title: String, description: String?, due: String?, high: Boolean, app: String? = null, projectId: String? = null, pomos: Int? = null): Result<String> {
        // El id (UUID) se genera aquí y es el mismo que tendrá la tarjeta: el reenvío no duplica.
        val id = java.util.UUID.randomUUID().toString()
        val sp = ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE)
        val seen = sp.getStringSet(SEEN_KEY, emptySet()).orEmpty()
        sp.edit()
            .putStringSet(SEEN_KEY, seen + id)
            .putLong(REMIND_PREFIX + id, System.currentTimeMillis())
            .apply()
        local(ctx, OutboxOp.CreateTask(id, title, description, due, high, app, projectId, pomos))
        return Result.success(id)
    }

    /** Marca o quita la ★. El primer recordatorio llega una hora después de marcarla. */
    suspend fun setPriority(ctx: Context, id: String, high: Boolean): Result<Unit> {
        local(ctx, OutboxOp.SetPriority(id, high))
        ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE).edit()
            .putLong(REMIND_PREFIX + id, System.currentTimeMillis()).apply()
        return Result.success(Unit)
    }

    /** Guarda la edición. Si se acaba de marcar ★, el primer recordatorio llega en una hora. */
    suspend fun update(ctx: Context, id: String, title: String, description: String?, due: String?, high: Boolean, app: String?, project: TaskProject?, pomos: Int? = null): Result<Unit> {
        val before = _tasks.value.firstOrNull { it.id == id }
        // Cambió de épica: nuevo ID con el prefijo de la nueva (SP-7 → LAV-4), como en Jira; llega al sincronizar.
        val rekey = before != null && before.project?.id != project?.id
        local(ctx, OutboxOp.UpdateTask(id, title, description, due, high, app, project?.id, pomos, rekey))
        if (project != null) _tasks.value = _tasks.value.map { if (it.id == id) it.copy(project = project) else it }
        if (high && before?.priority != true) {
            ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE).edit()
                .putLong(REMIND_PREFIX + id, System.currentTimeMillis()).apply()
        }
        return Result.success(Unit)
    }

    /** Pausa o reanuda una épica. Al reanudar, [shiftDays] corre las fechas de sus pendientes. */
    suspend fun setEpicPaused(ctx: Context, projectId: String, paused: Boolean, shiftDays: Int = 0): Result<Unit> {
        val r = NeonRepository.setProjectPaused(ctx, projectId, paused)
            .mapCatching { if (!paused && shiftDays != 0) NeonRepository.shiftEpicDates(ctx, projectId, shiftDays).getOrThrow() }
        if (r.isSuccess) sync(ctx)
        return r
    }

    /** Empezar (en curso) o volver a pendiente. */
    suspend fun setStarted(ctx: Context, id: String, started: Boolean): Result<Unit> {
        local(ctx, OutboxOp.SetStarted(id, started))
        return Result.success(Unit)
    }

    class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            if (!Prefs.isConfigured(applicationContext)) return Result.success()
            DailySummary.arm(applicationContext)
            val ok = sync(applicationContext).isSuccess
            if (ok) DailySummary.catchUp(applicationContext)   // respaldo si la alarma de 7/21 se perdió
            return if (ok) Result.success() else Result.retry()
        }
    }
}
