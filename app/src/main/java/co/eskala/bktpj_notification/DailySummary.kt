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
import co.eskala.bktpj_notification.ui.components.isOverdue
import co.eskala.bktpj_notification.ui.components.isToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Resumen diario por notificación:
 *  - 7:00 a.m. "Tu día" y 9:00 p.m. "Cierre del día". La notificación solo avisa; el resumen se ve
 *    en la app (ui/summary/DaySummaryScreen), con una frase del día.
 *
 * Se programa con AlarmManager (inexacta, permitida en reposo: sin permiso de alarmas exactas) y el
 * worker de 15 min hace de respaldo si la alarma se perdió (reinicio, ahorro de batería). Cada resumen
 * sale como máximo una vez por día.
 */
object DailySummary {

    enum class Kind(val hour: Int, val lastHour: Int, val requestCode: Int) {
        MORNING(7, 11, 7101),   // si a las 12 no ha salido, ya no tiene sentido
        EVENING(21, 23, 7121),
    }

    const val ACTION_FIRE = "co.eskala.bktpj_notification.DAILY_SUMMARY"
    const val EXTRA_KIND = "kind"
    const val EXTRA_OPEN_SUMMARY = "open_summary"

    private const val CHANNEL_ID = "spur_daily"
    private const val PREFS = "spur_daily"
    private const val NOTIF_MORNING = 9301
    private const val NOTIF_EVENING = 9302
    private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Frases (originales; se elige una por día para que no se repita todos los días igual) ──

    private val MORNING_LINES = listOf(
        "Un paso a la vez también es avanzar.",
        "Empieza por la que más pesa: lo demás se siente liviano.",
        "Hoy no tiene que ser perfecto, solo tiene que moverse.",
        "Lo que hagas hoy le ahorra peso a tu yo de mañana.",
        "Café, respira, y la primera tarea. Tú puedes.",
        "Pequeño pero hecho le gana a grande pero pendiente.",
        "Elige una ★ y dale con todo.",
        "Cada tarea cerrada es un hilo menos en la cabeza.",
        "Buen día para tachar cosas.",
        "No hay que hacerlo todo, hay que hacer lo importante.",
        "Arranca suave, termina fuerte.",
        "Lo difícil primero, la recompensa después.",
    )
    private val MORNING_FREE = listOf(
        "Día sin pendientes con fecha. ¿Qué te gustaría avanzar?",
        "Agenda despejada: buen momento para adelantar algo que vienes aplazando.",
        "Nada urgente hoy. Disfruta el espacio, o planea el siguiente paso.",
    )
    private val EVENING_ALL = listOf(
        "Día completo. Eso merece descanso de verdad.",
        "Todo tachado. Qué buena jornada.",
        "Cumpliste con lo de hoy. Desconecta con calma.",
        "Lista en cero. Mañana arrancas liviano.",
    )
    private val EVENING_SOME = listOf(
        "Avanzaste, y eso cuenta. Lo que falta puede esperar a mañana.",
        "Buen trabajo hoy. Descansa: mañana sigues con el resto.",
        "No todo, pero sí lo suficiente para sentirte bien.",
        "Cada ✓ de hoy es progreso real.",
    )
    private val EVENING_NONE = listOf(
        "Hay días así. Mañana es otra oportunidad.",
        "Hoy tocó otra cosa, y está bien. Mañana retomas.",
        "Descansa: las tareas siguen ahí y tú vuelves con energía.",
    )
    private val EVENING_FREE = listOf(
        "Día sin pendientes en la lista. Descansa bien.",
        "Hoy cerraste lo que había. Buenas noches.",
    )

    private fun <T> List<T>.ofToday(): T = this[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % size]

    // ── Programación ──

    /** Arma (o rearma) las dos alarmas. Idempotente: se llama al abrir la app y en cada sync. */
    fun arm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        Kind.entries.forEach { kind ->
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(kind.hour), firePending(ctx, kind))
        }
    }

    private fun nextAt(hour: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun firePending(ctx: Context, kind: Kind): PendingIntent = PendingIntent.getBroadcast(
        ctx, kind.requestCode,
        Intent(ctx, DailySummaryReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Respaldo desde el worker: si estamos en la ventana y aún no salió hoy, lo envía. */
    suspend fun catchUp(ctx: Context) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        Kind.entries.filter { hour in it.hour..it.lastHour && !sentToday(ctx, it) }.forEach { show(ctx, it) }
    }

    private fun today() = ISO.format(Date())
    private fun sentToday(ctx: Context, kind: Kind) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(kind.name, null) == today()
    private fun markSent(ctx: Context, kind: Kind) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(kind.name, today()).apply()

    // ── Contenido ──

    suspend fun show(ctx: Context, kind: Kind) {
        if (!Prefs.isConfigured(ctx) || sentToday(ctx, kind)) return
        // Datos frescos; si no hay red, usa lo último que se leyó.
        SpurTasks.sync(ctx)
        val today = todayTasks()
        // La notificación solo avisa; el resumen completo vive en la app (pantalla "Tu día" / "Cierre del día").
        val (title, text) = when (kind) {
            Kind.MORNING -> "☀️ Buenos días, tu día está listo" to when {
                today.isEmpty() -> "Hoy no tienes tareas con fecha · toca para verlo"
                else -> "${plural(today.size, "tarea")} para hoy" +
                    (today.count { it.priority }.takeIf { it > 0 }?.let { " · $it ★" } ?: "") + " · toca para verlo"
            }
            Kind.EVENING -> {
                val done = SpurTasks.doneToday.value.size
                "🌙 Tu cierre del día está listo" to when {
                    done + today.size == 0 -> "Toca para verlo"
                    today.isEmpty() -> "Terminaste todo lo de hoy ✓"
                    else -> "Terminaste $done de ${done + today.size} · toca para verlo"
                }
            }
        }
        val notif = base(ctx)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openSummary(ctx, kind))
            .build()
        post(ctx, if (kind == Kind.MORNING) NOTIF_MORNING else NOTIF_EVENING, notif)
        markSent(ctx, kind)
    }

    /** Pendientes con fecha de hoy, ★ primero. */
    fun todayTasks(): List<SpurTask> =
        SpurTasks.tasks.value.filter { it.due != null && isToday(it.due) }.sortedByDescending { it.priority }

    fun overdueCount(): Int = SpurTasks.tasks.value.count { it.due != null && isOverdue(it.due) }

    /** Frase del día según el momento y cómo va el día. */
    fun phrase(kind: Kind, pending: Int, done: Int): String = when (kind) {
        Kind.MORNING -> if (pending == 0) MORNING_FREE.ofToday() else MORNING_LINES.ofToday()
        Kind.EVENING -> when {
            pending + done == 0 -> EVENING_FREE.ofToday()
            pending == 0 -> EVENING_ALL.ofToday()
            done == 0 -> EVENING_NONE.ofToday()
            else -> EVENING_SOME.ofToday()
        }
    }

    /** Resumen vigente ahora (dentro de su ventana horaria), o null. */
    fun currentKind(): Kind? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return Kind.entries.firstOrNull { hour in it.hour..it.lastHour }
    }

    /** Ya se abrió hoy: el aviso de Inicio desaparece. */
    fun seenToday(ctx: Context, kind: Kind) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("seen_" + kind.name, null) == today()

    fun markSeen(ctx: Context, kind: Kind) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("seen_" + kind.name, today()).apply()
        NotificationManagerCompat.from(ctx).cancel(if (kind == Kind.MORNING) NOTIF_MORNING else NOTIF_EVENING)
    }

    /** Pedido de abrir un resumen (al tocar la notificación). */
    val openRequest = kotlinx.coroutines.flow.MutableStateFlow<Kind?>(null)

    /** Mueve las pendientes de hoy a mañana. Devuelve cuántas se movieron. */
    suspend fun postponeToday(ctx: Context): Int {
        val tomorrow = ISO.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)
        if (SpurTasks.tasks.value.isEmpty()) SpurTasks.sync(ctx)
        return todayTasks().count { SpurTasks.setDue(ctx, it.id, tomorrow).isSuccess }
    }

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    private fun base(ctx: Context) = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(0xFFCF3F0A.toInt())
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)

    private fun openSummary(ctx: Context, kind: Kind): PendingIntent = PendingIntent.getActivity(
        ctx, 7200 + kind.ordinal,
        Intent(ctx, TableroActivity::class.java)
            .putExtra(EXTRA_OPEN_SUMMARY, kind.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(ctx: Context, id: Int, notif: android.app.Notification) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Resumen diario", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tu día a las 7:00 a.m. y el cierre a las 9:00 p.m."
            }
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(ctx).notify(id, notif)
    }
}

/** Dispara el aviso del resumen (alarma) y rearma las alarmas. */
class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DailySummary.ACTION_FIRE -> {
                        val kind = runCatching { DailySummary.Kind.valueOf(intent.getStringExtra(DailySummary.EXTRA_KIND)!!) }.getOrNull()
                        if (kind != null) DailySummary.show(ctx, kind)
                    }
                }
            } finally {
                DailySummary.arm(ctx)
                pending.finish()
            }
        }
    }
}

/** Tras reiniciar el teléfono las alarmas se pierden: se vuelven a armar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) DailySummary.arm(context.applicationContext)
    }
}
