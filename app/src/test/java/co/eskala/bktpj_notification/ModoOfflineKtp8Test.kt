package co.eskala.bktpj_notification

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * QA · KTP-8 (modo offline). Casos `auto` de qa/casos/KTP-8.md sobre la lógica pura de la cola
 * ([OutboxPolicy]), la caché ([CacheCodec]) y el balance ([Balance]). Datos inventados.
 */
class ModoOfflineKtp8Test {
    private val bogota = TimeZone.getTimeZone("America/Bogota")
    private lateinit var tzAntes: TimeZone

    @Before fun zona() { tzAntes = TimeZone.getDefault(); TimeZone.setDefault(bogota) }
    @After fun restaurar() { TimeZone.setDefault(tzAntes) }

    private fun crear(id: String, titulo: String = "Tarea prueba", due: String? = "2026-11-26") =
        OutboxOp.CreateTask(id, titulo, null, due, false, null, null, null)

    private fun editar(id: String, titulo: String, due: String? = "2026-11-26") =
        OutboxOp.UpdateTask(id, titulo, null, due, false, null, null, null, rekey = false)

    private fun encolar(vararg ops: OutboxOp): List<OutboxEntry> =
        ops.fold(emptyList()) { acc, op -> OutboxPolicy.enqueue(acc, op, (acc.maxOfOrNull { it.seq } ?: 0L) + 1, 1000L) }

    /** Vacía la cola como lo hace OfflineSync.flush cuando todo sale bien; devuelve lo enviado en orden. */
    private fun subirTodo(cola: List<OutboxEntry>, now: Long = 10_000L): List<OutboxOp> {
        var q = cola
        val enviados = ArrayList<OutboxOp>()
        while (true) {
            val e = OutboxPolicy.next(q, now) ?: break
            enviados += e.op
            q = q.filterNot { it.seq == e.seq }
        }
        return enviados
    }

    /** Estado final de las tareas en la base tras aplicar lo enviado, en orden. */
    private fun estadoFinal(enviados: List<OutboxOp>): Pair<List<SpurTask>, List<SpurTask>> =
        OutboxPolicy.applyToTasks(emptyList(), emptyList(), enviados) { null }

    // KTP-8-14
    @Test fun sinCambiosPendientesNoSeEnviaNada() {
        assertNull(OutboxPolicy.next(emptyList(), Long.MAX_VALUE))
        assertTrue(OutboxPolicy.decodeAll("").isEmpty())
        assertTrue(OutboxPolicy.pending(emptyList()).isEmpty())
        val t = SpurTask("t1", "Ya existente", null, null)
        assertEquals(listOf(t) to emptyList<SpurTask>(), OutboxPolicy.applyToTasks(listOf(t), emptyList(), emptyList()) { null })
    }

    // KTP-8-15
    @Test fun unCambioPendienteSubeExactamenteUnaVez() {
        val enviados = subirTodo(encolar(OutboxOp.CompleteTask("t1")))
        assertEquals(listOf<OutboxOp>(OutboxOp.CompleteTask("t1")), enviados)
    }

    // KTP-8-16 (parte automática: nada se pierde al guardar en disco y al subir)
    @Test fun cienCambiosSobrevivenAlDiscoYSubenTodosEnOrden() {
        val ops = ArrayList<OutboxOp>()
        repeat(40) { ops += crear("t$it", "Viaje día ${it % 5}\tcon tab\ny salto \\ barra ✓") }
        repeat(30) { ops += OutboxOp.CheckHabit("h${it % 3}", "2026-11-%02d".format(it + 1), it) }
        repeat(30) { ops += OutboxOp.SetPriority("viejo$it", it % 2 == 0) }
        val cola = encolar(*ops.toTypedArray())
        assertEquals(100, cola.size)
        val disco = OutboxPolicy.decodeAll(OutboxPolicy.encodeAll(cola))
        assertEquals(cola, disco)
        assertEquals(ops, subirTodo(disco))
    }

    // KTP-8-17
    @Test fun tareaCreadaAntesDeMedianocheConservaLaFechaDelDiaEnQueSeCreo() {
        // Creada el 26 a las 11:50 p.m. Bogotá con fecha "Hoy"; sube el 27 a las 12:10 a.m.
        val subida = Calendar.getInstance(bogota).apply { set(2026, Calendar.NOVEMBER, 27, 0, 10) }.timeInMillis
        val cola = OutboxPolicy.decodeAll(OutboxPolicy.encodeAll(encolar(crear("t1", due = "2026-11-26"))))
        val enviada = OutboxPolicy.next(cola, subida)!!.op as OutboxOp.CreateTask
        assertEquals("2026-11-26", enviada.due)
    }

    // KTP-8-18
    @Test fun habitoMarcadoAntesDeMedianocheQuedaEnSuDiaYNoRompeLaRacha() {
        val cola = OutboxPolicy.decodeAll(OutboxPolicy.encodeAll(encolar(OutboxOp.CheckHabit("h1", "2026-11-26", 10))))
        val op = cola.single().op as OutboxOp.CheckHabit
        assertEquals("2026-11-26", op.day)
        val h = Habit("h1", "Leer", null, emptySet(), 10, "páginas", null, null, null,
            mapOf("2026-11-24" to 10, "2026-11-25" to 10))
        val conCheck = OutboxPolicy.applyToHabits(listOf(h), listOf(op)).single()
        val manana = HabitDates.parse("2026-11-27").apply { set(Calendar.HOUR_OF_DAY, 8) }
        assertEquals(3, conCheck.streak(manana))
    }

    // KTP-8-19 (Balance del celular: cuenta por la hora en que se capturó, no por la de subida)
    @Test fun gastoDelUltimoDiaDelMesCuentaEnEseMes() {
        val ultimoDiaMesPasado = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 0)
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        val c = Capture("c1", ultimoDiaMesPasado, CaptureSource.MANUAL,
            NuTransaction("Tienda prueba", "8.500", "Tienda prueba", "cargo", ""), CaptureStatus.OK)
        assertEquals(listOf(c), Balance.month(listOf(c), -1))
        assertTrue(Balance.month(listOf(c), 0).isEmpty())
    }

    // KTP-8-03 (parte automática: el gasto sin red suma al total del día)
    @Ignore("BUG-2: un gasto guardado sin red queda como ERROR y no suma al total del día ni al balance")
    @Test fun gastoSinRedSumaAlTotalDelDia() {
        val c = Capture("c1", System.currentTimeMillis(), CaptureSource.MANUAL,
            NuTransaction("Tienda prueba", "8.500", "Tienda prueba", "cargo", ""),
            CaptureStatus.ERROR, error = OfflineSync.OFFLINE_MESSAGE)
        assertEquals(8_500.0, Balance.variableOf(listOf(c), emptySet()), 0.01)
    }

    // KTP-8-11
    @Test fun crearEditarYCompletarSinRedDejaUnaSolaTareaConElEstadoFinal() {
        val enviados = subirTodo(encolar(crear("t1", "Borrador"), editar("t1", "Título final"), OutboxOp.CompleteTask("t1")))
        assertEquals(1, enviados.count { it is OutboxOp.CreateTask })
        assertTrue(enviados.first() is OutboxOp.CreateTask)          // primero se crea, luego se completa
        assertTrue(enviados.last() is OutboxOp.CompleteTask)
        val (abiertas, hechas) = estadoFinal(enviados)
        assertTrue(abiertas.isEmpty())
        assertEquals("Título final", hechas.single().title)
    }

    // KTP-8-11 / KTP-8-26 (gana el último cambio): crear → posponer → editar fecha
    @Ignore("BUG-3: la edición que se funde en la creación queda antes de un cambio de fecha/★ hecho en medio; gana el cambio viejo")
    @Test fun editarDespuesDePosponerSinRedGanaLaUltimaFecha() {
        val cola = encolar(
            crear("t1", due = "2026-11-26"),
            OutboxOp.SetDue("t1", "2026-11-27"),          // pospone a mañana
            editar("t1", "Prueba", due = "2026-11-30"),    // luego la edita y deja el 30
        )
        val (abiertas, _) = estadoFinal(subirTodo(cola))
        assertEquals("2026-11-30", abiertas.single().due)
    }

    // KTP-8-23 (corte a mitad: el reintento lleva el mismo id, así no se duplica la tarea)
    @Test fun reintentoDeCreacionConservaElMismoId() {
        val cola = encolar(crear("uuid-fijo-1"))
        val fallo = OutboxPolicy.afterFailure(cola.single(), 2000L, "Timeout al conectar con Neon")
        val reenvio = OutboxPolicy.next(listOf(fallo), 2000L + OutboxPolicy.backoffMs(1))!!
        assertEquals("uuid-fijo-1", (reenvio.op as OutboxOp.CreateTask).id)
    }

    // KTP-8-23 · Carrera: la creación se está subiendo y en ese momento se edita la tarea.
    @Ignore("BUG-1: una edición hecha mientras se sube la creación se funde en esa entrada y se borra al confirmar")
    @Test fun edicionMientrasSeSubeLaCreacionNoSePierde() {
        var cola = encolar(crear("t1", "Borrador"))
        val enVuelo = OutboxPolicy.next(cola, 10_000L)!!                         // flush la toma y la envía
        cola = OutboxPolicy.enqueue(cola, editar("t1", "Título final"), 2, 10_001L) // el usuario edita
        cola = cola.filterNot { it.seq == enVuelo.seq }                           // la base confirma: Outbox.remove(seq)
        val pendientes = cola.map { it.op }
        assertTrue("La edición debía seguir pendiente: $pendientes",
            pendientes.any { (it as? OutboxOp.UpdateTask)?.title == "Título final" || (it as? OutboxOp.CreateTask)?.title == "Título final" })
    }

    @Ignore("BUG-1: si la creación en vuelo falla en la base, Outbox.replace la reescribe con la versión vieja y borra la edición")
    @Test fun edicionMientrasFallaLaCreacionNoSePierde() {
        var cola = encolar(crear("t1", "Borrador"))
        val enVuelo = OutboxPolicy.next(cola, 10_000L)!!
        cola = OutboxPolicy.enqueue(cola, editar("t1", "Título final"), 2, 10_001L)
        val fallo = OutboxPolicy.afterFailure(enVuelo, 10_002L, "error de la base")
        cola = cola.map { if (it.seq == fallo.seq) fallo else it }               // Outbox.replace
        assertEquals("Título final", (cola.single().op as OutboxOp.CreateTask).title)
    }

    // KTP-8-24
    @Test fun tareaCreadaSinRedNoMuestraIdHastaSubir() {
        val (abiertas, _) = OutboxPolicy.applyToTasks(emptyList(), emptyList(), listOf(crear("t1"))) { null }
        assertNull(abiertas.single().key)
    }

    // KTP-8-25 (descartar la creación pendiente quita también lo que dependía de ella)
    @Test fun descartarCreacionPendienteNoDejaNadaQueSubir() {
        val cola = encolar(crear("t1"), OutboxOp.SetPriority("t1", true), OutboxOp.CompleteTask("t1"), OutboxOp.CheckHabit("h1", "2026-11-26", null))
        val tras = OutboxPolicy.discard(cola, cola.first().seq)
        assertEquals(listOf<OutboxOp>(OutboxOp.CheckHabit("h1", "2026-11-26", null)), tras.map { it.op })
    }

    // KTP-8-28
    @Test fun errorDeBaseNoBloqueaLosDemasYQuedaVisibleParaReintentar() {
        var cola = encolar(crear("t1"), OutboxOp.CompleteTask("t1"), OutboxOp.CheckHabit("h1", "2026-11-26", 5))
        var now = 10_000L
        repeat(OutboxPolicy.MAX_ATTEMPTS) {
            val e = cola.first { it.op is OutboxOp.CreateTask }
            cola = cola.map { if (it.seq == e.seq) OutboxPolicy.afterFailure(e, now, "violates constraint") else it }
            now += OutboxPolicy.MAX_BACKOFF_MS
        }
        assertEquals(1, OutboxPolicy.parked(cola).size)
        val siguiente = OutboxPolicy.next(cola, now)!!
        assertTrue(siguiente.op is OutboxOp.CheckHabit)          // el hábito sube; completar t1 espera a su creación
        assertNull(OutboxPolicy.next(cola.filterNot { it.seq == siguiente.seq }, now))
        assertEquals(3, OutboxPolicy.retryParked(cola).let { OutboxPolicy.pending(it) }.size)
    }

    // Revisión: archivo de la cola cortado (apagón a mitad de escribir) no rompe la carga.
    @Test fun colaConUltimaLineaCortadaConservaLasDemas() {
        val texto = OutboxPolicy.encodeAll(encolar(crear("t1"), crear("t2"), crear("t3")))
        val cortado = texto.dropLast(5)
        val cargada = OutboxPolicy.decodeAll(cortado)
        assertEquals(listOf("t1", "t2"), cargada.map { it.op.taskId })
    }

    // KTP-8-12 / KTP-8-30: la caché conserva la pausa de la épica (sus tareas siguen fuera de Hoy sin red).
    @Test fun cacheConservaTareasYEpicaEnPausa() {
        val epica = TaskProject("p1", "Épica prueba", "#2E7D32", "EPI", 4, 1, paused = true)
        val t = SpurTask("t1", "En épica pausada", "desc\ncon salto", "2026-11-26", true, null, epica, "EPI-3", started = true, pomos = 2)
        val snap = CacheSnapshot(tasks = listOf(t), doneDay = "2026-11-26", doneToday = emptyList(), projects = listOf(epica))
        val leida = CacheCodec.decode(CacheCodec.encode(snap))
        assertNotNull(leida)
        assertEquals(snap, leida)
        assertFalse(leida!!.tasks!!.single().active)
    }
}
