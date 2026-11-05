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

    /** Tareas creadas desde Claude Code con el skill /spur (tablero "Spur"), pendientes. */
    suspend fun fetchClaudeTasks(ctx: Context): Result<List<SpurTask>> =
        sql(
            ctx,
            """SELECT c.id, c.title, c.description, to_char(c.due_date, 'YYYY-MM-DD') AS due,
                      c.priority IN ('HIGH', 'CRITICAL') AS high,
                      (SELECT substring(l FROM 5) FROM unnest(c.labels) l WHERE l LIKE 'app:%' LIMIT 1) AS app,
                      c.project_id, p.title AS project, p.color AS project_color
               FROM cards c LEFT JOIN projects p ON p.id = c.project_id
               WHERE c.source_type IN ('claude', 'spur') AND NOT c.done AND NOT c.archived
               ORDER BY c.priority IN ('HIGH', 'CRITICAL') DESC, c.created_at DESC
               LIMIT 50""",
            emptyList()
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                SpurTask(
                    id = row.getString("id"),
                    title = row.getString("title"),
                    description = row.optString("description").takeIf { !row.isNull("description") && it.isNotBlank() },
                    due = row.optString("due").takeIf { !row.isNull("due") && it.isNotBlank() },
                    priority = row.optBoolean("high", false),
                    app = row.optString("app").takeIf { !row.isNull("app") && it.isNotBlank() },
                    project = row.optString("project_id").takeIf { !row.isNull("project_id") && it.isNotBlank() }?.let {
                        TaskProject(it, row.optString("project"), row.optString("project_color").takeIf { c -> c.isNotBlank() })
                    },
                )
            }
        }

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
        ).map { id }
    }

    /**
     * Tareas completadas desde [sinceUtc] (inicio del día local, en UTC). Usa `done_at`, que llena un
     * trigger de la base; si la columna aún no existe (migración sin aplicar) la consulta falla y la
     * sección "Terminadas hoy" simplemente no se muestra.
     */
    suspend fun fetchTasksDoneSince(ctx: Context, sinceUtc: String): Result<List<SpurTask>> =
        sql(
            ctx,
            """SELECT id, title, description, to_char(due_date, 'YYYY-MM-DD') AS due
               FROM cards
               WHERE source_type IN ('claude', 'spur') AND done AND NOT archived
                 AND done_at >= $1::timestamp
               ORDER BY done_at DESC
               LIMIT 50""",
            listOf(sinceUtc),
        ).mapCatching { obj ->
            val rows = obj.getJSONArray("rows")
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                SpurTask(
                    id = row.getString("id"),
                    title = row.getString("title"),
                    description = row.optString("description").takeIf { !row.isNull("description") && it.isNotBlank() },
                    due = row.optString("due").takeIf { !row.isNull("due") && it.isNotBlank() },
                )
            }
        }

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

    /** Proyectos activos de KTPJ, para agrupar tareas (el mismo "Proyecto" de la web). */
    suspend fun fetchProjects(ctx: Context): Result<List<TaskProject>> =
        sql(ctx, "SELECT id, title, color FROM projects WHERE status = 'ACTIVE' ORDER BY title", emptyList())
            .mapCatching { obj ->
                val rows = obj.getJSONArray("rows")
                (0 until rows.length()).map { i ->
                    val row = rows.getJSONObject(i)
                    TaskProject(row.getString("id"), row.getString("title"), row.optString("color").takeIf { it.isNotBlank() })
                }
            }

    suspend fun markTaskDone(ctx: Context, id: String): Result<Unit> =
        sql(ctx, "UPDATE cards SET done = true WHERE id = $1 AND source_type IN ('claude', 'spur')", listOf(id)).map { }
}
