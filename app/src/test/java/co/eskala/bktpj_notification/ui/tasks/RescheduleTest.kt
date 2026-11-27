package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.SpurTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** KTP-13: reprogramar tareas atrasadas (Hoy · Mañana · Otra) desde la tarjeta. */
class RescheduleTest {
    private val today = LocalDate.of(2026, 11, 26)

    private fun task(id: String, due: String?, key: String? = null, title: String = "Tarea $id") =
        SpurTask(id, title, null, due, key = key)

    @Test fun fechasDeLosAtajos() {
        assertEquals("2026-11-26", quickDueDate(QuickDue.HOY, today))
        assertEquals("2026-11-27", quickDueDate(QuickDue.MANANA, today))
        // Cambio de mes y de año.
        assertEquals("2026-12-01", quickDueDate(QuickDue.MANANA, LocalDate.of(2026, 11, 30)))
        assertEquals("2027-01-01", quickDueDate(QuickDue.MANANA, LocalDate.of(2026, 12, 31)))
    }

    @Test fun queCuentaComoAnterior() {
        assertTrue(isOverdueOn("2026-11-25", today))
        assertTrue(isOverdueOn("2025-01-01", today))
        assertFalse(isOverdueOn("2026-11-26", today))   // hoy no es anterior
        assertFalse(isOverdueOn("2026-11-27", today))
        assertFalse(isOverdueOn(null, today))           // sin fecha
        assertFalse(isOverdueOn("basura", today))       // fecha mala no se marca
    }

    @Test fun seccionAnterioresSoloAtrasadasYOrdenadas() {
        val list = listOf(
            task("a", "2026-11-20"),
            task("b", "2026-11-26"),
            task("c", null),
            task("d", "2026-11-01"),
            task("e", "2026-12-03"),
        )
        assertEquals(listOf("d", "a"), overdueTasks(list, today).map { it.id })
        // Al pasarla a hoy sale de Anteriores.
        val moved = list.map { if (it.id == "d") it.copy(due = quickDueDate(QuickDue.HOY, today)) else it }
        assertEquals(listOf("a"), overdueTasks(moved, today).map { it.id })
    }

    @Test fun mensajesDeConfirmacion() {
        val lav = task("1", "2026-11-20", key = "LAV-3")
        assertEquals("LAV-3 → Hoy", rescheduleMessage(lav, "2026-11-26", today))
        assertEquals("LAV-3 → Mañana", rescheduleMessage(lav, "2026-11-27", today))
        assertEquals("LAV-3 → 5 dic", rescheduleMessage(lav, "2026-12-05", today))
        assertEquals("LAV-3 → Sin fecha", rescheduleMessage(lav, null, today))
        assertEquals("1 tarea → Hoy", bulkRescheduleMessage(1, "2026-11-26", today))
        assertEquals("3 tareas → Hoy", bulkRescheduleMessage(3, "2026-11-26", today))
    }

    @Test fun referenciaSinId() {
        assertEquals("Pagar luz", taskRef(task("1", null, title = "Pagar luz")))
        val long = taskRef(task("2", null, title = "Revisar Gmail: qué pasó con la entrevista"))
        assertTrue(long.endsWith("…"))
        assertTrue(long.length <= 24)
    }
}
