package co.eskala.bktpj_notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Ubicación del teléfono al capturar una transacción (KTP-7), con el LocationManager de la plataforma
 * (sin Google Play Services). Nunca lanza ni demora el guardado más que [LocationBudget] (≤ 3 s):
 * apagado o sin permiso → null y todo sigue como antes.
 * Solo se usa para transacciones; no hay seguimiento ni se pide en ningún otro momento.
 */
object TxLocator {

    /** Un SMS con varios movimientos (o dos capturas seguidas) reutiliza la misma ubicación. */
    private const val REUSE_MS = 60_000L

    private const val PREFS = "spur_location"
    private const val KEY_ENABLED = "enabled"

    private val geoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var recent: Pair<Long, TxLocation>? = null

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, v).apply()
        if (!v) recent = null
    }

    fun hasForegroundPermission(ctx: Context): Boolean =
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Android 10+: sin este permiso no hay ubicación para los gastos que llegan con la app cerrada. */
    fun hasBackgroundPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Ubicación para una captura que se está guardando ahora, o null. */
    suspend fun capture(ctx: Context): TxLocation? {
        val budget = runCatching {
            LocationBudget.plan(
                enabled = isEnabled(ctx),
                foregroundPermission = hasForegroundPermission(ctx),
                backgroundPermission = hasBackgroundPermission(ctx),
                inForeground = isAppInForeground(),
            )
        }.getOrNull() ?: return null
        recent?.let { (at, loc) -> if (SystemClock.elapsedRealtime() - at < REUSE_MS) return loc }
        val loc = runCatching {
            // Margen: locate() ya reparte el presupuesto; esto solo corta si algo se cuelga.
            withTimeoutOrNull(budget.totalMs + 300) { locate(ctx.applicationContext, budget) }
        }.getOrNull()
        if (loc != null) recent = SystemClock.elapsedRealtime() to loc
        return loc
    }

    /** ¿Hay una pantalla de la app visible? (Android solo da fixes nuevos en segundo plano con ACCESS_BACKGROUND_LOCATION.) */
    private fun isAppInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private suspend fun locate(ctx: Context, budget: LocationBudget): TxLocation? {
        val start = SystemClock.elapsedRealtime()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null

        val lastKnown = withContext(Dispatchers.IO) { LocationPick.best(lastKnownFixes(lm)) }
        val fix = if (!budget.allowFresh || LocationPick.freshEnough(lastKnown)) lastKnown else {
            val fresh = withTimeoutOrNull(budget.freshMs) { currentFix(lm) }
            LocationPick.choose(fresh, lastKnown)
        } ?: return null

        val address = geocode(ctx, fix.lat, fix.lng, budget.geocodeTimeout(SystemClock.elapsedRealtime() - start))
        return TxLocation(
            lat = fix.lat,
            lng = fix.lng,
            accuracyM = fix.accuracyM,
            address = address,
            fixAt = System.currentTimeMillis() - fix.ageMs,
        )
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFixes(lm: LocationManager): List<LocationFix> =
        lm.getProviders(true).mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.toFix()
        }

    /** Proveedor para el fix nuevo: fused (Android 12+) > red (rápido en interiores) > GPS. */
    private fun provider(lm: LocationManager): String? {
        val enabled = lm.getProviders(true)
        return listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { it in enabled }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(lm: LocationManager): LocationFix? {
        val p = provider(lm) ?: return null
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { runCatching { signal.cancel() } }
            try {
                LocationManagerCompat.getCurrentLocation(lm, p, signal, executor) { loc ->
                    if (cont.isActive) cont.resume(loc?.toFix())
                }
            } catch (e: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun Location.toFix() = LocationFix(
        lat = latitude,
        lng = longitude,
        accuracyM = if (hasAccuracy()) accuracy else null,
        ageMs = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L,
        provider = provider.orEmpty(),
    )

    /**
     * Dirección corta, a lo sumo [timeoutMs]. Geocoder bloquea (y necesita red), así que corre
     * aparte en IO; si tarda, se deja seguir y se guarda sin dirección.
     */
    private suspend fun geocode(ctx: Context, lat: Double, lng: Double, timeoutMs: Long): String? {
        if (timeoutMs <= 0 || !Geocoder.isPresent()) return null
        val job = geoScope.async {
            runCatching {
                @Suppress("DEPRECATION")
                val a = Geocoder(ctx, Locale("es", "CO")).getFromLocation(lat, lng, 1)?.firstOrNull()
                    ?: return@runCatching null
                AddressFormat.short(
                    thoroughfare = a.thoroughfare,
                    subThoroughfare = a.subThoroughfare,
                    locality = a.locality,
                    subLocality = a.subLocality,
                    addressLine = if (a.maxAddressLineIndex >= 0) a.getAddressLine(0) else null,
                    countryName = a.countryName,
                )
            }.getOrNull()
        }
        return withTimeoutOrNull(timeoutMs) { job.await() }
    }
}
