package co.eskala.bktpj_notification

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.abs

/** Montos en formato colombiano: "45.900" / "45.900,50" (punto de miles, coma decimal). */
object Money {

    private val symbols = DecimalFormatSymbols().apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val whole = DecimalFormat("#,##0", symbols)
    private val withCents = DecimalFormat("#,##0.00", symbols)

    /** "20.600,00" → 20600.0 (misma conversión que hace NeonRepository al guardar). */
    fun parse(amount: String?): Double? =
        amount?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull()

    /** 20600.0 → "20.600"; 20600.5 → "20.600,50". */
    fun format(value: Double): String {
        val v = abs(value)
        return if (v % 1.0 == 0.0) whole.format(v) else withCents.format(v)
    }

    /** Monto con signo según el tipo: cargo "−45.900", abono "+1.200.000", desconocido "45.900". */
    fun signed(amount: String?, type: String): String? {
        val v = parse(amount) ?: return null
        return when (type) {
            "cargo" -> "−${format(v)}"
            "abono" -> "+${format(v)}"
            else -> format(v)
        }
    }

    /** Valor con signo para sumas del día (cargos restan, abonos suman). */
    fun signedValue(amount: String?, type: String): Double {
        val v = parse(amount) ?: return 0.0
        return if (type == "cargo") -v else v
    }

    fun signedTotal(value: Double): String = when {
        value < 0 -> "−${format(value)}"
        value > 0 -> "+${format(value)}"
        else -> "0"
    }
}
