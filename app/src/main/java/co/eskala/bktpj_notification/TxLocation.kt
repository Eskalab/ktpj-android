package co.eskala.bktpj_notification

import java.util.Locale

/**
 * Ubicación del teléfono en el momento de una captura (KTP-7).
 * Se guarda en el historial ([Capture.location]) para que un reintento envíe la ORIGINAL,
 * y va a `transactions.location_lat / location_lng / location_address`.
 */
data class TxLocation(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float? = null,
    val address: String? = null,
    /** Hora (epoch ms) del fix, no de la captura. */
    val fixAt: Long = 0L,
) {
    /** "📍 Cra 7 #45-10, Bogotá" o, sin dirección, "📍 4.6097, -74.0817". */
    val label: String get() = "📍 " + (address ?: coords())

    /** Para lectores de pantalla (sin el emoji). */
    val spokenLabel: String get() = "Ubicación: " + (address ?: coords())

    private fun coords() = String.format(Locale.US, "%.4f, %.4f", lat, lng)

    /** Parámetros $lat, $lng, $address del INSERT en `transactions`. */
    fun sqlParams(): List<Any?> = listOf(lat, lng, address)

    companion object {
        /** Sin ubicación: las tres columnas quedan NULL, como antes de KTP-7. */
        fun sqlParams(loc: TxLocation?): List<Any?> = loc?.sqlParams() ?: listOf(null, null, null)
    }
}

/** Un fix candidato, sin depender de android.location (se puede probar en la JVM). */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    /** Metros (radio 68 %); null = desconocida. */
    val accuracyM: Float?,
    /** Antigüedad del fix en ms al momento de la captura. */
    val ageMs: Long,
    val provider: String = "",
)

/** Reglas puras para elegir el fix (por antigüedad y precisión). */
object LocationPick {
    /** Último conocido aceptable como respaldo. */
    const val MAX_LAST_KNOWN_AGE_MS = 15 * 60_000L
    /** Un último conocido así de reciente y preciso se usa sin pedir uno nuevo (no demora el guardado). */
    const val FRESH_ENOUGH_AGE_MS = 2 * 60_000L
    const val FRESH_ENOUGH_ACCURACY_M = 100f
    /** Diferencia de tiempo a partir de la cual el más nuevo gana aunque sea menos preciso. */
    private const val SIGNIFICANT_AGE_MS = 2 * 60_000L
    /** Precisión peor que esto no se usa nunca (no dice nada útil del lugar). */
    const val MAX_ACCURACY_M = 5_000f

    private fun usable(f: LocationFix, maxAgeMs: Long) =
        f.ageMs in 0..maxAgeMs &&
            (f.accuracyM == null || f.accuracyM <= MAX_ACCURACY_M) &&
            f.lat in -90.0..90.0 && f.lng in -180.0..180.0 &&
            !(f.lat == 0.0 && f.lng == 0.0)

    /** true si [a] es mejor que [b]: mucho más nuevo gana; si no, el más preciso; empate → el más nuevo. */
    fun isBetter(a: LocationFix, b: LocationFix): Boolean {
        val newerBy = b.ageMs - a.ageMs
        if (newerBy > SIGNIFICANT_AGE_MS) return true
        if (newerBy < -SIGNIFICANT_AGE_MS) return false
        val accA = a.accuracyM ?: Float.MAX_VALUE
        val accB = b.accuracyM ?: Float.MAX_VALUE
        if (accA != accB) return accA < accB
        return newerBy > 0
    }

    /** El mejor de los últimos conocidos de cada proveedor, o null si ninguno sirve. */
    fun best(fixes: List<LocationFix>, maxAgeMs: Long = MAX_LAST_KNOWN_AGE_MS): LocationFix? =
        fixes.filter { usable(it, maxAgeMs) }
            .fold(null as LocationFix?) { acc, f -> if (acc == null || isBetter(f, acc)) f else acc }

    /** ¿Se puede usar ya, sin esperar un fix nuevo? */
    fun freshEnough(f: LocationFix?): Boolean =
        f != null && f.ageMs <= FRESH_ENOUGH_AGE_MS &&
            (f.accuracyM ?: Float.MAX_VALUE) <= FRESH_ENOUGH_ACCURACY_M

    /** Fix nuevo (si llegó y sirve) o, si no, el último conocido. */
    fun choose(fresh: LocationFix?, lastKnown: LocationFix?): LocationFix? {
        val f = fresh?.takeIf { usable(it, MAX_LAST_KNOWN_AGE_MS) }
        return when {
            f == null -> lastKnown
            lastKnown == null -> f
            isBetter(lastKnown, f) -> lastKnown
            else -> f
        }
    }
}

/** Dirección corta a partir de los campos de android.location.Address. */
object AddressFormat {
    private const val MAX_LEN = 80

    private val STREET_ABBR = listOf(
        "Carrera" to "Cra",
        "Calle" to "Cl",
        "Avenida" to "Av",
        "Transversal" to "Tv",
        "Diagonal" to "Dg",
        "Autopista" to "Autop",
    )

    /** "Carrera 7" → "Cra 7"; deja igual lo que no reconoce. */
    fun abbreviateStreet(s: String): String {
        val t = s.trim()
        for ((long, short) in STREET_ABBR) {
            if (t.startsWith("$long ", ignoreCase = true)) return short + t.substring(long.length)
        }
        return t
    }

    /**
     * "Cra 7 #45-10, Bogotá". Sin calle usa la primera línea de la dirección (sin el país);
     * sin nada útil, la ciudad o el barrio; si no hay nada, null.
     */
    fun short(
        thoroughfare: String?,
        subThoroughfare: String?,
        locality: String?,
        subLocality: String? = null,
        addressLine: String? = null,
        countryName: String? = null,
    ): String? {
        val street = thoroughfare?.trim()?.takeIf { it.isNotEmpty() }?.let { abbreviateStreet(it) }
        val number = subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val city = locality?.trim()?.takeIf { it.isNotEmpty() }
            ?: subLocality?.trim()?.takeIf { it.isNotEmpty() }

        val result = if (street != null) {
            val num = number?.let { if (it.startsWith("#") || it.startsWith("No", ignoreCase = true)) " $it" else " #$it" } ?: ""
            listOfNotNull(street + num, city?.takeUnless { street.contains(it, ignoreCase = true) })
                .joinToString(", ")
        } else {
            addressLine?.let { stripCountry(it, countryName) }?.takeIf { it.isNotEmpty() } ?: city
        }
        return result?.let { if (it.length > MAX_LEN) it.take(MAX_LEN - 1).trimEnd() + "…" else it }
    }

    private fun stripCountry(line: String, country: String?): String {
        val parts = line.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (parts.size > 1 && country != null && parts.last().equals(country, ignoreCase = true)) parts.removeAt(parts.lastIndex)
        // Códigos postales sueltos al final ("110231") no aportan.
        if (parts.size > 1 && parts.last().all { it.isDigit() || it == ' ' }) parts.removeAt(parts.lastIndex)
        return parts.joinToString(", ")
    }
}

/**
 * Cuánto puede esperar una captura por la ubicación, según si la app está en pantalla y si hay
 * permiso de segundo plano. La ubicación nunca debe poner en riesgo el guardado: un SMS corre en
 * goAsync() (~10 s en total para la red), y el gasto manual debe sentirse inmediato.
 */
data class LocationBudget(
    /** Tope de todo (fix + dirección). */
    val totalMs: Long,
    /** Espera por un fix nuevo; 0 = solo el último conocido. */
    val freshMs: Long,
    /** Espera por la dirección (Geocoder). */
    val geocodeMs: Long,
) {
    val allowFresh: Boolean get() = freshMs > 0

    /** Lo que le queda a la dirección tras [elapsedMs] buscando el fix (nunca pasa del total). */
    fun geocodeTimeout(elapsedMs: Long): Long = minOf(geocodeMs, totalMs - elapsedMs).coerceAtLeast(0)

    companion object {
        /** Gasto manual / atajo con la app en pantalla: rápido, para que "Tarjeta creada" no se demore. */
        val FOREGROUND = LocationBudget(totalMs = 3_000, freshMs = 2_000, geocodeMs = 1_000)
        /** Nu / Davivienda con la app cerrada y "Permitir todo el tiempo": corto, el SMS no puede esperar. */
        val BACKGROUND = LocationBudget(totalMs = 2_000, freshMs = 1_200, geocodeMs = 700)
        /** Con la app cerrada y sin permiso de segundo plano un fix nuevo no puede llegar: solo último conocido. */
        val LAST_KNOWN_ONLY = LocationBudget(totalMs = 1_000, freshMs = 0, geocodeMs = 700)

        /** null = no buscar ubicación (apagado o sin permiso). */
        fun plan(enabled: Boolean, foregroundPermission: Boolean, backgroundPermission: Boolean, inForeground: Boolean): LocationBudget? =
            when {
                !enabled || !foregroundPermission -> null
                inForeground -> FOREGROUND
                backgroundPermission -> BACKGROUND
                else -> LAST_KNOWN_ONLY
            }
    }
}
