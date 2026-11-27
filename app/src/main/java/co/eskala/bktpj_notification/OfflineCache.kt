package co.eskala.bktpj_notification

import android.content.Context
import java.io.File

/**
 * KTP-8 · Lo último que se leyó bien de la base (tareas, terminadas hoy, épicas y hábitos), para abrir la
 * app sin red. Se guarda cada vez que una lectura sale bien; los cambios sin subir se aplican encima
 * desde la cola ([OutboxPolicy.applyToTasks]).
 */
data class CacheSnapshot(
    val tasks: List<SpurTask>? = null,
    val doneDay: String? = null,            // día (YYYY-MM-DD) de doneToday: otro día ya no vale
    val doneToday: List<SpurTask>? = null,
    val projects: List<TaskProject>? = null,
    val habits: List<Habit>? = null,
)

/** Serialización pura de la caché (probada en OfflineCacheTest). */
object CacheCodec {
    private fun b(v: Boolean) = if (v) "1" else "0"

    fun project(p: TaskProject): String = OfflineCodec.record(
        p.id, p.title, p.color, p.prefix, p.total.toString(), p.done.toString(), b(p.paused),
    )

    fun parseProject(s: String): TaskProject {
        val f = OfflineCodec.parse(s)
        return TaskProject(f[0]!!, f[1].orEmpty(), f[2], f[3], f[4]?.toIntOrNull() ?: 0, f[5]?.toIntOrNull() ?: 0, f[6] == "1")
    }

    fun task(t: SpurTask): String = OfflineCodec.record(
        t.id, t.title, t.description, t.due, b(t.priority), t.app, t.project?.let(::project), t.key,
        b(t.started), t.pomos?.toString(),
    )

    fun parseTask(s: String): SpurTask {
        val f = OfflineCodec.parse(s)
        return SpurTask(
            id = f[0]!!, title = f[1].orEmpty(), description = f[2], due = f[3], priority = f[4] == "1",
            app = f[5], project = f[6]?.let(::parseProject), key = f[7], started = f[8] == "1",
            pomos = f.getOrNull(9)?.toIntOrNull(),
        )
    }

    fun habit(h: Habit): String = OfflineCodec.record(
        h.id, h.title, h.key, h.days.sorted().joinToString(","), h.goal?.toString(), h.unit,
        h.remindAt?.toString(), h.app, h.project?.let(::project),
        OfflineCodec.list(h.checks.map { (d, a) -> OfflineCodec.record(d, a?.toString()) }),
    )

    fun parseHabit(s: String): Habit {
        val f = OfflineCodec.parse(s)
        return Habit(
            id = f[0]!!, title = f[1].orEmpty(), key = f[2],
            days = f[3].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
            goal = f[4]?.toIntOrNull(), unit = f[5], remindAt = f[6]?.toIntOrNull(), app = f[7],
            project = f[8]?.let(::parseProject),
            checks = OfflineCodec.parseList(f[9]).associate { r -> OfflineCodec.parse(r).let { it[0]!! to it.getOrNull(1)?.toIntOrNull() } },
        )
    }

    fun encode(c: CacheSnapshot): String = OfflineCodec.record(
        "v1",
        c.tasks?.let { OfflineCodec.list(it.map(::task)) },
        c.doneDay,
        c.doneToday?.let { OfflineCodec.list(it.map(::task)) },
        c.projects?.let { OfflineCodec.list(it.map(::project)) },
        c.habits?.let { OfflineCodec.list(it.map(::habit)) },
    )

    fun decode(s: String): CacheSnapshot? = runCatching {
        val f = OfflineCodec.parse(s)
        if (f[0] != "v1") return null
        CacheSnapshot(
            tasks = f[1]?.let { OfflineCodec.parseList(it).map(::parseTask) },
            doneDay = f[2],
            doneToday = f[3]?.let { OfflineCodec.parseList(it).map(::parseTask) },
            projects = f[4]?.let { OfflineCodec.parseList(it).map(::parseProject) },
            habits = f[5]?.let { OfflineCodec.parseList(it).map(::parseHabit) },
        )
    }.getOrNull()
}

/** La caché en disco (filesDir/offline_cache.txt). */
object OfflineCache {
    private const val FILE = "offline_cache.txt"

    @Volatile private var snapshot: CacheSnapshot? = null

    fun get(ctx: Context): CacheSnapshot = synchronized(this) {
        snapshot ?: (runCatching { file(ctx).takeIf { it.exists() }?.readText()?.let(CacheCodec::decode) }.getOrNull()
            ?: CacheSnapshot()).also { snapshot = it }
    }

    fun saveTasks(ctx: Context, tasks: List<SpurTask>) = update(ctx) { it.copy(tasks = tasks) }
    fun saveDoneToday(ctx: Context, done: List<SpurTask>) = update(ctx) { it.copy(doneDay = HabitDates.today(), doneToday = done) }
    fun saveProjects(ctx: Context, projects: List<TaskProject>) = update(ctx) { it.copy(projects = projects) }
    fun saveHabits(ctx: Context, habits: List<Habit>) = update(ctx) { it.copy(habits = habits) }

    /** Terminadas hoy guardadas, solo si son de hoy. */
    fun doneToday(ctx: Context): List<SpurTask>? = get(ctx).takeIf { it.doneDay == HabitDates.today() }?.doneToday

    /** Épica por id (para mostrar una tarea creada sin red con su épica). */
    fun project(ctx: Context, id: String): TaskProject? = get(ctx).projects?.firstOrNull { it.id == id }

    private fun update(ctx: Context, f: (CacheSnapshot) -> CacheSnapshot) = synchronized(this) {
        val next = f(get(ctx))
        snapshot = next
        OfflineFiles.writeAtomic(file(ctx), CacheCodec.encode(next))
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
}
