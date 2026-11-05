package co.eskala.bktpj_notification

import android.content.Context
import java.util.UUID

/**
 * Guarda un movimiento en Neon (Inbox y, si está configurada y es otra columna, Hoy),
 * lo registra en [CaptureLog] y avisa con [SpurNotifications].
 * Lo usan el servicio de notificaciones de Nu, el gasto manual y los reintentos.
 */
object CardSaver {

    suspend fun save(
        ctx: Context,
        tx: NuTransaction,
        source: CaptureSource,
        captureId: String = UUID.randomUUID().toString(),
        at: Long = System.currentTimeMillis(),
        notify: Boolean = true,
    ): Capture {
        val inboxId = Prefs.getColumnInbox(ctx)
        val hoyId = Prefs.getColumnHoy(ctx)
        // Hoy es opcional: si está vacío o es la misma columna que Inbox, solo se crea una tarjeta.
        val alsoHoy = hoyId.isNotEmpty() && hoyId != inboxId
        val destinations = if (alsoHoy) "Inbox + Hoy" else "Inbox"

        val error: String? = if (inboxId.isEmpty()) {
            "Configura las columnas de destino"
        } else {
            val r1 = NeonRepository.insertCard(ctx, tx, inboxId, source)
            val r2 = if (alsoHoy && r1.isSuccess) {
                NeonRepository.insertCard(ctx, tx, hoyId, source, withTransaction = false)
            } else r1
            r1.exceptionOrNull()?.message ?: r2.exceptionOrNull()?.let { "Hoy: ${it.message}" }
                ?: if (r1.isFailure || r2.isFailure) "Error desconocido" else null
        }

        val capture = Capture(
            id = captureId,
            at = at,
            source = source,
            tx = tx,
            status = if (error == null) CaptureStatus.OK else CaptureStatus.ERROR,
            error = error,
            destinations = destinations,
        )
        CaptureLog.put(ctx, capture)

        if (notify) {
            if (error == null) SpurNotifications.showSaved(ctx, capture)
            else SpurNotifications.showError(ctx, capture)
        }
        return capture
    }

    /** Reenvía el payload guardado de una captura (Inicio, Historial o notificación). */
    suspend fun retry(ctx: Context, captureId: String): Capture? {
        val c = CaptureLog.get(ctx, captureId) ?: return null
        SpurNotifications.cancelError(ctx, captureId)
        return save(ctx, c.tx, c.source, captureId = c.id, at = c.at)
    }
}
