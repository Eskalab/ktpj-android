package co.eskala.bktpj_notification

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// ── Capturas (Nu / Davivienda / manual / atajos) que fallaron por falta de red ──

/** Reintento automático de una captura del historial. */
data class CaptureRetry(
    val id: String,
    val attempts: Int = 0,        // fallos del servidor (los de red no cuentan)
    val lastAttemptAt: Long = 0,
    val parked: Boolean = false,  // dejó de reintentarse sola; sigue en el historial con Reintentar
)

/** Reglas puras del reintento de capturas (probadas en CaptureRetryPolicyTest). */
object CaptureRetryPolicy {
    enum class Outcome { OK, NETWORK, SERVER }

    /**
     * Resultado de guardar la captura [id]. Devuelve el nuevo estado y si hay que avisar el error con la
     * notificación de siempre (Reintentar / Descartar): solo cuando no es de red y ya no se va a reintentar sola.
     */
    fun onResult(list: List<CaptureRetry>, id: String, outcome: Outcome, now: Long): Pair<List<CaptureRetry>, Boolean> {
        val cur = list.firstOrNull { it.id == id }
        val rest = list.filterNot { it.id == id }
        return when (outcome) {
            Outcome.OK -> rest to false
            Outcome.NETWORK -> (rest + (cur?.copy(parked = false) ?: CaptureRetry(id))) to false
            Outcome.SERVER -> {
                if (cur == null) return list to true   // primer intento con red: error normal, como antes
                val n = cur.attempts + 1
                val parked = n >= OutboxPolicy.MAX_ATTEMPTS
                (rest + cur.copy(attempts = n, lastAttemptAt = now, parked = parked)) to parked
            }
        }
    }

    fun due(list: List<CaptureRetry>, now: Long): List<CaptureRetry> =
        list.filter { !it.parked && (it.attempts == 0 || it.lastAttemptAt + OutboxPolicy.backoffMs(it.attempts) <= now) }

    fun encodeAll(list: List<CaptureRetry>): String = list.joinToString("\n") {
        OfflineCodec.record(it.id, it.attempts.toString(), it.lastAttemptAt.toString(), if (it.parked) "1" else "0")
    }

    fun decodeAll(text: String): List<CaptureRetry> = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        runCatching { OfflineCodec.parse(line).let { f -> CaptureRetry(f[0]!!, f[1]!!.toInt(), f[2]!!.toLong(), f[3] == "1") } }.getOrNull()
    }.toList()
}

/** Capturas por subir, en disco (filesDir/capture_retry.txt). El payload sigue en [CaptureLog]. */
object PendingCaptures {
    private const val FILE = "capture_retry.txt"
    private val _list = MutableStateFlow<List<CaptureRetry>>(emptyList())
    val list: StateFlow<List<CaptureRetry>> = _list.asStateFlow()
    @Volatile private var loaded = false

    fun load(ctx: Context): List<CaptureRetry> = synchronized(this) {
        if (!loaded) {
            _list.value = runCatching { file(ctx).takeIf { it.exists() }?.readText()?.let(CaptureRetryPolicy::decodeAll) }.getOrNull().orEmpty()
            loaded = true
        }
        _list.value
    }

    /** Registra el resultado; true = mostrar la notificación de error. */
    fun onResult(ctx: Context, id: String, outcome: CaptureRetryPolicy.Outcome): Boolean = synchronized(this) {
        val (next, notify) = CaptureRetryPolicy.onResult(load(ctx), id, outcome, System.currentTimeMillis())
        save(ctx, next)
        notify
    }

    fun update(ctx: Context, f: (List<CaptureRetry>) -> List<CaptureRetry>) = synchronized(this) { save(ctx, f(load(ctx))) }

    private fun save(ctx: Context, list: List<CaptureRetry>) {
        _list.value = list
        OfflineFiles.writeAtomic(file(ctx), CaptureRetryPolicy.encodeAll(list))
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
}

/**
 * KTP-8 · Modo sin conexión. Envía la cola ([Outbox]) y las capturas pendientes cuando hay red:
 * con la app abierta (callback de ConnectivityManager) y con la app cerrada (WorkManager con red).
 */
object OfflineSync {
    const val OFFLINE_MESSAGE = "Sin conexión · se sube sola al volver la red"
    private const val WORK_NAME = "spur_outbox"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Red según el sistema (null = aún no se sabe). */
    private val _online = MutableStateFlow<Boolean?>(null)
    val online: StateFlow<Boolean?> = _online.asStateFlow()

    /** La última lectura de la base falló por red: lo que se ve es la caché. */
    val readFailed = MutableStateFlow(false)

    /** Hay una pasada de envío en curso (para el "Enviando…" del botón Reintentar). */
    val flushing = MutableStateFlow(false)

    /** Falla de red (sin internet, modo avión, DNS, timeout…) y no un error de la base. */
    fun isNetworkError(t: Throwable?): Boolean {
        var e = t
        var depth = 0
        while (e != null && depth++ < 8) {
            if (e is IOException) return true
            e = e.cause
        }
        return false
    }

    /** Registra el callback de red (una vez por proceso). Al volver la red sube lo pendiente y recarga. */
    fun start(ctx: Context) {
        if (_online.value != null) return
        val app = ctx.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        _online.value = cm.getNetworkCapabilities(cm.activeNetwork)?.let(::usable) ?: false
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = setOnline(app, usable(caps))
                override fun onLost(network: Network) = setOnline(app, false)
            })
        }
    }

    private fun usable(caps: NetworkCapabilities) =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private fun setOnline(app: Context, now: Boolean) {
        val was = _online.value
        _online.value = now
        if (now && was == false) scope.launch { SpurTasks.sync(app) }   // sync sube la cola y luego lee
    }

    /** Tras un cambio local: intenta subirlo ya y deja programado el envío para cuando haya red. */
    fun kick(ctx: Context) {
        val app = ctx.applicationContext
        scheduleWork(app)
        scope.launch {
            val r = flush(app)
            if (r.refresh) SpurTasks.sync(app)   // la tarea nueva (o cambiada de épica) trae su ID de la base
        }
    }

    fun scheduleWork(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        runCatching { WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, req) }
    }

    data class FlushResult(val sent: Int, val refresh: Boolean, val networkDown: Boolean, val remaining: Int)

    /** Una pasada: capturas primero, luego la cola en orden. Para en el primer error de red. */
    suspend fun flush(ctx: Context): FlushResult = mutex.withLock {
        if (!Prefs.isConfigured(ctx)) return FlushResult(0, false, false, 0)
        flushing.value = true
        try {
            var sent = 0
            var refresh = false
            var down = false

            CaptureLog.load(ctx)
            for (p in CaptureRetryPolicy.due(PendingCaptures.load(ctx), System.currentTimeMillis())) {
                val c = CaptureLog.get(ctx, p.id)
                if (c == null || c.status == CaptureStatus.OK) {   // borrada del historial o ya subida a mano
                    PendingCaptures.update(ctx) { l -> l.filterNot { it.id == p.id } }
                    continue
                }
                SpurNotifications.cancelError(ctx, c.id)
                val saved = CardSaver.save(ctx, c.tx, c.source, captureId = c.id, at = c.at, notify = c.source != CaptureSource.MANUAL)
                if (saved.status == CaptureStatus.OK) sent++
                else if (saved.error == OFFLINE_MESSAGE) { down = true; break }
            }

            val skip = HashSet<String>()
            while (!down) {
                val e = OutboxPolicy.next(Outbox.load(ctx), System.currentTimeMillis(), skip) ?: break
                val r = send(ctx, e.op)
                when {
                    r.isSuccess -> {
                        Outbox.remove(ctx, e.seq)
                        r.getOrNull()?.let { realId -> e.op.taskId?.let { tmp -> if (realId != tmp) Outbox.update(ctx) { OutboxPolicy.remap(it, tmp, realId) } } }
                        sent++
                        if (e.op is OutboxOp.CreateTask || (e.op as? OutboxOp.UpdateTask)?.rekey == true) refresh = true
                    }
                    isNetworkError(r.exceptionOrNull()) -> down = true
                    else -> {
                        Outbox.replace(ctx, OutboxPolicy.afterFailure(e, System.currentTimeMillis(), r.exceptionOrNull()?.message))
                        skip += e.op.entity   // lo demás de esa misma cosa espera; el resto de la cola sigue
                    }
                }
            }
            if (down) readFailed.value = true
            FlushResult(sent, refresh, down, OutboxPolicy.pending(Outbox.load(ctx)).size + CaptureRetryPolicy.due(PendingCaptures.load(ctx), Long.MAX_VALUE).size)
        } finally {
            flushing.value = false
        }
    }

    /** Envía un cambio. En éxito devuelve el id real de la tarea (o null si no aplica). */
    private suspend fun send(ctx: Context, op: OutboxOp): Result<String?> = when (op) {
        is OutboxOp.CreateTask -> NeonRepository.insertTask(ctx, op.title, op.description, op.due, op.high, op.app, op.projectId, op.pomos, id = op.id)
        is OutboxOp.UpdateTask -> NeonRepository.updateTask(ctx, op.id, op.title, op.description, op.due, op.high, op.app, op.projectId, op.pomos)
            .mapCatching { if (op.rekey) NeonRepository.assignKey(ctx, op.id, op.projectId).getOrThrow(); op.id }
        is OutboxOp.CompleteTask -> NeonRepository.markTaskDone(ctx, op.id, op.at).map { op.id }
        is OutboxOp.SetStarted -> NeonRepository.setTaskStarted(ctx, op.id, op.started).map { op.id }
        is OutboxOp.SetDue -> NeonRepository.setTaskDue(ctx, op.id, op.due).map { op.id }
        is OutboxOp.SetPriority -> NeonRepository.setTaskPriority(ctx, op.id, op.high).map { op.id }
        is OutboxOp.CheckHabit -> NeonRepository.checkHabit(ctx, op.habitId, op.day, op.amount).map { null }
        is OutboxOp.UncheckHabit -> NeonRepository.uncheckHabit(ctx, op.habitId, op.day).map { null }
    }

    /** "Reintentar": lo apartado (cola y capturas) vuelve a intentarse ya. */
    fun retryFailed(ctx: Context) {
        Outbox.update(ctx, OutboxPolicy::retryParked)
        PendingCaptures.update(ctx) { l -> l.map { it.copy(parked = false, attempts = 0, lastAttemptAt = 0) } }
        kick(ctx)
    }

    /** Descarta un cambio apartado de la cola y recarga para quitar su efecto de la pantalla. */
    fun discard(ctx: Context, seq: Long) {
        Outbox.update(ctx) { OutboxPolicy.discard(it, seq) }
        val app = ctx.applicationContext
        scope.launch { SpurTasks.sync(app) }
    }

    /** Deja de reintentar sola una captura (sigue en el historial con su Reintentar). */
    fun discardCapture(ctx: Context, id: String) = PendingCaptures.update(ctx) { l -> l.filterNot { it.id == id } }

    /** Con la app cerrada: corre cuando hay red. Si queda algo esperando su reintento, vuelve a programarse. */
    class OutboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val r = flush(applicationContext)
            if (r.refresh) SpurTasks.sync(applicationContext)
            return if (r.networkDown || r.remaining > 0) Result.retry() else Result.success()
        }
    }
}
