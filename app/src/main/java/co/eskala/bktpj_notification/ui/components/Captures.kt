package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Capture
import co.eskala.bktpj_notification.CaptureStatus
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HOUR = SimpleDateFormat("HH:mm", Locale.getDefault())

fun Capture.hour(): String = HOUR.format(Date(at))

/** "cargo" | "abono" | "timeout"… — segunda línea de las tarjetas de Inicio. */
fun Capture.kindLabel(): String = when {
    status == CaptureStatus.ERROR -> error?.let { shortError(it) } ?: "error"
    tx.type == "desconocido" -> "aviso"
    else -> tx.type
}

private fun shortError(e: String) = if (e.contains("timeout", ignoreCase = true)) "timeout" else "error"

/** Color del monto: abonos en green, el resto en fg. */
@Composable
fun amountColor(c: Capture): Color =
    if (c.tx.type == "abono") KtpjTheme.colors.green else KtpjTheme.colors.fg

/**
 * Tarjeta de captura de Inicio: borde 1.5dp, comercio 16/700, "HH:mm · cargo" 13/500, monto 20/800.
 * En error: fondo accent, texto onAccent y pill "Reintentar".
 */
@Composable
fun CaptureCard(capture: Capture, onRetry: () -> Unit, retrying: Boolean) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val isError = capture.status == CaptureStatus.ERROR
    val shape = KtpjShapes.card
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isError) Modifier.background(c.accent)
                else Modifier.border(KtpjDimens.borderCard, c.border, shape)
            )
            .padding(KtpjDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                capture.label,
                style = t.rowTitle,
                color = if (isError) c.onAccent else c.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${capture.hour()} · ${capture.kindLabel()}",
                style = t.caption,
                color = if (isError) c.onAccent else c.muted,
            )
        }
        if (isError) {
            RetryPill(onRetry, retrying, onAccentBackground = true)
        } else {
            capture.signedAmount?.let { Text(it, style = t.amount, color = amountColor(capture)) }
        }
    }
}

/** Pill "Reintentar". Sobre accent: fondo onAccent + texto accentInk; en listas: fondo accent + onAccent. */
@Composable
fun RetryPill(onClick: () -> Unit, busy: Boolean, onAccentBackground: Boolean) {
    val c = KtpjTheme.colors
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .alpha(if (busy) 0.6f else 1f)
            .clip(KtpjShapes.pill)
            .background(if (onAccentBackground) c.onAccent else c.accent)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (busy) "Enviando…" else "Reintentar",
            style = KtpjTheme.type.captionStrong,
            color = if (onAccentBackground) c.accentInk else c.onAccent,
        )
    }
}

/** Estado del encabezado de Inicio. */
enum class ServiceState(val label: String) { ACTIVE("Activo"), NO_ACCESS("Sin acceso"), OFFLINE("Sin conexión") }

/** Chip de estado: "Activo" en invBg con punto `dot`; los problemas en accent. */
@Composable
fun StatusChip(state: ServiceState) {
    val c = KtpjTheme.colors
    val ok = state == ServiceState.ACTIVE
    Row(
        Modifier
            .clip(KtpjShapes.pill)
            .background(if (ok) c.invBg else c.accent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(KtpjDimens.statusDot)
                .background(if (ok) c.dot else c.onAccent, CircleShape),
        )
        Text(state.label, style = KtpjTheme.type.captionStrong, color = if (ok) c.invFg else c.onAccent)
    }
}

/** Pill con borde (botón "Probar" de Ajustes, "+ Gasto manual" de Inicio). */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = KtpjTheme.colors
    Box(
        modifier
            .heightIn(min = KtpjDimens.minTouchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(KtpjShapes.pill)
            .border(KtpjDimens.borderCard, c.border, KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KtpjTheme.type.buttonSecondary, color = c.fg)
    }
}

/** Tarjeta `card` con overline (secciones de Ajustes). */
@Composable
fun SectionCard(overline: String, content: @Composable ColumnScope.() -> Unit) {
    val c = KtpjTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, KtpjShapes.card)
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(overline, style = KtpjTheme.type.overline, color = c.muted)
        content()
    }
}

/** Chip de filtro / segmento: seleccionado invBg, el resto con borde. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = KtpjTheme.colors
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(KtpjShapes.pill)
            .then(
                if (selected) Modifier.background(c.invBg)
                else Modifier.border(KtpjDimens.borderCard, c.border, KtpjShapes.pill)
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KtpjTheme.type.captionStrong, color = if (selected) c.invFg else c.fg)
    }
}
