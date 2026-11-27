package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxPolicyTest {
    private val create = OutboxOp.CreateTask("t1", "Llamar", null, "2026-11-26", false, null, null, 2)

    private fun queue(vararg ops: OutboxOp): List<OutboxEntry> =
        ops.foldIndexed(emptyList()) { i, acc, op -> OutboxPolicy.enqueue(acc, op, i + 1L, 1000L) }

    @Test fun enqueueKeepsOrder() {
        val q = queue(create, OutboxOp.CompleteTask("t1"), OutboxOp.CheckHabit("h1", "2026-11-26", 10))
        assertEquals(listOf(1L, 2L, 3L), q.map { it.seq })
        assertTrue(q[0].op is OutboxOp.CreateTask)
    }

    @Test fun sameKeyLastWriteWinsAndMovesToEnd() {
        val q = queue(OutboxOp.SetDue("t1", "2026-11-27"), OutboxOp.CompleteTask("t2"), OutboxOp.SetDue("t1", null))
        assertEquals(2, q.size)
        assertEquals(OutboxOp.CompleteTask("t2"), q[0].op)
        assertEquals(OutboxOp.SetDue("t1", null), q[1].op)
    }

    @Test fun checkThenUncheckSameDayKeepsOnlyLast() {
        val q = queue(OutboxOp.CheckHabit("h1", "2026-11-26", 10), OutboxOp.UncheckHabit("h1", "2026-11-26"))
        assertEquals(listOf<OutboxOp>(OutboxOp.UncheckHabit("h1", "2026-11-26")), q.map { it.op })
        // Otro día no se fusiona
        assertEquals(2, queue(OutboxOp.CheckHabit("h1", "2026-11-25", 1), OutboxOp.CheckHabit("h1", "2026-11-26", 1)).size)
    }

    @Test fun repeatedCompleteIsDeduplicated() {
        assertEquals(1, queue(OutboxOp.CompleteTask("t1"), OutboxOp.CompleteTask("t1")).size)
    }

    @Test fun updateOfUnsentTaskFoldsIntoANewCreateEntry() {
        val q = queue(create, OutboxOp.UpdateTask("t1", "Llamar al técnico", "hoy", null, true, null, "p1", 3, rekey = true))
        val c = q.single().op as OutboxOp.CreateTask
        assertEquals("Llamar al técnico", c.title)
        assertEquals("p1", c.projectId)
        assertTrue(c.high)
        assertEquals(2L, q.single().seq)   // entrada nueva: la vieja (quizá en vuelo) no se toca
    }

    @Test fun foldKeepsFinalStateAndDependentsWaitForCreate() {
        val q = queue(
            create,                                        // due 26
            OutboxOp.SetDue("t1", "2026-11-27"),
            OutboxOp.SetStarted("t1", true),
            OutboxOp.UpdateTask("t1", "X", null, "2026-11-30", false, null, null, null, rekey = false),
            OutboxOp.SetPriority("t1", true),               // después de la edición: va aparte
        )
        assertEquals(listOf(3L, 4L, 5L), q.map { it.seq })
        assertEquals("2026-11-30", (q[1].op as OutboxOp.CreateTask).due)
        // Sale primero la creación aunque tenga seq mayor que "en curso"
        assertEquals(4L, OutboxPolicy.next(q, 0)?.seq)
        val sent = generateSequence(q) { cur -> OutboxPolicy.next(cur, 0)?.let { e -> cur.filterNot { it.seq == e.seq } } }
            .zipWithNext { a, b -> (a - b.toSet()).single().op }.toList()
        assertTrue(sent.first() is OutboxOp.CreateTask)
        val t = OutboxPolicy.applyToTasks(emptyList(), emptyList(), sent) { null }.first.single()
        assertEquals("2026-11-30", t.due); assertTrue(t.started); assertTrue(t.priority)
        // La vista local con la cola tal cual da lo mismo
        assertEquals(t, OutboxPolicy.applyToTasks(emptyList(), emptyList(), q.map { it.op }) { null }.first.single())
    }

    @Test fun coalescedUpdateKeepsRekey() {
        val q = queue(
            OutboxOp.UpdateTask("t1", "A", null, null, false, null, "p2", null, rekey = true),
            OutboxOp.UpdateTask("t1", "B", null, null, false, null, "p2", null, rekey = false),
        )
        assertEquals(1, q.size)
        val u = q[0].op as OutboxOp.UpdateTask
        assertEquals("B", u.title)
        assertTrue(u.rekey)
    }

    @Test fun nextIsFirstInOrder() {
        val q = queue(OutboxOp.CompleteTask("t1"), OutboxOp.CompleteTask("t2"))
        assertEquals(1L, OutboxPolicy.next(q, 2000L)?.seq)
        assertNull(OutboxPolicy.next(emptyList(), 0))
    }

    @Test fun failedEntryWaitsBackoffAndBlocksSameEntityOnly() {
        var q = queue(OutboxOp.SetDue("t1", "2026-11-27"), OutboxOp.CompleteTask("t1"), OutboxOp.CompleteTask("t2"))
        q = q.map { if (it.seq == 1L) OutboxPolicy.afterFailure(it, 10_000L, "boom") else it }
        // t1 espera su reintento: su "completar" no se adelanta; t2 sí sale
        assertEquals(3L, OutboxPolicy.next(q, 10_001L)?.seq)
        // Pasado el backoff vuelve el primero
        assertEquals(1L, OutboxPolicy.next(q, 10_000L + OutboxPolicy.backoffMs(1))?.seq)
    }

    @Test fun skipSetSkipsEntity() {
        val q = queue(OutboxOp.CompleteTask("t1"), OutboxOp.CheckHabit("h1", "2026-11-26", null))
        assertEquals(2L, OutboxPolicy.next(q, 0, skip = setOf("task:t1"))?.seq)
    }

    @Test fun poisonGetsParkedAfterMaxAttemptsAndDoesNotBlockOthers() {
        var e = queue(OutboxOp.SetDue("t1", null)).single()
        repeat(OutboxPolicy.MAX_ATTEMPTS - 1) { e = OutboxPolicy.afterFailure(e, 0, "x") }
        assertFalse(e.parked)
        e = OutboxPolicy.afterFailure(e, 0, "x")
        assertTrue(e.parked)
        val q = listOf(e) + OutboxEntry(2, OutboxOp.CompleteTask("t1"), 0)
        // Una edición apartada no detiene el "completar" de la misma tarea
        assertEquals(2L, OutboxPolicy.next(q, Long.MAX_VALUE)?.seq)
    }

    @Test fun parkedCreateBlocksItsDependents() {
        val parked = OutboxEntry(1, create, 0, attempts = 5, parked = true)
        val q = listOf(parked, OutboxEntry(2, OutboxOp.CompleteTask("t1"), 0), OutboxEntry(3, OutboxOp.CompleteTask("t9"), 0))
        assertEquals(3L, OutboxPolicy.next(q, Long.MAX_VALUE)?.seq)
    }

    @Test fun backoffGrowsAndCaps() {
        assertEquals(0L, OutboxPolicy.backoffMs(0))
        assertEquals(30_000L, OutboxPolicy.backoffMs(1))
        assertEquals(60_000L, OutboxPolicy.backoffMs(2))
        assertEquals(120_000L, OutboxPolicy.backoffMs(3))
        assertEquals(OutboxPolicy.MAX_BACKOFF_MS, OutboxPolicy.backoffMs(10))
        assertEquals(OutboxPolicy.MAX_BACKOFF_MS, OutboxPolicy.backoffMs(100))
    }

    @Test fun remapReplacesTempIdInAllTaskOps() {
        val q = queue(
            OutboxOp.CreateTask("tmp", "X", null, null, false, null, null, null),
            OutboxOp.SetStarted("tmp", true),
            OutboxOp.CompleteTask("other"),
            OutboxOp.CheckHabit("tmp", "2026-11-26", null),   // un hábito con el mismo texto no se toca
        )
        val r = OutboxPolicy.remap(q, "tmp", "real")
        assertEquals("real", (r[0].op as OutboxOp.CreateTask).id)
        assertEquals(OutboxOp.SetStarted("real", true), r[1].op)
        assertEquals(OutboxOp.CompleteTask("other"), r[2].op)
        assertEquals(OutboxOp.CheckHabit("tmp", "2026-11-26", null), r[3].op)
        assertEquals(q, OutboxPolicy.remap(q, "tmp", "tmp"))
    }

    @Test fun retryParkedResetsAttempts() {
        val q = listOf(OutboxEntry(1, create, 0, attempts = 5, lastAttemptAt = 9, parked = true))
        val r = OutboxPolicy.retryParked(q).single()
        assertFalse(r.parked)
        assertEquals(0, r.attempts)
        assertEquals(1L, OutboxPolicy.next(listOf(r), 0)?.seq)
    }

    @Test fun discardCreateAlsoDropsDependents() {
        val q = queue(create, OutboxOp.CompleteTask("t1"), OutboxOp.CompleteTask("t2"))
        assertEquals(listOf<OutboxOp>(OutboxOp.CompleteTask("t2")), OutboxPolicy.discard(q, 1).map { it.op })
        assertEquals(2, OutboxPolicy.discard(q, 2).size)
    }

    @Test fun serializationRoundTripsEveryOp() {
        val ops = listOf(
            OutboxOp.CreateTask("t1", "Título\tcon tab\ny salto", "detalle \\ raro", "2026-11-26", true, "com.app", "p1", 3),
            OutboxOp.CreateTask("t2", "Sin nada", null, null, false, null, null, null),
            OutboxOp.UpdateTask("t1", "T", "", null, false, null, null, null, rekey = true),
            OutboxOp.CompleteTask("t1"),
            OutboxOp.CompleteTask("t2", at = 1_764_200_000_000L),
            OutboxOp.SetStarted("t1", false),
            OutboxOp.SetDue("t1", null),
            OutboxOp.SetPriority("t1", true),
            OutboxOp.CheckHabit("h1", "2026-11-26", null),
            OutboxOp.CheckHabit("h1", "2026-11-25", 10),
            OutboxOp.UncheckHabit("h1", "2026-11-24"),
        )
        val entries = ops.mapIndexed { i, op -> OutboxEntry(i + 1L, op, 123L + i, attempts = i % 3, lastAttemptAt = 7L * i, lastError = if (i % 2 == 0) "err\t$i" else null, parked = i == 4) }
        assertEquals(entries, OutboxPolicy.decodeAll(OutboxPolicy.encodeAll(entries)))
    }

    @Test fun decodeSkipsUnknownOrBrokenLines() {
        val good = OutboxEntry(1, OutboxOp.CompleteTask("t1"), 5)
        val text = OutboxPolicy.encode(good) + "\nbasura\n" + OfflineCodec.record("2", OfflineCodec.record("futuro", "x"), "0", "0", "0", null, "0")
        assertEquals(listOf(good), OutboxPolicy.decodeAll(text))
    }

    @Test fun unreadableLinesAreReturnedAsRejects() {
        val good = OutboxEntry(1, OutboxOp.CompleteTask("t1"), 5)
        val (ok, bad) = OutboxPolicy.decodeWithRejects(OutboxPolicy.encode(good) + "\nbasura\n\n")
        assertEquals(listOf(good), ok)
        assertEquals(listOf("basura"), bad)
    }

    @Test fun oldCompleteLineWithoutTimeStillDecodes() {
        assertEquals(OutboxOp.CompleteTask("t1"), OutboxPolicy.decodeOp(OfflineCodec.record("done", "t1")))
    }

    // ── Vista local ──

    private val p1 = TaskProject("p1", "Pasaporte", "#2E7D32", "PAS")
    private fun task(id: String, high: Boolean = false) = SpurTask(id, "Tarea $id", null, null, priority = high)

    @Test fun overlayCreatesCompletesAndEdits() {
        val ops = listOf(
            OutboxOp.CreateTask("n1", "Nueva", null, "2026-11-26", false, null, "p1", null),
            OutboxOp.CompleteTask("a"),
            OutboxOp.SetDue("b", "2026-12-01"),
            OutboxOp.SetPriority("b", true),
            OutboxOp.SetStarted("n1", true),
        )
        val (open, done) = OutboxPolicy.applyToTasks(listOf(task("a"), task("b")), emptyList(), ops) { if (it == "p1") p1 else null }
        assertEquals(listOf("b", "n1"), open.map { it.id })   // ★ primero
        assertEquals("2026-12-01", open[0].due)
        assertTrue(open[1].started)
        assertEquals(p1, open[1].project)
        assertNull(open[1].key)
        assertEquals(listOf("a"), done.map { it.id })
    }

    @Test fun overlayIsIdempotent() {
        val ops = listOf(OutboxOp.CreateTask("n1", "Nueva", null, null, false, null, null, null), OutboxOp.CompleteTask("a"))
        val once = OutboxPolicy.applyToTasks(listOf(task("a")), emptyList(), ops) { null }
        val twice = OutboxPolicy.applyToTasks(once.first, once.second, ops) { null }
        assertEquals(once, twice)
    }

    @Test fun overlayDoesNotRecreateTaskAlreadyDone() {
        val ops = listOf(OutboxOp.CreateTask("n1", "Nueva", null, null, false, null, null, null), OutboxOp.CompleteTask("n1"))
        val (open, done) = OutboxPolicy.applyToTasks(emptyList(), emptyList(), ops) { null }
        assertTrue(open.isEmpty())
        assertEquals(listOf("n1"), done.map { it.id })
        val again = OutboxPolicy.applyToTasks(open, done, ops) { null }
        assertTrue(again.first.isEmpty())
    }

    @Test fun overlayUpdateKeepsKnownProject() {
        val t = task("a").copy(project = p1, key = "PAS-3")
        val ops = listOf(OutboxOp.UpdateTask("a", "Renovar", null, null, false, null, "p1", 2, rekey = false))
        val r = OutboxPolicy.applyToTasks(listOf(t), emptyList(), ops) { null }.first.single()
        assertEquals("Renovar", r.title)
        assertEquals(p1, r.project)
        assertEquals("PAS-3", r.key)
        assertEquals(2, r.pomos)
    }

    @Test fun overlayHabits() {
        val h = Habit("h1", "Leer", null, emptySet(), 10, "páginas", null, null, null, mapOf("2026-11-25" to 10))
        val r = OutboxPolicy.applyToHabits(
            listOf(h),
            listOf(OutboxOp.CheckHabit("h1", "2026-11-26", 12), OutboxOp.UncheckHabit("h1", "2026-11-25")),
        ).single()
        assertEquals(mapOf("2026-11-26" to 12), r.checks)
    }
}

class PendingUploadCaptureTest {
    private fun cap(status: CaptureStatus, error: String?) =
        Capture("c", 0, CaptureSource.MANUAL, NuTransaction("Manual: \$8.500", "8.500", "Tienda", "cargo", ""), status, error)

    @Test fun onlyOfflineErrorsArePendingUpload() {
        assertTrue(cap(CaptureStatus.ERROR, OfflineSync.OFFLINE_MESSAGE).pendingUpload)
        assertFalse(cap(CaptureStatus.ERROR, "Configura las columnas de destino").pendingUpload)
        assertFalse(cap(CaptureStatus.OK, null).pendingUpload)
        // Un error real de la base sigue sin contar en el balance
        assertEquals(0.0, Balance.variableOf(listOf(cap(CaptureStatus.ERROR, "boom")), emptySet()), 0.0)
    }
}
