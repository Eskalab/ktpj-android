package co.eskala.bktpj_notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class CaptureSource { NU, MANUAL, DAVIVIENDA }
enum class CaptureStatus { OK, ERROR }

/**
 * Una captura: un movimiento (notificación de Nu o gasto manual) y el resultado de guardarlo en Neon.
 * Guarda el payload completo para poder reintentar.
 */
data class Capture(
    val id: String,
    val at: Long,
    val source: CaptureSource,
    val tx: NuTransaction,
    val status: CaptureStatus,
    val error: String? = null,
    val destinations: String = "Inbox",   // "Inbox" | "Inbox + Hoy"
    // Dónde estaba el teléfono al capturar (KTP-7); null = apagado, sin permiso o sin fix.
    val location: TxLocation? = null,
) {
    /** Nombre para mostrar: comercio, o el título sin el prefijo "Nu: ". */
    val label: String
        get() = tx.merchant?.takeIf { it.isNotBlank() }
            ?: when (tx.type) {
                "abono" -> if (source == CaptureSource.NU) "Transferencia" else "Abono"
                else -> tx.title.removePrefix("Nu: ").removePrefix("Manual: ")
            }

    val signedAmount: String? get() = Money.signed(tx.amount, tx.type)
}

/**
 * Historial local de capturas (05 Historial): un archivo JSON en filesDir.
 * El servicio de notificaciones y la UI corren en el mismo proceso y comparten [captures].
 */
object CaptureLog {

    private const val FILE = "captures.json"
    private const val MAX_ENTRIES = 1000

    private val _captures = MutableStateFlow<List<Capture>>(emptyList())
    val captures: StateFlow<List<Capture>> = _captures.asStateFlow()

    @Volatile private var loaded = false

    fun load(ctx: Context): List<Capture> = synchronized(this) {
        if (!loaded) {
            _captures.value = runCatching { read(file(ctx)) }.getOrDefault(emptyList())
            loaded = true
        }
        _captures.value
    }

    fun get(ctx: Context, id: String): Capture? = load(ctx).firstOrNull { it.id == id }

    /** Inserta o reemplaza (por id) y deja la lista ordenada de más reciente a más antigua. */
    fun put(ctx: Context, capture: Capture) = synchronized(this) {
        val updated = (listOf(capture) + load(ctx).filterNot { it.id == capture.id })
            .sortedByDescending { it.at }
            .take(MAX_ENTRIES)
        _captures.value = updated
        write(file(ctx), updated)
    }

    fun remove(ctx: Context, id: String) = synchronized(this) {
        val updated = load(ctx).filterNot { it.id == id }
        _captures.value = updated
        write(file(ctx), updated)
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    private fun read(f: File): List<Capture> {
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Capture(
                id = o.getString("id"),
                at = o.getLong("at"),
                source = CaptureSource.valueOf(o.optString("source", "NU")),
                tx = NuTransaction(
                    title = o.getString("title"),
                    amount = o.optStringOrNull("amount"),
                    merchant = o.optStringOrNull("merchant"),
                    type = o.optString("type", "desconocido"),
                    rawText = o.optString("rawText", ""),
                    account = o.optStringOrNull("account"),
                    last4 = o.optStringOrNull("last4"),
                    messageId = o.optStringOrNull("messageId"),
                    recordTransaction = o.optBoolean("recordTransaction", true),
                ),
                status = CaptureStatus.valueOf(o.getString("status")),
                error = o.optStringOrNull("error"),
                destinations = o.optString("destinations", "Inbox"),
                location = readLocation(o),
            )
        }
    }

    // Escritura atómica: archivo temporal + rename, para no corromper el historial si el proceso muere.
    private fun write(f: File, list: List<Capture>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("at", c.at)
                put("source", c.source.name)
                put("title", c.tx.title)
                put("amount", c.tx.amount ?: JSONObject.NULL)
                put("merchant", c.tx.merchant ?: JSONObject.NULL)
                put("type", c.tx.type)
                put("rawText", c.tx.rawText)
                put("account", c.tx.account ?: JSONObject.NULL)
                put("last4", c.tx.last4 ?: JSONObject.NULL)
                put("messageId", c.tx.messageId ?: JSONObject.NULL)
                put("recordTransaction", c.tx.recordTransaction)
                put("status", c.status.name)
                put("error", c.error ?: JSONObject.NULL)
                put("destinations", c.destinations)
                c.location?.let { l ->
                    put("location", JSONObject().apply {
                        put("lat", l.lat)
                        put("lng", l.lng)
                        put("accuracy", l.accuracyM?.toDouble() ?: JSONObject.NULL)
                        put("address", l.address ?: JSONObject.NULL)
                        put("fixAt", l.fixAt)
                    })
                }
            })
        }
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun readLocation(o: JSONObject): TxLocation? {
        val l = o.optJSONObject("location") ?: return null
        if (!l.has("lat") || !l.has("lng")) return null
        return TxLocation(
            lat = l.getDouble("lat"),
            lng = l.getDouble("lng"),
            accuracyM = if (l.isNull("accuracy")) null else l.optDouble("accuracy").toFloat(),
            address = l.optStringOrNull("address"),
            fixAt = l.optLong("fixAt", 0L),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
