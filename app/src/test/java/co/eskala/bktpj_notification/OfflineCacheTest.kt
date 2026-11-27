package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCodecTest {
    @Test fun roundTripsSpecialCharactersAndNulls() {
        val fields = listOf("a\tb", null, "", "línea\nnueva\r", "barra \\ y \\0 literal", "ñ ★ 🍅")
        assertEquals(fields, OfflineCodec.parse(OfflineCodec.record(fields)))
    }

    @Test fun recordsNest() {
        val inner = OfflineCodec.record("x", null, "y\tz")
        val outer = OfflineCodec.record("cabeza", inner, OfflineCodec.list(listOf(inner, inner)))
        val f = OfflineCodec.parse(outer)
        assertEquals(listOf("x", null, "y\tz"), OfflineCodec.parse(f[1]!!))
        assertEquals(2, OfflineCodec.parseList(f[2]).size)
        assertEquals(listOf("x", null, "y\tz"), OfflineCodec.parse(OfflineCodec.parseList(f[2])[1]))
    }

    @Test fun recordHasNoRawNewlines() {
        assertFalse(OfflineCodec.record("a\nb", "c").contains('\n'))
    }

    @Test fun emptyList() {
        assertTrue(OfflineCodec.parseList(OfflineCodec.list(emptyList<String>())).isEmpty())
        assertTrue(OfflineCodec.parseList(null).isEmpty())
    }
}

class OfflineCacheTest {
    private val p = TaskProject("p1", "Pasaporte\tviejo", "#2E7D32", "PAS", total = 5, done = 2, paused = true)
    private val tasks = listOf(
        SpurTask("t1", "Llamar", "detalle\ncon salto", "2026-11-26", priority = true, app = "com.x", project = p, key = "PAS-3", started = true, pomos = 3),
        SpurTask("t2", "Sin nada", null, null),
    )
    private val habit = Habit("h1", "Leer", "SP-9", setOf(1, 3, 5), 10, "páginas", 20, null, p, mapOf("2026-11-25" to 10, "2026-11-26" to null))

    @Test fun snapshotRoundTrips() {
        val snap = CacheSnapshot(tasks = tasks, doneDay = "2026-11-26", doneToday = tasks.take(1), projects = listOf(p), habits = listOf(habit))
        assertEquals(snap, CacheCodec.decode(CacheCodec.encode(snap)))
    }

    @Test fun emptyAndMissingSectionsStayDistinct() {
        val snap = CacheSnapshot(tasks = emptyList(), habits = null)
        val back = CacheCodec.decode(CacheCodec.encode(snap))!!
        assertEquals(emptyList<SpurTask>(), back.tasks)
        assertNull(back.habits)
        assertNull(back.projects)
    }

    @Test fun habitWithoutChecksOrDays() {
        val h = Habit("h2", "Agua", null, emptySet(), null, null, null, "com.y", null, emptyMap())
        assertEquals(h, CacheCodec.parseHabit(CacheCodec.habit(h)))
    }

    @Test fun garbageDecodesToNull() {
        assertNull(CacheCodec.decode("v0\tlo-que-sea"))
        assertNull(CacheCodec.decode("v1\tbasura"))
    }
}

class CaptureRetryPolicyTest {
    private val net = CaptureRetryPolicy.Outcome.NETWORK
    private val server = CaptureRetryPolicy.Outcome.SERVER
    private val ok = CaptureRetryPolicy.Outcome.OK

    @Test fun networkFailureQueuesWithoutNotifying() {
        val (l, notify) = CaptureRetryPolicy.onResult(emptyList(), "c1", net, 0)
        assertEquals(listOf(CaptureRetry("c1")), l)
        assertFalse(notify)
        // Otro fallo de red no cuenta intentos
        assertEquals(0, CaptureRetryPolicy.onResult(l, "c1", net, 5).first.single().attempts)
    }

    @Test fun firstServerErrorNotifiesLikeBefore() {
        val (l, notify) = CaptureRetryPolicy.onResult(emptyList(), "c1", server, 0)
        assertTrue(l.isEmpty())
        assertTrue(notify)
    }

    @Test fun queuedCaptureParksAfterMaxServerFailures() {
        var l = CaptureRetryPolicy.onResult(emptyList(), "c1", net, 0).first
        var notify = false
        repeat(OutboxPolicy.MAX_ATTEMPTS) { i ->
            val r = CaptureRetryPolicy.onResult(l, "c1", server, 100L + i)
            l = r.first; notify = r.second
            assertEquals(i == OutboxPolicy.MAX_ATTEMPTS - 1, notify)
        }
        assertTrue(l.single().parked)
        assertTrue(CaptureRetryPolicy.due(l, Long.MAX_VALUE).isEmpty())
    }

    @Test fun successClears() {
        val l = CaptureRetryPolicy.onResult(emptyList(), "c1", net, 0).first
        assertTrue(CaptureRetryPolicy.onResult(l, "c1", ok, 1).first.isEmpty())
    }

    @Test fun dueRespectsBackoff() {
        val l = listOf(CaptureRetry("a"), CaptureRetry("b", attempts = 1, lastAttemptAt = 1000))
        assertEquals(listOf("a"), CaptureRetryPolicy.due(l, 1001).map { it.id })
        assertEquals(listOf("a", "b"), CaptureRetryPolicy.due(l, 1000 + OutboxPolicy.backoffMs(1)).map { it.id })
    }

    @Test fun serializationRoundTrips() {
        val l = listOf(CaptureRetry("a"), CaptureRetry("b\tc", 3, 99, parked = true))
        assertEquals(l, CaptureRetryPolicy.decodeAll(CaptureRetryPolicy.encodeAll(l)))
    }
}

class OfflineErrorTest {
    @Test fun networkErrorsAreDetectedThroughCauses() {
        assertTrue(OfflineSync.isNetworkError(Exception("Unable to resolve host", java.net.UnknownHostException("x"))))
        assertTrue(OfflineSync.isNetworkError(Exception("Timeout al conectar con Neon", java.net.SocketTimeoutException())))
        assertTrue(OfflineSync.isNetworkError(java.net.ConnectException()))
        assertFalse(OfflineSync.isNetworkError(Exception("relation \"habit_checks\" does not exist")))
        assertFalse(OfflineSync.isNetworkError(null))
    }
}
