package co.eskala.bktpj_notification.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App instalada que se puede abrir desde una tarea. */
data class InstalledApp(val pkg: String, val label: String, val icon: ImageBitmap?)

private const val ICON_PX = 96

/** Apps con ícono en el launcher (Gmail, Zoho, WhatsApp…), ordenadas por nombre. Spur no aparece. */
suspend fun loadLaunchableApps(ctx: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = ctx.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(launcher, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { ri ->
            InstalledApp(
                pkg = ri.activityInfo.packageName,
                label = ri.loadLabel(pm).toString(),
                icon = runCatching { ri.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** Nombre visible de un paquete, o null si la app no está instalada en este teléfono. */
fun appLabel(ctx: Context, pkg: String): String? = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
}.getOrNull()

/** Abre la app; si ya no está instalada, lo avisa. */
fun openApp(ctx: Context, pkg: String) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
    if (intent == null) {
        Toast.makeText(ctx, "Esa app no está instalada en este teléfono", Toast.LENGTH_SHORT).show()
        return
    }
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** En la tarea: "↗ Zoho Mail", toca y abre la app. */
@Composable
fun AppLink(pkg: String, enabled: Boolean = true) {
    val ctx = LocalContext.current
    val label = remember(pkg) { appLabel(ctx, pkg) } ?: return   // no instalada aquí: no se muestra
    Text(
        "↗ $label",
        style = KtpjTheme.type.captionStrong,
        color = KtpjTheme.colors.accent,
        modifier = Modifier
            .clip(KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Abrir $label") { openApp(ctx, pkg) }
            .padding(vertical = 4.dp),
    )
}

/** En el formulario: "Elegir app…" o la app elegida con "Quitar". */
@Composable
fun AppField(value: String?, onChange: (String?) -> Unit) {
    val ctx = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    val label = value?.let { remember(it) { appLabel(ctx, it) ?: it } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(label ?: "Elegir app…", value != null, { picking = true })
            if (value != null) Chip("Quitar", false, { onChange(null) })
        }
        Text(
            if (value != null) "La tarea muestra un botón para abrir $label."
            else "Si eliges una, la tarea muestra un botón para abrirla.",
            style = KtpjTheme.type.caption,
            color = KtpjTheme.colors.muted,
        )
    }
    if (picking) AppPickerDialog(onPick = { picking = false; onChange(it) }, onDismiss = { picking = false })
}

@Composable
fun AppPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val apps by produceState<List<InstalledApp>?>(null) { value = loadLaunchableApps(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.orEmpty().filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(c.bg, KtpjShapes.notification)
                .padding(KtpjDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
        ) {
            Text("Elegir app", style = t.screenTitle, color = c.fg)
            BasicTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                singleLine = true,
                textStyle = t.body.copy(color = c.fg),
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar app" },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(KtpjDimens.borderField, c.fg, KtpjShapes.row)
                            .padding(KtpjDimens.cardPadding),
                    ) {
                        if (query.isEmpty()) Text("Buscar (Gmail, Zoho, WhatsApp…)", style = t.body, color = c.muted)
                        inner()
                    }
                },
            )
            when {
                apps == null -> Text("Cargando apps…", style = t.secondary, color = c.muted)
                shown.isEmpty() -> Text("Ninguna app con ese nombre.", style = t.secondary, color = c.muted)
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown, key = { it.pkg }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(KtpjShapes.row)
                                .clickable(role = Role.Button) { onPick(app.pkg) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (app.icon != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                            else Box(Modifier.size(36.dp))
                            Text(app.label, style = t.body, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            SecondaryButton("Cancelar", onClick = onDismiss)
        }
    }
}
