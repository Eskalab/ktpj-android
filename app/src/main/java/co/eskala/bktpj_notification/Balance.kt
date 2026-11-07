package co.eskala.bktpj_notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Calendar

/**
 * Comercios marcados como "Fijo" (arriendo, administración, servicios…). Se recuerda por comercio:
 * marcar uno aplica a todos sus movimientos, pasados y futuros. Solo en el celular.
 */
object FixedStore {
    private const val PREFS = "spur_fixed"
    private const val KEY = "merchants"

    private val _fixed = MutableStateFlow<Set<String>>(emptySet())
    val fixed: StateFlow<Set<String>> = _fixed.asStateFlow()
    @Volatile private var loaded = false

    fun load(ctx: Context): Set<String> {
        if (!loaded) {
            _fixed.value = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
            loaded = true
        }
        return _fixed.value
    }

    /** Clave del comercio: sin tildes, minúsculas, espacios simples. */
    fun keyOf(c: Capture): String =
        Normalizer.normalize(c.label, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .lowercase().trim().replace(Regex("\\s+"), " ")

    fun isFixed(c: Capture): Boolean = keyOf(c) in _fixed.value

    fun setFixed(ctx: Context, c: Capture, on: Boolean) {
        load(ctx)
        val updated = if (on) _fixed.value + keyOf(c) else _fixed.value - keyOf(c)
        _fixed.value = updated
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, updated).apply()
    }
}

/**
 * Balance de un período. Tres cifras que nunca se suman entre sí: gastos variables, gastos fijos e ingresos.
 * Solo capturas guardadas; los movimientos internos (depósitos de bolsillo, sin transacción) no cuentan.
 */
data class Balance(
    val variable: Double,
    val fixed: Double,
    val income: Double,
    val fixedIncome: Double,
    val topVariable: List<Pair<String, Double>>,
    val topFixed: List<Pair<String, Double>>,
    val count: Int,
) {
    val left: Double get() = income - variable - fixed

    companion object {
        fun counted(c: Capture) = c.status == CaptureStatus.OK && c.tx.recordTransaction && Money.parse(c.tx.amount) != null
        fun isIncome(c: Capture) = c.tx.type == "abono"

        /** Lo que de verdad "gastaste": cargos no fijos. Es la cifra del día en Inicio y en Gastos. */
        fun variableOf(list: List<Capture>, fixed: Set<String>): Double =
            list.filter { counted(it) && !isIncome(it) && FixedStore.keyOf(it) !in fixed }
                .sumOf { Money.parse(it.tx.amount) ?: 0.0 }

        fun of(list: List<Capture>, fixed: Set<String>): Balance {
            val ok = list.filter(::counted)
            val (inc, out) = ok.partition(::isIncome)
            val (fix, vari) = out.partition { FixedStore.keyOf(it) in fixed }
            fun top(l: List<Capture>) = l.groupBy { it.label }
                .map { (k, v) -> k to v.sumOf { Money.parse(it.tx.amount) ?: 0.0 } }
                .sortedByDescending { it.second }
                .take(5)
            fun sum(l: List<Capture>) = l.sumOf { Money.parse(it.tx.amount) ?: 0.0 }
            return Balance(
                variable = sum(vari),
                fixed = sum(fix),
                income = sum(inc),
                fixedIncome = sum(inc.filter { FixedStore.keyOf(it) in fixed }),
                topVariable = top(vari),
                topFixed = top(fix),
                count = ok.size,
            )
        }

        /** Capturas del mes [offset] (0 = este mes, -1 = el anterior…). */
        fun month(list: List<Capture>, offset: Int): List<Capture> {
            val start = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.MONTH, offset)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            return list.filter { it.at >= start.timeInMillis && it.at < end.timeInMillis }
        }
    }
}
