package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class HabitTest {
    private fun habit(days: Set<Int> = emptySet(), vararg done: String) =
        Habit("h", "Leer", null, days, 10, "páginas", null, null, null, done.associateWith { 10 })

    // Sábado 7 nov 2026
    private val sat = HabitDates.parse("2026-11-07").apply { set(Calendar.HOUR_OF_DAY, 12) }

    @Test fun streakCountsBackAndTodayPendingDoesNotBreak() {
        val h = habit(emptySet(), "2026-11-04", "2026-11-05", "2026-11-06")
        assertEquals(3, h.streak(sat))                       // hoy sin hacer: no rompe
        assertEquals(4, h.copy(checks = h.checks + ("2026-11-07" to 10)).streak(sat))
    }

    @Test fun missedDayBreaksStreak() {
        assertEquals(1, habit(emptySet(), "2026-11-03", "2026-11-04", "2026-11-06").streak(sat))
    }

    @Test fun daysNotScheduledDoNotBreak() {
        // Entre semana: viernes 6, jueves 5 hechos; sábado (hoy) no toca
        val h = habit(setOf(1, 2, 3, 4, 5), "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06")
        assertEquals(5, h.streak(sat))
        val mon = HabitDates.parse("2026-11-09")
        assertEquals(5, h.streak(mon))                       // lunes sin hacer aún, el finde no rompe
    }

    @Test fun bestStreakAndMonth() {
        val h = habit(emptySet(), "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-05", "2026-11-06")
        assertEquals(3, h.bestStreak(sat))
        assertEquals(5 to 7, h.monthRate(sat))
    }

    @Test fun daysLabel() {
        assertEquals("Todos los días", HabitDates.label(emptySet()))
        assertEquals("Entre semana", HabitDates.label(setOf(1, 2, 3, 4, 5)))
        assertEquals("L X V", HabitDates.label(setOf(5, 1, 3)))
    }

    @Test fun diasProgramados() {
        val weekdays = habit(setOf(1, 2, 3, 4, 5))
        assertEquals(false, weekdays.scheduledOn(sat))                          // sábado no toca
        assertEquals(true, weekdays.scheduledOn(HabitDates.parse("2026-11-09")))  // lunes sí
        assertEquals(true, habit().scheduledOn(sat))                            // sin días = todos
    }

    @Test fun hechoUnDiaQueNoTocabaSuma() {
        // Solo entre semana, pero también lo hizo el sábado 31 oct y el domingo 1 nov
        val h = habit(setOf(1, 2, 3, 4, 5), "2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02")
        assertEquals(4, h.streak(HabitDates.parse("2026-11-02")))
    }

    @Test fun etiquetaDeMeta() {
        assertEquals("10 páginas", habit().goalLabel)
        assertEquals("10", habit().copy(unit = "").goalLabel)
        assertEquals(null, habit().copy(goal = null).goalLabel)
    }

    @Test fun sinNingunDiaHechoRachaCero() {
        assertEquals(0, habit().streak(sat))
        assertEquals(0, habit().bestStreak(sat))
    }
}
