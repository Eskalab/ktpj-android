package co.eskala.bktpj_notification

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "nu_ktpj_prefs"

    private val DEFAULT_HOST = BuildConfig.NEON_HOST
    private val DEFAULT_USER = BuildConfig.NEON_USER
    private val DEFAULT_PASS = BuildConfig.NEON_PASS
    private val DEFAULT_DB   = BuildConfig.NEON_DB
    private const val DEFAULT_NU_PKG       = "com.nu.production"
    private const val DEFAULT_COL_INBOX    = "nu_col_inbox"
    private const val DEFAULT_COL_HOY      = "nu_col_inbox"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun initDefaults(ctx: Context) {
        if (sp(ctx).getString("neon_host", "").isNullOrEmpty()) {
            setNeonHost(ctx, DEFAULT_HOST)
            setNeonUser(ctx, DEFAULT_USER)
            setNeonPassword(ctx, DEFAULT_PASS)
            setNeonDb(ctx, DEFAULT_DB)
            setNuPackage(ctx, DEFAULT_NU_PKG)
            setColumnInbox(ctx, DEFAULT_COL_INBOX)
            setColumnHoy(ctx, DEFAULT_COL_HOY)
        }
    }

    fun getNeonHost(ctx: Context) = sp(ctx).getString("neon_host", DEFAULT_HOST) ?: DEFAULT_HOST
    fun setNeonHost(ctx: Context, v: String) = sp(ctx).edit().putString("neon_host", v).apply()

    fun getNeonUser(ctx: Context) = sp(ctx).getString("neon_user", "") ?: ""
    fun setNeonUser(ctx: Context, v: String) = sp(ctx).edit().putString("neon_user", v).apply()

    fun getNeonPassword(ctx: Context) = sp(ctx).getString("neon_password", "") ?: ""
    fun setNeonPassword(ctx: Context, v: String) = sp(ctx).edit().putString("neon_password", v).apply()

    fun getNeonDb(ctx: Context) = sp(ctx).getString("neon_db", "neondb") ?: "neondb"
    fun setNeonDb(ctx: Context, v: String) = sp(ctx).edit().putString("neon_db", v).apply()

    fun getColumnInbox(ctx: Context) = sp(ctx).getString("column_inbox", "") ?: ""
    fun setColumnInbox(ctx: Context, v: String) = sp(ctx).edit().putString("column_inbox", v).apply()

    fun getColumnHoy(ctx: Context) = sp(ctx).getString("column_hoy", "") ?: ""
    fun setColumnHoy(ctx: Context, v: String) = sp(ctx).edit().putString("column_hoy", v).apply()

    fun getNuPackage(ctx: Context) = sp(ctx).getString("nu_package", DEFAULT_NU_PKG) ?: DEFAULT_NU_PKG
    fun setNuPackage(ctx: Context, v: String) = sp(ctx).edit().putString("nu_package", v).apply()

    fun getTheme(ctx: Context) = sp(ctx).getString("theme", "system") ?: "system"
    fun setTheme(ctx: Context, v: String) = sp(ctx).edit().putString("theme", v).apply()

    fun getNeonConfig(ctx: Context) =
        NeonConfig(getNeonHost(ctx), getNeonUser(ctx), getNeonPassword(ctx), getNeonDb(ctx))

    fun saveNeonConfig(ctx: Context, cfg: NeonConfig) {
        setNeonHost(ctx, cfg.host)
        setNeonUser(ctx, cfg.user)
        setNeonPassword(ctx, cfg.password)
        setNeonDb(ctx, cfg.db)
    }

    fun hasConnection(ctx: Context) = getNeonHost(ctx).isNotEmpty() && getNeonUser(ctx).isNotEmpty()

    // Se marca al terminar el onboarding (01 → 02 → 03); el permiso se sigue revisando aparte.
    fun isOnboarded(ctx: Context) = sp(ctx).getBoolean("onboarded", false)
    fun setOnboarded(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("onboarded", v).apply()

    fun parseConnectionString(connStr: String): NeonConfig? {
        return try {
            val url = connStr.trim().removePrefix("postgresql://").removePrefix("postgres://")
            val atIdx = url.lastIndexOf('@')
            if (atIdx < 0) return null
            val credentials = url.substring(0, atIdx)
            val rest = url.substring(atIdx + 1)
            val colonIdx = credentials.indexOf(':')
            val user = if (colonIdx >= 0) credentials.substring(0, colonIdx) else credentials
            val pass = if (colonIdx >= 0) credentials.substring(colonIdx + 1) else ""
            val slashIdx = rest.indexOf('/')
            val host = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
            val db = if (slashIdx >= 0) rest.substring(slashIdx + 1).substringBefore('?') else "neondb"
            if (host.isEmpty() || user.isEmpty()) null else NeonConfig(host, user, pass, db.ifEmpty { "neondb" })
        } catch (e: Exception) {
            null
        }
    }

    fun parseAndSaveConnectionString(ctx: Context, connStr: String): Boolean {
        val cfg = parseConnectionString(connStr) ?: return false
        saveNeonConfig(ctx, cfg)
        return true
    }

    fun isConfigured(ctx: Context) =
        getNeonHost(ctx).isNotEmpty() && getNeonUser(ctx).isNotEmpty() && getColumnInbox(ctx).isNotEmpty()
}

data class NeonConfig(
    val host: String,
    val user: String,
    val password: String,
    val db: String,
) {
    val connectionString get() = "postgresql://$user:$password@$host/$db?sslmode=require"
}
