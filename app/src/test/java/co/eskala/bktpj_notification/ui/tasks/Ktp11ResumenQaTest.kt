package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.TaskProject
import co.eskala.bktpj_notification.ui.components.dueLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * QA KTP-11 opción B: la línea de resumen de la barra fija dice exactamente lo que se va a guardar.
 * Se arma igual que en NewTaskScreen: dueLabel(fecha) + epicShortName(épica) + taskSummaryLine(...).
 */
class Ktp11ResumenQaTest {
    private fun day(offset: Int) = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)

    private val pas = TaskProject("p1", "Pasaporte", null, prefix = "PAS")

    private fun resumen(due: String?, priority: Boolean, epic: TaskProject?, pomos: Int, app: String?) =
        taskSummaryLine(due?.let { dueLabel(it) }, priority, epicShortName(epic), pomos, app)

    // KTP-11-B2: Mañana, ★, épica PAS y 2 🍅 → el resumen muestra exactamente eso, en ese orden.
    @Test fun resumenMuestraLoElegido() {
        assertEquals("Mañana · ★ · PAS · 🍅2", resumen(day(1), true, pas, 2, null))
    }

    // KTP-11-B2: "cambia al instante": quitar la ★ o los 🍅 los saca del resumen; cambiar la fecha la cambia.
    @Test fun resumenCambiaAlCambiarOpciones() {
        assertEquals("Mañana · PAS · 🍅2", resumen(day(1), false, pas, 2, null))
        assertEquals("Mañana · ★ · PAS", resumen(day(1), true, pas, 0, null))
        assertEquals("Hoy · ★ · PAS · 🍅2", resumen(day(0), true, pas, 2, null))
        assertEquals("Sin fecha · ★ · PAS · 🍅2", resumen(null, true, pas, 2, null))
    }

    // KTP-11-04: abrir desde Inicio con Hoy precargado → el resumen ya dice Hoy sin tocar nada.
    @Test fun hoyPrecargadoSeVeEnElResumen() {
        assertEquals("Hoy", resumen(day(0), false, null, 0, null))
    }

    // KTP-11-B2 / KTP-11-24: épica sin prefijo (o en pausa) se muestra por su nombre.
    @Test fun epicaSinPrefijoUsaNombre() {
        val sinPrefijo = TaskProject("p2", "Casa", null)
        assertEquals("Mañana · Casa", resumen(day(1), false, sinPrefijo, 0, null))
    }

    // KTP-11-27: 10 pomodoros caben como "🍅10" (no diez tomates).
    @Test fun muchosPomodorosEnUnSoloTexto() {
        assertEquals("Sin fecha · 🍅10", resumen(null, false, null, 10, null))
    }

    // KTP-11-09: la app elegida aparece en el resumen.
    @Test fun appElegidaApareceEnResumen() {
        assertEquals("Hoy · ↗ Waze", resumen(day(0), false, null, 0, "Waze"))
    }

    // KTP-11-28: TalkBack lee el resumen en palabras, con todas las opciones elegidas.
    @Test fun talkBackLeeElResumenEnPalabras() {
        val dicho = taskSummarySpoken(dueLabel(day(1)), true, epicShortName(pas), 2, "Waze")
        assertEquals("Se guardará: Mañana, prioritaria, épica PAS, 2 pomodoros, abre Waze", dicho)
        assertFalse("no debe leer emojis", dicho.contains("🍅") || dicho.contains("★"))
    }

    // KTP-11-B3 (parte automática): un nombre de épica largo se pasa entero; el recorte con "…"
    // depende de la pantalla (no hay maxLines en la barra; ver revisión).
    @Test fun epicaLargaLlegaEnteraAlResumen() {
        val larga = TaskProject("p3", "Revalidar pasaporte y visa de trabajo 2027", null)
        assertTrue(resumen(day(1), true, larga, 3, null).contains("Revalidar pasaporte y visa de trabajo 2027"))
    }
}
