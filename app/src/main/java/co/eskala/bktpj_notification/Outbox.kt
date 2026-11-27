package co.eskala.bktpj_notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * KTP-8 · Cambios hechos en el celular que todavía no llegan a la base. Cada uno es idempotente:
 * si se reenvía (porque se cortó la respuesta) no duplica la tarea ni el check.
 * Las tareas creadas aquí llevan desde el principio el UUID que tendrá la tarjeta en la base.
 */
sealed class OutboxOp {
    /** Qué se está cambiando ("task:<id>", "habit:<id>"): los cambios de una misma cosa van en orden. */
    abstract val entity: String

    /** Dos cambios con la misma clave: el nuevo reemplaza al viejo (gana el último). null = no se fusiona. */
    abstract val coalesceKey: String?

    data class CreateTask(
        val id: String, val title: String, val description: String?, val due: String?, val high: Boolean,
        val app: String?, val projectId: String?, val pomos: Int?,
    ) : OutboxOp() {
        override val entity get() = "task:$id"
        override val coalesceKey: String? get() = null
    }

    /** Edición completa. [rekey] = cambió de épica y hay que darle un ID con el nuevo prefijo. */
    data class UpdateTask(
        val id: String, val title: String, val description: String?, val due: String?, val high: Boolean,
        val app: String?, val projectId: String?, val pomos: Int?, val rekey: Boolean,
    ) : OutboxOp() {
        override val entity get() = "task:$id"
        override val coalesceKey get() = "update:$id"
    }

    data class CompleteTask(val id: String) : OutboxOp() {
        override val entity get() = "task:$id"
        override val coalesceKey get() = "done:$id"
    }

    data class SetStarted(val id: String, val started: Boolean) : OutboxOp() {
        override val entity get() = "task:$id"
        override val coalesceKey get() = "started:$id"
    }

    data class SetDue(val id: String, val due: String?) : OutboxOp() {
        override val entity get() = "task:$id"
        override val coalesceKey get() = "due:$id"
    }

    data class SetPriority(val id: String, val high: Boolean) : OutboxOp() {
        override val entity get() = "task:$id"
        override val coalesceKey get() = "prio:$id"
    }

    data class CheckHabit(val habitId: String, val day: String, val amount: Int?) : OutboxOp() {
        override val entity get() = "habit:$habitId"
        override val coalesceKey get() = "habit:$habitId:$day"
    }

    data class UncheckHabit(val habitId: String, val day: String) : OutboxOp() {
        override val entity get() = "habit:$habitId"
        override val coalesceKey get() = "habit:$habitId:$day"
    }

    /** Id de la tarea que toca (null si es un hábito). */
    val taskId: String?
        get() = when (this) {
            is CreateTask -> id; is UpdateTask -> id; is CompleteTask -> id
            is SetStarted -> id; is SetDue -> id; is SetPriority -> id
            is CheckHabit, is UncheckHabit -> null
        }

    /** Mismo cambio sobre otra tarea (cuando un id temporal se reemplaza por el real). */
    fun withTaskId(newId: String): OutboxOp = when (this) {
        is CreateTask -> copy(id = newId); is UpdateTask -> copy(id = newId); is CompleteTask -> copy(id = newId)
        is SetStarted -> copy(id = newId); is SetDue -> copy(id = newId); is SetPriority -> copy(id = newId)
        is CheckHabit, is UncheckHabit -> this
    }
}

data class OutboxEntry(
    val seq: Long,
    val op: OutboxOp,
    val createdAt: Long,
    val attempts: Int = 0,         // fallos del servidor (los de red no cuentan)
    val lastAttemptAt: Long = 0,
    val lastError: String? = null,
    val parked: Boolean = false,   // falló demasiadas veces: queda aparte hasta "Reintentar"
)

/** Reglas puras de la cola (probadas en OutboxPolicyTest). */
object OutboxPolicy {
    const val MAX_ATTEMPTS = 5
    const val BASE_BACKOFF_MS = 30_000L
    const val MAX_BACKOFF_MS = 15 * 60_000L

    /** Espera antes del siguiente intento tras [attempts] fallos: 30 s, 1, 2, 4, 8 min… hasta 15 min. */
    fun backoffMs(attempts: Int): Long {
        if (attempts <= 0) return 0
        val exp = BASE_BACKOFF_MS shl (attempts - 1).coerceAtMost(20)
        return exp.coerceAtMost(MAX_BACKOFF_MS)
    }

    fun readyAt(e: OutboxEntry): Long = if (e.attempts == 0) 0 else e.lastAttemptAt + backoffMs(e.attempts)

    /**
     * Agrega [op] al final. Si ya había uno con la misma clave, el viejo se quita (gana el último).
     * Una edición de una tarea que aún no se ha creado en la base se funde en la creación.
     */
    fun enqueue(entries: List<OutboxEntry>, op: OutboxOp, seq: Long, now: Long): List<OutboxEntry> {
        if (op is OutboxOp.UpdateTask) {
            val i = entries.indexOfFirst { (it.op as? OutboxOp.CreateTask)?.id == op.id }
            if (i >= 0) {
                val merged = OutboxOp.CreateTask(op.id, op.title, op.description, op.due, op.high, op.app, op.projectId, op.pomos)
                return entries.toMutableList().also { it[i] = it[i].copy(op = merged) }
            }
        }
        var rekey = (op as? OutboxOp.UpdateTask)?.rekey ?: false
        val kept = entries.filterNot { e ->
            val same = op.coalesceKey != null && e.op.coalesceKey == op.coalesceKey
            if (same) rekey = rekey || (e.op as? OutboxOp.UpdateTask)?.rekey == true
            same
        }
        val finalOp = if (op is OutboxOp.UpdateTask) op.copy(rekey = rekey) else op
        return kept + OutboxEntry(seq, finalOp, now)
    }

    /**
     * Siguiente cambio que se puede enviar, en orden. Un cambio espera si antes hay otro de la misma cosa
     * todavía pendiente (esperando su reintento) o si la creación de su tarea quedó aparte: sin la tarea
     * en la base no tiene sentido enviarlo. [skip] = cosas que ya fallaron en esta pasada.
     */
    fun next(entries: List<OutboxEntry>, now: Long, skip: Set<String> = emptySet()): OutboxEntry? {
        val blocked = HashSet<String>(skip)
        for (e in entries.sortedBy { it.seq }) {
            val entity = e.op.entity
            if (entity in blocked) continue
            if (e.parked) {
                if (e.op is OutboxOp.CreateTask) blocked += entity
                continue
            }
            if (readyAt(e) > now) { blocked += entity; continue }
            return e
        }
        return null
    }

    /** Falló en el servidor: un intento más y, al llegar a [MAX_ATTEMPTS], queda aparte. */
    fun afterFailure(e: OutboxEntry, now: Long, error: String?): OutboxEntry {
        val n = e.attempts + 1
        return e.copy(attempts = n, lastAttemptAt = now, lastError = error, parked = n >= MAX_ATTEMPTS)
    }

    /** Reemplaza un id temporal de tarea por el real en todos los cambios pendientes. */
    fun remap(entries: List<OutboxEntry>, from: String, to: String): List<OutboxEntry> =
        if (from == to) entries
        else entries.map { e -> if (e.op.taskId == from) e.copy(op = e.op.withTaskId(to)) else e }

    /** "Reintentar": los apartados vuelven a la cola desde cero. */
    fun retryParked(entries: List<OutboxEntry>): List<OutboxEntry> =
        entries.map { if (it.parked || it.attempts > 0) it.copy(parked = false, attempts = 0, lastAttemptAt = 0) else it }

    /** Descarta un cambio; si era la creación de una tarea, también todo lo que dependía de ella. */
    fun discard(entries: List<OutboxEntry>, seq: Long): List<OutboxEntry> {
        val e = entries.firstOrNull { it.seq == seq } ?: return entries
        return if (e.op is OutboxOp.CreateTask) entries.filterNot { it.op.entity == e.op.entity }
        else entries.filterNot { it.seq == seq }
    }

    fun pending(entries: List<OutboxEntry>) = entries.filterNot { it.parked }
    fun parked(entries: List<OutboxEntry>) = entries.filter { it.parked }

    // ── Vista local: los cambios pendientes se aplican encima de lo que se leyó (o de la caché) ──

    /**
     * Aplica [ops] en orden sobre las pendientes y las terminadas hoy. Es idempotente: aplicar dos veces
     * lo mismo da igual, así que sirve tanto tras leer la base como sobre la caché.
     */
    fun applyToTasks(
        tasks: List<SpurTask>,
        doneToday: List<SpurTask>,
        ops: List<OutboxOp>,
        project: (String) -> TaskProject?,
    ): Pair<List<SpurTask>, List<SpurTask>> {
        var open = tasks
        var done = doneToday
        fun edit(id: String, f: (SpurTask) -> SpurTask) { open = open.map { if (it.id == id) f(it) else it } }
        for (op in ops) when (op) {
            is OutboxOp.CreateTask -> if (open.none { it.id == op.id } && done.none { it.id == op.id }) {
                open = listOf(SpurTask(op.id, op.title, op.description, op.due, op.high, op.app,
                    op.projectId?.let(project), key = null, pomos = op.pomos)) + open
            }
            is OutboxOp.UpdateTask -> edit(op.id) {
                val p = op.projectId?.let { id -> it.project?.takeIf { p -> p.id == id } ?: project(id) }
                it.copy(title = op.title, description = op.description, due = op.due, priority = op.high,
                    app = op.app, project = p, pomos = op.pomos)
            }
            is OutboxOp.CompleteTask -> open.firstOrNull { it.id == op.id }?.let { t ->
                open = open.filterNot { it.id == op.id }
                done = listOf(t) + done.filterNot { it.id == op.id }
            }
            is OutboxOp.SetStarted -> edit(op.id) { it.copy(started = op.started) }
            is OutboxOp.SetDue -> edit(op.id) { it.copy(due = op.due) }
            is OutboxOp.SetPriority -> edit(op.id) { it.copy(priority = op.high) }
            is OutboxOp.CheckHabit, is OutboxOp.UncheckHabit -> Unit
        }
        return open.sortedByDescending { it.priority } to done
    }

    fun applyToHabits(habits: List<Habit>, ops: List<OutboxOp>): List<Habit> {
        var list = habits
        for (op in ops) when (op) {
            is OutboxOp.CheckHabit -> list = list.map { if (it.id == op.habitId) it.copy(checks = it.checks + (op.day to op.amount)) else it }
            is OutboxOp.UncheckHabit -> list = list.map { if (it.id == op.habitId) it.copy(checks = it.checks - op.day) else it }
            else -> Unit
        }
        return list
    }

    // ── Serialización (una línea por cambio) ──

    private fun b(v: Boolean) = if (v) "1" else "0"

    fun encodeOp(op: OutboxOp): String = when (op) {
        is OutboxOp.CreateTask -> OfflineCodec.record("create", op.id, op.title, op.description, op.due, b(op.high), op.app, op.projectId, op.pomos?.toString())
        is OutboxOp.UpdateTask -> OfflineCodec.record("update", op.id, op.title, op.description, op.due, b(op.high), op.app, op.projectId, op.pomos?.toString(), b(op.rekey))
        is OutboxOp.CompleteTask -> OfflineCodec.record("done", op.id)
        is OutboxOp.SetStarted -> OfflineCodec.record("started", op.id, b(op.started))
        is OutboxOp.SetDue -> OfflineCodec.record("due", op.id, op.due)
        is OutboxOp.SetPriority -> OfflineCodec.record("prio", op.id, b(op.high))
        is OutboxOp.CheckHabit -> OfflineCodec.record("check", op.habitId, op.day, op.amount?.toString())
        is OutboxOp.UncheckHabit -> OfflineCodec.record("uncheck", op.habitId, op.day)
    }

    /** null si la línea no se entiende (p. ej. de una versión futura): se ignora en vez de romper la cola. */
    fun decodeOp(s: String): OutboxOp? = runCatching {
        val f = OfflineCodec.parse(s)
        fun g(i: Int) = f.getOrNull(i)
        fun r(i: Int) = g(i)!!
        fun bool(i: Int) = g(i) == "1"
        when (f[0]) {
            "create" -> OutboxOp.CreateTask(r(1), r(2), g(3), g(4), bool(5), g(6), g(7), g(8)?.toIntOrNull())
            "update" -> OutboxOp.UpdateTask(r(1), r(2), g(3), g(4), bool(5), g(6), g(7), g(8)?.toIntOrNull(), bool(9))
            "done" -> OutboxOp.CompleteTask(r(1))
            "started" -> OutboxOp.SetStarted(r(1), bool(2))
            "due" -> OutboxOp.SetDue(r(1), g(2))
            "prio" -> OutboxOp.SetPriority(r(1), bool(2))
            "check" -> OutboxOp.CheckHabit(r(1), r(2), g(3)?.toIntOrNull())
            "uncheck" -> OutboxOp.UncheckHabit(r(1), r(2))
            else -> null
        }
    }.getOrNull()

    fun encode(e: OutboxEntry): String = OfflineCodec.record(
        e.seq.toString(), encodeOp(e.op), e.createdAt.toString(), e.attempts.toString(),
        e.lastAttemptAt.toString(), e.lastError, b(e.parked),
    )

    fun decode(line: String): OutboxEntry? = runCatching {
        val f = OfflineCodec.parse(line)
        OutboxEntry(
            seq = f[0]!!.toLong(),
            op = decodeOp(f[1]!!) ?: return null,
            createdAt = f[2]!!.toLong(),
            attempts = f[3]!!.toInt(),
            lastAttemptAt = f[4]!!.toLong(),
            lastError = f[5],
            parked = f[6] == "1",
        )
    }.getOrNull()

    fun encodeAll(entries: List<OutboxEntry>): String = entries.joinToString("\n") { encode(it) }

    fun decodeAll(text: String): List<OutboxEntry> =
        text.lineSequence().filter { it.isNotBlank() }.mapNotNull { decode(it) }.sortedBy { it.seq }.toList()
}

/** La cola en disco (filesDir/outbox.txt). Sobrevive a cerrar la app y a reiniciar el teléfono. */
object Outbox {
    private const val FILE = "outbox.txt"

    private val _entries = MutableStateFlow<List<OutboxEntry>>(emptyList())
    val entries: StateFlow<List<OutboxEntry>> = _entries.asStateFlow()

    @Volatile private var loaded = false

    fun load(ctx: Context): List<OutboxEntry> = synchronized(this) {
        if (!loaded) {
            _entries.value = runCatching { file(ctx).takeIf { it.exists() }?.readText()?.let(OutboxPolicy::decodeAll) }
                .getOrNull().orEmpty()
            loaded = true
        }
        _entries.value
    }

    fun enqueue(ctx: Context, op: OutboxOp) = update(ctx) { list ->
        val seq = (list.maxOfOrNull { it.seq } ?: 0L) + 1
        OutboxPolicy.enqueue(list, op, seq, System.currentTimeMillis())
    }

    /** Ya se envió: se quita (si mientras tanto lo reemplazó uno más nuevo, ya no está y no pasa nada). */
    fun remove(ctx: Context, seq: Long) = update(ctx) { list -> list.filterNot { it.seq == seq } }

    /** Guarda el fallo solo si el cambio sigue en la cola (no se resucita uno reemplazado). */
    fun replace(ctx: Context, entry: OutboxEntry) = update(ctx) { list -> list.map { if (it.seq == entry.seq) entry else it } }

    fun update(ctx: Context, f: (List<OutboxEntry>) -> List<OutboxEntry>) = synchronized(this) {
        val updated = f(load(ctx))
        _entries.value = updated
        write(file(ctx), updated)
    }

    /** Todos los cambios sin subir (también los apartados), para la vista local. */
    fun pendingOps(ctx: Context): List<OutboxOp> = load(ctx).map { it.op }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    // Escritura atómica: temporal + rename, como el historial de capturas.
    private fun write(f: File, list: List<OutboxEntry>) {
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(OutboxPolicy.encodeAll(list))
        tmp.renameTo(f)
    }
}
