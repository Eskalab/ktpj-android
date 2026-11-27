package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitPauseTest {
    private fun habit(pauses: List<HabitPause>, vararg done: String, days: Set<Int> = emptySet()) =
        Habit("h", "Leer", null, days, 10, "páginas", null, null, null, done.associateWith { 10 }, pauses)

    private fun day(d: String) = HabitDates.parse(d).apply { set(Calendar.HOUR_OF_DAY, 12) }

    // ── Etiqueta ──

    @Test fun etiquetaIdaYVuelta() {
        val p = HabitPause("2026-11-27", "2026-12-05", "viaje")
        assertEquals("habit_pause:2026-11-27:2026-12-05:viaje", p.toLabel())
        assertEquals(p, HabitPause.parse(p.toLabel()))
    }

    @Test fun etiquetaSinFinNiMotivo() {
        val open = HabitPause("2026-11-27", null, "enfermo")
        assertEquals("habit_pause:2026-11-27::enfermo", open.toLabel())
        assertEquals(open, HabitPause.parse(open.toLabel()))
        val bare = HabitPause("2026-11-27", null, null)
        assertEquals(bare, HabitPause.parse(bare.toLabel()))
        assertEquals(bare, HabitPause.parse("habit_pause:2026-11-27"))   // tolera partes faltantes
    }

    @Test fun etiquetasQueNoSonPausa() {
        assertNull(HabitPause.parse("habit_goal:10"))
        assertNull(HabitPause.parse("habit_pause:mañana::viaje"))
        assertNull(HabitPause.parse("habit_pause:2026-11-27:pronto:viaje"))
    }

    @Test fun textoDeLaPausa() {
        assertEquals("⏸ En pausa hasta 5 dic · Viaje", HabitPause("2026-11-27", "2026-12-05", "viaje").label("2026-11-27"))
        assertEquals("⏸ En pausa", HabitPause("2026-11-27", null, null).label("2026-11-27"))
        assertEquals("⏸ En pausa hasta 3 ene 2027 · Descanso", HabitPause("2026-12-27", "2027-01-03", "descanso").label("2026-12-27"))
    }

    // ── isPausedOn ──

    @Test fun enPausaIncluyeInicioYFin() {
        val h = habit(listOf(HabitPause("2026-11-10", "2026-11-12")))
        assertFalse(h.isPausedOn("2026-11-09"))
        assertTrue(h.isPausedOn("2026-11-10"))
        assertTrue(h.isPausedOn("2026-11-12"))
        assertFalse(h.isPausedOn("2026-11-13"))   // termina sola
    }

    @Test fun pausaSinFinSigue() {
        val h = habit(listOf(HabitPause("2026-11-10", null)))
        assertTrue(h.isPausedOn("2027-05-01"))
        assertFalse(h.isPausedOn("2026-11-09"))
        assertFalse(h.dueOn(day("2026-11-20")))
    }

    // ── Racha, mejor racha, mes ──

    @Test fun losDiasEnPausaNoRompenLaRacha() {
        // Hecho 1-4, pausa 5-8 (viaje), hecho 9 y 10
        val h = habit(listOf(HabitPause("2026-11-05", "2026-11-08", "viaje")),
            "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-09", "2026-11-10")
        assertEquals(6, h.streak(day("2026-11-10")))
        assertEquals(6, h.streak(day("2026-11-11")))   // hoy pendiente no rompe
        assertEquals(6, h.bestStreak(day("2026-11-11")))
        // Sin la pausa, el 5 rompía
        assertEquals(2, h.copy(pauses = emptyList()).streak(day("2026-11-10")))
    }

    @Test fun pausaQueCubreHoy() {
        val h = habit(listOf(HabitPause("2026-11-05", null)), "2026-11-02", "2026-11-03", "2026-11-04")
        assertEquals(3, h.streak(day("2026-11-07")))
        assertFalse(h.dueOn(day("2026-11-07")))
        // Marcar en pausa cuenta normal
        assertEquals(4, h.copy(checks = h.checks + ("2026-11-07" to 10)).streak(day("2026-11-07")))
    }

    @Test fun variasPausas() {
        val pauses = listOf(HabitPause("2026-11-03", "2026-11-04", "enfermo"), HabitPause("2026-11-07", "2026-11-08", "viaje"))
        val h = habit(pauses, "2026-11-01", "2026-11-02", "2026-11-05", "2026-11-06", "2026-11-09")
        assertEquals(5, h.streak(day("2026-11-09")))
        assertEquals(5, h.bestStreak(day("2026-11-09")))
        // Falló el 10 (sin pausa): rompe
        assertEquals(0, h.streak(day("2026-11-11")))
        assertEquals(5, h.bestStreak(day("2026-11-11")))
    }

    @Test fun elMesNoCuentaDiasEnPausa() {
        val h = habit(listOf(HabitPause("2026-11-03", "2026-11-04")), "2026-11-01", "2026-11-02", "2026-11-05", "2026-11-06")
        assertEquals(4 to 5, h.monthRate(day("2026-11-07")))
        val open = habit(listOf(HabitPause("2026-11-05", null)), "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04")
        assertEquals(4 to 4, open.monthRate(day("2026-11-07")))
    }

    @Test fun pausaConDiasQueNoTocan() {
        // Entre semana; pausa lunes 9 a miércoles 11; hecho jue 5, vie 6, jue 12
        val h = habit(listOf(HabitPause("2026-11-09", "2026-11-11")), "2026-11-05", "2026-11-06", "2026-11-12", days = setOf(1, 2, 3, 4, 5))
        assertEquals(3, h.streak(day("2026-11-12")))
    }

    // ── Pausar / reanudar ──

    @Test fun pausarAgregaYConservaHistorial() {
        val old = HabitPause("2026-10-01", "2026-10-03", "viaje")
        val list = HabitPause.add(listOf(old), "2026-11-27", "2026-12-05", "Viaje")
        assertEquals(listOf(old, HabitPause("2026-11-27", "2026-12-05", "viaje")), list)
        // Pausar de nuevo reemplaza la que cubre hoy
        assertEquals(listOf(old, HabitPause("2026-11-27", null, null)), HabitPause.add(list, "2026-11-27", null, null))
    }

    @Test fun reanudarTerminaAyer() {
        val p = HabitPause("2026-11-20", null, "descanso")
        val r = HabitPause.resume(listOf(HabitPause("2026-10-01", "2026-10-03"), p), "2026-11-27")
        assertEquals(listOf(HabitPause("2026-10-01", "2026-10-03"), p.copy(end = "2026-11-26")), r)
        val h = habit(r)
        assertFalse(h.isPausedOn("2026-11-27"))
        assertTrue(h.isPausedOn("2026-11-26"))
    }

    @Test fun reanudarElMismoDiaBorraLaPausa() {
        assertEquals(emptyList<HabitPause>(), HabitPause.resume(listOf(HabitPause("2026-11-27", "2026-12-05")), "2026-11-27"))
    }

    @Test fun fechaCorta() {
        assertEquals("5 dic", HabitDates.short("2026-12-05", "2026-11-27"))
        assertEquals("2026-12-04", HabitDates.plusDays("2026-11-27", 7))
    }
}
