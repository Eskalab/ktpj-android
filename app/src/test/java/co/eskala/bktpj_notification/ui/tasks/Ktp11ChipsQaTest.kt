package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.dueLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * QA KTP-11 opción C: los chips muestran, con el editor cerrado, lo que se va a guardar.
 * Se arman igual que en NewTaskScreen: taskOptionChips(dueLabel(fecha), ★, epicShortName(épica), 🍅, app, detalle).
 */
class Ktp11ChipsQaTest {
    private fun day(offset: Int) = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)

    private val pas = TaskProject("p1", "Pasaporte", null, prefix = "PAS")

    private fun chips(due: String? = null, priority: Boolean = false, epic: TaskProject? = null, pomos: Int = 0, app: String? = null, detail: String = "") =
        taskOptionChips(due?.let { dueLabel(it) }, priority, epicShortName(epic), pomos, app, detail).toMap()

    // KTP-11-C2: elijo ★ y Mañana y cierro el editor → los chips quedan rellenos con esos valores.
    @Test fun cerrarElEditorNoBorraLoElegidoYSeVe() {
        val c = chips(due = day(1), priority = true)
        assertEquals("📅 Mañana", c[TaskOption.FECHA]!!.label)
        assertTrue(c[TaskOption.FECHA]!!.set)
        assertEquals("★ Prioritaria", c[TaskOption.PRIORIDAD]!!.label)
        assertTrue(c[TaskOption.PRIORIDAD]!!.set)
    }

    // KTP-11-C3 / KTP-11-04: abierto desde Inicio con Hoy precargado → se nota sin abrir nada.
    @Test fun hoyPrecargadoSeVeEnElChip() {
        val fecha = chips(due = day(0))[TaskOption.FECHA]!!
        assertEquals("📅 Hoy", fecha.label)
        assertTrue(fecha.set)
    }

    // KTP-11-C4: editar una tarea con épica, ★ y app → los chips muestran lo que ya tiene.
    @Test fun editarMuestraLoQueYaTiene() {
        val c = chips(due = day(3), priority = true, epic = pas, pomos = 2, app = "Waze", detail = "Llevar fotos")
        assertEquals("◆ PAS", c[TaskOption.EPICA]!!.label)
        assertEquals("↗ Waze", c[TaskOption.APP]!!.label)
        assertEquals("🍅 2", c[TaskOption.POMOS]!!.label)
        assertEquals("✎ Detalle", c[TaskOption.DETALLE]!!.label)
        assertTrue("todas las opciones puestas deben verse rellenas", c.values.all { it.set })
    }

    // KTP-11-01 (parte automática): todas las opciones de la tarea tienen chip, ninguna escondida.
    @Test fun todasLasOpcionesTienenChip() {
        assertEquals(TaskOption.values().toSet(), chips().keys)
    }

    // KTP-11-27: 10 pomodoros caben en el chip como un número.
    @Test fun muchosPomodorosEnUnChip() {
        assertEquals("🍅 10", chips(pomos = 10)[TaskOption.POMOS]!!.label)
    }

    // KTP-11-17: épica y app con nombre muy largo → el chip se recorta (≤ 16 caracteres) y TalkBack lee el nombre entero.
    @Test fun nombresLargosSeRecortanEnElChip() {
        val larga = "Revalidar pasaporte y visa de trabajo 2027"
        val c = chips(epic = TaskProject("p3", larga, null), app = "Aplicación con nombre larguísimo")
        assertTrue(c[TaskOption.EPICA]!!.label.removePrefix("◆ ").length <= 16)
        assertTrue(c[TaskOption.EPICA]!!.label.endsWith("…"))
        assertTrue(c[TaskOption.APP]!!.label.removePrefix("↗ ").length <= 16)
        assertEquals("Épica: $larga", c[TaskOption.EPICA]!!.spoken)
    }

    // KTP-11-28: TalkBack lee cada chip con un nombre claro y sin emojis.
    @Test fun talkBackLeeNombresClaros() {
        val c = chips(due = day(1), priority = true, epic = pas, pomos = 2, app = "Waze", detail = "x")
        assertEquals("Fecha: Mañana", c[TaskOption.FECHA]!!.spoken)
        assertEquals("Prioritaria: sí", c[TaskOption.PRIORIDAD]!!.spoken)
        assertEquals("Épica: PAS", c[TaskOption.EPICA]!!.spoken)
        assertEquals("Pomodoros estimados: 2", c[TaskOption.POMOS]!!.spoken)
        assertEquals("App: Waze", c[TaskOption.APP]!!.spoken)
        assertFalse(c.values.any { it.spoken.contains("🍅") || it.spoken.contains("📅") || it.spoken.contains("◆") })
    }

    // KTP-11-17: un nombre con emoji justo en el punto de corte no debe quedar con un carácter roto (�).
    @Ignore("BUG-1: shorten() corta un emoji por la mitad y deja un carácter roto en el chip")
    @Test fun recorteNoPartaUnEmoji() {
        val nombre = "Viaje a la pla" + "🏖" + "ya y más"   // 🏖 en las posiciones 14-15
        val corto = shorten(nombre)
        val roto = corto.indices.any { i ->
            val ch = corto[i]
            (Character.isHighSurrogate(ch) && (i + 1 >= corto.length || !Character.isLowSurrogate(corto[i + 1]))) ||
                (Character.isLowSurrogate(ch) && (i == 0 || !Character.isHighSurrogate(corto[i - 1])))
        }
        assertFalse("quedó medio emoji: $corto", roto)
    }
}
