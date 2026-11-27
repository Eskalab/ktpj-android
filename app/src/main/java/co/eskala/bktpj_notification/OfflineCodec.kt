package co.eskala.bktpj_notification

/**
 * Formato de texto mínimo para guardar la cola y la caché sin depender de org.json (que en las pruebas
 * JVM no funciona). Un registro es una lista de campos (texto o null) en una sola línea:
 * los campos se separan con TAB; dentro de un campo se escapan `\` `TAB` y saltos de línea, y null es `\0`.
 * Un registro codificado es a su vez un campo válido, así que se pueden anidar (lista de tareas, épica…).
 */
object OfflineCodec {
    private const val SEP = '\t'
    private const val NULL = "\\0"

    fun field(s: String?): String {
        if (s == null) return NULL
        val b = StringBuilder(s.length + 4)
        for (ch in s) when (ch) {
            '\\' -> b.append("\\\\")
            '\t' -> b.append("\\t")
            '\n' -> b.append("\\n")
            '\r' -> b.append("\\r")
            else -> b.append(ch)
        }
        return b.toString()
    }

    fun record(fields: List<String?>): String = fields.joinToString(SEP.toString()) { field(it) }

    fun record(vararg fields: String?): String = record(fields.toList())

    /** Inverso de [record]. Una línea vacía es un registro de un solo campo vacío. */
    fun parse(line: String): List<String?> {
        val out = ArrayList<String?>()
        val cur = StringBuilder()
        var isNull = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == SEP -> { out += if (isNull) null else cur.toString(); cur.setLength(0); isNull = false }
                ch == '\\' && i + 1 < line.length -> {
                    when (val n = line[++i]) {
                        't' -> cur.append('\t')
                        'n' -> cur.append('\n')
                        'r' -> cur.append('\r')
                        '0' -> isNull = true
                        else -> cur.append(n)
                    }
                }
                else -> cur.append(ch)
            }
            i++
        }
        out += if (isNull) null else cur.toString()
        return out
    }

    /** Lista de registros como un solo campo (cada registro anidado se escapa otra vez). */
    fun list(items: List<String>): String = record(items)

    fun parseList(s: String?): List<String> =
        if (s.isNullOrEmpty()) emptyList() else parse(s).map { it.orEmpty() }
}
