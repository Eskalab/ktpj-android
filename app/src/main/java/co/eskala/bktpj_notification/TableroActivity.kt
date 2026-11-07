package co.eskala.bktpj_notification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import co.eskala.bktpj_notification.ui.TableroApp
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/** Punto de entrada del rediseño "Tablero" (Compose). */
class TableroActivity : ComponentActivity() {

    private var hasListenerAccess by mutableStateOf(false)
    private var theme by mutableStateOf(ThemeMode.SYSTEM)

    // Android 13+: sin este permiso no se ven las notificaciones de "Tarjeta creada" / "No se pudo guardar".
    // Notificaciones (Android 13+) y copia de SMS entrantes (Davivienda). Se piden juntos al abrir.
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.initDefaults(this)
        SpurTasks.schedule(this)
        DailySummary.arm(this)
        handleOpenSummary(intent)
        hasListenerAccess = NotificationAccess.isGranted(this)
        theme = ThemeMode.fromKey(Prefs.getTheme(this))
        applySystemBars()

        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.RECEIVE_SMS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) askPermissions.launch(missing.toTypedArray())

        setContent {
            KtpjTheme(theme) {
                TableroApp(
                    hasListenerAccess = hasListenerAccess,
                    onRecheckAccess = {
                        hasListenerAccess = NotificationAccess.isGranted(this)
                        hasListenerAccess
                    },
                    onOpenAccessSettings = { startActivity(NotificationAccess.settingsIntent()) },
                    onLegacyConfig = { startActivity(Intent(this, MainActivity::class.java)) },
                    theme = theme,
                    onThemeChange = { mode ->
                        Prefs.setTheme(this, mode.key)
                        theme = mode
                        applySystemBars()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenSummary(intent)
    }

    private fun handleOpenSummary(intent: Intent?) {
        val kind = intent?.getStringExtra(DailySummary.EXTRA_OPEN_SUMMARY) ?: return
        DailySummary.openRequest.value = runCatching { DailySummary.Kind.valueOf(kind) }.getOrNull()
        intent.removeExtra(DailySummary.EXTRA_OPEN_SUMMARY)   // no reabrir al rotar
    }

    override fun onResume() {
        super.onResume()
        hasListenerAccess = NotificationAccess.isGranted(this)
    }

    private fun applySystemBars() {
        val dark = when (theme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
}
