package co.eskala.bktpj_notification.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import co.eskala.bktpj_notification.TxLocator
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

private data class LocState(val enabled: Boolean, val foreground: Boolean, val background: Boolean) {
    val on get() = enabled && foreground
}

private fun read(ctx: Context) = LocState(
    TxLocator.isEnabled(ctx),
    TxLocator.hasForegroundPermission(ctx),
    TxLocator.hasBackgroundPermission(ctx),
)

/**
 * KTP-7: "Guardar ubicación de los gastos". Pide ubicación (aprox./precisa) y, en Android 10+,
 * "Permitir todo el tiempo" (Android 11+ solo deja darlo desde los ajustes del sistema).
 * Apagado o sin permiso, la captura funciona igual y la ubicación queda vacía.
 */
@Composable
fun LocationSettingRow() {
    val ctx = LocalContext.current
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var state by remember { mutableStateOf(read(ctx)) }

    // Al volver de los ajustes del sistema el permiso pudo cambiar.
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state = read(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun openAppSettings() {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state = read(ctx)
        if (!ok) openAppSettings()
    }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res.values.any { it }
        TxLocator.setEnabled(ctx, ok)
        state = read(ctx)
        // Negado "para siempre": Android ya no muestra el diálogo, solo queda abrir los ajustes de la app.
        val canAskAgain = (ctx as? Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: true
        if (!ok && !canAskAgain) openAppSettings()
    }

    fun toggle() {
        when {
            state.on -> TxLocator.setEnabled(ctx, false)
            state.foreground -> TxLocator.setEnabled(ctx, true)
            else -> foregroundLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        state = read(ctx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = KtpjDimens.minTouchTarget)
                .clickable(role = Role.Switch, onClick = ::toggle)
                .semantics { stateDescription = if (state.on) "Activado" else "Desactivado" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Guardar ubicación de los gastos", style = t.rowTitle, color = c.fg, modifier = Modifier.weight(1f))
            LocSwitch(state.on)
        }
        Text(
            "Anota dónde estabas cuando se registró cada gasto (📍 en el historial y en la base). " +
                "Solo se usa en las transacciones, nunca para otra cosa.",
            style = t.caption,
            color = c.muted,
        )
        if (state.on && !state.background) {
            Text(
                "Para los gastos que llegan con la app cerrada (Nu, Davivienda), Android pide " +
                    "«Permitir todo el tiempo» en los ajustes de ubicación.",
                style = t.caption,
                color = c.muted,
            )
            Text(
                "Permitir todo el tiempo ›",
                style = t.captionStrong,
                color = c.fg,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = KtpjDimens.minTouchTarget)
                    .clickable(role = Role.Button) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else openAppSettings()
                    }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/** Mismo switch 44×26dp que "Acceso a notificaciones". */
@Composable
private fun LocSwitch(on: Boolean) {
    val c = KtpjTheme.colors
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(KtpjShapes.pill)
            .background(if (on) c.invBg else c.line)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(if (on) c.dot else c.muted, CircleShape))
    }
}
