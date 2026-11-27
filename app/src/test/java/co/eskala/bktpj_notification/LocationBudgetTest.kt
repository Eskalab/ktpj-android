package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** KTP-7 (hallazgos QA 1, 2, 5): cuánto espera una captura por la ubicación y si pide un fix nuevo. */
class LocationBudgetTest {

    private fun plan(enabled: Boolean = true, fg: Boolean = true, bg: Boolean = true, inForeground: Boolean) =
        LocationBudget.plan(enabled, fg, bg, inForeground)

    @Test fun apagadoOSinPermisoNoBusca() {
        assertNull(plan(enabled = false, inForeground = true))
        assertNull(plan(enabled = false, inForeground = false))
        assertNull(plan(fg = false, inForeground = true))
        assertNull(plan(fg = false, bg = true, inForeground = false))
    }

    @Test fun gastoManualEnPantallaEsRapido() {
        val b = plan(inForeground = true)!!
        assertSame(LocationBudget.FOREGROUND, b)
        assertTrue(b.allowFresh)
        assertTrue(b.totalMs <= 3_000)
        // En primer plano no hace falta el permiso de segundo plano.
        assertSame(LocationBudget.FOREGROUND, plan(bg = false, inForeground = true))
    }

    @Test fun segundoPlanoConPermisoTopeDeDosSegundos() {
        val b = plan(bg = true, inForeground = false)!!
        assertSame(LocationBudget.BACKGROUND, b)
        assertTrue(b.allowFresh)
        assertTrue(b.totalMs <= 2_000)
        assertTrue(b.freshMs < b.totalMs)
    }

    @Test fun segundoPlanoSinPermisoSoloUltimoConocido() {
        val b = plan(bg = false, inForeground = false)!!
        assertSame(LocationBudget.LAST_KNOWN_ONLY, b)
        assertFalse(b.allowFresh)
        assertEquals(0, b.freshMs)
        assertTrue(b.totalMs <= 1_000)
    }

    @Test fun ningunPresupuestoPoneEnRiesgoElSms() {
        // goAsync() da ~10 s; la red de Neon necesita la mayor parte.
        listOf(LocationBudget.FOREGROUND, LocationBudget.BACKGROUND, LocationBudget.LAST_KNOWN_ONLY).forEach {
            assertTrue(it.totalMs <= 3_000)
            assertTrue(it.freshMs <= it.totalMs)
            assertTrue(it.geocodeMs <= it.totalMs)
        }
    }

    @Test fun laDireccionUsaSoloLoQueQueda() {
        val b = LocationBudget.FOREGROUND   // 3 000 total, 1 000 dirección
        assertEquals(1_000, b.geocodeTimeout(0))
        assertEquals(1_000, b.geocodeTimeout(2_000))
        assertEquals(500, b.geocodeTimeout(2_500))
        assertEquals(0, b.geocodeTimeout(3_000))
        assertEquals(0, b.geocodeTimeout(5_000))
    }
}
