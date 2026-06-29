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
import java.util.UUID
import java.util.concurrent.TimeUnit

object NeonRepository {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private suspend fun sql(ctx: Context, query: String, params: List<Any?>): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val host = Prefs.getNeonHost(ctx)
                val connStr = "postgresql://${Prefs.getNeonUser(ctx)}:${Prefs.getNeonPassword(ctx)}@$host/${Prefs.getNeonDb(ctx)}?sslmode=require"

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
                else Result.failure(Exception("HTTP ${resp.code}: $respBody"))
            } catch (e: Throwable) {
                Result.failure(Exception(e.message ?: e.toString()))
            }
        }

    suspend fun testConnection(ctx: Context): Result<Unit> =
        sql(ctx, "SELECT 1", emptyList()).map { }

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

    suspend fun insertCard(ctx: Context, tx: NuTransaction, columnId: String): Result<String> {
        val cardId = UUID.randomUUID().toString()
        val txId   = UUID.randomUUID().toString()
        val srcId  = "nu_${System.currentTimeMillis()}_${cardId.take(8)}"

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
                   $1, $5, 'nu_colombia', 'push_notification',
                   'NONE', '{}', false, false, false, NOW()
            FROM next_ord
            ON CONFLICT (source_message_id) DO NOTHING
        """.trimIndent()

        val txQuery = """
            INSERT INTO transactions (
                id, message_id, account, bank, amount, currency,
                merchant, occurred_at, received_at, status, raw_snippet, created_at
            ) VALUES (
                $1, $2, 'Nu Colombia', 'Nu', $3, 'COP',
                $4, NOW(), NOW(), 'UNREVIEWED', $5, NOW()
            )
            ON CONFLICT (message_id) DO NOTHING
        """.trimIndent()

        val amount = tx.amount
            ?.replace(".", "")
            ?.replace(",", ".")
            ?.toDoubleOrNull() ?: 0.0

        sql(ctx, cardQuery, listOf(columnId, cardId, tx.title, tx.rawText, srcId))
        sql(ctx, txQuery, listOf(txId, srcId, amount, tx.merchant ?: "Nu Colombia", tx.rawText))

        return Result.success(cardId)
    }
}
