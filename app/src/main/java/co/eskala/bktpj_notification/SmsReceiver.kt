package co.eskala.bktpj_notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe una copia de los SMS entrantes y procesa solo los de Davivienda.
 * No borra ni oculta nada: el SMS sigue en la app de Mensajes (una app que no es la de SMS
 * por defecto no puede interceptarlo).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.isConfigured(context)) return

        // Un SMS largo llega partido en varias partes: se unen por remitente.
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val bySender = parts.groupBy { it.originatingAddress }
        val txs = bySender.mapNotNull { (sender, msgs) ->
            val body = msgs.joinToString("") { it.messageBody.orEmpty() }
            if (!DaviviendaSms.isFromDavivienda(sender, body)) return@mapNotNull null
            DaviviendaSms.parse(body, msgs.first().timestampMillis)
        }
        if (txs.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                txs.forEach { CardSaver.save(context, it, CaptureSource.DAVIVIENDA) }
            } finally {
                pending.finish()
            }
        }
    }
}
