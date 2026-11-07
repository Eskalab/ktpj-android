package co.eskala.bktpj_notification.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Tokens de color del rediseño "Tablero" (ver handoff, tabla de colores). */
@Immutable
data class KtpjColors(
    val bg: Color,          // fondo de pantalla
    val fg: Color,          // texto principal
    val muted: Color,       // texto secundario
    val card: Color,        // superficies / tarjetas rellenas
    val line: Color,        // divisores, borde de bottom nav
    val border: Color,      // borde 1.5dp de tarjetas de transacción, chips
    val accent: Color,      // acento, errores, destino Inbox
    val onAccent: Color,    // texto sobre accent
    val accentInk: Color,   // texto del botón "Reintentar" (fondo onAccent)
    val accentSoft: Color,  // tile Inbox
    val accentText: Color,  // etiqueta sobre accentSoft
    val green: Color,       // abonos (+), "✓ Guardado"
    val invBg: Color,       // botón primario, chip "Activo", chip seleccionado
    val invFg: Color,
    val dot: Color,         // punto de estado activo
    val shade: Color,       // fondo de pantalla de bloqueo / panel de notificaciones
    val blue: Color,        // tarea "en curso"
    val blueSoft: Color,    // fondo suave de "en curso"
    val chartVariable: Color, // gráficos: gastos variables (validado CVD con chartFixed)
    val chartFixed: Color,    // gráficos: gastos fijos
    val isDark: Boolean,
)

val LightKtpjColors = KtpjColors(
    bg         = Color(0xFFFFFFFF),
    fg         = Color(0xFF111111),
    muted      = Color(0xFF555555),
    card       = Color(0xFFF2F2F2),
    line       = Color(0xFFEEEEEE),
    border     = Color(0xFF111111),
    accent     = Color(0xFFCF3F0A),
    onAccent   = Color(0xFFFFFFFF),
    accentInk  = Color(0xFF111111),
    accentSoft = Color(0xFFFFF1EA),
    accentText = Color(0xFFA83409),
    green      = Color(0xFF137A3A),
    invBg      = Color(0xFF111111),
    invFg      = Color(0xFFFFFFFF),
    dot        = Color(0xFF3BD16F),
    shade      = Color(0xFFE9E9EB),
    blue       = Color(0xFF1F5FD1),
    blueSoft   = Color(0xFFEAF1FF),
    chartVariable = Color(0xFFEB6834),
    chartFixed    = Color(0xFF2A78D6),
    isDark     = false,
)

val DarkKtpjColors = KtpjColors(
    bg         = Color(0xFF111113),
    fg         = Color(0xFFF4F4F5),
    muted      = Color(0xFFA1A1AA),
    card       = Color(0xFF1C1C20),
    line       = Color(0xFF26262B),
    border     = Color(0xFF3F3F46),
    accent     = Color(0xFFFF7A45),
    onAccent   = Color(0xFF111113),
    accentInk  = Color(0xFFF4F4F5),
    accentSoft = Color(0xFF3A1C10),
    accentText = Color(0xFFFF9C73),
    green      = Color(0xFF5EDB8C),
    invBg      = Color(0xFFF4F4F5),
    invFg      = Color(0xFF111113),
    dot        = Color(0xFF1E9E4E),
    shade      = Color(0xFF000000),
    blue       = Color(0xFF7AA7FF),
    blueSoft   = Color(0xFF14213D),
    chartVariable = Color(0xFFD95926),
    chartFixed    = Color(0xFF3987E5),
    isDark     = true,
)
