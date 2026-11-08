package co.eskala.bktpj_notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INBOX = "co.eskala.bktpj_notification.ACTION_INBOX"
        const val ACTION_HOY   = "co.eskala.bktpj_notification.ACTION_HOY"
        const val ACTION_RETRY   = "co.eskala.bktpj_notification.ACTION_RETRY"
        const val ACTION_DISMISS = "co.eskala.bktpj_notification.ACTION_DISMISS"
        const val ACTION_TASK_DONE = "co.eskala.bktpj_notification.ACTION_TASK_DONE"
        const val ACTION_HABIT_DONE = "co.eskala.bktpj_notification.ACTION_HABIT_DONE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TITLE     = "extra_title"
        const val EXTRA_AMOUNT    = "extra_amount"
        const val EXTRA_MERCHANT  = "extra_merchant"
        const val EXTRA_TYPE      = "extra_type"
        const val EXTRA_RAW_TEXT  = "extra_raw_text"
        const val EXTRA_NOTIF_ID  = "extra_notif_id"
        const val EXTRA_CAPTURE_ID = "extra_capture_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RETRY -> return retry(context, intent)
            ACTION_TASK_DONE -> return taskDone(context, intent)
            ACTION_HABIT_DONE -> return habitDone(context, intent)
            ACTION_DISMISS -> {
                // Solo cierra la notificación: la captura sigue en el historial para reintentar después.
                cancel(context, intent)
                return
            }
        }

        val columnId = when (intent.action) {
            ACTION_HOY -> Prefs.getColumnHoy(context)
            else       -> Prefs.getColumnInbox(context)
        }

        if (columnId.isEmpty()) {
            Toast.makeText(context, "Abre Spur y configura los IDs de columna", Toast.LENGTH_LONG).show()
            return
        }

        val tx = NuTransaction(
            title    = intent.getStringExtra(EXTRA_TITLE)    ?: "Notificación Nu",
            amount   = intent.getStringExtra(EXTRA_AMOUNT),
            merchant = intent.getStringExtra(EXTRA_MERCHANT),
            type     = intent.getStringExtra(EXTRA_TYPE)     ?: "desconocido",
            rawText  = intent.getStringExtra(EXTRA_RAW_TEXT) ?: ""
        )

        cancel(context, intent)

        val label = if (intent.action == ACTION_HOY) "Hoy" else "Inbox"

        CoroutineScope(Dispatchers.Main).launch {
            val result = NeonRepository.insertCard(context, tx, columnId)
            if (result.isSuccess) {
                Toast.makeText(context, "Agregado a $label en KTPJ", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "Error al guardar: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun retry(context: Context, intent: Intent) {
        val captureId = intent.getStringExtra(EXTRA_CAPTURE_ID) ?: return
        cancel(context, intent)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = CardSaver.retry(context, captureId)
                withContext(Dispatchers.Main) {
                    if (c == null) Toast.makeText(context, "La captura ya no está en el historial", Toast.LENGTH_LONG).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun taskDone(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val r = SpurTasks.markDone(context, taskId)
                withContext(Dispatchers.Main) {
                    val msg = if (r.isSuccess) "Tarea completada" else "No se pudo completar: ${r.exceptionOrNull()?.message}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun habitDone(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val amount = intent.getIntExtra(EXTRA_AMOUNT, -1).takeIf { it > 0 }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val r = Habits.check(context, id, amount)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, if (r.isSuccess) "✓ Hábito de hoy cumplido" else "No se pudo: ${r.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun cancel(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
    }
}
