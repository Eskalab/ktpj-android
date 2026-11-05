package co.eskala.bktpj_notification.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme

const val ONBOARDING_STEPS = 3

/**
 * Estructura común de los 3 pasos: progreso, overline "PASO N DE 3", título, cuerpo,
 * contenido propio del paso (con scroll) y acciones fijas abajo.
 * Con [step] = null (pantallas abiertas desde Ajustes) no se muestran progreso ni overline.
 */
@Composable
fun OnboardingScaffold(
    step: Int?,
    title: String,
    body: String?,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KtpjDimens.screenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGap),
        ) {
            if (step != null) {
                StepProgress(step)
                Text("PASO $step DE $ONBOARDING_STEPS", style = t.overlineLarge, color = c.muted)
            }
            Text(title, style = t.onboardingTitle, color = c.fg)
            if (body != null) Text(body, style = t.body, color = c.muted)
            content()
        }
        Column(
            Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
            content = actions,
        )
    }
}

/** 3 barras de 4dp (gap 6dp): pasos hechos/actual en accent, el resto en card. */
@Composable
internal fun StepProgress(step: Int) {
    val c = KtpjTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Paso $step de $ONBOARDING_STEPS" },
        horizontalArrangement = Arrangement.spacedBy(KtpjDimens.progressGap),
    ) {
        repeat(ONBOARDING_STEPS) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(KtpjDimens.progressHeight)
                    .background(if (i < step) c.accent else c.card, KtpjShapes.progress),
            )
        }
    }
}
