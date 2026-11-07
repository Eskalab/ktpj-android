package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceTest {
    private fun cap(merchant: String, amount: String, type: String, record: Boolean = true, ok: Boolean = true) = Capture(
        id = merchant + amount, at = 0L, source = CaptureSource.NU,
        tx = NuTransaction("Nu: $merchant", amount, merchant, type, "", recordTransaction = record),
        status = if (ok) CaptureStatus.OK else CaptureStatus.ERROR,
    )

    @Test fun rentNeverOffsetsSpending() {
        val list = listOf(
            cap("Rappi", "45.900", "cargo"),
            cap("Éxito", "120.000", "cargo"),
            cap("Arriendo Nogales", "5.000.000", "cargo"),
            cap("Panorama arriendo", "2.500.000", "abono"),
            cap("Bolsillo", "300.000", "abono", record = false),   // movimiento interno: no cuenta
            cap("Rappi", "10.000", "cargo", ok = false),            // error: no cuenta
        )
        val fixed = setOf("arriendo nogales", "panorama arriendo")
        val b = Balance.of(list, fixed)
        assertEquals(165_900.0, b.variable, 0.01)
        assertEquals(5_000_000.0, b.fixed, 0.01)
        assertEquals(2_500_000.0, b.income, 0.01)
        assertEquals(2_500_000.0, b.fixedIncome, 0.01)
        assertEquals(165_900.0, Balance.variableOf(list, fixed), 0.01)
        assertEquals("Éxito", b.topVariable.first().first)
    }

    @Test fun fixedKeyIgnoresAccentsAndCase() {
        assertEquals("exito", FixedStore.keyOf(cap("ÉXITO ", "1", "cargo")))
    }
}
