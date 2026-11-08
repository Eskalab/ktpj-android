package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.Pomodoro
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

/** "−  3  +" con el tiempo de enfoque que suma. 0 = sin estimar. */
@Composable
fun PomosField(value: Int, onChange: (Int) -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val cfg by Pomodoro.config.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Stepper(
            value = value,
            label = if (value == 0) "Sin estimar" else "🍅".repeat(value.coerceAtMost(6)) + if (value > 6) " $value" else "",
            onChange = onChange,
            range = 0..20,
            description = "Pomodoros estimados",
        )
        Text(
            if (value == 0) "Opcional. Cada pomodoro son ${cfg.focus} min de enfoque."
            else "$value × ${cfg.focus} min ≈ ${duration(value * cfg.focus)} de enfoque. Al empezar la tarea ves cuántos llevas.",
            style = t.caption,
            color = c.muted,
        )
    }
}

private fun duration(min: Int) = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}

/** Botones − / + con el valor en el medio. */
@Composable
fun Stepper(value: Int, label: String, onChange: (Int) -> Unit, range: IntRange, description: String) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { stateDescription = "$description: $label" },
    ) {
        StepButton("−", "Menos", value > range.first) { onChange(value - 1) }
        Text(label, style = t.rowTitle, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        StepButton("+", "Más", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = KtpjTheme.colors
    Box(
        Modifier
            .size(KtpjDimens.minTouchTarget)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(KtpjShapes.pill)
            .border(KtpjDimens.borderCard, c.border, KtpjShapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = KtpjTheme.type.amount, color = c.fg)
    }
}

/** Ajustes → Pomodoro: duraciones y cada cuántos enfoques toca descanso largo. */
@Composable
fun PomodoroSettings(onChange: (co.eskala.bktpj_notification.PomoConfig) -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val cfg by Pomodoro.config.collectAsState()
    @Composable
    fun line(name: String, value: Int, unit: String, range: IntRange, set: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = KtpjDimens.minTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = t.rowTitle, color = c.fg, modifier = Modifier.weight(1f))
            Stepper(value, "$value $unit", set, range, name)
        }
    }
    line("Enfoque", cfg.focus, "min", 5..90) { onChange(cfg.copy(focus = it)) }
    line("Descanso", cfg.short, "min", 1..30) { onChange(cfg.copy(short = it)) }
    line("Descanso largo", cfg.long, "min", 5..60) { onChange(cfg.copy(long = it)) }
    line("Largo cada", cfg.longEvery, "🍅", 2..8) { onChange(cfg.copy(longEvery = it)) }
    Text(
        "Se aplica al siguiente pomodoro; el que está corriendo termina con su tiempo.",
        style = t.caption,
        color = c.muted,
    )
}
