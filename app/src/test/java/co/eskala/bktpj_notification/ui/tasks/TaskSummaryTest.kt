package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.TaskProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Resumen de una línea en la barra de "Nueva tarea" (KTP-11 B). */
class TaskSummaryTest {
    @Test fun soloFecha() {
        assertEquals("Sin fecha", taskSummaryLine(null, false, null, 0, null))
        assertEquals("Hoy", taskSummaryLine("Hoy", false, null, 0, null))
    }

    @Test fun todoPuesto() {
        assertEquals("Hoy · ★ · LAV · 🍅2 · ↗ Waze", taskSummaryLine("Hoy", true, "LAV", 2, "Waze"))
        assertEquals("5 nov · 🍅1", taskSummaryLine("5 nov", false, null, 1, null))
    }

    @Test fun vaciosNoSeMuestran() {
        assertEquals("Mañana", taskSummaryLine("Mañana", false, "  ", 0, ""))
        assertEquals("Mañana", taskSummaryLine("Mañana", false, null, -1, null))
    }

    @Test fun enPalabras() {
        assertEquals("Se guardará: sin fecha, sin prioridad", taskSummarySpoken(null, false, null, 0, null))
        assertEquals(
            "Se guardará: Hoy, prioritaria, épica LAV, 1 pomodoro, abre Waze",
            taskSummarySpoken("Hoy", true, "LAV", 1, "Waze"),
        )
        assertEquals("Se guardará: Hoy, sin prioridad, 3 pomodoros", taskSummarySpoken("Hoy", false, null, 3, null))
    }

    @Test fun nombreCortoDeEpica() {
        assertNull(epicShortName(null))
        assertEquals("LAV", epicShortName(TaskProject("1", "Lavandería", null, prefix = "LAV")))
        assertEquals("Lavandería", epicShortName(TaskProject("1", "Lavandería", null, prefix = " ")))
        assertEquals("Lavandería", epicShortName(TaskProject("1", "Lavandería", null)))
    }
}
