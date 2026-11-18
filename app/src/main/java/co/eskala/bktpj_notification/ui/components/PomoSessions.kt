package co.eskala.bktpj_notification.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.PomoEnd
import co.eskala.bktpj_notification.PomoLog
import co.eskala.bktpj_notification.PomoSession
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HOUR = SimpleDateFormat("h:mm a", Locale("es", "CO"))
private val DAY = SimpleDateFormat("EEE d MMM", Locale("es", "CO"))

fun clockLabel(ms: Long): String = synchronized(HOUR) { HOUR.format(Date(ms)) }
fun rangeLabel(s: PomoSession): String = "${clockLabel(s.start)} – ${clockLabel(s.end)}"

fun startOfDay(ms: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun statusMark(s: PomoEnd) = when (s) { PomoEnd.COMPLETED -> "🍅"; PomoEnd.SKIPPED -> "⏭"; PomoEnd.STOPPED -> "◐" }

/**
 * Enfoques anotados de una tarea: "🍅 9:10 a. m. – 9:35 a. m. · 25 min". [todayOnly] para el pomodoro;
 * si no, agrupa por día (al editar la tarea). [ink] permite texto blanco sobre el fondo del pomodoro.
 */
@Composable
fun PomoSessionsList(taskId: String, todayOnly: Boolean, ink: Color? = null, soft: Color? = null, max: Int = 30) {
    val t = KtpjTheme.type
    val fg = ink ?: KtpjTheme.colors.fg
    val muted = soft ?: KtpjTheme.colors.muted
    val all by PomoLog.sessions.collectAsState()
    val today = startOfDay()
    val list = all.filter { it.taskId == taskId && (!todayOnly || it.start >= today) }.sortedByDescending { it.start }.take(max)
    if (list.isEmpty()) return
    val total = list.sumOf { it.minutes }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (todayOnly) "HOY EN ESTA TAREA" else "REGISTRO DE POMODOROS") + " · ${durationLabel(total)}",
            style = t.overline,
            color = muted,
        )
        var lastDay: Long? = null
        list.forEach { s ->
            val d = startOfDay(s.start)
            if (!todayOnly && d != lastDay) {
                Text(synchronized(DAY) { DAY.format(Date(s.start)) }.replaceFirstChar { it.uppercase() }, style = t.captionStrong, color = muted)
                lastDay = d
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statusMark(s.status), style = t.secondary, color = fg)
                Text(rangeLabel(s), style = t.secondary, color = fg, modifier = Modifier.weight(1f))
                Text("${s.minutes} min" + if (s.status != PomoEnd.COMPLETED) " · ${s.status.label}" else "", style = t.captionStrong, color = muted)
            }
        }
    }
}

fun durationLabel(min: Int) = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}
