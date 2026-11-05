package co.eskala.bktpj_notification

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Estado del permiso "Acceso a notificaciones" que necesita NuNotificationService. */
object NotificationAccess {

    fun isGranted(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun settingsIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
