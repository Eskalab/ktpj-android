package co.eskala.bktpj_notification.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Radios del handoff. */
object KtpjShapes {
    val pill = CircleShape                      // botones, chips, pills (999)
    val card = RoundedCornerShape(18.dp)        // tarjetas
    val row = RoundedCornerShape(14.dp)         // filas de columnas, campo de texto
    val notification = RoundedCornerShape(22.dp)
    val progress = RoundedCornerShape(2.dp)     // barras de progreso
}

/** Espaciados y alturas del handoff. */
object KtpjDimens {
    val screenPadding = 20.dp
    val blockGap = 20.dp
    val blockGapSmall = 16.dp
    val cardGap = 10.dp
    val cardGapSmall = 8.dp
    val cardPadding = 14.dp

    val buttonPaddingVertical = 17.dp
    val secondaryButtonPadding = 12.dp
    val minTouchTarget = 44.dp

    val bottomNavHeight = 64.dp
    val progressHeight = 4.dp
    val progressGap = 6.dp
    val dropZoneMinHeight = 76.dp
    val statusDot = 8.dp

    val borderCard = 1.5.dp
    val borderField = 2.dp
    val divider = 1.dp
}
