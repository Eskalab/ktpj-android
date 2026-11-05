package co.eskala.bktpj_notification.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode

/**
 * 01 Onboarding — Acceso a notificaciones.
 * [notGrantedYet] se muestra cuando el usuario toca "Ya lo activé" pero el permiso sigue apagado.
 */
@Composable
fun NotificationAccessScreen(
    sourcePackage: String,
    notGrantedYet: Boolean,
    onOpenSettings: () -> Unit,
    onAlreadyEnabled: () -> Unit,
) {
    OnboardingScaffold(
        step = 1,
        title = "Dale acceso a las notificaciones",
        body = "La app solo lee las notificaciones de Nu Colombia y crea una tarjeta por cada movimiento.",
        actions = {
            if (notGrantedYet) {
                Text(
                    "Todavía no está activado. Busca Spur en la lista y enciéndelo.",
                    style = KtpjTheme.type.secondary,
                    color = KtpjTheme.colors.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            PrimaryButton("Abrir ajustes de Android", onClick = onOpenSettings)
            SecondaryButton("Ya lo activé", onClick = onAlreadyEnabled)
        },
    ) {
        CaptureIllustration(sourcePackage)
    }
}

/** Ilustración con UI real: notificación de Nu → tarjeta en Inbox + Hoy. */
@Composable
private fun CaptureIllustration(sourcePackage: String) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Ejemplo: la notificación de Nu \"Compraste \$45.900 en Rappi\" " +
                "se convierte en una tarjeta en Inbox y Hoy por −45.900"
        },
        verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(KtpjDimens.borderCard, c.border, KtpjShapes.card)
                .padding(KtpjDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Nu · $sourcePackage", style = t.captionSemi, color = c.muted)
                Text("ahora", style = t.captionSemi, color = c.muted)
            }
            Text("Compraste \$45.900 en Rappi", style = t.rowTitle, color = c.fg)
        }

        Text(
            "↓",
            style = t.rowTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .background(c.accentSoft, KtpjShapes.card)
                .padding(KtpjDimens.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("TARJETA EN INBOX + HOY", style = t.overline, color = c.accentText)
                Text("Rappi", style = t.rowTitle, color = c.fg)
            }
            Text("−45.900", style = t.amount, color = c.fg)
        }
    }
}

@Preview(name = "01 Acceso — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun NotificationAccessLightPreview() = KtpjTheme(ThemeMode.LIGHT) {
    NotificationAccessScreen("com.nu.production", notGrantedYet = false, onOpenSettings = {}, onAlreadyEnabled = {})
}

@Preview(name = "01 Acceso — Oscuro, sin permiso", widthDp = 360, heightDp = 760)
@Composable
private fun NotificationAccessDarkPreview() = KtpjTheme(ThemeMode.DARK) {
    NotificationAccessScreen("com.nu.production", notGrantedYet = true, onOpenSettings = {}, onAlreadyEnabled = {})
}
