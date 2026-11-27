package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** KTP-7: elegir el fix por antigüedad / precisión, dirección corta y parámetros del INSERT. */
class TxLocationTest {

    private fun fix(acc: Float?, ageMin: Double, lat: Double = 4.6, lng: Double = -74.08) =
        LocationFix(lat, lng, acc, (ageMin * 60_000).toLong())

    @Test fun ultimoConocidoMenorA15Minutos() {
        assertNull(LocationPick.best(listOf(fix(20f, 16.0))))
        assertEquals(20f, LocationPick.best(listOf(fix(20f, 14.0)))!!.accuracyM)
        assertNull(LocationPick.best(emptyList()))
    }

    @Test fun descartaFixesInservibles() {
        assertNull(LocationPick.best(listOf(fix(10f, 1.0, lat = 0.0, lng = 0.0))))   // "isla nula"
        assertNull(LocationPick.best(listOf(fix(9_000f, 1.0))))                       // demasiado impreciso
        assertNull(LocationPick.best(listOf(LocationFix(4.6, -74.0, 10f, -5_000))))  // reloj raro
    }

    @Test fun masPrecisoGanaSiTienenEdadParecida() {
        val gps = fix(8f, 1.5)
        val red = fix(300f, 0.5)
        assertSame(gps, LocationPick.best(listOf(red, gps)))
    }

    @Test fun muchoMasNuevoGanaAunqueSeaMenosPreciso() {
        val viejo = fix(5f, 12.0)
        val nuevo = fix(150f, 1.0)
        assertSame(nuevo, LocationPick.best(listOf(viejo, nuevo)))
        assertTrue(LocationPick.isBetter(nuevo, viejo))
        assertFalse(LocationPick.isBetter(viejo, nuevo))
    }

    @Test fun empateDePrecisionGanaElMasNuevo() {
        val a = fix(30f, 1.0)
        val b = fix(30f, 0.2)
        assertSame(b, LocationPick.best(listOf(a, b)))
    }

    @Test fun suficientementeFrescoNoEsperaFixNuevo() {
        assertTrue(LocationPick.freshEnough(fix(40f, 1.0)))
        assertFalse(LocationPick.freshEnough(fix(40f, 3.0)))
        assertFalse(LocationPick.freshEnough(fix(500f, 0.1)))
        assertFalse(LocationPick.freshEnough(fix(null, 0.1)))
        assertFalse(LocationPick.freshEnough(null))
    }

    @Test fun eligeFixNuevoOUltimoConocido() {
        val nuevo = fix(25f, 0.0)
        val viejo = fix(25f, 10.0)
        assertSame(nuevo, LocationPick.choose(nuevo, viejo))
        assertSame(viejo, LocationPick.choose(null, viejo))
        assertSame(nuevo, LocationPick.choose(nuevo, null))
        assertNull(LocationPick.choose(null, null))
        // Un fix nuevo malo (sin precisión, de poco) no reemplaza a uno preciso de hace un minuto.
        val preciso = fix(10f, 1.0)
        val malo = fix(null, 0.0)
        assertSame(preciso, LocationPick.choose(malo, preciso))
    }

    @Test fun direccionCortaConCalleYNumero() {
        assertEquals("Cra 7 #45-10, Bogotá", AddressFormat.short("Carrera 7", "45-10", "Bogotá"))
        assertEquals("Cl 100 #19-61, Bogotá", AddressFormat.short("Calle 100", "#19-61", "Bogotá"))
        assertEquals("Av Boyacá, Bogotá", AddressFormat.short("Avenida Boyacá", null, "Bogotá"))
        assertEquals("Cra 7 #45", AddressFormat.short("Carrera 7", "45", null))
    }

    @Test fun direccionSinCalleUsaLineaOCiudad() {
        assertEquals(
            "Cra. 7 #45-10, Bogotá",
            AddressFormat.short(null, null, "Bogotá", addressLine = "Cra. 7 #45-10, Bogotá, Colombia", countryName = "Colombia"),
        )
        assertEquals(
            "Peaje Andes, Cundinamarca",
            AddressFormat.short(null, null, null, addressLine = "Peaje Andes, Cundinamarca, 250001", countryName = "Colombia"),
        )
        assertEquals("Chapinero", AddressFormat.short(null, null, null, subLocality = "Chapinero"))
        assertNull(AddressFormat.short(null, " ", null))
    }

    @Test fun direccionLargaSeRecorta() {
        val r = AddressFormat.short("Carrera " + "7".repeat(100), null, "Bogotá")!!
        assertTrue(r.length <= 80)
        assertTrue(r.endsWith("…"))
    }

    @Test fun etiquetaConDireccionOCoordenadas() {
        assertEquals("📍 Cra 7 #45, Bogotá", TxLocation(4.6, -74.08, address = "Cra 7 #45, Bogotá").label)
        assertEquals("📍 4.6097, -74.0817", TxLocation(4.60971, -74.08172).label)
        assertEquals("Ubicación: Cra 7 #45, Bogotá", TxLocation(4.6, -74.08, address = "Cra 7 #45, Bogotá").spokenLabel)
    }

    @Test fun parametrosSqlDeUbicacion() {
        assertEquals(listOf(null, null, null), TxLocation.sqlParams(null))
        assertEquals(listOf(4.6, -74.08, "Cra 7 #45"), TxLocation.sqlParams(TxLocation(4.6, -74.08, 12f, "Cra 7 #45")))
        assertEquals(listOf(4.6, -74.08, null), TxLocation.sqlParams(TxLocation(4.6, -74.08)))
    }
}
