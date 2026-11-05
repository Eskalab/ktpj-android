package co.eskala.bktpj_notification.ui.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Money
import co.eskala.bktpj_notification.NuTransaction
import co.eskala.bktpj_notification.Shortcut
import co.eskala.bktpj_notification.manualTransaction
import co.eskala.bktpj_notification.ui.components.Chip
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import java.util.UUID

/** Qué hace la pantalla: registrar un gasto suelto, o crear / editar un atajo. */
sealed interface ManualMode {
    data object Entry : ManualMode
    data class EditShortcut(val existing: Shortcut?) : ManualMode   // null = atajo nuevo
}

/**
 * Gasto manual (no está en el handoff; sigue sus tokens): monto, comercio y cargo/abono.
 * Crea la tarjeta en Inbox (+ Hoy) igual que una notificación de Nu.
 * En modo [ManualMode.EditShortcut] pide además el nombre del botón y guarda el atajo sin crear tarjeta.
 */
@Composable
fun ManualEntryScreen(
    saving: Boolean,
    hoyConfigured: Boolean,
    onSave: (NuTransaction, alsoShortcut: Shortcut?) -> Unit,
    onCancel: () -> Unit,
    mode: ManualMode = ManualMode.Entry,
    onSaveShortcut: (Shortcut) -> Unit = {},
    onDeleteShortcut: (() -> Unit)? = null,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val existing = (mode as? ManualMode.EditShortcut)?.existing
    val editingShortcut = mode is ManualMode.EditShortcut
    var type by rememberSaveable { mutableStateOf(existing?.type ?: "cargo") }
    var digits by rememberSaveable { mutableStateOf(existing?.amount?.toString() ?: "") }      // solo dígitos: pesos enteros
    var merchant by rememberSaveable { mutableStateOf(existing?.merchant ?: "") }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var alsoShortcut by rememberSaveable { mutableStateOf(false) }

    val value = digits.toLongOrNull()
    val formatted = value?.let { Money.format(it.toDouble()) }
    val needsName = editingShortcut || alsoShortcut
    // Sin nombre, el botón usa el comercio.
    val buttonName = name.trim().ifEmpty { merchant.trim() }
    val canSave = !saving && value != null && value > 0 && (!needsName || buttonName.isNotEmpty())

    fun shortcut() = Shortcut(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = buttonName,
        amount = value ?: 0,
        merchant = merchant.trim().ifEmpty { null },
        type = type,
    )

    fun build(): NuTransaction =
        manualTransaction(value ?: 0, merchant, type, "Registro manual en Spur")

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            Text(
                when {
                    !editingShortcut -> "NUEVO MOVIMIENTO"
                    existing == null -> "NUEVO ATAJO"
                    else -> "EDITAR ATAJO"
                },
                style = t.overlineLarge,
                color = c.muted,
            )
            Text(if (editingShortcut) "Atajo" else "Gasto manual", style = t.onboardingTitle, color = c.fg)
            if (editingShortcut) {
                Text(
                    "Queda como botón en Inicio: un toque crea la tarjeta con este monto. Mantenlo presionado para editarlo.",
                    style = t.secondary,
                    color = c.muted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Cargo (−)", type == "cargo", { type = "cargo" })
                Chip("Abono (+)", type == "abono", { type = "abono" })
            }

            Field(label = "Monto") {
                BasicTextField(
                    value = digits,
                    onValueChange = { s -> digits = s.filter(Char::isDigit).take(12).trimStart('0') },
                    singleLine = true,
                    textStyle = t.amount.copy(color = c.fg),
                    cursorBrush = SolidColor(c.fg),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Monto en pesos" },
                    decorationBox = { inner -> Boxed { if (digits.isEmpty()) Text("0", style = t.amount, color = c.muted); inner() } },
                )
                Text(
                    formatted?.let { (if (type == "cargo") "−" else "+") + "\$$it COP" } ?: "Pesos colombianos, sin decimales",
                    style = t.caption,
                    color = if (type == "abono" && formatted != null) c.green else c.muted,
                )
            }

            Field(label = "Comercio o descripción") {
                BasicTextField(
                    value = merchant,
                    onValueChange = { merchant = it.take(80) },
                    singleLine = true,
                    textStyle = t.body.copy(color = c.fg),
                    cursorBrush = SolidColor(c.fg),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Comercio o descripción" },
                    decorationBox = { inner -> Boxed { if (merchant.isEmpty()) Text("Ej. Peaje Andes", style = t.body, color = c.muted); inner() } },
                )
            }

            if (!editingShortcut) {
                Chip("Guardar también como atajo", alsoShortcut, { alsoShortcut = !alsoShortcut })
            }

            if (needsName) {
                Field(label = "Nombre del botón") {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        singleLine = true,
                        textStyle = t.body.copy(color = c.fg),
                        cursorBrush = SolidColor(c.fg),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Nombre del botón" },
                        decorationBox = { inner ->
                            Boxed { if (name.isEmpty()) Text(merchant.trim().ifEmpty { "Ej. Peaje Andes" }, style = t.body, color = c.muted); inner() }
                        },
                    )
                    Text("Si lo dejas vacío, usa el comercio.", style = t.caption, color = c.muted)
                }
            }

            Text(
                when {
                    editingShortcut && hoyConfigured -> "Cada toque crea una tarjeta en Inbox y otra en Hoy."
                    editingShortcut -> "Cada toque crea una tarjeta en Inbox."
                    hoyConfigured -> "Se crea una tarjeta en Inbox y otra en Hoy, como con las notificaciones de Nu."
                    else -> "Se crea una tarjeta en Inbox, como con las notificaciones de Nu."
                },
                style = t.secondary,
                color = c.muted,
            )
        }
        Column(
            Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
        ) {
            PrimaryButton(
                when {
                    saving -> "Guardando…"
                    editingShortcut -> "Guardar atajo"
                    else -> "Guardar"
                },
                onClick = {
                    if (editingShortcut) onSaveShortcut(shortcut())
                    else onSave(build(), if (alsoShortcut) shortcut() else null)
                },
                enabled = canSave,
            )
            if (onDeleteShortcut != null) {
                SecondaryButton("Eliminar atajo", onClick = onDeleteShortcut, enabled = !saving)
            }
            SecondaryButton("Cancelar", onClick = onCancel, enabled = !saving)
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        Text(label, style = KtpjTheme.type.rowTitle, color = KtpjTheme.colors.fg)
        content()
    }
}

@Composable
private fun Boxed(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .border(KtpjDimens.borderField, KtpjTheme.colors.fg, KtpjShapes.row)
            .padding(KtpjDimens.cardPadding),
    ) { content() }
}

@Preview(name = "Gasto manual — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun ManualPreview() = KtpjTheme(ThemeMode.LIGHT) {
    ManualEntryScreen(saving = false, hoyConfigured = false, onSave = { _, _ -> }, onCancel = {})
}

@Preview(name = "Editar atajo — Oscuro", widthDp = 360, heightDp = 760)
@Composable
private fun EditShortcutPreview() = KtpjTheme(ThemeMode.DARK) {
    ManualEntryScreen(
        saving = false, hoyConfigured = true, onSave = { _, _ -> }, onCancel = {},
        mode = ManualMode.EditShortcut(Shortcut(name = "Peaje Andes", amount = 16100, merchant = "Peaje Andes", type = "cargo")),
        onDeleteShortcut = {},
    )
}
