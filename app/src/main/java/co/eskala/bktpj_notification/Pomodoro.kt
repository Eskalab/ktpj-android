package co.eskala.bktpj_notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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

enum class PomoMode(val label: String) {
    FOCUS("Enfoque"),
    SHORT("Descanso"),
    LONG("Descanso largo");

    /** Minutos según Ajustes. */
    val minutes: Int get() = Pomodoro.config.value.minutes(this)
    val fullMs: Long get() = minutes * 60_000L
}

/** Duraciones del pomodoro (Ajustes). [longEvery] = cada cuántos enfoques toca descanso largo. */
data class PomoConfig(val focus: Int = 25, val short: Int = 5, val long: Int = 15, val longEvery: Int = 4) {
    fun minutes(m: PomoMode) = when (m) { PomoMode.FOCUS -> focus; PomoMode.SHORT -> short; PomoMode.LONG -> long }
}

/**
 * Pomodoro de una tarea. Mientras corre se guarda la hora de fin ([endsAt]); en pausa, lo que queda
 * ([remainingMs]). [round] = pomodoros de enfoque terminados (cada 4 toca descanso largo).
 */
data class PomoState(
    val taskId: String,
    val taskTitle: String,
    val taskKey: String?,
    val mode: PomoMode,
    val endsAt: Long?,
    val remainingMs: Long,
    val round: Int,
    val estimate: Int? = null,   // pomodoros estimados de la tarea
) {
    val running: Boolean get() = endsAt != null
    fun remaining(now: Long = System.currentTimeMillis()): Long =
        endsAt?.let { (it - now).coerceAtLeast(0) } ?: remainingMs
}

/**
 * Temporizador pomodoro ligado a "▶ Empezar". Sobrevive a cerrar la app: el estado vive en
 * SharedPreferences, una alarma exacta avisa al terminar y una notificación fija muestra la cuenta atrás.
 */
object Pomodoro {
    const val EXTRA_OPEN = "open_pomodoro"
    const val ACTION_END = "co.eskala.bktpj_notification.POMO_END"
    const val ACTION_PAUSE = "co.eskala.bktpj_notification.POMO_PAUSE"
    const val ACTION_RESUME = "co.eskala.bktpj_notification.POMO_RESUME"

    private const val PREFS = "spur_pomodoro"
    private const val CH_RUN = "spur_pomodoro_run"
    private const val CH_END = "spur_pomodoro_end"
    private const val NOTIF_RUN = 9401
    private const val NOTIF_END = 9402
    private const val COLOR = 0xFFCF3F0A.toInt()

    private const val COUNTS = "spur_pomodoro_counts"

    private val _config = MutableStateFlow(PomoConfig())
    val config: StateFlow<PomoConfig> = _config.asStateFlow()

    /** Pomodoros de enfoque terminados por tarea (solo en el celular). */
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    private val _state = MutableStateFlow<PomoState?>(null)
    val state: StateFlow<PomoState?> = _state.asStateFlow()
    @Volatile private var loaded = false

    /** Pedido de abrir la pantalla (notificación tocada). */
    val openRequest = MutableStateFlow(false)

    fun load(ctx: Context): PomoState? {
        if (!loaded) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _config.value = PomoConfig(
                focus = p.getInt("cfg_focus", 25), short = p.getInt("cfg_short", 5),
                long = p.getInt("cfg_long", 15), longEvery = p.getInt("cfg_every", 4),
            )
            _counts.value = ctx.getSharedPreferences(COUNTS, Context.MODE_PRIVATE).all
                .mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
            _state.value = p.getString("taskId", null)?.let { id ->
                PomoState(
                    taskId = id,
                    taskTitle = p.getString("title", "").orEmpty(),
                    taskKey = p.getString("key", null),
                    mode = runCatching { PomoMode.valueOf(p.getString("mode", "FOCUS")!!) }.getOrDefault(PomoMode.FOCUS),
                    endsAt = p.getLong("endsAt", 0L).takeIf { it > 0 },
                    remainingMs = p.getLong("remaining", PomoMode.FOCUS.fullMs),
                    round = p.getInt("round", 0),
                    estimate = p.getInt("estimate", 0).takeIf { it > 0 },
                )
            }
            loaded = true
        }
        return _state.value
    }

    /** "▶ Empezar": abre el pomodoro de la tarea y arranca el enfoque. Si ya era de esa tarea, sigue donde iba. */
    fun begin(ctx: Context, task: SpurTask) {
        load(ctx)
        val cur = _state.value
        if (cur != null && cur.taskId == task.id) {
            if (cur.estimate != task.pomos) set(ctx, cur.copy(estimate = task.pomos))
            if (!cur.running) start(ctx)
            return
        }
        set(ctx, PomoState(task.id, task.title, task.key, PomoMode.FOCUS, null, PomoMode.FOCUS.fullMs, 0, task.pomos))
        start(ctx)
    }

    fun start(ctx: Context) {
        val s = load(ctx) ?: return
        if (s.running) return
        val ms = if (s.remainingMs <= 0) s.mode.fullMs else s.remainingMs
        set(ctx, s.copy(endsAt = System.currentTimeMillis() + ms, remainingMs = ms))
    }

    fun pause(ctx: Context) {
        val s = load(ctx) ?: return
        if (!s.running) return
        set(ctx, s.copy(endsAt = null, remainingMs = s.remaining()))
    }

    /** Cambiar de modo a mano (pestañas): queda detenido con el tiempo completo. */
    fun select(ctx: Context, mode: PomoMode) {
        val s = load(ctx) ?: return
        set(ctx, s.copy(mode = mode, endsAt = null, remainingMs = mode.fullMs))
    }

    /** Vuelve a empezar el modo actual, detenido. */
    fun reset(ctx: Context) {
        val s = load(ctx) ?: return
        set(ctx, s.copy(endsAt = null, remainingMs = s.mode.fullMs))
    }

    /** Saltar al siguiente: un enfoque saltado cuenta como hecho (igual que al terminar). */
    fun skip(ctx: Context) {
        val s = load(ctx) ?: return
        if (s.mode == PomoMode.FOCUS) countDone(ctx, s.taskId)
        set(ctx, advance(s))
    }

    fun doneFor(taskId: String): Int = _counts.value[taskId] ?: 0

    private fun countDone(ctx: Context, taskId: String) {
        val n = doneFor(taskId) + 1
        _counts.value = _counts.value + (taskId to n)
        ctx.getSharedPreferences(COUNTS, Context.MODE_PRIVATE).edit().putInt(taskId, n).apply()
    }

    /** Ajustes: nuevas duraciones. Si el pomodoro está detenido con el tiempo completo, toma la nueva. */
    fun setConfig(ctx: Context, cfg: PomoConfig) {
        load(ctx)
        val s = _state.value
        val wasFull = s != null && !s.running && s.remainingMs == s.mode.fullMs
        _config.value = cfg
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("cfg_focus", cfg.focus).putInt("cfg_short", cfg.short)
            .putInt("cfg_long", cfg.long).putInt("cfg_every", cfg.longEvery).apply()
        if (s != null && wasFull) set(ctx, s.copy(remainingMs = s.mode.fullMs))
    }

    /** Llamado por la alarma o por la pantalla cuando el tiempo llega a cero. No hace nada si no tocaba. */
    fun finishIfDue(ctx: Context) {
        val s = load(ctx) ?: return
        val end = s.endsAt ?: return
        if (end > System.currentTimeMillis() + 500) return
        if (s.mode == PomoMode.FOCUS) countDone(ctx, s.taskId)
        val next = advance(s)
        set(ctx, next)
        notifyEnd(ctx, s, next)
    }

    /** Cerrar el pomodoro (tarea hecha, "● En curso" apagado o "Terminar"). */
    fun stop(ctx: Context, taskId: String? = null) {
        val s = load(ctx) ?: return
        if (taskId != null && s.taskId != taskId) return
        set(ctx, null)
        NotificationManagerCompat.from(ctx).cancel(NOTIF_END)
    }

    /** Tras reiniciar: rearma la alarma o cierra el ciclo si ya pasó. */
    fun rearm(ctx: Context) {
        val s = load(ctx) ?: return
        if (s.running && s.remaining() == 0L) finishIfDue(ctx) else sync(ctx, s)
    }

    private fun advance(s: PomoState): PomoState {
        val (mode, round) = if (s.mode == PomoMode.FOCUS) {
            val r = s.round + 1
            (if (r % _config.value.longEvery == 0) PomoMode.LONG else PomoMode.SHORT) to r
        } else PomoMode.FOCUS to s.round
        return s.copy(mode = mode, round = round, endsAt = null, remainingMs = mode.fullMs)
    }

    private fun set(ctx: Context, s: PomoState?) {
        _state.value = s
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        listOf("taskId", "title", "key", "mode", "endsAt", "remaining", "round", "estimate").forEach { e.remove(it) }
        if (s != null) {
            e.putString("taskId", s.taskId).putString("title", s.taskTitle).putString("key", s.taskKey)
                .putString("mode", s.mode.name).putLong("endsAt", s.endsAt ?: 0L)
                .putLong("remaining", s.remainingMs).putInt("round", s.round).putInt("estimate", s.estimate ?: 0)
        }
        e.apply()
        sync(ctx, s)
    }

    // ── Alarma + notificación fija ──

    private fun sync(ctx: Context, s: PomoState?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, ACTION_END)
        am.cancel(pi)
        val nm = NotificationManagerCompat.from(ctx)
        if (s == null) { nm.cancel(NOTIF_RUN); return }
        val end = s.endsAt
        if (end != null) {
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi)
        }
        postRun(ctx, s)
    }

    private fun postRun(ctx: Context, s: PomoState) {
        ensureChannels(ctx)
        val title = (s.taskKey?.let { "$it · " } ?: "") + s.taskTitle
        val b = NotificationCompat.Builder(ctx, CH_RUN)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR)
            .setContentTitle(title)
            .setContentIntent(openScreen(ctx))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        val end = s.endsAt
        if (end != null) {
            b.setContentText("${s.mode.label} · termina a las ${clock(end)}")
                .setWhen(end).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setOngoing(true)
                .addAction(0, "Pausar", broadcast(ctx, ACTION_PAUSE))
        } else {
            b.setContentText("${s.mode.label} · en pausa, quedan ${format(s.remainingMs)}")
                .setShowWhen(false)
                .setOngoing(false)
                .addAction(0, "Seguir", broadcast(ctx, ACTION_RESUME))
        }
        post(ctx, NOTIF_RUN, b.build())
    }

    private fun notifyEnd(ctx: Context, done: PomoState, next: PomoState) {
        ensureChannels(ctx)
        val title = if (done.mode == PomoMode.FOCUS) "🍅 Pomodoro ${doneFor(done.taskId)}${done.estimate?.let { " de $it" } ?: ""} listo" else "Descanso terminado"
        val text = if (done.mode == PomoMode.FOCUS) "Toma ${next.mode.minutes} minutos de ${next.mode.label.lowercase()}. Te lo ganaste."
        else "A enfocarse: ${done.taskTitle}"
        val notif = NotificationCompat.Builder(ctx, CH_END)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openScreen(ctx))
            .addAction(0, "Empezar ${next.mode.label.lowercase()}", broadcast(ctx, ACTION_RESUME))
            .setAutoCancel(true)
            .build()
        post(ctx, NOTIF_END, notif)
    }

    fun format(ms: Long): String {
        val total = ((ms + 999) / 1000).toInt()
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun clock(at: Long) = java.text.SimpleDateFormat("h:mm a", java.util.Locale("es", "CO")).format(java.util.Date(at))

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "Pomodoro en curso", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CH_END, "Pomodoro terminado", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) }
        )
    }

    private fun broadcast(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, action.hashCode(),
        Intent(ctx, PomodoroReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openScreen(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 9400,
        Intent(ctx, TableroActivity::class.java)
            .putExtra(EXTRA_OPEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(ctx: Context, id: Int, notif: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(ctx).notify(id, notif)
    }
}

/** Fin del tiempo (alarma) y acciones Pausar / Seguir de la notificación. */
class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            Pomodoro.ACTION_END -> Pomodoro.finishIfDue(ctx)
            Pomodoro.ACTION_PAUSE -> Pomodoro.pause(ctx)
            Pomodoro.ACTION_RESUME -> {
                NotificationManagerCompat.from(ctx).cancel(9402)
                Pomodoro.start(ctx)
            }
        }
    }
}
