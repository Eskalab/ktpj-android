package co.eskala.bktpj_notification.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.eskala.bktpj_notification.Habit
import co.eskala.bktpj_notification.HabitDates
import co.eskala.bktpj_notification.Habits
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import kotlinx.coroutines.launch

/**
 * Fila de hábito: círculo para marcar hoy, título (tachado al cumplir), meta y racha 🔥.
 * Si tiene meta con unidad, al marcar pregunta cuánto hiciste (con la meta ya puesta).
 */
@Composable
fun HabitRow(habit: Habit, onOpen: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    val done = habit.doneToday
    val due = habit.dueToday
    val streak = habit.streak()

    fun check(amount: Int?) = scope.launch {
        val r = Habits.check(ctx, habit.id, amount)
        if (r.isFailure) Toast.makeText(ctx, "No se pudo marcar: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
    }
    fun toggle() {
        if (done) scope.launch {
            val r = Habits.uncheck(ctx, habit.id)
            if (r.isFailure) Toast.makeText(ctx, "No se pudo: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        } else if (habit.goal != null && !habit.unit.isNullOrBlank()) asking = true
        else check(habit.goal)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (due || done) 1f else 0.55f)
            .clip(KtpjShapes.card)
            .background(if (c.isDark) c.bg else c.card)
            .then(if (c.isDark) Modifier.border(1.dp, c.line, KtpjShapes.card) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Ver hábito", onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(KtpjDimens.minTouchTarget)
                .clip(KtpjShapes.pill)
                .clickable(role = Role.Checkbox, onClickLabel = if (done) "Desmarcar hoy" else "Marcar hoy", onClick = ::toggle)
                .semantics { stateDescription = if (done) "Hecho hoy" else "Pendiente hoy" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(KtpjShapes.pill)
                    .then(if (done) Modifier.background(c.green) else Modifier.border(2.dp, if (due) c.green else c.muted, KtpjShapes.pill)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Text("✓", style = t.captionStrong, color = if (c.isDark) c.bg else c.invFg)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                habit.title,
                style = t.rowTitle.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) c.muted else c.fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val todayAmount = habit.checks[HabitDates.today()]
            val meta = listOfNotNull(
                when {
                    done && todayAmount != null && habit.unit != null -> "$todayAmount ${habit.unit} hoy"
                    else -> habit.goalLabel
                },
                if (!due && !done) "hoy no toca" else null,
                if (streak > 0) "🔥 $streak" else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = t.captionStrong, color = c.muted)
        }
        Text("›", style = t.amount, color = c.muted)
    }

    if (asking) {
        AmountDialog(
            habit = habit,
            onSave = { asking = false; check(it) },
            onDismiss = { asking = false },
        )
    }
}

/** "¿Cuánto hiciste hoy?": − / + con la meta como valor inicial. */
@Composable
fun AmountDialog(habit: Habit, onSave: (Int) -> Unit, onDismiss: () -> Unit, initial: Int? = null) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    var amount by rememberSaveable { mutableStateOf(initial ?: habit.goal ?: 1) }
    val step = if ((habit.goal ?: 0) >= 30) 5 else 1
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(c.bg, KtpjShapes.notification).padding(KtpjDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(habit.title, style = t.secondary, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("¿Cuánto hiciste?", style = t.screenTitle, color = c.fg)
            Stepper(amount, "$amount ${habit.unit.orEmpty()}", { amount = it.coerceAtLeast(1) }, 1..9999, "Cantidad")
            // Atajos de un toque alrededor de la meta, para no tocar + muchas veces.
            habit.goal?.let { g ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(g / 2, g, g + g / 2, g * 2).filter { it > 0 }.distinct().forEach { v ->
                        Chip("$v", amount == v, { amount = v })
                    }
                }
            }
            if (step > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("−$step", false, { amount = (amount - step).coerceAtLeast(1) })
                Chip("+$step", false, { amount += step })
            }
            Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
                PrimaryButton("✓ Listo", onClick = { onSave(amount) })
                SecondaryButton("Cancelar", onClick = onDismiss)
            }
        }
    }
}
