package co.eskala.bktpj_notification

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

object NeonRepository {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private suspend fun sql(ctx: Context, query: String, params: List<Any?>): Result<JSONObject> =
        sql(Prefs.getNeonConfig(ctx), query, params)

    private suspend fun sql(cfg: NeonConfig, query: String, params: List<Any?>): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val host = cfg.host
                val connStr = cfg.connectionString

                val body = JSONObject().apply {
                    put("query", query)
                    put("params", JSONArray().also { arr -> params.forEach { arr.put(it) } })
                }.toString().toRequestBody(JSON_MEDIA)

                val req = Request.Builder()
                    .url("https://$host/sql")
                    .addHeader("Neon-Connection-String", connStr)
                    .post(body)
                    .build()

                val resp     = http.newCall(req).execute()
                val respBody = resp.body?.string() ?: "{}"

                if (resp.isSuccessful) Result.success(JSONObject(respBody))
                else Result.failure(Exception(neonError(resp.code, respBody)))
            } catch (e: SocketTimeoutException) {
                Result.failure(Exception("Timeout al conectar con Neon"))
            } catch (e: Throwable) {
                Result.failure(Exception(e.message ?: e.toString()))
            }
        }

    // Neon responde {"message": "..."} en los errores; mostramos eso en vez del JSON crudo.
    private fun neonError(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
        return if (msg.isNullOrBlank()) "HTTP $code: $body" else msg
    }

    suspend fun testConnection(ctx: Context): Result<Unit> =
        sql(ctx, "SELECT 1", emptyList()).map { }

    data class ConnectionCheck(val columnCount: Int, val millis: Long)

    /** Prueba una configuración sin guardarla (onboarding 02): cuenta columnas y mide la latencia. */
    suspend fun checkConnection(cfg: NeonConfig): Result<ConnectionCheck> {
        val start = System.nanoTime()
        return sql(cfg, "SELECT COUNT(*)::int AS n FROM columns", emptyList()).mapCatching { obj ->
            val millis = (System.nanoTime() - start) / 1_000_000
            val n = obj.getJSONArray("rows").getJSONObject(0).get("n").toString().toInt()
            ConnectionCheck(n, millis)
        }
    }

    suspend fun fetchColumns(ctx: Context): Result<List<Pair<String, String>>> =
        sql(
            ctx,
            """SELECT c.id, b.title || ' / ' || c.title AS label
               FROM columns c JOIN boards b ON b.id = c.board_id
               ORDER BY b.title, c."order" """,
            emptyList()
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                row.getString("id") to row.getString("label")
            }
        }

    data class NeonColumn(val id: String, val title: String, val board: String, val cardCount: Int)

    /** Columnas de tableros no archivados con su conteo de tarjetas (selector de columnas, 03). */
    suspend fun fetchColumnsWithCounts(ctx: Context): Result<List<NeonColumn>> =
        sql(
            ctx,
            """SELECT c.id, c.title, b.title AS board,
                      COUNT(k.id) FILTER (WHERE NOT k.archived)::int AS n
               FROM columns c
               JOIN boards b ON b.id = c.board_id
               LEFT JOIN cards k ON k.column_id = c.id
               WHERE NOT b.archived
               GROUP BY c.id, c.title, c."order", b.title
               ORDER BY b.title, c."order" """,
            emptyList()
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                NeonColumn(
                    id = row.getString("id"),
                    title = row.getString("title"),
                    board = row.getString("board"),
                    cardCount = row.get("n").toString().toInt(),
                )
            }
        }

    /**
     * Crea la tarjeta en [columnId] y, si [withTransaction], la fila en `transactions`.
     * Falla si no se pudo crear la tarjeta (el registro en `transactions` es secundario).
     */
    suspend fun insertCard(
        ctx: Context,
        tx: NuTransaction,
        columnId: String,
        source: CaptureSource = CaptureSource.NU,
        withTransaction: Boolean = true,
    ): Result<String> {
        val cardId = UUID.randomUUID().toString()
        val txId   = UUID.randomUUID().toString()
        // Con id estable (SMS) un reintento o un SMS repetido no duplica: ON CONFLICT DO NOTHING.
        // La tarjeta de Hoy (withTransaction = false) lleva sufijo para no chocar con la de Inbox.
        val srcId  = tx.messageId?.let { if (withTransaction) it else "${it}_hoy" }
            ?: "nu_${System.currentTimeMillis()}_${cardId.take(8)}"

        val cardQuery = """
            WITH next_ord AS (
                SELECT COALESCE(MAX("order"), -1) + 1 AS n
                FROM cards WHERE column_id = $1
            )
            INSERT INTO cards (
                id, title, description, "order", priority, labels,
                column_id, source_message_id, source_from, source_type,
                recurrence, recurrence_days, done, starred, archived, created_at
            )
            SELECT $2, $3, $4, n, 'MEDIUM', '{}',
                   $1, $5, $6, $7,
                   'NONE', '{}', false, false, false, NOW()
            FROM next_ord
            ON CONFLICT (source_message_id) DO NOTHING
        """.trimIndent()

        val txQuery = """
            INSERT INTO transactions (
                id, message_id, account, bank, amount, currency,
                merchant, last4, occurred_at, received_at, status, raw_snippet, created_at
            ) VALUES (
                $1, $2, $6, $7, $3, 'COP',
                $4, $8, NOW(), NOW(), 'UNREVIEWED', $5, NOW()
            )
            ON CONFLICT (message_id) DO NOTHING
        """.trimIndent()

        val amount = tx.amount
            ?.replace(".", "")
            ?.replace(",", ".")
            ?.toDoubleOrNull() ?: 0.0

        val (sourceFrom, sourceType) = when (source) {
            CaptureSource.NU -> "nu_colombia" to "push_notification"
            CaptureSource.MANUAL -> "manual" to "manual"
            CaptureSource.DAVIVIENDA -> "davivienda_sms" to "sms"
        }
        val (account, bank) = when (source) {
            CaptureSource.NU -> "Nu Colombia" to "Nu"
            CaptureSource.MANUAL -> "Manual" to "Manual"
            CaptureSource.DAVIVIENDA -> (tx.account ?: "Davivienda") to "Davivienda"
        }

        val card = sql(ctx, cardQuery, listOf(columnId, cardId, tx.title, tx.rawText, srcId, sourceFrom, sourceType))
        if (card.isFailure) return Result.failure(card.exceptionOrNull()!!)
        if (withTransaction && tx.recordTransaction) {
            sql(ctx, txQuery, listOf(txId, srcId, amount, tx.merchant ?: account, tx.rawText, account, bank, tx.last4))
        }

        return Result.success(cardId)
    }

    data class ColumnCount(val title: String, val count: Int)

    /** Título y tarjetas pendientes (no archivadas ni hechas) de las columnas de Inbox y Hoy (04 Inicio). */
    suspend fun fetchColumnCounts(ctx: Context, inboxId: String, hoyId: String): Result<Map<String, ColumnCount>> =
        sql(
            ctx,
            """SELECT c.id, c.title,
                      COUNT(k.id) FILTER (WHERE NOT k.archived AND NOT k.done)::int AS n
               FROM columns c
               LEFT JOIN cards k ON k.column_id = c.id
               WHERE c.id IN ($1, $2)
               GROUP BY c.id, c.title""",
            listOf(inboxId, hoyId.ifEmpty { inboxId })
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).associate { i ->
                val row = rows.getJSONObject(i)
                row.getString("id") to ColumnCount(row.getString("title"), row.get("n").toString().toInt())
            }
        }

    /** Columnas comunes de una tarea (alias c = cards, p = projects). */
    private const val TASK_COLS = """c.id, c.title, c.description, to_char(c.due_date, 'YYYY-MM-DD') AS due,
                      c.priority IN ('HIGH', 'CRITICAL') AS high,
                      (SELECT substring(l FROM 5) FROM unnest(c.labels) l WHERE l LIKE 'app:%' LIMIT 1) AS app,
                      c.project_id, p.title AS project, p.color AS project_color, p.prefix AS project_prefix,
                      c.key, c.started_at IS NOT NULL AS started, c.done"""

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }

    private fun taskFromRow(row: JSONObject) = SpurTask(
        id = row.getString("id"),
        title = row.getString("title"),
        description = row.str("description"),
        due = row.str("due"),
        priority = row.optBoolean("high", false),
        app = row.str("app"),
        project = row.str("project_id")?.let { TaskProject(it, row.str("project").orEmpty(), row.str("project_color"), row.str("project_prefix")) },
        key = row.str("key"),
        started = row.optBoolean("started", false),
    )

    private fun JSONObject.taskRows(): List<SpurTask> {
        val rows = getJSONArray("rows")
        return (0 until rows.length()).map { taskFromRow(rows.getJSONObject(it)) }
    }

    /** Tareas de Spur (app y /spur) pendientes. */
    suspend fun fetchClaudeTasks(ctx: Context): Result<List<SpurTask>> =
        sql(
            ctx,
            """SELECT $TASK_COLS
               FROM cards c LEFT JOIN projects p ON p.id = c.project_id
               WHERE c.source_type IN ('claude', 'spur') AND NOT c.done AND NOT c.archived
               ORDER BY c.priority IN ('HIGH', 'CRITICAL') DESC, c.created_at DESC
               LIMIT 80""",
            emptyList()
        ).mapCatching { it.taskRows() }

    /** Prioritaria = HIGH (la web la muestra así); quitarla la deja en MEDIUM, el valor por defecto. */
    suspend fun setTaskPriority(ctx: Context, id: String, high: Boolean): Result<Unit> =
        sql(
            ctx,
            """UPDATE cards SET priority = $2::"Priority" WHERE id = $1 AND source_type IN ('claude', 'spur')""",
            listOf(id, if (high) "HIGH" else "MEDIUM"),
        ).map { }

    /**
     * Tarea creada en el celular: va al tablero "Spur → Por hacer" (spur_col_todo, el mismo del skill /spur)
     * con source_type 'spur'. Devuelve el id de la tarjeta.
     */
    suspend fun insertTask(
        ctx: Context,
        title: String,
        description: String?,
        due: String?,
        high: Boolean,
        app: String? = null,
        projectId: String? = null,
    ): Result<String> {
        val id = UUID.randomUUID().toString()
        return sql(
            ctx,
            """WITH next_ord AS (SELECT COALESCE(MAX("order"), -1) + 1 AS n FROM cards WHERE column_id = 'spur_col_todo')
               INSERT INTO cards (id, title, description, "order", priority, labels, column_id,
                                  source_message_id, source_from, source_type, due_date,
                                  recurrence, recurrence_days, done, starred, archived, created_at, project_id)
               SELECT $1, $2, $3, n, $4::"Priority",
                      CASE WHEN $7::text IS NULL THEN '{}'::text[] ELSE ARRAY['app:' || $7::text] END, 'spur_col_todo', $5, 'spur_app', 'spur', $6::timestamp,
                      'NONE', '{}', false, false, false, NOW(), $8
               FROM next_ord""",
            listOf(id, title, description, if (high) "HIGH" else "MEDIUM", "spur_$id", due, app, projectId),
        ).mapCatching { assignKey(ctx, id, projectId).getOrThrow(); id }
    }

    /**
     * Da a la tarjeta su ID tipo Jira: siguiente número de la épica (LAV-4) o, sin épica o si la épica
     * no tiene prefijo, del tablero Spur (SP-12). Los números no se reutilizan (contadores en la base).
     */
    suspend fun assignKey(ctx: Context, cardId: String, projectId: String?): Result<String?> =
        sql(
            ctx,
            """WITH pr AS (
                 UPDATE projects SET key_seq = key_seq + 1
                 WHERE id = $2 AND prefix IS NOT NULL
                 RETURNING prefix || '-' || key_seq AS k
               ), bd AS (
                 UPDATE boards SET card_count = card_count + 1
                 WHERE id = 'spur_board' AND NOT EXISTS (SELECT 1 FROM pr)
                 RETURNING COALESCE(NULLIF(prefix, ''), 'SP') || '-' || card_count AS k
               )
               UPDATE cards SET key = COALESCE((SELECT k FROM pr), (SELECT k FROM bd))
               WHERE id = $1
               RETURNING key""",
            listOf(cardId, projectId),
        ).map { obj -> obj.getJSONArray("rows").optJSONObject(0)?.str("key") }

    /** En curso (true) o de vuelta a pendiente (false). */
    suspend fun setTaskStarted(ctx: Context, id: String, started: Boolean): Result<Unit> =
        sql(
            ctx,
            "UPDATE cards SET started_at = CASE WHEN $2::boolean THEN COALESCE(started_at, NOW()) END WHERE id = $1 AND source_type IN ('claude', 'spur')",
            listOf(id, started),
        ).map { }

    /** Todas las tarjetas de una épica (Spur y web), sin archivadas. */
    suspend fun fetchEpicCards(ctx: Context, projectId: String): Result<List<EpicCard>> =
        sql(
            ctx,
            """SELECT $TASK_COLS, c.source_type IN ('claude', 'spur') AS from_spur,
                      b.title AS board, co.title AS col
               FROM cards c
               LEFT JOIN projects p ON p.id = c.project_id
               LEFT JOIN columns co ON co.id = c.column_id
               LEFT JOIN boards b ON b.id = co.board_id
               WHERE c.project_id = $1 AND NOT c.archived
               ORDER BY c.done, c.started_at IS NULL, c.due_date NULLS LAST, c.done_at DESC NULLS LAST, c.created_at""",
            listOf(projectId),
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val spur = row.optBoolean("from_spur", false)
                EpicCard(
                    task = taskFromRow(row),
                    done = row.optBoolean("done", false),
                    fromSpur = spur,
                    where = if (spur) null else listOfNotNull(row.str("board"), row.str("col")).joinToString(" · ").ifEmpty { null },
                )
            }
        }

    /**
     * Tareas completadas desde [sinceUtc] (inicio del día local, en UTC). Usa `done_at`, que llena un
     * trigger de la base; si la columna aún no existe (migración sin aplicar) la consulta falla y la
     * sección "Terminadas hoy" simplemente no se muestra.
     */
    suspend fun fetchTasksDoneSince(ctx: Context, sinceUtc: String): Result<List<SpurTask>> =
        sql(
            ctx,
            """SELECT $TASK_COLS
               FROM cards c LEFT JOIN projects p ON p.id = c.project_id
               WHERE c.source_type IN ('claude', 'spur') AND c.done AND NOT c.archived
                 AND c.done_at >= $1::timestamp
               ORDER BY c.done_at DESC
               LIMIT 50""",
            listOf(sinceUtc),
        ).mapCatching { it.taskRows() }

    /** Fecha para hacerla (YYYY-MM-DD) o null para quitarla. */
    suspend fun setTaskDue(ctx: Context, id: String, due: String?): Result<Unit> =
        sql(
            ctx,
            "UPDATE cards SET due_date = $2::timestamp WHERE id = $1 AND source_type IN ('claude', 'spur')",
            listOf(id, due),
        ).map { }

    /** Edición completa desde el celular: título, detalle, fecha y ★. */
    suspend fun updateTask(ctx: Context, id: String, title: String, description: String?, due: String?, high: Boolean, app: String?, projectId: String?): Result<Unit> =
        sql(
            ctx,
            // La app vive en las etiquetas como "app:<paquete>"; se conservan las demás etiquetas.
            """UPDATE cards SET title = $2, description = $3, due_date = $4::timestamp, priority = $5::"Priority",
                      labels = ARRAY(SELECT l FROM unnest(labels) l WHERE l NOT LIKE 'app:%')
                               || CASE WHEN $6::text IS NULL THEN '{}'::text[] ELSE ARRAY['app:' || $6::text] END,
                      project_id = $7
               WHERE id = $1 AND source_type IN ('claude', 'spur')""",
            listOf(id, title, description, due, if (high) "HIGH" else "MEDIUM", app, projectId),
        ).map { }

    /** Épicas activas (Projects de KTPJ) con prefijo y avance (tarjetas hechas / total). */
    suspend fun fetchProjects(ctx: Context): Result<List<TaskProject>> =
        sql(
            ctx,
            """SELECT p.id, p.title, p.color, p.prefix,
                      COUNT(c.id) FILTER (WHERE NOT c.archived) AS total,
                      COUNT(c.id) FILTER (WHERE NOT c.archived AND c.done) AS done
               FROM projects p LEFT JOIN cards c ON c.project_id = p.id
               WHERE p.status = 'ACTIVE'
               GROUP BY p.id ORDER BY p.title""",
            emptyList(),
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                TaskProject(
                    row.getString("id"), row.getString("title"), row.str("color"), row.str("prefix"),
                    total = row.opt("total").toString().toIntOrNull() ?: 0,
                    done = row.opt("done").toString().toIntOrNull() ?: 0,
                )
            }
        }

    /** Prefijos ya usados (épicas y tableros): un ID no puede chocar con otro tablero de KTPJ. */
    suspend fun fetchUsedPrefixes(ctx: Context): Result<Set<String>> =
        sql(ctx, "SELECT prefix FROM projects WHERE prefix IS NOT NULL UNION SELECT prefix FROM boards WHERE prefix <> ''", emptyList())
            .mapCatching { obj ->
                val rows = obj.getJSONArray("rows")
                (0 until rows.length()).mapNotNull { rows.getJSONObject(it).str("prefix")?.uppercase() }.toSet() + "SP"
            }

    /**
     * Épica nueva desde el celular (solo nombre): es un Project de KTPJ con el mismo dueño del tablero
     * Spur. El color rota en una paleta para que cada épica se distinga en la lista.
     */
    suspend fun insertProject(ctx: Context, title: String, prefix: String): Result<TaskProject> {
        val id = "spur_" + UUID.randomUUID().toString().replace("-", "").take(20)
        val palette = listOf("#2E7D32", "#1565C0", "#C62828", "#EF6C00", "#6A1B9A", "#00838F", "#AD1457", "#5D4037")
        return sql(
            ctx,
            """INSERT INTO projects (id, title, owner_id, color, prefix)
               SELECT $1, $2, owner_id,
                      ($3::text[])[1 + (SELECT COUNT(*) FROM projects)::int % array_length($3::text[], 1)], $4
               FROM boards WHERE id = 'spur_board'
                 AND NOT EXISTS (SELECT 1 FROM boards WHERE prefix = $4)
               RETURNING id, title, color, prefix""",
            listOf(id, title, palette.joinToString(",", "{", "}"), prefix),   // literal de arreglo Postgres
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            check(rows.length() == 1) { "el prefijo $prefix ya lo usa un tablero" }
            val row = rows.getJSONObject(0)
            TaskProject(row.getString("id"), row.getString("title"), row.str("color"), row.str("prefix"))
        }
    }

    suspend fun markTaskDone(ctx: Context, id: String): Result<Unit> =
        sql(ctx, "UPDATE cards SET done = true WHERE id = $1 AND source_type IN ('claude', 'spur')", listOf(id)).map { }
}
