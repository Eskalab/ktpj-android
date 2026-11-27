package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.Calendar

/**
 * Casos de QA de KTP-10 (pausar hábitos), escritos primero como caja negra en qa/casos/KTP-10.md.
 * Cada prueba lleva el ID del caso. Fechas y datos inventados.
 */
class HabitPauseCasosTest {
    private fun habit(pauses: List<HabitPause>, vararg done: String, days: Set<Int> = emptySet()) =
        Habit("h", "Leer", null, days, 10, "páginas", null, null, null, done.associateWith { 10 }, pauses)

    private fun at(d: String, hour: Int = 12, minute: Int = 0) =
        HabitDates.parse(d).apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }

    /** Días seguidos desde [from] (inclusive), [n] días. */
    private fun run(from: String, n: Int) = (0 until n).map { HabitDates.plusDays(from, it) }.toTypedArray()

    // KTP-10-02: en pausa 4 días y al reanudar la racha sigue en 6
    @Test fun reanudarTrasCuatroDiasDePausaConservaLaRacha() {
        val done = run("2026-11-01", 6)                           // 1…6 nov
        val paused = HabitPause.add(emptyList(), "2026-11-07", null, "viaje")
        val resumed = HabitPause.resume(paused, "2026-11-11")    // pausa 7…10
        assertEquals(listOf(HabitPause("2026-11-07", "2026-11-10", "viaje")), resumed)
        val h = habit(resumed, *done)
        assertEquals(6, h.streak(at("2026-11-11")))
        assertTrue("al reanudar, hoy vuelve a tocar", h.dueOn(at("2026-11-11")))
    }

    // KTP-10-05: con fecha de regreso, se reanuda sola al día siguiente del último día en pausa
    @Test fun pausaConFechaSeTerminaSola() {
        val h = habit(HabitPause.add(emptyList(), "2026-11-26", "2026-12-02", null))
        assertFalse(h.dueOn(at("2026-12-02")))
        assertTrue(h.dueOn(at("2026-12-03")))
        assertNull(h.pauseOn("2026-12-03"))
    }

    // KTP-10-08: entre semana, pausa de viernes a lunes; ni el fin de semana ni la pausa rompen la racha
    @Test fun entreSemanaConPausaDeViernesALunes() {
        val weekdays = setOf(1, 2, 3, 4, 5)
        // mié 25 y jue 26 hechos; pausa vie 27 … lun 30; mar 1 dic hecho
        val h = habit(listOf(HabitPause("2026-11-27", "2026-11-30", "viaje")),
            "2026-11-25", "2026-11-26", "2026-12-01", days = weekdays)
        assertEquals(3, h.streak(at("2026-12-01")))
        assertEquals(3, h.bestStreak(at("2026-12-01")))
    }

    // KTP-10-10: los 5 días en pausa no cuentan como fallados en el % del mes
    @Test fun mesConCincoDiasDePausaLosExcluye() {
        val pause = HabitPause("2026-11-10", "2026-11-14")
        val done = run("2026-11-01", 9) + run("2026-11-15", 6)   // 1…9 y 15…20
        assertEquals(15 to 15, habit(listOf(pause), *done).monthRate(at("2026-11-20")))
    }

    // KTP-10-11: marcado hoy y pausado hoy → el ✓ de hoy se conserva y cuenta
    @Test fun pausarDespuesDeMarcarHoyConservaElCheck() {
        val today = "2026-11-26"
        val h = habit(HabitPause.add(emptyList(), today, null, null), *run("2026-11-22", 5))
        assertTrue(h.doneOn(today))
        assertEquals(5, h.streak(at(today, 21)))
    }

    // KTP-10-12: sin marcar hoy y pausado hoy → hoy cuenta como pausa, no rompe
    @Test fun pausarHoySinMarcarNoRompeLaRacha() {
        val h = habit(HabitPause.add(emptyList(), "2026-11-26", null, null), *run("2026-11-21", 5))
        assertFalse(h.dueOn(at("2026-11-26", 23, 30)))
        assertEquals(5, h.streak(at("2026-11-26", 23, 30)))
        assertEquals(5, h.streak(at("2026-11-27")))   // la pausa sigue mañana
    }

    // KTP-10-13: pausa de un solo día y reanudar al otro día
    @Test fun pausaDeUnDia() {
        val resumed = HabitPause.resume(HabitPause.add(emptyList(), "2026-11-07", null, null), "2026-11-08")
        assertEquals(listOf(HabitPause("2026-11-07", "2026-11-07")), resumed)
        val h = habit(resumed, *run("2026-11-01", 6))
        assertEquals(6, h.streak(at("2026-11-08")))
        assertEquals(7, h.copy(checks = h.checks + ("2026-11-08" to 10)).streak(at("2026-11-08")))
    }

    // KTP-10-14: pausar y reanudar enseguida deja todo como antes (sin días raros)
    @Test fun pausaDeCeroDiasNoDejaRastro() {
        val old = listOf(HabitPause("2026-10-01", "2026-10-03", "enfermo"))
        val after = HabitPause.resume(HabitPause.add(old, "2026-11-26", "2026-12-03", "viaje"), "2026-11-26")
        assertEquals(old, after)
        val before = habit(old, *run("2026-11-20", 6))
        val now = before.copy(pauses = after)
        assertEquals(before.streak(at("2026-11-26")), now.streak(at("2026-11-26")))
        assertTrue(now.dueOn(at("2026-11-26")))
    }

    // KTP-10-15: pausa de 60 días; racha y mejor racha intactas
    @Test fun pausaMuyLarga() {
        val done = run("2026-09-01", 6)
        val pauses = HabitPause.resume(HabitPause.add(emptyList(), "2026-09-07", null, "viaje"), "2026-11-06")
        assertEquals("2026-11-05", pauses.single().end)          // 7 sep … 5 nov = 60 días
        val h = habit(pauses, *done)
        assertEquals(6, h.streak(at("2026-11-06")))
        assertEquals(6, h.bestStreak(at("2026-11-06")))
        assertEquals(7, h.copy(checks = h.checks + ("2026-11-06" to 10)).streak(at("2026-11-06")))
    }

    // KTP-10-16: la pausa es por día local (hora del teléfono), no por UTC: a las 11:55 p.m. sigue siendo ese día
    @Test fun pausaPorDiaLocalCercaDeMedianoche() {
        val lateNight = at("2026-11-26", 23, 55)
        val start = HabitDates.iso(lateNight)
        assertEquals("2026-11-26", start)
        val h = habit(HabitPause.add(emptyList(), start, "2026-11-27", null))
        assertFalse(h.dueOn(lateNight))
        assertTrue(h.dueOn(at("2026-11-25", 23, 55)))
        assertFalse(h.dueOn(at("2026-11-27", 0, 5)))
        assertTrue(h.dueOn(at("2026-11-28", 0, 5)))
    }

    // KTP-10-17: reanudar a las 12:05 a.m. → hoy ya toca
    @Test fun reanudarDespuesDeMedianocheHoyToca() {
        val pauses = HabitPause.resume(listOf(HabitPause("2026-11-20", null, "descanso")), "2026-11-27")
        val h = habit(pauses)
        assertTrue(h.dueOn(at("2026-11-27", 0, 5)))
        assertTrue(h.isPausedOn("2026-11-26"))
    }

    // KTP-10-18: pausa que cruza de mes; cada mes excluye sus días
    @Test fun pausaQueCruzaElMes() {
        val pause = HabitPause("2026-11-28", "2026-12-03")
        val done = run("2026-11-01", 27) + arrayOf("2026-12-04", "2026-12-05")
        val h = habit(listOf(pause), *done)
        assertEquals(27 to 27, h.monthRate(at("2026-11-30")))
        assertEquals(2 to 2, h.monthRate(at("2026-12-05")))
        assertEquals(29, h.streak(at("2026-12-05")))
    }

    // KTP-10-24: fecha de regreso en el pasado → nunca queda una pausa negativa
    @Test fun fechaDeRegresoEnElPasadoNoDaPausaNegativa() {
        val p = HabitPause.add(emptyList(), "2026-11-26", "2026-11-20", null).single()
        assertEquals("2026-11-26", p.start)
        assertTrue(p.end!! >= p.start)
        assertTrue(p.covers("2026-11-26"))
    }

    // Revisión (caja blanca): el motivo no puede romper la etiqueta habit_pause:INICIO:FIN:MOTIVO
    @Test fun motivoRaroNoRompeLaEtiqueta() {
        val withColon = HabitPause.add(emptyList(), "2026-11-26", null, "viaje: Cali").single()
        assertNull(withColon.reason)
        val p = HabitPause.add(emptyList(), "2026-11-26", "2026-11-30", "  Enfermo  ").single()
        assertEquals(p, HabitPause.parse(p.toLabel()))
        assertEquals("Enfermo", p.reasonLabel)
    }

    // Revisión: el % del mes nunca debe pasar de 100% aunque se haga el hábito durante la pausa
    // ("si lo haces igual, cuenta").
    @Ignore("BUG-1: % del mes pasa de 100% si se marca el hábito durante una pausa")
    @Test fun porcentajeDelMesNoPasaDeCien() {
        // Hecho 1…4; pausa desde el 5 sin fecha; lo hace igual el 6 y el 7
        val h = habit(listOf(HabitPause("2026-11-05", null)), *run("2026-11-01", 4), "2026-11-06", "2026-11-07")
        val (done, due) = h.monthRate(at("2026-11-07"))
        assertTrue("este mes $done/$due = ${done * 100 / due}%", done <= due)
    }
}
