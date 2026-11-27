package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.TaskProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Etiquetas de los chips de opciones en "Nueva tarea" (KTP-11 C). */
class TaskOptionChipsTest {
    private fun chips(due: String? = null, priority: Boolean = false, epic: String? = null, pomos: Int = 0, app: String? = null, detail: String = "") =
        taskOptionChips(due, priority, epic, pomos, app, detail).toMap()

    @Test fun ordenFijo() {
        assertEquals(
            listOf(TaskOption.FECHA, TaskOption.PRIORIDAD, TaskOption.EPICA, TaskOption.POMOS, TaskOption.APP, TaskOption.DETALLE),
            taskOptionChips(null, false, null, 0, null, "").map { it.first },
        )
    }

    @Test fun vacios() {
        val c = chips()
        assertEquals("📅 Sin fecha", c[TaskOption.FECHA]!!.label)
        assertEquals("☆ Prioritaria", c[TaskOption.PRIORIDAD]!!.label)
        assertEquals("+ Épica", c[TaskOption.EPICA]!!.label)
        assertEquals("+ 🍅", c[TaskOption.POMOS]!!.label)
        assertEquals("+ App", c[TaskOption.APP]!!.label)
        assertEquals("+ Detalle", c[TaskOption.DETALLE]!!.label)
        assertTrue(c.values.none { it.set })
    }

    @Test fun conValores() {
        val c = chips(due = "Hoy", priority = true, epic = "LAV", pomos = 2, app = "Waze", detail = "llamar")
        assertEquals("📅 Hoy", c[TaskOption.FECHA]!!.label)
        assertEquals("★ Prioritaria", c[TaskOption.PRIORIDAD]!!.label)
        assertEquals("◆ LAV", c[TaskOption.EPICA]!!.label)
        assertEquals("🍅 2", c[TaskOption.POMOS]!!.label)
        assertEquals("↗ Waze", c[TaskOption.APP]!!.label)
        assertEquals("✎ Detalle", c[TaskOption.DETALLE]!!.label)
        assertTrue(c.values.all { it.set })
    }

    @Test fun lecturaTalkBack() {
        val vacio = chips()
        assertEquals("Fecha: sin fecha", vacio[TaskOption.FECHA]!!.spoken)
        assertEquals("Prioritaria: no", vacio[TaskOption.PRIORIDAD]!!.spoken)
        assertEquals("Épica: sin épica", vacio[TaskOption.EPICA]!!.spoken)
        assertEquals("Pomodoros estimados: sin estimar", vacio[TaskOption.POMOS]!!.spoken)
        assertEquals("App: ninguna", vacio[TaskOption.APP]!!.spoken)
        assertEquals("Detalle: vacío", vacio[TaskOption.DETALLE]!!.spoken)
        assertEquals("Épica: Revalidar pasaporte", chips(epic = "Revalidar pasaporte")[TaskOption.EPICA]!!.spoken)
    }

    @Test fun nombresLargosYBlancos() {
        assertEquals("◆ Revalidar pasap…", chips(epic = "Revalidar pasaporte")[TaskOption.EPICA]!!.label)
        assertFalse(chips(epic = " ", app = "", detail = "  ")[TaskOption.EPICA]!!.set)
        assertEquals("+ App", chips(app = "")[TaskOption.APP]!!.label)
        assertEquals("+ Detalle", chips(detail = "  ")[TaskOption.DETALLE]!!.label)
        assertEquals("Corto", shorten("  Corto "))
    }

    @Test fun nombreCortoDeEpica() {
        assertNull(epicShortName(null))
        assertEquals("LAV", epicShortName(TaskProject("1", "Lavandería", null, prefix = "LAV")))
        assertEquals("Lavandería", epicShortName(TaskProject("1", "Lavandería", null)))
    }
}
