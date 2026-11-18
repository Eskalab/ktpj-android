package co.eskala.bktpj_notification.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.eskala.bktpj_notification.PomoMode
import co.eskala.bktpj_notification.PomoState
import co.eskala.bktpj_notification.Pomodoro
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import kotlinx.coroutines.delay

/** Fondo de pantalla completa según el modo: cálido para enfocarse, verde y azul para descansar. */
@Composable
fun pomoColor(mode: PomoMode): Color {
    val dark = KtpjTheme.colors.isDark
    return when (mode) {
        PomoMode.FOCUS -> if (dark) Color(0xFF7A2E12) else Color(0xFFB8461B)
        PomoMode.SHORT -> if (dark) Color(0xFF135240) else Color(0xFF1F7A5A)
        PomoMode.LONG -> if (dark) Color(0xFF1C3D70) else Color(0xFF2B5FAF)
    }
}

/**
 * Pomodoro de la tarea en curso: solo el tiempo. Pestañas de modo, cuenta atrás grande, un botón
 * (Empezar / Pausar), saltar, y el contador de pomodoros. La pantalla no se apaga mientras está abierta.
 */
@Composable
fun PomodoroScreen(
    state: PomoState,
    onBack: () -> Unit,
    onTaskDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val t = KtpjTheme.type
    val bg by animateColorAsState(pomoColor(state.mode), tween(600), label = "pomoBg")
    val white = Color.White
    val soft = white.copy(alpha = 0.78f)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.endsAt) {
        while (state.running) {
            now = System.currentTimeMillis()
            if (state.remaining(now) == 0L) { Pomodoro.finishIfDue(ctx); break }
            delay(250)
        }
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val remaining = state.remaining(now)
    val cfg by Pomodoro.config.collectAsState()
    val counts by Pomodoro.counts.collectAsState()
    val doneHere = counts[state.taskId] ?: 0

    Column(
        Modifier.fillMaxSize().background(bg).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction("‹ Volver", soft, onBack)
            Spacer(Modifier.weight(1f))
            TextAction("Cerrar pomodoro", soft) { Pomodoro.stop(ctx); onBack() }
        }

        Spacer(Modifier.height(20.dp))
        state.taskKey?.let { Text(it, style = t.mono, color = soft) }
        Text(
            state.taskTitle,
            style = t.zoneTitle,
            color = white,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )

        Spacer(Modifier.weight(1f))

        // Tarjeta del temporizador
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KtpjShapes.notification)
                .background(white.copy(alpha = 0.12f))
                .padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PomoMode.entries.forEach { m ->
                    val on = m == state.mode
                    Text(
                        m.label,
                        style = t.captionStrong,
                        color = white,
                        modifier = Modifier
                            .clip(KtpjShapes.pill)
                            .background(if (on) white.copy(alpha = 0.22f) else Color.Transparent)
                            .semantics { selected = on }
                            .clickable(role = Role.Tab) { if (!on) Pomodoro.select(ctx, m) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Text(
                Pomodoro.format(remaining),
                style = t.hero.copy(fontSize = 92.sp, lineHeight = 92.sp, fontFeatureSettings = "tnum"),
                color = white,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Quedan ${remaining / 60_000} minutos"
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .width(190.dp)
                        .clip(KtpjShapes.row)
                        .background(white)
                        .clickable(role = Role.Button) { if (state.running) Pomodoro.pause(ctx) else Pomodoro.start(ctx) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.running) "PAUSAR" else if (remaining < state.mode.fullMs) "SEGUIR" else "EMPEZAR",
                        style = t.amount.copy(letterSpacing = 1.5.sp),
                        color = bg,
                    )
                }
                if (state.running || remaining < state.mode.fullMs) {
                    Text(
                        "⏭",
                        style = t.amount,
                        color = white,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(KtpjShapes.pill)
                            .clickable(role = Role.Button, onClickLabel = "Saltar al siguiente") { Pomodoro.skip(ctx) }
                            .padding(10.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // Ciclo: 4 puntos, uno por pomodoro de enfoque antes del descanso largo
        val inCycle = state.round % cfg.longEvery
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            repeat(cfg.longEvery) { i ->
                val filled = i < inCycle || (inCycle == 0 && state.round > 0 && state.mode == PomoMode.LONG)
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(KtpjShapes.pill)
                        .background(if (filled) white else Color.Transparent)
                        .border(1.5.dp, white, KtpjShapes.pill)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (state.mode) {
                PomoMode.FOCUS -> "Pomodoro ${doneHere + 1}" + (state.estimate?.let { " de $it" } ?: "") + " · a enfocarse"
                PomoMode.SHORT -> "$doneHere${state.estimate?.let { " de $it" } ?: ""} hecho${if (doneHere == 1) "" else "s"} · respira un momento"
                PomoMode.LONG -> "$doneHere${state.estimate?.let { " de $it" } ?: ""} hechos · descanso largo, te lo ganaste"
            },
            style = t.secondary,
            color = soft,
        )

        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(KtpjShapes.pill)
                .border(1.5.dp, white.copy(alpha = 0.6f), KtpjShapes.pill)
                .clickable(role = Role.Button, onClick = onTaskDone)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (state.habit) "✓ Listo por hoy" else "✓ Terminé la tarea", style = t.button, color = white)
        }
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = KtpjTheme.type.captionStrong,
        color = color,
        modifier = Modifier.clip(KtpjShapes.pill).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

/** Barra fina sobre la navegación cuando hay un pomodoro abierto: modo, tiempo y tarea. Tocar abre la pantalla. */
@Composable
fun PomodoroBar(state: PomoState, onOpen: () -> Unit) {
    val t = KtpjTheme.type
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.endsAt) {
        while (state.running) { now = System.currentTimeMillis(); delay(1000) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(pomoColor(state.mode))
            .clickable(role = Role.Button, onClickLabel = "Abrir pomodoro", onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(Pomodoro.format(state.remaining(now)), style = t.amountList.copy(fontFeatureSettings = "tnum"), color = Color.White)
        Text(
            (if (state.running) state.mode.label else "En pausa") + " · " + state.taskTitle,
            style = t.secondary,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = t.amount, color = Color.White)
    }
}
