package co.eskala.bktpj_notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INBOX = "co.eskala.bktpj_notification.ACTION_INBOX"
        const val ACTION_HOY   = "co.eskala.bktpj_notification.ACTION_HOY"
        const val EXTRA_TITLE     = "extra_title"
        const val EXTRA_AMOUNT    = "extra_amount"
        const val EXTRA_MERCHANT  = "extra_merchant"
        const val EXTRA_TYPE      = "extra_type"
        const val EXTRA_RAW_TEXT  = "extra_raw_text"
        const val EXTRA_NOTIF_ID  = "extra_notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val columnId = when (intent.action) {
            ACTION_HOY -> Prefs.getColumnHoy(context)
            else       -> Prefs.getColumnInbox(context)
        }

        if (columnId.isEmpty()) {
            Toast.makeText(context, "Abre Nu KTPJ y configura los IDs de columna", Toast.LENGTH_LONG).show()
            return
        }

        val tx = NuTransaction(
            title    = intent.getStringExtra(EXTRA_TITLE)    ?: "Notificación Nu",
            amount   = intent.getStringExtra(EXTRA_AMOUNT),
            merchant = intent.getStringExtra(EXTRA_MERCHANT),
            type     = intent.getStringExtra(EXTRA_TYPE)     ?: "desconocido",
            rawText  = intent.getStringExtra(EXTRA_RAW_TEXT) ?: ""
        )

        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)

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
}
