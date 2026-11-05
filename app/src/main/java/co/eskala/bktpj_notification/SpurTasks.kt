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
    val project: TaskProject? = null, // proyecto de KTPJ (opcional)
)

/** Proyecto de KTPJ; [color] en formato "#RRGGBB". */
data class TaskProject(val id: String, val title: String, val color: String?)

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
        NeonRepository.fetchTasksDoneSince(ctx, startOfTodayUtc()).getOrNull()?.let { _doneToday.value = it }
    }

    suspend fun setDue(ctx: Context, id: String, due: String?): Result<Unit> {
        val r = NeonRepository.setTaskDue(ctx, id, due)
        if (r.isSuccess) _tasks.value = _tasks.value.map { if (it.id == id) it.copy(due = due) else it }
        return r
    }

    /** Revisión periódica (15 min es el mínimo de Android) con red disponible. */
    fun schedule(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun sync(ctx: Context): Result<List<SpurTask>> {
        val r = NeonRepository.fetchClaudeTasks(ctx)
        r.getOrNull()?.let { list ->
            _tasks.value = list
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
                    task.priority && now - last >= REMIND_EVERY_MS && inReminderHours() -> {
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
        val r = NeonRepository.markTaskDone(ctx, id)
        if (r.isSuccess) {
            val done = _tasks.value.firstOrNull { it.id == id }
            _tasks.value = _tasks.value.filterNot { it.id == id }
            // Aparece tachada de inmediato; la próxima lectura de la base la confirma.
            if (done != null) _doneToday.value = listOf(done) + _doneToday.value.filterNot { it.id == id }
            SpurNotifications.cancelTask(ctx, id)
        }
        return r
    }

    /**
     * Crea una tarea desde el celular. Se marca como vista para no notificar algo que uno mismo
     * acaba de escribir; si es prioritaria, el primer recordatorio llega en una hora.
     */
    suspend fun create(ctx: Context, title: String, description: String?, due: String?, high: Boolean, app: String? = null, projectId: String? = null): Result<String> {
        val r = NeonRepository.insertTask(ctx, title, description, due, high, app, projectId)
        r.getOrNull()?.let { id ->
            val sp = ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE)
            val seen = sp.getStringSet(SEEN_KEY, emptySet()).orEmpty()
            sp.edit()
                .putStringSet(SEEN_KEY, seen + id)
                .putLong(REMIND_PREFIX + id, System.currentTimeMillis())
                .apply()
            sync(ctx)
        }
        return r
    }

    /** Marca o quita la ★. El primer recordatorio llega una hora después de marcarla. */
    suspend fun setPriority(ctx: Context, id: String, high: Boolean): Result<Unit> {
        val r = NeonRepository.setTaskPriority(ctx, id, high)
        if (r.isSuccess) {
            _tasks.value = _tasks.value
                .map { if (it.id == id) it.copy(priority = high) else it }
                .sortedByDescending { it.priority }
            ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE).edit()
                .putLong(REMIND_PREFIX + id, System.currentTimeMillis()).apply()
        }
        return r
    }

    /** Guarda la edición. Si se acaba de marcar ★, el primer recordatorio llega en una hora. */
    suspend fun update(ctx: Context, id: String, title: String, description: String?, due: String?, high: Boolean, app: String?, project: TaskProject?): Result<Unit> {
        val before = _tasks.value.firstOrNull { it.id == id }
        val r = NeonRepository.updateTask(ctx, id, title, description, due, high, app, project?.id)
        if (r.isSuccess) {
            _tasks.value = _tasks.value
                .map { if (it.id == id) it.copy(title = title, description = description, due = due, priority = high, app = app, project = project) else it }
                .sortedByDescending { it.priority }
            if (high && before?.priority != true) {
                ctx.getSharedPreferences(SEEN_FILE, Context.MODE_PRIVATE).edit()
                    .putLong(REMIND_PREFIX + id, System.currentTimeMillis()).apply()
            }
        }
        return r
    }

    class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            if (!Prefs.isConfigured(applicationContext)) return Result.success()
            return if (sync(applicationContext).isSuccess) Result.success() else Result.retry()
        }
    }
}
