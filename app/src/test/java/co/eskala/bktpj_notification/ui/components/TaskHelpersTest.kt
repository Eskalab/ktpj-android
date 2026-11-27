package co.eskala.bktpj_notification.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Fechas de las tareas ("Hoy", "Mañana", vencida) y prefijos de épica (LAV-3). */
class TaskHelpersTest {
    private fun day(offset: Int) = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)

    @Test fun etiquetasDeFecha() {
        assertEquals("Hoy", dueLabel(day(0)))
        assertEquals("Mañana", dueLabel(day(1)))
        assertEquals("Ayer", dueLabel(day(-1)))
        assertEquals("5 nov", dueLabel("2020-11-05"))
        assertEquals("no-es-fecha", dueLabel("no-es-fecha"))
    }

    @Test fun vencidaYDeHoy() {
        assertTrue(isToday(day(0)))
        assertFalse(isOverdue(day(0)))
        assertTrue(isOverdue(day(-1)))
        assertFalse(isOverdue(day(3)))
        assertFalse(isOverdue("basura"))   // una fecha mala no se marca como vencida
        assertFalse(isToday("basura"))
    }

    @Test fun limpiarPrefijo() {
        assertEquals("LAVAN", cleanPrefix("Lavandería 123"))   // tildes fuera, máximo 5
        assertEquals("NINO", cleanPrefix("ni ño"))
        assertEquals("AB1", cleanPrefix("a-b_1!"))
        assertEquals("", cleanPrefix("   "))
    }

    @Test fun sugerirPrefijo() {
        assertEquals("LAV", suggestPrefix("Lavandería", emptySet()))
        assertEquals("LRO", suggestPrefix("Lava ropa", setOf("LAV")))      // inicial + siguiente palabra
        assertEquals("LA2", suggestPrefix("Lavandería", setOf("LAV")))     // una sola palabra: número
        assertEquals("LA3", suggestPrefix("Lavandería", setOf("LAV", "LA2")))
        assertEquals("", suggestPrefix("¡¡!!", emptySet()))
    }
}
