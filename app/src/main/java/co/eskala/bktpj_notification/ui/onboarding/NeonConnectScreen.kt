package co.eskala.bktpj_notification.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/** `connectionStatus` del handoff. */
sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Testing : ConnectionStatus
    data class Ok(val columnCount: Int, val millis: Long) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

/**
 * 02 Onboarding — Conectar Neon.
 * "Continuar" solo se habilita después de una prueba exitosa del valor actual del campo.
 */
@Composable
fun NeonConnectScreen(
    connectionString: String,
    status: ConnectionStatus,
    onConnectionStringChange: (String) -> Unit,
    onTest: () -> Unit,
    onContinue: () -> Unit,
    step: Int? = 2,
    continueLabel: String = "Continuar",
) {
    val tested = status is ConnectionStatus.Ok || status is ConnectionStatus.Error
    OnboardingScaffold(
        step = step,
        title = "Conecta tu base Neon",
        body = null,
        actions = {
            PrimaryButton(continueLabel, onClick = onContinue, enabled = status is ConnectionStatus.Ok)
            SecondaryButton(
                if (tested) "Probar otra vez" else "Probar conexión",
                onClick = onTest,
                enabled = status != ConnectionStatus.Testing && connectionString.isNotBlank(),
            )
        },
    ) {
        ConnectionField(connectionString, onConnectionStringChange, onTest)
        if (status != ConnectionStatus.Idle) StatusCard(status)
    }
}

@Composable
private fun ConnectionField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
        // Etiqueta visible (antes solo era hint gris).
        Text("Connection string", style = t.rowTitle, color = c.fg)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = t.mono.copy(color = c.fg, lineHeight = 1.4.em),
            cursorBrush = SolidColor(c.fg),
            visualTransformation = MaskPassword,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Connection string" },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .border(KtpjDimens.borderField, c.fg, KtpjShapes.row)
                        .padding(KtpjDimens.cardPadding),
                ) {
                    if (value.isEmpty()) {
                        Text("postgresql://usuario:contraseña@host/neondb", style = t.mono, color = c.muted)
                    }
                    inner()
                }
            },
        )
        Text("Neon › Dashboard › Connect. Se guarda solo en este teléfono.", style = t.caption, color = c.muted)
    }
}

/** Tarjeta de resultado: círculo de 32dp (verde ✓ / accent !) + título y detalle. */
@Composable
private fun StatusCard(status: ConnectionStatus) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val (title, detail) = when (status) {
        is ConnectionStatus.Ok -> "Conexión correcta" to "${status.columnCount} columnas encontradas · ${status.millis} ms"
        is ConnectionStatus.Error -> "No se pudo conectar" to status.message
        else -> "Probando conexión…" to "Consultando las columnas en Neon"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card, KtpjShapes.card)
            .padding(KtpjDimens.cardPadding)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            is ConnectionStatus.Ok -> StatusCircle("✓", c.green)
            is ConnectionStatus.Error -> StatusCircle("!", c.accent)
            else -> CircularProgressIndicator(Modifier.size(32.dp), color = c.fg, strokeWidth = 3.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = t.rowTitle, color = if (status is ConnectionStatus.Error) c.accent else c.fg)
            Text(detail, style = t.caption, color = c.muted)
        }
    }
}

@Composable
private fun StatusCircle(glyph: String, color: Color) {
    Box(
        Modifier
            .size(32.dp)
            .background(color, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = KtpjTheme.type.rowTitle.copy(fontWeight = FontWeight.ExtraBold), color = KtpjTheme.colors.bg)
    }
}

/** Enmascara la contraseña (entre "usuario:" y "@") con un • por carácter; las posiciones no cambian. */
private object MaskPassword : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val scheme = s.indexOf("://")
        val colon = if (scheme >= 0) s.indexOf(':', scheme + 3) else -1
        val at = s.lastIndexOf('@')
        if (colon < 0 || at <= colon + 1) return TransformedText(text, OffsetMapping.Identity)
        val masked = s.substring(0, colon + 1) + "•".repeat(at - colon - 1) + s.substring(at)
        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }
}

private const val SAMPLE = "postgresql://ktpj:s3cr3t@ep-cool-sun.neon.tech/ktpj"

@Preview(name = "02 Neon — Claro, OK", widthDp = 360, heightDp = 760)
@Composable
private fun NeonConnectOkPreview() = KtpjTheme(ThemeMode.LIGHT) {
    NeonConnectScreen(SAMPLE, ConnectionStatus.Ok(5, 182), {}, {}, {})
}

@Preview(name = "02 Neon — Oscuro, error", widthDp = 360, heightDp = 760)
@Composable
private fun NeonConnectErrorPreview() = KtpjTheme(ThemeMode.DARK) {
    NeonConnectScreen(SAMPLE, ConnectionStatus.Error("Timeout al conectar con Neon"), {}, {}, {})
}
