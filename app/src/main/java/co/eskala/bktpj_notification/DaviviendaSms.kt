package co.eskala.bktpj_notification

import java.security.MessageDigest

/**
 * Convierte un SMS de Davivienda (número corto 87188) en un movimiento, con las mismas reglas que la
 * carga histórica del 2026-11-03:
 * - Compras aprobadas con tarjeta → "TC Crédito NNNN".
 * - Salidas de la cuenta de ahorros (PSE, retiros, transferencias y compras con llave) → "Ahorros NNNN".
 * - Depósitos → tarjeta en Inbox, sin fila en `transactions` (la tabla no distingue ingresos).
 * - Se ignoran: rechazos, reversos, traslados del bolsillo, avances y su abono, pagos de la TC,
 *   ingresos a la app, códigos y publicidad.
 */
object DaviviendaSms {

    val SENDERS = setOf("87188")

    fun isFromDavivienda(sender: String?, body: String): Boolean =
        sender?.filter(Char::isDigit) in SENDERS || body.trimStart().startsWith("DAVIVIENDA", ignoreCase = true)

    private val AMOUNT = Regex("""\$\s?([\d.,]+)""")
    private val CARD = Regex("""Tarjeta\s*\*(\d{4})""")
    private val SAVINGS = Regex("""Cta de Ahorros\s*\*(\d{4})""")
    private val PLACE = Regex("""Lugar\s+(.+?)\s*\.*\s*$""")

    fun parse(body: String, receivedAt: Long): NuTransaction? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        val t = text.removePrefix("DAVIVIENDA").trimStart(':', ' ')

        if (Regex("Declinad|Reversad|ingresado exitosamente|codigo|código", RegexOption.IGNORE_CASE).containsMatchIn(t)) return null
        if (Regex("Bolsillo a Cuenta|Abono Uso Tarjeta|Pago Tarjeta de Credito|^Avance", RegexOption.IGNORE_CASE).containsMatchIn(t)) return null

        val amount = AMOUNT.find(t)?.groupValues?.get(1)?.let(::toColombian) ?: return null
        val place = PLACE.find(t)?.groupValues?.get(1)?.trim()
        val id = messageId(text, receivedAt)

        CARD.find(t)?.let { card ->
            if (!t.startsWith("Compra", ignoreCase = true) || !t.contains("Aprobad", ignoreCase = true)) return null
            val last4 = card.groupValues[1]
            return tx("Davivienda: \$$amount en ${place ?: "compra"}", amount, place, "cargo", text, "TC Crédito $last4", last4, id)
        }

        val last4 = SAVINGS.find(t)?.groupValues?.get(1)
        val account = last4?.let { "Ahorros $it" } ?: "Davivienda"
        return when {
            t.startsWith("Deposito", ignoreCase = true) || t.startsWith("Depósito", ignoreCase = true) ->
                tx("Davivienda: +\$$amount depósito" + (place?.let { " de $it" } ?: ""), amount, place, "abono", text, account, last4, id)
                    .copy(recordTransaction = false)

            Regex("Transferencia a (una )?llave", RegexOption.IGNORE_CASE).containsMatchIn(t) ->
                tx("Davivienda: \$$amount transferencia a llave", amount, "Transferencia a llave", "cargo", text, account, last4, id)

            t.startsWith("Retiro", ignoreCase = true) -> {
                val m = "Retiro" + (place?.let { " $it" } ?: "")
                tx("Davivienda: \$$amount $m", amount, m, "cargo", text, account, last4, id)
            }

            t.startsWith("Descuento", ignoreCase = true) ->
                tx("Davivienda: \$$amount en ${place ?: "pago"}", amount, place, "cargo", text, account, last4, id)

            else -> null
        }
    }

    private fun tx(title: String, amount: String, merchant: String?, type: String, raw: String, account: String, last4: String?, id: String) =
        NuTransaction(title, amount, merchant, type, raw, account = account, last4 = last4, messageId = id)

    /** "$546,000" (formato del SMS) → "546.000" (formato del resto de la app). */
    private fun toColombian(raw: String): String? {
        val v = raw.replace(",", "").substringBefore('.').toLongOrNull() ?: return null
        return Money.format(v.toDouble())
    }

    /** Id estable: mismo SMS (texto + minuto de llegada) → mismo id, para no duplicar. */
    private fun messageId(text: String, receivedAt: Long): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$text|${receivedAt / 60_000}".toByteArray())
        return "sms_davivienda_" + digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
