package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Notificaciones de Nu → movimiento. */
class NuParserTest {
    @Test fun compra() {
        val tx = NuParser.parse("Nu", "Compraste \$45.900 en Rappi.")
        assertEquals("cargo", tx.type)
        assertEquals("45.900", tx.amount)
        assertEquals("Rappi", tx.merchant)
        assertEquals("Nu: \$45.900 en Rappi", tx.title)
    }

    @Test fun pago() {
        val tx = NuParser.parse("", "Pagaste \$12.000 a Juan Pérez")
        assertEquals("cargo", tx.type)
        assertEquals("12.000", tx.amount)
        assertEquals("Juan Pérez", tx.merchant)
    }

    @Test fun ingreso() {
        val tx = NuParser.parse("Nu", "Recibiste \$1.200.000 de Empresa SAS")
        assertEquals("abono", tx.type)
        assertEquals("1.200.000", tx.amount)
        assertNull(tx.merchant)
    }

    @Test fun desconocidaConMontoGuardaElMonto() {
        val tx = NuParser.parse("Tu factura", "Tu factura de \$300.000 está lista")
        assertEquals("desconocido", tx.type)
        assertEquals("300.000", tx.amount)
        assertEquals("Nu: Tu factura", tx.title)
    }

    @Test fun desconocidaSinTituloNiMonto() {
        val tx = NuParser.parse("", "Hola, tenemos novedades")
        assertEquals("desconocido", tx.type)
        assertNull(tx.amount)
        assertEquals("Notificación Nu", tx.title)
    }
}
