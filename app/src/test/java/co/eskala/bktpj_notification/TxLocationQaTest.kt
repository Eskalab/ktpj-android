package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * QA KTP-7: casos `auto` de qa/casos/KTP-7.md (escritos antes del código).
 * Complementa TxLocationTest (pruebas del desarrollador). Coordenadas y direcciones inventadas.
 */
class TxLocationQaTest {

    private fun fix(lat: Double, lng: Double, acc: Float? = 20f, ageMin: Double = 0.5) =
        LocationFix(lat, lng, acc, (ageMin * 60_000).toLong())

    // KTP-7-12: la última ubicación conocida es de hace 3 horas (otra ciudad) → no se usa como actual.
    @Test fun ubicacionDeHaceTresHorasNoSeGuarda() {
        val viejaMedellin = fix(6.2442, -75.5812, acc = 10f, ageMin = 180.0)
        assertNull(LocationPick.best(listOf(viejaMedellin)))
        assertNull(LocationPick.choose(fresh = null, lastKnown = LocationPick.best(listOf(viejaMedellin))))
        // Tampoco si llega como "fix nuevo" con esa antigüedad (proveedor devolviendo caché).
        assertNull(LocationPick.choose(fresh = viejaMedellin, lastKnown = null))
        assertFalse(LocationPick.freshEnough(viejaMedellin))
    }

    // KTP-7-12: entre una vieja (3 h) y una reciente, gana la reciente aunque sea menos precisa.
    @Test fun ubicacionViejaNoLeGanaAUnaReciente() {
        val vieja = fix(6.2442, -75.5812, acc = 5f, ageMin = 180.0)
        val reciente = fix(4.6097, -74.0817, acc = 800f, ageMin = 1.0)
        assertSame(reciente, LocationPick.best(listOf(vieja, reciente)))
    }

    // KTP-7-13: coordenadas extremas válidas de Colombia conservan signo y decimales.
    @Test fun coordenadasExtremasSeConservanConSignoYDecimales() {
        val leticia = fix(-4.215432, -69.940712)
        val sanAndres = fix(12.584791, -81.700556)
        assertSame(leticia, LocationPick.best(listOf(leticia)))
        assertSame(sanAndres, LocationPick.best(listOf(sanAndres)))

        val p = TxLocation(leticia.lat, leticia.lng).sqlParams()
        assertEquals(-4.215432, p[0] as Double, 0.0)
        assertEquals(-69.940712, p[1] as Double, 0.0)
        val q = TxLocation(sanAndres.lat, sanAndres.lng).sqlParams()
        assertEquals(12.584791, q[0] as Double, 0.0)
        assertEquals(-81.700556, q[1] as Double, 0.0)
    }

    // KTP-7-14: un proveedor roto (0,0 o fuera de rango) → se guarda sin ubicación.
    @Test fun coordenadasInvalidasSeDescartan() {
        listOf(
            fix(0.0, 0.0),
            fix(95.0, -74.0),
            fix(-91.0, -74.0),
            fix(4.6, 181.0),
            fix(4.6, -200.0),
            fix(Double.NaN, -74.0),
            fix(4.6, Double.NaN),
        ).forEach { bad ->
            assertNull("debía descartar $bad", LocationPick.best(listOf(bad)))
            assertNull("debía descartar $bad", LocationPick.choose(fresh = bad, lastKnown = null))
        }
        // Si hay una buena junto a la rota, se usa la buena.
        val buena = fix(4.6097, -74.0817, acc = 200f)
        assertSame(buena, LocationPick.best(listOf(fix(0.0, 0.0, acc = 5f), buena)))
        // Bordes exactos del rango sí son válidos (no hay ciudades ahí, pero no son "rotos").
        assertNotNull(LocationPick.best(listOf(fix(90.0, 180.0))))
    }

    // KTP-7-14: sin ubicación las tres columnas quedan NULL (no 0,0).
    @Test fun sinUbicacionLasTresColumnasVanNull() {
        assertEquals(listOf<Any?>(null, null, null), TxLocation.sqlParams(null))
    }

    // KTP-7-15: teléfono en español (coma decimal) → números con punto, y lat/lng van como números, no texto.
    @Test fun localeEspanolNoCambiaElSeparadorDecimal() {
        val antes = Locale.getDefault()
        try {
            Locale.setDefault(Locale("es", "CO"))
            val l = TxLocation(4.65, -74.0817)
            assertEquals("📍 4.6500, -74.0817", l.label)
            assertEquals("Ubicación: 4.6500, -74.0817", l.spokenLabel)
            val p = l.sqlParams()
            assertTrue(p[0] is Double)
            assertTrue(p[1] is Double)
            assertEquals("4.65", p[0].toString())
        } finally {
            Locale.setDefault(antes)
        }
    }

    // KTP-7-20: dirección muy larga (barrio, ciudad, departamento, país) → se recorta sin romper.
    @Test fun direccionMuyLargaSeRecortaConPuntosSuspensivos() {
        val linea = "Carrera 7 # 45-10 Interior 3 Apartamento 502, Chapinero Alto, Localidad de Chapinero, " +
            "Bogotá, Distrito Capital de Bogotá, 110231, Colombia"
        val r = AddressFormat.short(null, null, "Bogotá", addressLine = linea, countryName = "Colombia")!!
        assertTrue("largo ${r.length}", r.length <= 80)
        assertTrue(r.endsWith("…"))
        assertTrue(r.startsWith("Carrera 7 # 45-10"))
        assertFalse(r.contains("Colombia"))

        val conCalle = AddressFormat.short("Avenida " + "Circunvalar ".repeat(10), "12-34", "Bogotá")!!
        assertTrue(conCalle.length <= 80)
        assertTrue(conCalle.endsWith("…"))
    }

    // KTP-7-20: la dirección justo de 80 caracteres no se recorta.
    @Test fun direccionDe80CaracteresNoSeRecorta() {
        val linea = "A".repeat(80)
        assertEquals(linea, AddressFormat.short(null, null, null, addressLine = linea))
    }

    // KTP-7-07 (parte pura): sin dirección se muestran las coordenadas, nunca una dirección inventada.
    @Test fun sinDireccionNoSeInventaUna() {
        assertNull(AddressFormat.short(null, null, null))
        val l = TxLocation(4.60971, -74.08172, address = null)
        assertNull(l.sqlParams()[2])
        assertEquals("📍 4.6097, -74.0817", l.label)
    }

    /**
     * Revisión (INSERT en transactions): cada columna tiene su valor y los $n del VALUES van de $1 a $12,
     * que es lo que se envía (8 de siempre + 3 de ubicación de KTP-7 + la hora real de captura de KTP-8).
     * Lee el texto del SQL desde el código. Al integrar KTP-7 con KTP-8 se pasó de $11 a $12 y se separan
     * los valores por comas de primer nivel (COALESCE(…, NOW()) lleva una coma dentro).
     */
    @Test fun insertDeTransaccionTieneTantosParametrosComoValores() {
        val src = listOf(
            File("src/main/java/co/eskala/bktpj_notification/NeonRepository.kt"),
            File("app/src/main/java/co/eskala/bktpj_notification/NeonRepository.kt"),
        ).first { it.exists() }.readText()
        val q = Regex("""INSERT INTO transactions \((.*?)\) VALUES \((.*?)\)\s*ON CONFLICT""", RegexOption.DOT_MATCHES_ALL)
            .find(src)!!
        val cols = q.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val vals = splitTopLevel(q.groupValues[2]).map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals("columnas vs valores", cols.size, vals.size)
        val placeholders = Regex("""\$(\d+)""").findAll(q.groupValues[2]).map { it.groupValues[1].toInt() }.toSet()
        assertEquals((1..12).toSet(), placeholders)
        assertEquals("\$9::double precision", vals[cols.indexOf("location_lat")])
        assertEquals("\$10::double precision", vals[cols.indexOf("location_lng")])
        assertEquals("\$11::text", vals[cols.indexOf("location_address")])
        // Y el orden de sqlParams es lat, lng, dirección.
        assertEquals(listOf<Any?>(1.0, 2.0, "x"), TxLocation(1.0, 2.0, address = "x").sqlParams())
    }

    private fun splitTopLevel(s: String): List<String> {
        val out = mutableListOf<String>(); var depth = 0; val cur = StringBuilder()
        for (ch in s) {
            when {
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth--; cur.append(ch) }
                ch == ',' && depth == 0 -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }
}
