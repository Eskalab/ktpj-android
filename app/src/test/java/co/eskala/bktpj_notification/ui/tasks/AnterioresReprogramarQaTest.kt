package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.SpurTask
import co.eskala.bktpj_notification.ui.components.dueLabel
import co.eskala.bktpj_notification.ui.components.isOverdue
import co.eskala.bktpj_notification.ui.components.isToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * QA de KTP-13 (casos en qa/casos/KTP-13.md): reprogramar tareas de Anteriores desde la tarjeta.
 * Datos inventados. "Hoy" siempre se calcula en hora de Bogotá.
 */
class AnterioresReprogramarQaTest {
    private val bogota = ZoneId.of("America/Bogota")
    private fun hoyEnBogota(instanteUtc: String): LocalDate = Instant.parse(instanteUtc).atZone(bogota).toLocalDate()

    private fun tarea(id: String, due: String?, key: String? = null, title: String = "Tarea de prueba $id") =
        SpurTask(id, title, null, due, key = key)

    // KTP-13-10: a las 12:05 a.m. de Bogotá (05:05 UTC) una tarea de ayer ya es anterior.
    @Test fun tareaDeAyerYaEsAnteriorPasadaLaMedianocheDeBogota() {
        val hoy = hoyEnBogota("2026-11-27T05:05:00Z")
        assertEquals(LocalDate.of(2026, 11, 27), hoy)
        assertTrue(isOverdueOn("2026-11-26", hoy))
        assertEquals(listOf("a"), overdueTasks(listOf(tarea("a", "2026-11-26")), hoy).map { it.id })
    }

    // KTP-13-10 (borde): a las 11:59 p.m. de Bogotá la tarea de hoy todavía no es anterior.
    @Test fun tareaDeHoyNoEsAnteriorUnMinutoAntesDeMedianoche() {
        val hoy = hoyEnBogota("2026-11-27T04:59:00Z")   // 26 nov 11:59 p.m. Bogotá
        assertFalse(isOverdueOn("2026-11-26", hoy))
    }

    // KTP-13-11: a las 8 p.m. de Bogotá ya es 1 a.m. del día siguiente en UTC; la tarea de hoy sigue en Hoy.
    @Test fun tareaDeHoyALas8pmSigueEnHoyAunqueEnUtcSeaManana() {
        val instante = "2026-11-27T01:00:00Z"
        val hoy = hoyEnBogota(instante)
        assertEquals(LocalDate.of(2026, 11, 26), hoy)
        assertFalse(isOverdueOn("2026-11-26", hoy))                 // no va a Anteriores
        assertEquals("Hoy", dueTargetLabel("2026-11-26", hoy))       // ni a Próximos días
        // Si se usara UTC, la de "ayer" según UTC se vería mal: documenta por qué debe ser Bogotá.
        val hoyUtc = Instant.parse(instante).atZone(ZoneId.of("UTC")).toLocalDate()
        assertTrue(isOverdueOn("2026-11-26", hoyUtc))
    }

    // KTP-13-12: elegir "Hoy" a las 11:58 p.m. deja la fecha de ese día; a medianoche vuelve a Anteriores.
    @Test fun hoyALas1158pmQuedaConLaFechaDeEseDia() {
        val alElegir = hoyEnBogota("2026-11-27T04:58:00Z")   // 26 nov 11:58 p.m. Bogotá
        val due = quickDueDate(QuickDue.HOY, alElegir)
        assertEquals("2026-11-26", due)
        assertFalse(isOverdueOn(due, alElegir))
        val despues = hoyEnBogota("2026-11-27T05:00:00Z")    // 27 nov 12:00 a.m. Bogotá
        assertTrue(isOverdueOn(due, despues))
    }

    // KTP-13-13: "Mañana" en cambio de mes, de año y en año bisiesto.
    @Test fun mananaEnCambioDeMesAnoYBisiesto() {
        assertEquals("2026-12-01", quickDueDate(QuickDue.MANANA, LocalDate.of(2026, 11, 30)))
        assertEquals("2027-01-01", quickDueDate(QuickDue.MANANA, LocalDate.of(2026, 12, 31)))
        assertEquals("2028-02-29", quickDueDate(QuickDue.MANANA, LocalDate.of(2028, 2, 28)))
        assertEquals("2028-03-01", quickDueDate(QuickDue.MANANA, LocalDate.of(2028, 2, 29)))
        assertEquals("Mañana", dueTargetLabel("2027-01-01", LocalDate.of(2026, 12, 31)))
    }

    // KTP-13-08: sin tareas, Anteriores queda vacía y no se rompe.
    @Test fun anterioresVaciaNoSeRompe() {
        val hoy = LocalDate.of(2026, 11, 26)
        assertTrue(overdueTasks(emptyList(), hoy).isEmpty())
        assertTrue(overdueTasks(listOf(tarea("a", null), tarea("b", "2026-11-26")), hoy).isEmpty())
    }

    // KTP-13-02/03/05/06: al reprogramar una sola, sale de Anteriores y las demás se quedan en su orden.
    @Test fun reprogramarUnaNoMueveLasOtras() {
        val hoy = LocalDate.of(2026, 11, 26)
        val lista = listOf(tarea("a", "2026-11-01"), tarea("b", "2026-11-10"), tarea("c", "2026-11-20"))
        for (nueva in listOf(quickDueDate(QuickDue.HOY, hoy), quickDueDate(QuickDue.MANANA, hoy), null)) {
            val movida = lista.map { if (it.id == "b") it.copy(due = nueva) else it }
            assertEquals(listOf("a", "c"), overdueTasks(movida, hoy).map { it.id })
        }
    }

    // KTP-13-14: una tarea atrasada hace un año sigue en Anteriores (la más vieja primero).
    @Test fun tareaDeHaceUnAnoSigueEnAnterioresPrimero() {
        val hoy = LocalDate.of(2026, 11, 26)
        val lista = listOf(tarea("reciente", "2026-11-25"), tarea("vieja", "2025-11-26"))
        assertEquals(listOf("vieja", "reciente"), overdueTasks(lista, hoy).map { it.id })
    }

    // KTP-13-14: la fecha de otro año debería mostrar el año (si no, "26 nov" de 2025 parece de este año).
    @Ignore("BUG-2: dueLabel y dueTargetLabel no muestran el año en fechas de otro año (\"26 nov\" en vez de \"26 nov 2025\")")
    @Test fun fechaDeOtroAnoMuestraElAno() {
        val hoy = LocalDate.of(2026, 11, 26)
        assertTrue(dueTargetLabel("2025-11-26", hoy).contains("2025"))
        assertTrue(dueLabel("2025-11-26").contains("2025"))
    }

    // KTP-13-15: una fecha pasada elegida en "Otra…" deja la tarea en Anteriores y el aviso lo dice.
    @Test fun fechaPasadaElegidaQuedaEnAnteriores() {
        val hoy = LocalDate.of(2026, 11, 26)
        assertTrue(isOverdueOn("2026-11-24", hoy))
        assertEquals("24 nov", dueTargetLabel("2026-11-24", hoy))
    }

    // Revisión: Anteriores usa isOverdueOn (java.time) y Hoy/Próximos usan isOverdue/isToday (SimpleDateFormat).
    // Con las fechas que llegan de la base (YYYY-MM-DD) cada tarea debe caer en una sola sección.
    @Test fun cadaTareaCaeEnUnaSolaSeccion() {
        val hoy = LocalDate.now()
        for (offset in -400L..400L) {
            val due = hoy.plusDays(offset).toString()
            val enAnteriores = isOverdueOn(due, hoy)
            val enHoy = isToday(due)
            val enProximos = !isOverdue(due) && !isToday(due)
            assertEquals("fecha $due en ${listOf(enAnteriores, enHoy, enProximos)}", 1, listOf(enAnteriores, enHoy, enProximos).count { it })
            assertEquals("isOverdue y isOverdueOn difieren en $due", isOverdue(due), enAnteriores)
        }
    }

    // KTP-13-27: el nombre que oye TalkBack incluye la tarea: su ID, o el título recortado a 24 caracteres.
    @Test fun referenciaDeLaTareaParaTalkBackYAvisos() {
        assertEquals("LAV-3", taskRef(tarea("1", null, key = "LAV-3")))
        val exacto = "x".repeat(24)
        assertEquals(exacto, taskRef(tarea("2", null, title = exacto)))
        val largo = taskRef(tarea("3", null, title = "Comprar regalo de cumpleaños para la tía"))
        assertEquals(24, largo.length)
        assertTrue(largo.endsWith("…"))
        assertFalse(largo.dropLast(1).endsWith(" "))   // sin espacio antes de los puntos suspensivos
    }

    // KTP-13 (atajo "Todas a hoy"): singular y plural del aviso.
    @Test fun avisoDeVariasTareas() {
        val hoy = LocalDate.of(2026, 11, 26)
        assertEquals("1 tarea → Mañana", bulkRescheduleMessage(1, "2026-11-27", hoy))
        assertEquals("12 tareas → Hoy", bulkRescheduleMessage(12, "2026-11-26", hoy))
    }
}
