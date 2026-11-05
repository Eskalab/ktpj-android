package co.eskala.bktpj_notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Gasto que se repite (p. ej. "Peaje Andes", $16.100): un toque en Inicio crea la tarjeta. */
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Long,          // pesos enteros
    val merchant: String?,
    val type: String,          // "cargo" | "abono"
) {
    val signedAmount: String? get() = Money.signed(Money.format(amount.toDouble()), type)

    fun toTransaction() = manualTransaction(amount, merchant ?: name, type, "Atajo «$name» en Spur")
}

/** Movimiento manual (gasto manual o atajo) con el mismo título que ve el tablero. */
fun manualTransaction(amount: Long, merchant: String?, type: String, rawText: String): NuTransaction {
    val formatted = Money.format(amount.toDouble())
    val m = merchant?.trim()?.ifEmpty { null }
    val title = when {
        type == "abono" -> "Manual: +\$$formatted" + (m?.let { " de $it" } ?: " recibido")
        m != null -> "Manual: \$$formatted en $m"
        else -> "Manual: \$$formatted"
    }
    return NuTransaction(title, formatted, m, type, rawText)
}

/** Atajos guardados en el teléfono: un archivo JSON en filesDir, como [CaptureLog]. */
object ShortcutStore {

    private const val FILE = "shortcuts.json"

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())
    val shortcuts: StateFlow<List<Shortcut>> = _shortcuts.asStateFlow()

    @Volatile private var loaded = false

    fun load(ctx: Context): List<Shortcut> = synchronized(this) {
        if (!loaded) {
            _shortcuts.value = runCatching { read(file(ctx)) }.getOrDefault(emptyList())
            loaded = true
        }
        _shortcuts.value
    }

    fun get(ctx: Context, id: String): Shortcut? = load(ctx).firstOrNull { it.id == id }

    /** Inserta al final o reemplaza en su lugar (por id), para que el orden de los botones no cambie al editar. */
    fun put(ctx: Context, shortcut: Shortcut) = synchronized(this) {
        val current = load(ctx)
        val updated = if (current.any { it.id == shortcut.id }) {
            current.map { if (it.id == shortcut.id) shortcut else it }
        } else current + shortcut
        save(ctx, updated)
    }

    fun remove(ctx: Context, id: String) = synchronized(this) {
        save(ctx, load(ctx).filterNot { it.id == id })
    }

    private fun save(ctx: Context, list: List<Shortcut>) {
        _shortcuts.value = list
        write(file(ctx), list)
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    private fun read(f: File): List<Shortcut> {
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Shortcut(
                id = o.getString("id"),
                name = o.getString("name"),
                amount = o.getLong("amount"),
                merchant = if (o.isNull("merchant")) null else o.optString("merchant").ifEmpty { null },
                type = o.optString("type", "cargo"),
            )
        }
    }

    // Escritura atómica, igual que el historial.
    private fun write(f: File, list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("amount", s.amount)
                put("merchant", s.merchant ?: JSONObject.NULL)
                put("type", s.type)
            })
        }
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }
}
