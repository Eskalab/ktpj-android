package co.eskala.bktpj_notification

data class NuTransaction(
    val title: String,
    val amount: String?,
    val merchant: String?,
    val type: String,   // "cargo" | "abono" | "desconocido"
    val rawText: String,
    // Solo para bancos con varias cuentas (Davivienda): "TC Crédito 8066", "Ahorros 8204".
    val account: String? = null,
    val last4: String? = null,
    // Id estable del movimiento (p. ej. del SMS) para no duplicarlo si llega dos veces.
    val messageId: String? = null,
    // false = solo tarjeta, sin fila en `transactions` (p. ej. depósitos: la tabla no distingue ingresos).
    val recordTransaction: Boolean = true,
)

object NuParser {

    private val AMOUNT_RE = Regex("""(?:\$|COP\s?)(\d[\d.,]*)""")

    private val PURCHASE = listOf(
        Regex("""[Cc]ompraste?\s+\$?([\d.,]+)\s+en\s+(.+)"""),
        Regex("""[Cc]ompra\s+(?:aprobada|exitosa)[^$]*\$?([\d.,]+)\s+en\s+(.+)"""),
        Regex("""[Pp]agaste?\s+\$?([\d.,]+)\s+(?:en|a)\s+(.+)"""),
        Regex("""[Uu]saste?\s+Nu.+\$?([\d.,]+)\s+en\s+(.+)"""),
    )

    private val INCOME = listOf(
        Regex("""[Rr]ecibiste?\s+\$?([\d.,]+)"""),
        Regex("""[Tt]ransferencia\s+recibida[^$]*\$?([\d.,]+)"""),
        Regex("""[Pp]ago\s+recibido[^$]*\$?([\d.,]+)"""),
    )

    fun parse(notifTitle: String, notifBody: String): NuTransaction {
        val full = "$notifTitle $notifBody".trim()

        for (re in PURCHASE) {
            val m = re.find(full) ?: continue
            val amount = m.groupValues.getOrNull(1)
            val merchant = m.groupValues.getOrNull(2)?.trim()?.removeSuffix(".")
            return NuTransaction(
                title = if (!merchant.isNullOrBlank()) "Nu: \$$amount en $merchant"
                        else "Nu: \$$amount",
                amount = amount,
                merchant = merchant,
                type = "cargo",
                rawText = full
            )
        }

        for (re in INCOME) {
            val m = re.find(full) ?: continue
            val amount = m.groupValues.getOrNull(1)
            return NuTransaction(
                title = "Nu: +\$$amount recibido",
                amount = amount,
                merchant = null,
                type = "abono",
                rawText = full
            )
        }

        val amount = AMOUNT_RE.find(full)?.groupValues?.getOrNull(1)
        return NuTransaction(
            title = if (notifTitle.isNotBlank()) "Nu: $notifTitle" else "Notificación Nu",
            amount = amount,
            merchant = null,
            type = "desconocido",
            rawText = full
        )
    }
}
