package co.eskala.bktpj_notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NuNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SpurNotifications.ensureChannel(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != Prefs.getNuPackage(this)) return
        if (!Prefs.isConfigured(this)) return

        val extras = sbn.notification.extras
        val title  = extras.getString(Notification.EXTRA_TITLE) ?: return
        val body   = extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getString(Notification.EXTRA_BIG_TEXT)
            ?: ""

        val tx = NuParser.parse(title, body)

        cancelNotification(sbn.key)

        // Guarda en Inbox (+ Hoy si aplica), lo registra en el historial y avisa éxito o error.
        scope.launch { CardSaver.save(this@NuNotificationService, tx, CaptureSource.NU) }
    }
}
