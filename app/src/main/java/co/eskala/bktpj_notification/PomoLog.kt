package co.eskala.bktpj_notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Cómo terminó un enfoque: completo (sonó la alarma), saltado (⏭) o detenido (cerrar, tarea hecha, otro modo). */
enum class PomoEnd(val label: String) { COMPLETED("completo"), SKIPPED("saltado"), STOPPED("detenido") }

/**
 * Un enfoque registrado: [start] = cuando arrancó, [end] = cuando terminó, [focusedMs] = tiempo real
 * corriendo (sin las pausas). Solo enfoques; los descansos no se anotan.
 */
data class PomoSession(
    val id: String,
    val taskId: String,
    val title: String,
    val key: String?,
    val habit: Boolean,
    val start: Long,
    val end: Long,
    val focusedMs: Long,
    val status: PomoEnd,
) {
    val minutes: Int get() = ((focusedMs + 30_000) / 60_000).toInt()
}

/**
 * KTP-4: registro de inicio y fin de cada enfoque del pomodoro, por tarea o hábito. Vive en el celular
 * (pomodoro_log.json), como los contadores de pomodoros. El enfoque abierto se guarda en preferencias para
 * que sobreviva a cerrar la app o reiniciar.
 */
object PomoLog {
    private const val FILE = "pomodoro_log.json"
    private const val PREFS = "spur_pomodoro_log"
    private const val MAX = 2000
    /** Menos de un minuto enfocado no se anota (arranques por error). */
    private const val MIN_MS = 60_000L

    private val _sessions = MutableStateFlow<List<PomoSession>>(emptyList())
    val sessions: StateFlow<List<PomoSession>> = _sessions.asStateFlow()
    @Volatile private var loaded = false

    fun load(ctx: Context): List<PomoSession> {
        if (!loaded) {
            _sessions.value = runCatching {
                val f = File(ctx.filesDir, FILE)
                if (!f.exists()) emptyList() else JSONArray(f.readText()).let { a -> (0 until a.length()).map { fromJson(a.getJSONObject(it)) } }
            }.getOrDefault(emptyList())
            loaded = true
        }
        return _sessions.value
    }

    /** El enfoque empieza a correr (Empezar / Seguir). Abre el registro si no había uno. */
    fun run(ctx: Context, s: PomoState, now: Long = System.currentTimeMillis()) {
        if (s.mode != PomoMode.FOCUS) return
        val p = prefs(ctx)
        val e = p.edit()
        if (p.getString("taskId", null) != s.taskId) {
            e.putString("taskId", s.taskId).putString("title", s.taskTitle).putString("key", s.taskKey)
                .putBoolean("habit", s.habit).putLong("start", now).putLong("focused", 0L)
        }
        e.putLong("segStart", now).apply()
    }

    /** Pausa: suma el tramo corrido. */
    fun pause(ctx: Context, now: Long = System.currentTimeMillis()) {
        val p = prefs(ctx)
        val seg = p.getLong("segStart", 0L).takeIf { it > 0 } ?: return
        p.edit().putLong("focused", p.getLong("focused", 0L) + (now - seg)).putLong("segStart", 0L).apply()
    }

    /** Cierra el enfoque abierto (si lo hay) y lo anota si pasó del minuto. */
    fun close(ctx: Context, status: PomoEnd, now: Long = System.currentTimeMillis()) {
        load(ctx)
        val p = prefs(ctx)
        val taskId = p.getString("taskId", null) ?: return
        val seg = p.getLong("segStart", 0L)
        val focused = p.getLong("focused", 0L) + if (seg > 0) (now - seg).coerceAtLeast(0) else 0L
        val session = PomoSession(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            title = p.getString("title", "").orEmpty(),
            key = p.getString("key", null),
            habit = p.getBoolean("habit", false),
            start = p.getLong("start", now),
            end = now,
            focusedMs = focused,
            status = status,
        )
        p.edit().clear().apply()
        if (focused < MIN_MS) return
        val list = (_sessions.value + session).takeLast(MAX)
        _sessions.value = list
        save(ctx, list)
    }

    fun forTask(taskId: String): List<PomoSession> = _sessions.value.filter { it.taskId == taskId }

    /** Sesiones que empezaron entre [from] y [to] (ms). */
    fun between(from: Long, to: Long): List<PomoSession> = _sessions.value.filter { it.start in from until to }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(ctx: Context, list: List<PomoSession>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        runCatching {
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(File(ctx.filesDir, FILE))
        }
    }

    private fun toJson(s: PomoSession) = JSONObject()
        .put("id", s.id).put("taskId", s.taskId).put("title", s.title).put("key", s.key ?: JSONObject.NULL)
        .put("habit", s.habit).put("start", s.start).put("end", s.end).put("focusedMs", s.focusedMs)
        .put("status", s.status.name)

    private fun fromJson(o: JSONObject) = PomoSession(
        id = o.getString("id"),
        taskId = o.getString("taskId"),
        title = o.optString("title"),
        key = if (o.isNull("key")) null else o.optString("key"),
        habit = o.optBoolean("habit", false),
        start = o.getLong("start"),
        end = o.getLong("end"),
        focusedMs = o.getLong("focusedMs"),
        status = runCatching { PomoEnd.valueOf(o.getString("status")) }.getOrDefault(PomoEnd.STOPPED),
    )
}
