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

/** 07 Notificaciones del sistema: tarjeta creada / no se pudo guardar (con Reintentar y Descartar). */
object SpurNotifications {

    const val CHANNEL_ID = "nu_ktpj_actions"
    private const val NOTIF_CONFIRM = 9001
    private const val NOTIF_ERROR_BASE = 9100
    private const val NOTIF_TASK_BASE = 80000
    private const val TASK_CHANNEL_ID = "spur_tasks"

    // accent / green del tema claro: el sistema los usa para tintar el ícono pequeño.
    private const val COLOR_ACCENT = 0xFFCF3F0A.toInt()
    private const val COLOR_GREEN = 0xFF137A3A.toInt()

    fun ensureChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Nu Colombia → KTPJ", NotificationManager.IMPORTANCE_DEFAULT
        )
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Éxito: "Tarjeta creada · −45.900" / "Rappi → Inbox y Hoy". */
    fun showSaved(ctx: Context, c: Capture) {
        val title = c.signedAmount?.let { "Tarjeta creada · $it" } ?: "Tarjeta creada"
        val where = if (c.destinations == "Inbox + Hoy") "Inbox y Hoy" else "Inbox"
        val notif = base(ctx)
            .setContentTitle(title)
            .setContentText("${c.label} → $where")
            .setColor(COLOR_GREEN)
            .build()
        post(ctx, NOTIF_CONFIRM, notif)
    }

    /** Error: "No se pudo guardar" con acciones Reintentar (vía ActionReceiver) y Descartar. */
    fun showError(ctx: Context, c: Capture) {
        val id = errorId(c.id)
        val text = "${c.error ?: "Error desconocido"}. Quedó en el historial para reintentar."
        val notif = base(ctx)
            .setContentTitle("No se pudo guardar")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${c.label}${c.signedAmount?.let { " · $it" } ?: ""}\n$text"))
            .setColor(COLOR_ACCENT)
            .addAction(0, "Reintentar", action(ctx, ActionReceiver.ACTION_RETRY, c.id, id))
            .addAction(0, "Descartar", action(ctx, ActionReceiver.ACTION_DISMISS, c.id, id))
            .build()
        post(ctx, id, notif)
    }

    /**
     * Tarea desde Claude (/spur): "Nueva tarea" o, para las prioritarias, "★ Recordatorio" cada hora.
     * Tocarla abre Spur; la acción "Hecha" la completa sin abrir la app.
     */
    fun showTask(ctx: Context, task: SpurTask, reminder: Boolean) {
        if (BuildConfig.IS_QA) return   // app de pruebas: los avisos de tareas ya los da Spur
        ensureTaskChannel(ctx)
        val text = listOfNotNull(task.due?.let { "Vence $it" }, task.description).joinToString(" · ")
        val notif = NotificationCompat.Builder(ctx, TASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                when {
                    reminder -> "★ Pendiente · ${task.title}"
                    task.priority -> "★ Nueva tarea · ${task.title}"
                    else -> "Nueva tarea · ${task.title}"
                }
            )
            .apply { if (text.isNotEmpty()) setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)) }
            .setColor(COLOR_ACCENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Hecha", taskDone(ctx, task.id))
            .setAutoCancel(true)
            .build()
        post(ctx, taskId(task.id), notif)
    }

    private fun taskDone(ctx: Context, taskId: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            "task_done:$taskId".hashCode(),
            Intent(ctx, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_TASK_DONE)
                .putExtra(ActionReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun cancelTask(ctx: Context, taskId: String) = NotificationManagerCompat.from(ctx).cancel(taskId(taskId))

    private fun taskId(id: String) = NOTIF_TASK_BASE + (id.hashCode() and 0xFFFF)

    private fun ensureTaskChannel(ctx: Context) {
        val channel = NotificationChannel(TASK_CHANNEL_ID, "Tareas de Claude", NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun cancelError(ctx: Context, captureId: String) =
        NotificationManagerCompat.from(ctx).cancel(errorId(captureId))

    private fun errorId(captureId: String) = NOTIF_ERROR_BASE + (captureId.hashCode() and 0xFFFF)

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, TableroActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(ctx: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
    }

    private fun action(ctx: Context, action: String, captureId: String, notifId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            "$action:$captureId".hashCode(),
            Intent(ctx, ActionReceiver::class.java)
                .setAction(action)
                .putExtra(ActionReceiver.EXTRA_CAPTURE_ID, captureId)
                .putExtra(ActionReceiver.EXTRA_NOTIF_ID, notifId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun post(ctx: Context, id: Int, notif: android.app.Notification) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(ctx).notify(id, notif)
    }
}
