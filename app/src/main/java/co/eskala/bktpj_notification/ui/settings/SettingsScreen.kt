package co.eskala.bktpj_notification.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.ui.components.OutlinePill
import co.eskala.bktpj_notification.ui.components.SectionCard
import co.eskala.bktpj_notification.ui.onboarding.ConnectionStatus
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/** 06 Ajustes: base de datos, destinos, fuente + acceso a notificaciones, tema. */
@Composable
fun SettingsScreen(
    neonHost: String,
    neonStatus: ConnectionStatus,
    onTestNeon: () -> Unit,
    onEditNeon: () -> Unit,
    inboxTitle: String?,
    hoyTitle: String?,
    onEditDestinations: () -> Unit,
    sourcePackage: String,
    onSavePackage: (String) -> Unit,
    hasListenerAccess: Boolean,
    onOpenAccessSettings: () -> Unit,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onLegacyConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
    ) {
        Text("Ajustes", style = t.screenTitle, color = c.fg)

        SectionCard("BASE DE DATOS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(neonLabel(neonStatus), style = t.rowTitle, color = if (neonStatus is ConnectionStatus.Error) c.accent else c.fg)
                    Text(neonHost.ifEmpty { "Sin configurar" }, style = t.monoSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinePill("Probar", onClick = onTestNeon, enabled = neonStatus != ConnectionStatus.Testing)
            }
            if (neonStatus is ConnectionStatus.Error) {
                Text(neonStatus.message, style = t.caption, color = c.muted)
            }
            LinkRow("Cambiar connection string", onEditNeon)
        }

        SectionCard("DESTINOS") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(KtpjShapes.row)
                    .clickable(role = Role.Button, onClickLabel = "Cambiar destinos", onClick = onEditDestinations),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Inbox → ${inboxTitle ?: "—"}", style = t.rowTitle, color = c.fg)
                Text("Hoy → ${hoyTitle ?: "Sin columna"}", style = t.rowTitle, color = c.fg)
                Text("Toca para cambiar", style = t.caption, color = c.muted)
            }
        }

        SectionCard("FUENTE") {
            PackageRow(sourcePackage, onSavePackage)
            Box(Modifier.fillMaxWidth().height(KtpjDimens.divider).background(c.line))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = KtpjDimens.minTouchTarget)
                    .clickable(role = Role.Switch, onClick = onOpenAccessSettings)
                    .semantics { stateDescription = if (hasListenerAccess) "Activado" else "Desactivado" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Acceso a notificaciones", style = t.rowTitle, color = c.fg, modifier = Modifier.weight(1f))
                AccessSwitch(hasListenerAccess)
            }
        }

        SectionCard("TEMA") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(KtpjShapes.pill)
                    .border(KtpjDimens.borderCard, c.border, KtpjShapes.pill)
                    .padding(4.dp),
            ) {
                listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro").forEach { (mode, label) ->
                    val selected = theme == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(KtpjShapes.pill)
                            .background(if (selected) c.invBg else c.card)
                            .clickable(role = Role.RadioButton, onClick = { onThemeChange(mode) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = t.captionStrong, color = if (selected) c.invFg else c.fg)
                    }
                }
            }
        }

        SectionCard("AVANZADO") {
            Text(
                "Pantalla de configuración anterior: IDs de columna a mano y lista de columnas con sus IDs.",
                style = t.caption,
                color = c.muted,
            )
            LinkRow("Abrir configuración anterior", onLegacyConfig)
        }
    }
}

private fun neonLabel(s: ConnectionStatus) = when (s) {
    is ConnectionStatus.Ok -> "Neon · conectado (${s.millis} ms)"
    is ConnectionStatus.Error -> "Neon · sin conexión"
    ConnectionStatus.Testing -> "Neon · probando…"
    ConnectionStatus.Idle -> "Neon"
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        "$text ›",
        style = KtpjTheme.type.captionStrong,
        color = KtpjTheme.colors.fg,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = KtpjDimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/** "App de Nu Colombia" + package en Mono, con acción "Editar" (campo en línea). */
@Composable
private fun PackageRow(value: String, onSave: (String) -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("App de Nu Colombia", style = t.rowTitle, color = c.fg)
                if (!editing) Text(value, style = t.monoSmall, color = c.muted)
            }
            Text(
                if (editing) "Guardar" else "Editar",
                style = t.captionStrong,
                color = c.fg,
                modifier = Modifier
                    .clip(KtpjShapes.pill)
                    .clickable(role = Role.Button) {
                        if (editing) onSave(draft.trim().ifEmpty { "com.nu.production" })
                        editing = !editing
                    }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = t.mono.copy(color = c.fg),
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Package de la app de Nu" }
                    .border(KtpjDimens.borderField, c.fg, KtpjShapes.row)
                    .padding(KtpjDimens.cardPadding),
            )
        }
    }
}

/** Switch 44×26dp: pista invBg y perilla `dot` cuando está activo. Abre los ajustes del sistema. */
@Composable
private fun AccessSwitch(on: Boolean) {
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

@Preview(name = "06 Ajustes — Claro", widthDp = 360, heightDp = 900)
@Composable
private fun SettingsPreview() = KtpjTheme(ThemeMode.LIGHT) {
    SettingsScreen(
        "ep-cool-sun.neon.tech", ConnectionStatus.Ok(5, 182), {}, {},
        "Inbox", null, {},
        "com.nu.production", {},
        true, {},
        ThemeMode.SYSTEM, {}, {},
    )
}
