package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresión del temporizador: lo que queda y cómo se muestra. */
class PomodoroTest {
    private fun state(endsAt: Long?, remainingMs: Long = 0) =
        PomoState("t", "Tarea", null, PomoMode.FOCUS, endsAt, remainingMs, round = 0)

    @Test fun corriendoCuentaHastaLaHoraDeFin() {
        val s = state(endsAt = 10_000)
        assertTrue(s.running)
        assertEquals(4_000, s.remaining(now = 6_000))
    }

    @Test fun nuncaQuedaTiempoNegativo() {
        assertEquals(0, state(endsAt = 10_000).remaining(now = 99_000))
    }

    @Test fun enPausaGuardaLoQueQuedaba() {
        val s = state(endsAt = null, remainingMs = 90_000)
        assertFalse(s.running)
        assertEquals(90_000, s.remaining(now = 123_456_789))
    }

    @Test fun formatoMinutosSegundosRedondeaHaciaArriba() {
        assertEquals("25:00", Pomodoro.format(25 * 60_000L))
        assertEquals("01:00", Pomodoro.format(59_001))   // nunca muestra 00:59 cuando falta más de 59 s
        assertEquals("00:01", Pomodoro.format(1))
        assertEquals("00:00", Pomodoro.format(0))
        assertEquals("90:00", Pomodoro.format(90 * 60_000L))
    }

    @Test fun minutosSegunAjustes() {
        val c = PomoConfig(focus = 50, short = 10, long = 20, longEvery = 3)
        assertEquals(50, c.minutes(PomoMode.FOCUS))
        assertEquals(10, c.minutes(PomoMode.SHORT))
        assertEquals(20, c.minutes(PomoMode.LONG))
        assertEquals(PomoConfig(25, 5, 15, 4), PomoConfig())   // valores por defecto tipo pomofocus
    }
}
