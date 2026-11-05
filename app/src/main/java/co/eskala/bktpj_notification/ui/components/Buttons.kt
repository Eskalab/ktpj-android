package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/** Botón primario: ancho completo, pill invBg, 16sp/700. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = KtpjTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(KtpjShapes.pill)
            .background(c.invBg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = KtpjDimens.buttonPaddingVertical),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KtpjTheme.type.button, color = c.invFg)
    }
}

/** Botón secundario: solo texto 15sp/700, sin fondo, padding 12dp. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = KtpjDimens.minTouchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(KtpjDimens.secondaryButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KtpjTheme.type.buttonSecondary, color = KtpjTheme.colors.fg)
    }
}
