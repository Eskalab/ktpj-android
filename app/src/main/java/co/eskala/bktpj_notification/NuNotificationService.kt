package co.eskala.bktpj_notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NuNotificationService : NotificationListenerService() {

    companion object {
        private const val CHANNEL_ID    = "nu_ktpj_actions"
        private const val NOTIF_CONFIRM = 9001
        private const val NOTIF_ERROR   = 9002
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "Nu Colombia → KTPJ", NotificationManager.IMPORTANCE_DEFAULT
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != Prefs.getNuPackage(this)) return
        if (!Prefs.isConfigured(this)) return

        val extras = sbn.notification.extras
        val title  = extras.getString(Notification.EXTRA_TITLE) ?: return
        val body   = extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getString(Notification.EXTRA_BIG_TEXT)
            ?: ""

        val tx      = NuParser.parse(title, body)
        val inboxId = Prefs.getColumnInbox(this)
        val hoyId   = Prefs.getColumnHoy(this)

        cancelNotification(sbn.key)

        if (inboxId.isEmpty() || hoyId.isEmpty()) {
            notify(NOTIF_ERROR, "Nu KTPJ — Error", "Configura los IDs de columna")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val r1 = NeonRepository.insertCard(this@NuNotificationService, tx, inboxId)
            val r2 = NeonRepository.insertCard(this@NuNotificationService, tx, hoyId)
            withContext(Dispatchers.Main) {
                if (r1.isSuccess && r2.isSuccess) {
                    val emoji = when (tx.type) { "cargo" -> "↑"; "abono" -> "↓"; else -> "•" }
                    val amount = if (tx.amount != null) "$emoji \$${tx.amount}" else emoji
                    notify(NOTIF_CONFIRM, tx.title, "$amount → Inbox + Hoy")
                } else {
                    val err = r1.exceptionOrNull()?.message ?: r2.exceptionOrNull()?.message
                    notify(NOTIF_ERROR, "Nu KTPJ — Error", err ?: "Error desconocido")
                }
            }
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFF0052CC.toInt())
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(id, notif)
    }
}
