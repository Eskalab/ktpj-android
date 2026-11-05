package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos reales de SMS de Davivienda (87188), ago–nov 2026. */
class DaviviendaSmsTest {

    private val at = 1_793_418_037_000L
    private fun parse(s: String) = DaviviendaSms.parse(s, at)

    @Test fun compraConTarjeta() {
        val tx = parse("DAVIVIENDA: Compra . Aprobado(a), \$268,650, Tarjeta *8066, Hora 17:50,Lugar LOS ANDES SUPERMERCADO.")!!
        assertEquals("268.650", tx.amount)
        assertEquals("LOS ANDES SUPERMERCADO", tx.merchant)
        assertEquals("cargo", tx.type)
        assertEquals("TC Crédito 8066", tx.account)
        assertEquals("8066", tx.last4)
        assertTrue(tx.recordTransaction)
    }

    @Test fun pseDesdeAhorros() {
        val tx = parse("DAVIVIENDA Descuento en Internet, \$546,000, Cta de Ahorros *8204,Hora 00:44,Lugar PSE SECRETARIA DE HACIEND.")!!
        assertEquals("546.000", tx.amount)
        assertEquals("PSE SECRETARIA DE HACIEND", tx.merchant)
        assertEquals("Ahorros 8204", tx.account)
    }

    @Test fun transferenciaALlave() {
        val tx = parse("DAVIVIENDA: Descuento Transferencia a una llave \$499,000, Cta de Ahorros *8204, Hora 19:53, Lugar App Davivienda.")!!
        assertEquals("499.000", tx.amount)
        assertEquals("Transferencia a llave", tx.merchant)
        assertEquals("Ahorros 8204", tx.account)
    }

    @Test fun compraConLlave() {
        val tx = parse("DAVIVIENDA: Descuento Compra con llave \$97,500, Cta de Ahorros *8204, Hora 17:02, Lugar INVERSIONES DBC.")!!
        assertEquals("INVERSIONES DBC", tx.merchant)
    }

    @Test fun retiro() {
        val tx = parse("DAVIVIENDA: Retiro \$400,000, Cta de Ahorros *8204, Hora 12:44, Lugar AV JIMENEZ BOGOTA.")!!
        assertEquals("Retiro AV JIMENEZ BOGOTA", tx.merchant)
        assertEquals("400.000", tx.amount)
    }

    @Test fun depositoSoloTarjeta() {
        val tx = parse("DAVIVIENDA: Deposito \$38,845,000, Cta de Ahorros *8204, Hora 10:06, Lugar BTA CONTADOR.")!!
        assertEquals("abono", tx.type)
        assertEquals("38.845.000", tx.amount)
        assertFalse(tx.recordTransaction)
    }

    @Test fun ignorados() {
        listOf(
            "DAVIVIENDA: Compra . Declinada(o) Tarjeta Bloqueada, \$63,198, Tarjeta *8066, Hora 15:32,Lugar ANTHROPIC* CLAUDE SUB .",
            "DAVIVIENDA: Compra . Declinada(o) Fondos Insuficientes, \$70,850, Tarjeta *8066, Hora 18:21,Lugar AUTOSERVICIO SANTANDER.",
            "DAVIVIENDA: Compra Reversada(o) , \$300, Tarjeta *8066, Hora 16:06,Lugar DLO*DiDi CO Payin (Reg.",
            "DAVIVIENDA: Transaccion Declinada,CVV errado, \$5,000, Tarjeta *8066. Verifique los datos en la App Davivienda.",
            "DAVIVIENDA: Descuento Transferencia Bolsillo a Cuenta, \$2,200,000, Cta de Ahorros *4792, Hora 13:05, Lugar App Davivienda.",
            "DAVIVIENDA: Descuento Pago Tarjeta de Credito, \$2,200,000, Cta de Ahorros *8204, Hora 13:05, Lugar App Davivienda.",
            "DAVIVIENDA: Avance . Aprobado(a), \$40,000, Tarjeta *8066, Hora 22:50,Lugar AUTORIZ DAVIPAYTRE DAV.",
            "DAVIVIENDA: Abono Uso Tarjeta de Credito, \$1,535,000, Cta de Ahorros *8204,Hora 23:00,Lugar App Davivienda.",
            "Ha ingresado exitosamente a la App Davivienda - 06:09:20",
            "Apreciado cliente, el codigo para autenticar su conversacion con el Contact Center de Davivienda es: 432166.",
        ).forEach { assertNull(it, parse(it)) }
    }

    @Test fun mismoSmsMismoId() {
        val s = "DAVIVIENDA: Compra . Aprobado(a), \$8,000, Tarjeta *8066, Hora 13:50,Lugar SAN ROQUE PROPIEDAD HO."
        assertEquals(parse(s)!!.messageId, DaviviendaSms.parse(s, at + 5_000)!!.messageId)
        assertNotEquals(parse(s)!!.messageId, DaviviendaSms.parse(s, at + 3_600_000)!!.messageId)
    }

    @Test fun remitente() {
        assertTrue(DaviviendaSms.isFromDavivienda("87188", "lo que sea"))
        assertTrue(DaviviendaSms.isFromDavivienda("+57 300", "DAVIVIENDA: Compra ..."))
        assertFalse(DaviviendaSms.isFromDavivienda("890059", "Un paso mas cerca de sus suenios! Davivienda le informa"))
    }
}
