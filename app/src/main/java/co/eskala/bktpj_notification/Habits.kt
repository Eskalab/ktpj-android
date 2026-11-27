package co.eskala.bktpj_notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Hábito (ej. "Leer 10 páginas"): una sola tarjeta que se repite y nunca se marca hecha.
 * [days] = días que toca (0 = domingo … 6 = sábado; vacío = todos). [checks] = día → cantidad hecha.
 */
data class Habit(
    val id: String,
    val title: String,
    val key: String?,
    val days: Set<Int>,
    val goal: Int?,
    val unit: String?,
    val remindAt: Int?,          // hora (0-23) para avisar si no se ha hecho
    val app: String?,
    val project: TaskProject?,
    val checks: Map<String, Int?>,
    val pauses: List<HabitPause> = emptyList(),   // historial de pausas (viaje, enfermo…)
) {
    fun scheduledOn(cal: Calendar): Boolean = days.isEmpty() || (cal.get(Calendar.DAY_OF_WEEK) - 1) in days
    fun doneOn(day: String): Boolean = day in checks
    val doneToday: Boolean get() = doneOn(HabitDates.today())

    /** En pausa ese día (YYYY-MM-DD). Una pausa vencida deja de aplicar sola. */
    fun isPausedOn(day: String): Boolean = pauses.any { it.covers(day) }
    fun pauseOn(day: String): HabitPause? = pauses.lastOrNull { it.covers(day) }
    val currentPause: HabitPause? get() = pauseOn(HabitDates.today())
    val pausedToday: Boolean get() = currentPause != null

    /** Toca ese día y no está en pausa. Los días en pausa cuentan como días que no tocan. */
    fun dueOn(cal: Calendar): Boolean = scheduledOn(cal) && !isPausedOn(HabitDates.iso(cal))
    val dueToday: Boolean get() = dueOn(Calendar.getInstance())
    val active: Boolean get() = project?.paused != true

    /** Meta corta: "10 páginas", "20 min"… */
    val goalLabel: String? get() = goal?.let { g -> if (unit.isNullOrBlank()) "$g" else "$g $unit" }

    /** Días seguidos cumplidos. Los días que no tocan no rompen la racha; hoy sin hacer todavía tampoco. */
    fun streak(now: Calendar = Calendar.getInstance()): Int {
        val cal = now.clone() as Calendar
        if (dueOn(cal) && !doneOn(HabitDates.iso(cal))) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        repeat(400) {
            val d = HabitDates.iso(cal)
            if (dueOn(cal)) { if (doneOn(d)) n++ else return n }
            else if (doneOn(d)) n++   // hecho un día que no tocaba: suma
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    /** Mejor racha dentro de lo leído (≈ últimos 6 meses). */
    fun bestStreak(now: Calendar = Calendar.getInstance()): Int {
        if (checks.isEmpty()) return 0
        val first = checks.keys.min()
        val cal = HabitDates.parse(first)
        val end = HabitDates.iso(now)
        var best = 0; var run = 0
        while (HabitDates.iso(cal) <= end) {
            val d = HabitDates.iso(cal)
            when {
                doneOn(d) -> { run++; best = maxOf(best, run) }
                dueOn(cal) && d != end -> run = 0
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return best
    }

    /** Este mes: días cumplidos / días que tocaban hasta hoy (sin los días en pausa). */
    fun monthRate(now: Calendar = Calendar.getInstance()): Pair<Int, Int> {
        val cal = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        var done = 0; var due = 0
        while (!cal.after(now)) {
            val d = HabitDates.iso(cal)
            if (dueOn(cal)) due++
            if (doneOn(d)) done++
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return done to due
    }
}

/**
 * Pausa de un hábito, del día [start] al día [end] inclusive (null = sin fecha fija), con motivo opcional
 * ("viaje", "enfermo", "descanso", "otro"). Se guarda como etiqueta de la tarjeta:
 * `habit_pause:2026-11-27:2026-12-05:viaje` (sin fin: `habit_pause:2026-11-27::viaje`).
 */
data class HabitPause(val start: String, val end: String?, val reason: String? = null) {
    fun covers(day: String): Boolean = day >= start && (end == null || day <= end)

    fun toLabel(): String = PREFIX + start + ":" + end.orEmpty() + ":" + reason.orEmpty()

    /** "Viaje", "Enfermo"… o null. */
    val reasonLabel: String? get() = reason?.let { r -> REASONS.firstOrNull { it.first == r }?.second ?: r.replaceFirstChar { it.uppercase() } }

    /** "⏸ En pausa hasta 5 dic · Viaje" / "⏸ En pausa · Viaje". */
    fun label(today: String = HabitDates.today()): String =
        "⏸ En pausa" + (end?.let { " hasta " + HabitDates.short(it, today) } ?: "") + (reasonLabel?.let { " · $it" } ?: "")

    companion object {
        const val PREFIX = "habit_pause:"
        val REASONS = listOf("viaje" to "Viaje", "enfermo" to "Enfermo", "descanso" to "Descanso", "otro" to "Otro")
        private val DAY = Regex("\\d{4}-\\d{2}-\\d{2}")

        /** Lee una etiqueta `habit_pause:…`; null si no es de pausa o está mal formada. */
        fun parse(label: String): HabitPause? {
            if (!label.startsWith(PREFIX)) return null
            val parts = label.removePrefix(PREFIX).split(':', limit = 3)
            val start = parts.getOrNull(0)?.takeIf { DAY.matches(it) } ?: return null
            val end = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            if (end != null && !DAY.matches(end)) return null
            val reason = parts.getOrNull(2)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            return HabitPause(start, end, reason)
        }

        /** Pausa nueva desde [today] hasta [end]: reemplaza la que cubra hoy, conserva el historial. */
        fun add(pauses: List<HabitPause>, today: String, end: String?, reason: String?): List<HabitPause> =
            pauses.filterNot { it.covers(today) || it.start > today } +
                HabitPause(today, end?.let { maxOf(it, today) }, reason?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && ':' !in it })

        /**
         * Reanudar hoy: la pausa que cubre hoy termina ayer (hoy ya toca). Si empezó hoy, se borra.
         * Las pausas pasadas quedan para calcular bien las rachas.
         */
        fun resume(pauses: List<HabitPause>, today: String): List<HabitPause> {
            val yesterday = HabitDates.iso(HabitDates.parse(today).apply { add(Calendar.DAY_OF_YEAR, -1) })
            return pauses.mapNotNull { p ->
                when {
                    p.start > today -> null
                    !p.covers(today) -> p
                    p.start > yesterday -> null
                    else -> p.copy(end = yesterday)
                }
            }
        }
    }
}

/** Lo que se escribe en el formulario del hábito. */
data class HabitDraft(
    val title: String,
    val days: Set<Int>,
    val goal: Int?,
    val unit: String?,
    val remindAt: Int?,
    val projectId: String?,
    val app: String? = null,
)

object HabitDates {
    private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun iso(cal: Calendar): String = synchronized(ISO) { ISO.format(cal.time) }
    fun today(): String = iso(Calendar.getInstance())
    fun parse(d: String): Calendar = Calendar.getInstance().apply { time = synchronized(ISO) { ISO.parse(d)!! } }
    fun daysAgo(n: Int): String = iso(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) })

    /** "Todos los días" / "L M X J V" / "Fines de semana". */
    fun label(days: Set<Int>): String = when {
        days.isEmpty() || days.size == 7 -> "Todos los días"
        days == setOf(1, 2, 3, 4, 5) -> "Entre semana"
        days == setOf(0, 6) -> "Fines de semana"
        else -> listOf(1, 2, 3, 4, 5, 6, 0).filter { it in days }.joinToString(" ") { LETTERS[it] }
    }
    val LETTERS = listOf("D", "L", "M", "X", "J", "V", "S")

    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** "5 dic" (con año si no es el de [today]: "5 dic 2027"). */
    fun short(day: String, today: String = today()): String {
        val cal = parse(day)
        val s = "${cal.get(Calendar.DAY_OF_MONTH)} ${MONTHS[cal.get(Calendar.MONTH)]}"
        return if (day.take(4) == today.take(4)) s else "$s ${cal.get(Calendar.YEAR)}"
    }

    fun plusDays(day: String, n: Int): String = iso(parse(day).apply { add(Calendar.DAY_OF_YEAR, n) })
}

/**
 * Hábitos de Spur: se leen junto con las tareas. Si la tabla habit_checks aún no existe (migración sin
 * aplicar), falla solo esta parte y las tareas siguen funcionando.
 */
object Habits {
    private const val PREFS = "spur_habits"
    private const val CHANNEL_ID = "spur_habits"
    private const val NOTIF_BASE = 9500

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    /** Error de la última lectura (p. ej. falta la migración), para mostrarlo en la pestaña Tareas. */
    val error = MutableStateFlow<String?>(null)

    suspend fun sync(ctx: Context): Result<List<Habit>> {
        val r = NeonRepository.fetchHabits(ctx, HabitDates.daysAgo(190))
        r.onSuccess { OfflineCache.saveHabits(ctx, it); show(ctx, it); error.value = null; remind(ctx, _habits.value) }
            .onFailure { error.value = if (OfflineSync.isNetworkError(it)) null else it.message }
        return r
    }

    // ── KTP-8: sin conexión. Marcar / desmarcar va a la cola y se ve al instante. ──

    private fun show(ctx: Context, list: List<Habit>, ops: List<OutboxOp> = Outbox.pendingOps(ctx)) {
        _habits.value = OutboxPolicy.applyToHabits(list, ops)
    }

    /** Lo último guardado, si aún no se ha leído nada (lo llama SpurTasks.restoreCache). */
    fun restoreCache(ctx: Context) {
        if (_habits.value.isEmpty()) OfflineCache.get(ctx).habits?.let { show(ctx, it) }
    }

    private fun local(ctx: Context, op: OutboxOp) {
        Outbox.enqueue(ctx, op)
        show(ctx, _habits.value, listOf(op))
        OfflineSync.kick(ctx)
    }

    suspend fun create(ctx: Context, d: HabitDraft): Result<String> =
        NeonRepository.insertHabit(ctx, d).also { if (it.isSuccess) sync(ctx) }

    suspend fun update(ctx: Context, id: String, d: HabitDraft): Result<Unit> =
        NeonRepository.updateHabit(ctx, id, d).also { if (it.isSuccess) sync(ctx) }

    suspend fun archive(ctx: Context, id: String): Result<Unit> =
        NeonRepository.archiveHabit(ctx, id).also { if (it.isSuccess) _habits.value = _habits.value.filterNot { h -> h.id == id } }

    /** Marca [day] (hoy por defecto). Se ve al instante; si falla, se revierte. */
    suspend fun check(ctx: Context, id: String, amount: Int?, day: String = HabitDates.today()): Result<Unit> {
        local(ctx, OutboxOp.CheckHabit(id, day, amount))
        if (day == HabitDates.today()) NotificationManagerCompat.from(ctx).cancel(notifId(id))
        return Result.success(Unit)
    }

    suspend fun uncheck(ctx: Context, id: String, day: String = HabitDates.today()): Result<Unit> {
        local(ctx, OutboxOp.UncheckHabit(id, day))
        return Result.success(Unit)
    }

    // ── Aviso: una vez al día, desde la hora elegida, solo si toca hoy y no se ha hecho ──

    private fun remind(ctx: Context, list: List<Habit>) {
        if (BuildConfig.IS_QA) return   // app de pruebas: no avisa hábitos (ya lo hace Spur)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val today = HabitDates.today()
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        list.forEach { h ->
            val at = h.remindAt ?: return@forEach
            if (!h.active || !h.dueToday || h.doneToday || hour < at) return@forEach
            if (sp.getString("reminded_" + h.id, null) == today) return@forEach
            show(ctx, h)
            sp.edit().putString("reminded_" + h.id, today).apply()
        }
    }

    private fun show(ctx: Context, h: Habit) {
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Hábitos", NotificationManager.IMPORTANCE_HIGH))
        val streak = h.streak()
        val text = if (streak > 0) "Llevas 🔥 $streak día${if (streak == 1) "" else "s"}. No la rompas hoy."
        else "Hoy es buen día para empezar la racha."
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF137A3A.toInt())
            .setContentTitle("↻ " + h.title + (h.goalLabel?.let { " · $it" } ?: ""))
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx, 0,
                    Intent(ctx, TableroActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .addAction(
                0, "✓ Hecho",
                PendingIntent.getBroadcast(
                    ctx, "habit:${h.id}".hashCode(),
                    Intent(ctx, ActionReceiver::class.java)
                        .setAction(ActionReceiver.ACTION_HABIT_DONE)
                        .putExtra(ActionReceiver.EXTRA_TASK_ID, h.id)
                        .putExtra(ActionReceiver.EXTRA_AMOUNT, h.goal ?: -1),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(ctx).notify(notifId(h.id), notif)
    }

    private fun notifId(id: String) = NOTIF_BASE + (id.hashCode() and 0x3FF)

    /** Pausa el hábito desde hoy hasta [end] (inclusive; null = sin fecha fija). Se ve al instante. */
    suspend fun pause(ctx: Context, id: String, end: String?, reason: String?): Result<Unit> {
        val r = setPauses(ctx, id) { HabitPause.add(it, HabitDates.today(), end, reason) }
        if (r.isSuccess) NotificationManagerCompat.from(ctx).cancel(notifId(id))
        return r
    }

    /** Termina la pausa actual: desde hoy vuelve a tocar. */
    suspend fun resume(ctx: Context, id: String): Result<Unit> =
        setPauses(ctx, id) { HabitPause.resume(it, HabitDates.today()) }

    private suspend fun setPauses(ctx: Context, id: String, change: (List<HabitPause>) -> List<HabitPause>): Result<Unit> {
        val before = _habits.value
        val h = before.firstOrNull { it.id == id } ?: return Result.failure(IllegalStateException("Hábito no encontrado"))
        val pauses = change(h.pauses)
        _habits.value = before.map { if (it.id == id) it.copy(pauses = pauses) else it }
        val r = NeonRepository.setHabitPauses(ctx, id, pauses.map { it.toLabel() })
        if (r.isFailure) _habits.value = before
        return r
    }

    @Suppress("unused") private fun now() = Date()
}
