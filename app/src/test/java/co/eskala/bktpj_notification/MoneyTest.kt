package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Montos en formato colombiano (punto de miles, coma decimal). */
class MoneyTest {
    @Test fun leeMontos() {
        assertEquals(20_600.0, Money.parse("20.600,00")!!, 0.001)
        assertEquals(45_900.0, Money.parse("45.900")!!, 0.001)
        assertEquals(1_200_000.5, Money.parse("1.200.000,50")!!, 0.001)
        assertNull(Money.parse(null))
        assertNull(Money.parse("abc"))
    }

    @Test fun escribeMontos() {
        assertEquals("20.600", Money.format(20_600.0))
        assertEquals("20.600,50", Money.format(20_600.5))
        assertEquals("45.900", Money.format(-45_900.0))   // el signo lo pone signed()
        assertEquals("0", Money.format(0.0))
    }

    @Test fun signoSegunTipo() {
        assertEquals("−45.900", Money.signed("45.900", "cargo"))
        assertEquals("+1.200.000", Money.signed("1.200.000", "abono"))
        assertEquals("45.900", Money.signed("45.900", "desconocido"))
        assertNull(Money.signed(null, "cargo"))
    }

    @Test fun sumaDelDia() {
        val total = Money.signedValue("45.900", "cargo") + Money.signedValue("100.000", "abono")
        assertEquals(54_100.0, total, 0.001)
        assertEquals(0.0, Money.signedValue("x", "cargo"), 0.001)
        assertEquals("+54.100", Money.signedTotal(total))
        assertEquals("−10", Money.signedTotal(-10.0))
        assertEquals("0", Money.signedTotal(0.0))
    }
}
