package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.eskala.bktpj_notification.Habit
import co.eskala.bktpj_notification.HabitDates
import co.eskala.bktpj_notification.HabitPause
import co.eskala.bktpj_notification.ui.components.Chip
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * "⏸ Pausar": hasta cuándo (Mañana · 1 semana · Elegir fecha… · Sin fecha fija) y motivo opcional.
 * [onPause] recibe el último día en pausa (inclusive; null = sin fecha fija) y el motivo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitPauseDialog(habit: Habit, onPause: (end: String?, reason: String?) -> Unit, onDismiss: () -> Unit) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val today = HabitDates.today()
    val tomorrow = HabitDates.plusDays(today, 1)
    val week = HabitDates.plusDays(today, 7)
    // "tomorrow" | "week" | "date" | "open"
    var choice by rememberSaveable { mutableStateOf("week") }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val end: String? = when (choice) {
        "tomorrow" -> tomorrow
        "week" -> week
        "date" -> picked
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.bg, KtpjShapes.notification)
                .verticalScroll(rememberScrollState())
                .padding(KtpjDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
        ) {
            Text(habit.title, style = t.secondary, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("⏸ Pausar hábito", style = t.screenTitle, color = c.fg, modifier = Modifier.semantics { heading() })

            Text("¿HASTA CUÁNDO?", style = t.overline, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Mañana", choice == "tomorrow", { choice = "tomorrow" })
                Chip("1 semana", choice == "week", { choice = "week" })
                Chip(
                    if (choice == "date" && picked != null) HabitDates.short(picked!!, today) else "Elegir fecha…",
                    choice == "date",
                    { picking = true },
                )
                Chip("Sin fecha fija", choice == "open", { choice = "open" })
            }
            Text(
                when {
                    end == null && choice == "date" -> "Elige el último día de la pausa."
                    end == null -> "Hasta que lo reanudes."
                    else -> "En pausa hasta el ${HabitDates.short(end, today)}; vuelve el ${HabitDates.short(HabitDates.plusDays(end, 1), today)}."
                },
                style = t.caption,
                color = c.muted,
            )

            Text("MOTIVO (OPCIONAL)", style = t.overline, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitPause.REASONS.forEach { (code, label) ->
                    Chip(label, reason == code, { reason = if (reason == code) null else code })
                }
            }

            Text(
                "Mientras está en pausa no sale en Inicio, no te avisa y no rompe la racha. Si lo haces igual, cuenta.",
                style = t.caption,
                color = c.muted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall)) {
                PrimaryButton("⏸ Pausar", enabled = !(choice == "date" && picked == null), onClick = { onPause(end, reason) })
                SecondaryButton("Cancelar", onClick = onDismiss)
            }
        }
    }

    if (picking) {
        PauseEndPicker(
            initial = picked ?: week,
            minDay = today,
            onPick = { day -> picking = false; picked = day; choice = "date" },
            onDismiss = { picking = false },
        )
    }
}

/** Calendario para el último día de la pausa (desde hoy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseEndPicker(initial: String, minDay: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val utc = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val minMillis = remember(minDay) { utc.parse(minDay)!!.time }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utc.parse(initial)?.time,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(utc.format(Date(it))) } ?: onDismiss() },
            ) { Text("Listo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = state, showModeToggle = false, title = { Text("Pausar hasta", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
